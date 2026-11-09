#!/usr/bin/env python3
"""Private, offline backup and fresh-scope restore. No credentials on stdout."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import zlib
import portfolio_db as pg

ROLES = ('portfolio_owner', 'portfolio_reader', 'portfolio_auth', 'portfolio_writer', 'portfolio_identity', 'portfolio_credentials')
TABLES = ('admin_account', 'admin_identity_audit', 'admin_login_bucket', 'category', 'flyway_schema_history',
          'image_asset', 'project', 'project_audit', 'project_evidence', 'project_media', 'project_technology',
          'spring_session', 'spring_session_attributes', 'taxonomy_audit', 'technology')
SESSIONS = ('spring_session', 'spring_session_attributes')
OBJECT = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg)')
SHA = re.compile(r'[0-9a-f]{64}')
MAX_BYTES = 512 * 1024 * 1024
MAX_OBJECTS = 10000

class BackupProblem(RuntimeError):
    pass

def require(value, message):
    if not value:
        raise BackupProblem(message)

def private_path(path):
    """Check unresolved ancestry first; resolve() alone hides symlink traversal."""
    raw = Path(os.path.abspath(path))
    for parent in (raw, *raw.parents):
        require(not parent.is_symlink(), 'Symbolic paths are refused')
    result = pg.private(raw)
    require(result != pg.ROOT / 'work', 'Choose a dedicated private subdirectory')
    return result

def file_guard(path, maximum=MAX_BYTES):
    private_path(path)
    require(path.is_file() and not path.is_symlink(), 'Regular private file required')
    require(0 <= path.stat().st_size <= maximum, 'Artifact exceeds supported size')

def digest(path):
    file_guard(path)
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            h.update(block)
    return h.hexdigest()

def write_json(path, value):
    with path.open('x') as stream:
        os.chmod(path, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n'); stream.flush(); os.fsync(stream.fileno())

def scope(directory):
    directory = private_path(directory)
    require(directory.is_dir() and not directory.stat().st_mode & 0o077, 'Private scope directory required')
    d, e, v = pg.configuration(directory)
    require(v.get('PORTFOLIO_MEDIA_ROOT') == str(d / 'media'), 'Scope-local media root required')
    for key, role in [('MIGRATION', 'portfolio_owner'), ('DB', 'portfolio_reader'), ('AUTH', 'portfolio_auth'),
                      ('WRITER', 'portfolio_writer'), ('IDENTITY', 'portfolio_identity'), ('CREDENTIALS', 'portfolio_credentials')]:
        require(v.get('PORTFOLIO_' + key + '_USER') == role, 'Complete standard runtime roles required')
    pg.container(v)
    return d, e, v

@contextmanager
def scope_lock(directory):
    path = directory / '.backup-lock'
    fd = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        try: fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError: raise BackupProblem('Another backup/recovery holds this scope')
        yield
    finally: os.close(fd)

def port_unused(port):
    require(isinstance(port, int) and 1024 <= port <= 65535, 'Explicit valid loopback port required')
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try: probe.bind(('127.0.0.1', port))
        except OSError: raise BackupProblem('Application/target port is occupied; stop only your own application')

def roles_state(d, v):
    rows = json.loads(pg.sql(d, v, "SELECT jsonb_object_agg(rolname,rolcanlogin)::text FROM pg_roles WHERE rolname IN ('" + "','".join(ROLES) + "');"))
    require(set(rows) == set(ROLES) and all(type(x) is bool for x in rows.values()), 'Expected isolated roles required')
    return rows

def set_access(d, v, state):
    require(set(state) == set(ROLES) and all(type(x) is bool for x in state.values()), 'Invalid access recovery journal')
    pg.sql(d, v, 'BEGIN; SET LOCAL lock_timeout=\'3s\'; ' + ' '.join('ALTER ROLE ' + role + (' LOGIN;' if state[role] else ' NOLOGIN;') for role in ROLES) + ' COMMIT;')

def disconnected(d, v):
    count = pg.sql(d, v, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid();")
    require(count == '0', 'Database clients are still connected; no clients are terminated by this tool')

def recover_access(directory):
    d, _, v = scope(directory)
    with scope_lock(d):
        path = d / '.backup-access.json'
        file_guard(path, 4096)
        journal = json.loads(path.read_text())
        require(set(journal) == {'scope', 'roles'} and journal['scope'] == v['COMPOSE_PROJECT_NAME'], 'Access recovery journal does not match this scope')
        set_access(d, v, journal['roles']); path.unlink()

def migrations():
    result = {}
    for path in sorted((pg.ROOT / 'backend/src/main/resources/db/migration').glob('V*.sql')):
        crc = 0
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            crc = zlib.crc32(line.encode('utf-8'), crc)
        if crc >= 2**31: crc -= 2**32
        result[path.name] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'checksum': crc}
    return result

def database_state(d, v):
    found = json.loads(pg.sql(d, v, "SELECT jsonb_agg(tablename ORDER BY tablename)::text FROM pg_tables WHERE schemaname='public';"))
    require(found == list(TABLES), 'Unsupported catalog schema; update backup contract explicitly')
    history = json.loads(pg.sql(d, v, 'SELECT jsonb_agg(jsonb_build_object(\'script\',script,\'checksum\',checksum,\'success\',success) ORDER BY installed_rank)::text FROM flyway_schema_history;'))
    known = migrations()
    require(len(history) == len(known) and all(x['success'] and x['script'] in known and x['checksum'] == known[x['script']]['checksum'] for x in history), 'Migration history does not match immutable repository migrations')
    result = {}
    for table in TABLES:
        count = int(pg.sql(d, v, 'SELECT count(*) FROM ' + table + ';'))
        require(count <= 100000, 'Table exceeds supported offline verification size')
        raw = pg.sql(d, v, "SELECT COALESCE(jsonb_agg(to_jsonb(x) ORDER BY to_jsonb(x)::text),'[]'::jsonb)::text FROM " + table + ' x;')
        result[table] = {'rows': count, 'sha256': hashlib.sha256(raw.encode()).hexdigest()}
    return result

def permission_inventory(d, v):
    # Names and logical grants only: no OIDs, credentials or session contents in the manifest.
    raw = pg.sql(d, v, """SELECT jsonb_build_object(
      'tables', (SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) FROM information_schema.table_privileges t WHERE table_schema='public'),
      'columns', (SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) FROM information_schema.column_privileges t WHERE table_schema='public'),
      'schema', (SELECT COALESCE(jsonb_agg(jsonb_build_object('grantor',pg_get_userbyid(a.grantor),'grantee',CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END,'privilege',a.privilege_type,'grantable',a.is_grantable) ORDER BY a.privilege_type,a.grantee),'[]'::jsonb) FROM pg_namespace n CROSS JOIN LATERAL aclexplode(n.nspacl) a WHERE n.nspname='public'),
      'database',(SELECT COALESCE(jsonb_agg(jsonb_build_object('grantor',pg_get_userbyid(a.grantor),'grantee',CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END,'privilege',a.privilege_type,'grantable',a.is_grantable) ORDER BY a.privilege_type,a.grantee),'[]'::jsonb) FROM pg_database n CROSS JOIN LATERAL aclexplode(n.datacl) a WHERE n.datname=current_database()),
      'defaults',(SELECT COALESCE(jsonb_agg(jsonb_build_object('owner',pg_get_userbyid(n.defaclrole),'schema',s.nspname,'kind',n.defaclobjtype,'grantor',pg_get_userbyid(a.grantor),'grantee',CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END,'privilege',a.privilege_type,'grantable',a.is_grantable) ORDER BY a.privilege_type,a.grantee),'[]'::jsonb) FROM pg_default_acl n JOIN pg_namespace s ON s.oid=n.defaclnamespace CROSS JOIN LATERAL aclexplode(n.defaclacl) a WHERE s.nspname='public'),
      'roles',(SELECT jsonb_agg(jsonb_build_object('name',rolname,'superuser',rolsuper,'createdb',rolcreatedb,'createrole',rolcreaterole,'replication',rolreplication,'bypassrls',rolbypassrls,'inherit',rolinherit,'config',rolconfig) ORDER BY rolname) FROM pg_roles WHERE rolname IN ('portfolio_owner','portfolio_reader','portfolio_auth','portfolio_writer','portfolio_identity','portfolio_credentials')),
      'owners',(SELECT jsonb_object_agg(c.relname,pg_get_userbyid(c.relowner)) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','S'))
    )::text;""")
    # Database name differs between demo/test targets; no privilege changes are hidden by this normalization.
    normalized = json.loads(raw)
    for key in ('tables', 'columns'):
        for row in normalized[key]: row.pop('table_catalog', None)
    for key in ('tables', 'columns', 'schema', 'database', 'defaults', 'roles'):
        normalized[key].sort(key=lambda x: json.dumps(x, sort_keys=True))
    return normalized

def permissions(d, v):
    return hashlib.sha256(json.dumps(permission_inventory(d, v), sort_keys=True).encode()).hexdigest()

def asset_rows(d, v):
    return json.loads(pg.sql(d, v, """SELECT COALESCE(jsonb_agg(jsonb_build_object('name',a.id::text||'.'||a.format,'bytes',a.bytes,'sha256',a.sha256,
        'required',a.retired_at IS NULL AND a.purged_at IS NULL,'referenced',EXISTS(SELECT 1 FROM project_media m WHERE m.asset_id=a.id)) ORDER BY a.id),'[]'::jsonb)::text FROM image_asset a;"""))

def media_entries(root):
    private_path(root)
    for path in (root, root / 'objects', root / 'incoming'):
        private_path(path); require(path.is_dir() and not path.stat().st_mode & 0o077, 'Private owned media directories required')
    marker = root / '.portfolio-media-v1'; file_guard(marker, 64)
    require(marker.read_text() == 'portfolio-media-v1\n', 'Owned media marker required')
    require(not list((root / 'incoming').iterdir()), 'Incoming media must be empty before offline backup')
    files = sorted((root / 'objects').iterdir())
    require(len(files) <= MAX_OBJECTS, 'Media count exceeds supported size')
    result = []
    total = 0
    for path in files:
        require(OBJECT.fullmatch(path.name), 'Unexpected media object name')
        file_guard(path, 10485760); size = path.stat().st_size; total += size
        require(total <= MAX_BYTES, 'Media total exceeds supported size')
        result.append({'name': path.name, 'bytes': size, 'sha256': digest(path)})
    return result

def check_assets(assets, objects):
    objects = {x['name']: x for x in objects}
    for row in assets:
        require(not row['referenced'] or row['required'], 'Referenced asset is retired/purged')
        require(not row['required'] or row['name'] in objects, 'Required managed image is missing')
        if row['name'] in objects:
            file = objects[row['name']]
            require(file['bytes'] == row['bytes'] and file['sha256'] == row['sha256'], 'Managed image checksum differs from PostgreSQL metadata')

def pg_binary(d, v, program, args, output=None, input_file=None):
    cmd = pg.DOCKER + ['exec', '-i', pg.container(v), program, '-U', 'portfolio_bootstrap', '-d', v['PORTFOLIO_DATABASE'], '--role=portfolio_owner'] + args
    log = d / (program + '-' + os.urandom(4).hex() + '.log')
    with log.open('xb') as errors:
        log.chmod(0o600)
        with input_file.open('rb') if input_file else open(os.devnull, 'rb') as source:
            if output:
                with output.open('xb') as target:
                    output.chmod(0o600)
                    run = subprocess.run(cmd, env=pg.environment(), stdin=source, stdout=target, stderr=errors, timeout=180)
                    target.flush(); os.fsync(target.fileno())
            else:
                run = subprocess.run(cmd, env=pg.environment(), stdin=source, stdout=subprocess.DEVNULL, stderr=errors, timeout=180)
    require(run.returncode == 0, 'PostgreSQL artifact operation failed; inspect private scope log')

def restore_archive(d, v, archive):
    # pg_dump treats public as the standard existing PG schema, omitting its default PUBLIC USAGE.
    # Keep the new scope's standard schema and omit only CREATE SCHEMA public from the archive TOC.
    log = d / 'restore-toc.log'
    with archive.open('rb') as source, log.open('xb') as errors:
        log.chmod(0o600)
        run = subprocess.run(pg.DOCKER + ['exec', '-i', pg.container(v), 'pg_restore', '--list'],
                             env=pg.environment(), stdin=source, stdout=subprocess.PIPE, stderr=errors, timeout=30)
    require(run.returncode == 0, 'Archive cannot be listed by PostgreSQL')
    lines = run.stdout.decode().splitlines()
    schemas = [i for i, line in enumerate(lines) if re.match(r'^\d+; \d+ \d+ SCHEMA ', line)]
    require(len(schemas) == 1 and re.fullmatch(r'\d+; \d+ \d+ SCHEMA - public portfolio_owner', lines[schemas[0]]), 'Only the owned public schema may be restored')
    lines[schemas[0]] = ';' + lines[schemas[0]]
    toc = d / 'restore-toc.list'; toc.write_text('\n'.join(lines) + '\n'); toc.chmod(0o600)
    # No table should inherit helper grants before archive ACLs run; DEFAULT ACL is restored later.
    pg.sql(d, v, 'SET ROLE portfolio_owner; ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE SELECT ON TABLES FROM portfolio_reader;')
    container = pg.container(v); remote = '/tmp/portfolio-toc-' + os.urandom(8).hex() + '.list'
    subprocess.run(pg.DOCKER + ['cp', str(toc), container + ':' + remote], env=pg.environment(), check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    try:
        pg_binary(d, v, 'pg_restore', ['--exit-on-error', '--single-transaction', '--no-owner', '--use-list=' + remote], input_file=archive)
    finally:
        subprocess.run(pg.DOCKER + ['exec', container, 'rm', '-f', remote], env=pg.environment(), check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)

def backup(directory, output, application_port):
    d, _, v = scope(directory)
    output = private_path(output); require(not output.exists(), 'Backup output already exists; preserve it')
    require(not output.is_relative_to(d), 'Keep backup outside its source scope')
    port_unused(application_port)
    output.mkdir(parents=True, mode=0o700)
    with scope_lock(d):
        journal_path = d / '.backup-access.json'
        require(not journal_path.exists(), 'Pending access recovery; use recover-access first')
        previous = roles_state(d, v)
        write_json(journal_path, {'scope': v['COMPOSE_PROJECT_NAME'], 'roles': previous})
        try:
            set_access(d, v, dict.fromkeys(ROLES, False))
            disconnected(d, v); port_unused(application_port)
            before = database_state(d, v); grant_hash = permissions(d, v)
            assets = asset_rows(d, v); source_media = Path(v['PORTFOLIO_MEDIA_ROOT'])
            objects = media_entries(source_media); check_assets(assets, objects)
            (output / 'objects').mkdir(mode=0o700)
            for row in objects:
                source = source_media / 'objects' / row['name']; target = output / 'objects' / row['name']
                # No tar extraction, recursive glob or attacker-controlled relative paths.
                with source.open('rb') as src, target.open('xb') as dst:
                    target.chmod(0o600)
                    for block in iter(lambda: src.read(65536), b''): dst.write(block)
                    dst.flush(); os.fsync(dst.fileno())
                require(digest(target) == row['sha256'], 'Image changed while copying')
                os.utime(target, ns=(source.stat().st_atime_ns, source.stat().st_mtime_ns))
            dump = output / 'database.dump'
            pg_binary(d, v, 'pg_dump', ['--format=custom', '--schema=public', '--exclude-table-data=public.spring_session', '--exclude-table-data=public.spring_session_attributes'], output=dump)
            disconnected(d, v)
            require(before == database_state(d, v) and grant_hash == permissions(d, v) and objects == media_entries(source_media), 'Source changed during offline backup')
            restored_state = dict(before)
            for table in SESSIONS: restored_state[table] = {'rows': 0, 'sha256': hashlib.sha256(b'[]').hexdigest()}
            manifest = {'format': 'portfolio-offline-v1', 'createdAt': datetime.now(timezone.utc).isoformat(), 'sourceScope': v['COMPOSE_PROJECT_NAME'],
                        'postgresMajor': 17, 'migrations': migrations(), 'roles': previous, 'tables': restored_state,
                        'permissionsSha256': grant_hash, 'sessionRowsExcluded': {table: before[table]['rows'] for table in SESSIONS},
                        'objects': objects, 'dump': {'bytes': dump.stat().st_size, 'sha256': digest(dump)}}
            write_json(output / 'manifest.json', manifest)
            with (output / 'COMPLETE').open('x') as marker:
                os.chmod(marker.name, 0o600); marker.write(digest(output / 'manifest.json') + '\n'); marker.flush(); os.fsync(marker.fileno())
            verify_backup(output)
        finally:
            set_access(d, v, previous)
            journal_path.unlink()
    return manifest

def verify_backup(directory):
    d = private_path(directory)
    require(d.is_dir() and not d.stat().st_mode & 0o077, 'Private backup directory required')
    require({p.name for p in d.iterdir()} == {'database.dump', 'manifest.json', 'COMPLETE', 'objects'}, 'Incomplete or unexpected backup artifacts')
    for name, maximum in [('manifest.json', 4194304), ('COMPLETE', 128), ('database.dump', MAX_BYTES)]:
        file_guard(d / name, maximum); require(not (d / name).stat().st_mode & 0o077, 'Private backup file required')
    require((d / 'COMPLETE').read_text() == digest(d / 'manifest.json') + '\n', 'Manifest checksum mismatch')
    m = json.loads((d / 'manifest.json').read_text())
    require(set(m) == {'format', 'createdAt', 'sourceScope', 'postgresMajor', 'migrations', 'roles', 'tables', 'permissionsSha256', 'sessionRowsExcluded', 'objects', 'dump'}, 'Unsupported manifest fields')
    require(m['format'] == 'portfolio-offline-v1' and m['postgresMajor'] == 17 and m['migrations'] == migrations(), 'Unsupported backup/migrations')
    require(re.fullmatch(r'portfolio-[0-9a-f]{12}', m['sourceScope']) and set(m['roles']) == set(ROLES) and all(type(x) is bool for x in m['roles'].values()), 'Invalid source scope/roles')
    require(set(m['tables']) == set(TABLES) and SHA.fullmatch(m['permissionsSha256']), 'Invalid table/grant inventory')
    for row in m['tables'].values(): require(set(row) == {'rows', 'sha256'} and type(row['rows']) is int and 0 <= row['rows'] <= 100000 and SHA.fullmatch(row['sha256']), 'Invalid table fingerprint')
    require(set(m['sessionRowsExcluded']) == set(SESSIONS) and all(type(n) is int and 0 <= n <= 100000 for n in m['sessionRowsExcluded'].values()), 'Invalid excluded-session inventory')
    for table in SESSIONS: require(m['tables'][table] == {'rows': 0, 'sha256': hashlib.sha256(b'[]').hexdigest()}, 'Restored sessions must be empty')
    require(set(m['dump']) == {'bytes', 'sha256'} and type(m['dump']['bytes']) is int and m['dump']['bytes'] > 0 and m['dump']['bytes'] == (d / 'database.dump').stat().st_size and m['dump']['sha256'] == digest(d / 'database.dump'), 'Database dump checksum mismatch')
    root = d / 'objects'; private_path(root)
    require(root.is_dir() and not root.stat().st_mode & 0o077 and isinstance(m['objects'], list) and len(m['objects']) <= MAX_OBJECTS, 'Invalid media inventory')
    names = []
    for row in m['objects']:
        require(set(row) == {'name', 'bytes', 'sha256'} and isinstance(row['name'], str) and OBJECT.fullmatch(row['name']) and type(row['bytes']) is int and 0 <= row['bytes'] <= 10485760 and SHA.fullmatch(row['sha256']), 'Invalid media entry')
        path = root / row['name']; file_guard(path, 10485760)
        require(not path.stat().st_mode & 0o077 and row['bytes'] == path.stat().st_size and row['sha256'] == digest(path), 'Image artifact checksum mismatch')
        names.append(row['name'])
    require(len(set(names)) == len(names) and set(names) == {p.name for p in root.iterdir()} and sum(x['bytes'] for x in m['objects']) <= MAX_BYTES, 'Unexpected/duplicate/oversized media files')
    return m

def restore(backup_directory, directory, port, database='portfolio_demo'):
    m = verify_backup(backup_directory)  # Before any target creation or SQL.
    source = private_path(backup_directory); target = private_path(directory)
    require(not target.exists() and not target.is_relative_to(source), 'Restore requires a new directory outside backup')
    port_unused(port)
    pg.initialize(target, port, database)
    d, e, v = pg.configuration(target)
    require(v['COMPOSE_PROJECT_NAME'] != m['sourceScope'], 'Restore must use an independent scope')
    pg.compose(e, 'up', '-d', '--wait'); pg.prepare_roles(d, v)
    # From here failure leaves this fresh target quarantined. It never purges caller data.
    set_access(d, v, dict.fromkeys(ROLES, False))
    with scope_lock(d):
        disconnected(d, v)
        require(pg.sql(d, v, "SELECT count(*) FROM pg_tables WHERE schemaname='public';") == '0', 'Restore target is not empty')
        restore_archive(d, v, source / 'database.dump')
        require(database_state(d, v) == m['tables'], 'Restored rows/audits/credentials differ')
        if permissions(d, v) != m['permissionsSha256']:
            write_json(d / 'restore-grant-inventory.json', permission_inventory(d, v))
            raise BackupProblem('Restored runtime grants differ')
        root = d / 'media'; root.mkdir(mode=0o700); (root / 'objects').mkdir(mode=0o700); (root / 'incoming').mkdir(mode=0o700)
        marker = root / '.portfolio-media-v1'; marker.write_text('portfolio-media-v1\n'); marker.chmod(0o600)
        for row in m['objects']:
            origin = source / 'objects' / row['name']; dest = root / 'objects' / row['name']
            with origin.open('rb') as src, dest.open('xb') as dst:
                dest.chmod(0o600)
                for block in iter(lambda: src.read(65536), b''): dst.write(block)
                dst.flush(); os.fsync(dst.fileno())
            os.utime(dest, ns=(origin.stat().st_atime_ns, origin.stat().st_mtime_ns))
        require(media_entries(root) == m['objects'], 'Restored image bytes differ')
        check_assets(asset_rows(d, v), m['objects'])
        # ACL/data checks completed before allowing application connections.
        write_json(d / 'RESTORE_READY.json', {'format': m['format'], 'dumpSha256': m['dump']['sha256'], 'sessions': 'EMPTY', 'sourceScope': m['sourceScope']})
        set_access(d, v, m['roles'])
    return d

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    b = sub.add_parser('backup'); b.add_argument('--directory', required=True); b.add_argument('--output', required=True); b.add_argument('--application-port', type=int, required=True)
    r = sub.add_parser('restore'); r.add_argument('--backup', required=True); r.add_argument('--directory', required=True); r.add_argument('--port', type=int, required=True); r.add_argument('--database', choices=['portfolio_demo', 'portfolio_test'], default='portfolio_demo')
    c = sub.add_parser('recover-access'); c.add_argument('--directory', required=True)
    v = sub.add_parser('verify'); v.add_argument('--backup', required=True)
    args = parser.parse_args()
    # SIGTERM/interrupt run the backup finally block. SIGKILL requires recover-access.
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(BackupProblem('Interrupted; inspect access recovery journal')))
    if args.action == 'backup': backup(args.directory, args.output, args.application_port)
    elif args.action == 'restore': restore(args.backup, args.directory, args.port, args.database)
    elif args.action == 'recover-access': recover_access(args.directory)
    else: verify_backup(args.backup)
    print('Private backup operation completed:', args.action)

if __name__ == '__main__':
    try: main()
    except (RuntimeError, ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError):
        print('Backup/restore refused or failed; preserve private artifacts and inspect the scope log/journal', file=sys.stderr)
        raise SystemExit(1)
