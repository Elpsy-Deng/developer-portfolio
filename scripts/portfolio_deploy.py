#!/usr/bin/env python3
"""Private, scoped loopback deployment. Credentials never enter images or API env."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import portfolio_db as pg

ROOT = pg.ROOT
COMPOSE = pg.DOCKER + ['compose', '-f', str(ROOT / 'infra/compose.deploy.yml')]

def require(value, message):
    if not value: raise RuntimeError(message)

def private(path):
    path = Path(os.path.abspath(path))
    require(not any(p.is_symlink() for p in (path, *path.parents)), 'Symbolic paths refused')
    return pg.private(path)

def save(path, data):
    temp = path.with_suffix(path.suffix + '.tmp')
    with temp.open('x') as f:
        temp.chmod(0o600); json.dump(data, f, indent=2); f.write('\n'); f.flush(); os.fsync(f.fileno())
    os.replace(temp, path)

def initialize(directory, pg_port, gateway_port, http):
    d = private(directory)
    require(os.getuid() != 0 and os.getgid() != 0, 'Generate deployment as a nonroot Linux/WSL user')
    require(gateway_port != pg_port and 1024 <= gateway_port <= 65535, 'Distinct own loopback ports required')
    for port in (pg_port, gateway_port):
        with socket.socket() as s: s.bind(('127.0.0.1', port))
    pg.initialize(d, pg_port, 'portfolio_demo')
    _, _, v = pg.configuration(d)
    (d / '.portfolio-deploy-v1').write_text(v['COMPOSE_PROJECT_NAME'] + '\n')
    (d / 'media').mkdir(mode=0o700)
    values = {'PORTFOLIO_DEPLOY_DIRECTORY': str(d), 'PORTFOLIO_RUNTIME_UID': str(os.getuid()),
              'PORTFOLIO_RUNTIME_GID': str(os.getgid()), 'PORTFOLIO_GATEWAY_PORT': str(gateway_port),
              'PORTFOLIO_APP_IMAGE': 'portfolio-app:unbuilt', 'PORTFOLIO_GATEWAY_IMAGE': 'portfolio-gateway:unbuilt'}
    (d / 'deploy.env').write_text(''.join(k + '=' + val + '\n' for k, val in values.items()))
    (d / 'deploy.env').chmod(0o600)
    url = 'jdbc:postgresql://postgres:5432/portfolio_demo'
    runtime = {'server.address': '0.0.0.0', 'server.port': '19186', 'spring.datasource.url': url,
               'spring.datasource.username': v['PORTFOLIO_DB_USER'], 'spring.datasource.password': v['PORTFOLIO_DB_PASSWORD'],
               'portfolio.media.root': '/data/media', 'portfolio.auth.cookie-secure': 'false' if http else 'true'}
    for prefix in ('AUTH', 'WRITER', 'IDENTITY', 'CREDENTIALS'):
        runtime['portfolio.' + prefix.lower() + '.username'] = v['PORTFOLIO_' + prefix + '_USER']
        runtime['portfolio.' + prefix.lower() + '.password'] = v['PORTFOLIO_' + prefix + '_PASSWORD']
    migration = {'url': url, 'user': v['PORTFOLIO_MIGRATION_USER'], 'password': v['PORTFOLIO_MIGRATION_PASSWORD']}
    for name, data in [('runtime.properties', runtime), ('migration.properties', migration)]:
        (d / name).write_text(''.join(k + '=' + val + '\n' for k, val in data.items())); (d / name).chmod(0o600)
    (d / 'bootstrap.password').write_text(v['PORTFOLIO_BOOTSTRAP_PASSWORD'] + '\n'); (d / 'bootstrap.password').chmod(0o600)
    print('Created private deployment; credentials not printed')

def configuration(directory):
    d = private(directory); _, e, v = pg.configuration(d)
    require(d.is_dir() and not d.stat().st_mode & 0o077 and d.stat().st_uid == os.getuid(), 'Private scope directory owned by this user required')
    require((d / '.portfolio-deploy-v1').read_text() == v['COMPOSE_PROJECT_NAME'] + '\n', 'Own deployment marker required')
    for name in ('deploy.env', 'runtime.properties', 'migration.properties', 'bootstrap.password'):
        p = private(d / name); require(p.is_file() and not p.stat().st_mode & 0o077, 'Private regular configuration required')
    v.update(dict(l.split('=', 1) for l in (d / 'deploy.env').read_text().splitlines() if l))
    require(v['PORTFOLIO_DEPLOY_DIRECTORY'] == str(d) and v['PORTFOLIO_MEDIA_ROOT'] == str(d / 'media'), 'Own mount paths required')
    require(int(v['PORTFOLIO_RUNTIME_UID']) == os.getuid() != 0 and int(v['PORTFOLIO_RUNTIME_GID']) == os.getgid() != 0, 'Matching nonroot scope owner required')
    return d, e, v

@contextmanager
def lock(d):
    fd = os.open(d / '.deploy-lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB); yield
    finally: os.close(fd)

def run(d, argv, label, env=None, timeout=180):
    path = d / (label + '-' + os.urandom(4).hex() + '.log')
    with path.open('x') as f:
        path.chmod(0o600)
        result = subprocess.run(argv, cwd=ROOT, env=env or pg.environment(), stdout=f, stderr=subprocess.STDOUT, timeout=timeout)
    require(result.returncode == 0, 'Operation failed; inspect private ' + path.name)

def compose(d, e, *args):
    overlay = ['-f',str(d / 'https-compose.yml')] if (d / '.portfolio-https-v1').exists() else []
    return COMPOSE + overlay + ['--env-file', str(e), '--env-file', str(d / 'deploy.env'), *args]

def ids(v, service, all_containers=False):
    return subprocess.check_output(pg.DOCKER + ['ps', '-aq' if all_containers else '-q', '--filter',
            'label=com.docker.compose.project=' + v['COMPOSE_PROJECT_NAME'], '--filter', 'label=com.docker.compose.service=' + service],
            env=pg.environment(), text=True).splitlines()

def stopped(v):
    require(not ids(v, 'api') and not ids(v, 'gateway') and not ids(v, 'migration'), 'Stop own API/gateway before migration or activation')

def hash_file(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def release(d, v, name):
    require(re.fullmatch(r'[a-z0-9][a-z0-9-]{0,29}', name or ''), 'Valid immutable release name required')
    p = private(d / ('release-' + name + '.json'))
    data = json.loads(p.read_text())
    require(data['scope'] == v['COMPOSE_PROJECT_NAME'] and data['release'] == name, 'Release belongs to another scope')
    for key in ('app', 'gateway'):
        require(re.fullmatch(r'sha256:[a-f0-9]{64}', data[key]), 'Pinned image ID required')
        scope = subprocess.check_output(pg.DOCKER + ['image', 'inspect', '--format', '{{ index .Config.Labels "dev.elpsy.portfolio.scope" }}', data[key]], env=pg.environment(), text=True).strip()
        require(scope == v['COMPOSE_PROJECT_NAME'], 'Image belongs to another scope')
    return data

def build(d, v, name):
    stopped(v); require(re.fullmatch(r'[a-z0-9][a-z0-9-]{0,29}', name or ''), 'Valid immutable release name required')
    record = d / ('release-' + name + '.json'); require(not record.exists(), 'Preserve immutable release record')
    jar = ROOT / 'backend/target/developer-portfolio-0.2.0.jar'; require(jar.is_file() and (ROOT / 'dist/index.html').is_file(), 'Package and production build first')
    artifacts = {str(p.relative_to(ROOT)): hash_file(p) for p in [jar, *sorted(p for p in (ROOT / 'dist').rglob('*') if p.is_file())]}
    result = {'scope': v['COMPOSE_PROJECT_NAME'], 'release': name, 'artifacts': artifacts}
    for key in ('app', 'gateway'):
        tag = 'portfolio-' + key + ':' + v['COMPOSE_PROJECT_NAME'] + '-' + name
        existing = subprocess.run(pg.DOCKER + ['image', 'inspect', tag], env=pg.environment(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        require(existing.returncode != 0, 'Do not overwrite existing image tag')
        iid = d / (key + '-' + name + '.iid')
        run(d, pg.DOCKER + ['build', '--label', 'dev.elpsy.portfolio.scope=' + v['COMPOSE_PROJECT_NAME'], '--label',
            'dev.elpsy.portfolio.release=' + name, '--iidfile', str(iid), '-t', tag, '-f', 'infra/' + key + '.Dockerfile', '.'], 'build-' + key, timeout=300)
        result[key] = iid.read_text().strip()
    require(all(hash_file(ROOT / p) == h for p, h in artifacts.items()), 'Artifacts changed during image build')
    save(record, result)

def activate(d, e, v, name, validate_only=False):
    stopped(v); r = release(d, v, name)
    env = pg.environment(); env.update(PORTFOLIO_APP_IMAGE=r['app'], PORTFOLIO_GATEWAY_IMAGE=r['gateway'])
    pg.container(v)  # Verify the owned PG mapping before any migration.
    if (d / '.portfolio-https-v1').exists():
        import portfolio_https
        portfolio_https.configure(d, e, v)
    run(d, compose(d, e, 'run', '--rm', '--no-deps', 'migration', *(['validate'] if validate_only else [])), 'migration', env)
    run(d, compose(d, e, 'up', '-d', '--wait', 'api', 'gateway'), 'activate', env)
    run(d, compose(d, e, 'exec', '-T', 'gateway', 'nginx', '-s', 'reload'), 'reload', env)
    save(d / 'active-release.json', r)
    print('Own pinned release activated; volumes preserved')

def compact(d, v, source_name, target_name):
    """Optional VFS-friendly root flattening of our built images, never mounted data."""
    stopped(v); source = release(d, v, source_name)
    require(re.fullmatch(r'[a-z0-9][a-z0-9-]{0,29}', target_name or ''), 'Valid immutable target release required')
    record = d / ('release-' + target_name + '.json'); require(not record.exists(), 'Preserve prior release')
    result = {**source, 'release': target_name, 'flattenedFrom': {'app':source['app'],'gateway':source['gateway']}}
    for key in ('app','gateway'):
        metadata = json.loads(subprocess.check_output(pg.DOCKER + ['image','inspect',source[key]],env=pg.environment(),text=True))[0]['Config']
        holder = 'portfolio-export-' + os.urandom(6).hex()
        archive = d / (holder + '.tar')
        run(d, pg.DOCKER + ['create','--name',holder,'--label','dev.elpsy.portfolio.scope='+v['COMPOSE_PROJECT_NAME'],source[key]],'export-create')
        try:
            run(d, pg.DOCKER + ['export','-o',str(archive),holder],'export-root')
            archive.chmod(0o600)
            changes=['--change','WORKDIR '+(metadata['WorkingDir'] or '/'),'--change','USER '+metadata['User'],
                     '--change','ENTRYPOINT '+json.dumps(metadata['Entrypoint']),'--change','CMD []',
                     '--change','LABEL dev.elpsy.portfolio.scope='+v['COMPOSE_PROJECT_NAME'],
                     '--change','LABEL dev.elpsy.portfolio.release='+target_name]
            # Only public base-image runtime settings; never container env/proxy/secret mounts.
            for item in metadata.get('Env',[]):
                if item.split('=',1)[0] in ['JAVA_HOME','PATH','LANG','LANGUAGE','LC_ALL','JAVA_VERSION','NGINX_VERSION','NJS_VERSION','NJS_RELEASE','PKG_RELEASE','DYNPKG_RELEASE']:
                    changes += ['--change','ENV '+item]
            tag='portfolio-'+key+':'+v['COMPOSE_PROJECT_NAME']+'-'+target_name
            require(subprocess.run(pg.DOCKER+['image','inspect',tag],env=pg.environment(),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode!=0,'Target image exists')
            run(d,pg.DOCKER+['import',*changes,str(archive),tag],'import-root')
            result[key]=subprocess.check_output(pg.DOCKER+['image','inspect','--format','{{.Id}}',tag],env=pg.environment(),text=True).strip()
            result[key+'RootSha256']=hash_file(archive)
        finally:
            run(d,pg.DOCKER+['rm',holder],'export-remove');archive.unlink(missing_ok=True)
    save(record,result)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['init', 'database', 'build', 'compact', 'up', 'rollback', 'stop', 'down', 'create-account'])
    p.add_argument('--directory', required=True); p.add_argument('--pg-port', type=int, default=25490)
    p.add_argument('--gateway-port', type=int, default=15190); p.add_argument('--loopback-http', action='store_true')
    p.add_argument('--release'); p.add_argument('--to-release'); p.add_argument('--username'); p.add_argument('--role', choices=['ADMIN','VIEWER'], default='ADMIN')
    p.add_argument('--purge', action='store_true'); args = p.parse_args()
    if args.action == 'init': initialize(args.directory, args.pg_port, args.gateway_port, args.loopback_http); return
    d, e, v = configuration(args.directory)
    with lock(d):
        if args.action == 'database':
            run(d, compose(d, e, 'up', '-d', '--wait', 'postgres'), 'database'); pg.prepare_roles(d, v)
        elif args.action == 'build': build(d, v, args.release)
        elif args.action == 'compact': compact(d, v, args.release, args.to_release)
        elif args.action == 'create-account': pg.provision_account(d, v, args.username, args.role)
        elif args.action in ('up', 'rollback'): activate(d, e, v, args.release, args.action == 'rollback')
        else:
            env = pg.environment()
            if (d / 'active-release.json').exists():
                r = json.loads((d / 'active-release.json').read_text()); env.update(PORTFOLIO_APP_IMAGE=r['app'],PORTFOLIO_GATEWAY_IMAGE=r['gateway'])
            run(d, compose(d, e, 'stop', 'gateway', 'api') if args.action == 'stop' else compose(d, e, 'down', *(['-v'] if args.purge else [])), args.action, env)

if __name__ == '__main__':
    try: main()
    except (RuntimeError, ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        print('Scoped deployment refused/failed:', str(error) if isinstance(error, RuntimeError) else type(error).__name__)
        sys.exit(1)
