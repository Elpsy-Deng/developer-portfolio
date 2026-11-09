#!/usr/bin/env python3
"""Own loopback PostgreSQL scope. Generated passwords and logs never go to stdout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
DOCKER = ['docker', '--host=unix:///var/run/docker.sock']
COMPOSE = DOCKER + ['compose', '-f', str(ROOT / 'infra/compose.pg.yml')]


def private(path):
    result = Path(path).resolve()
    if not result.is_relative_to(ROOT / 'work'):
        raise RuntimeError('Use a private directory within this repository work/')
    return result


def environment():
    result = os.environ.copy()
    for key in ['DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH']:
        result.pop(key, None)
    return result


def initialize(directory, port, database):
    directory = private(directory)
    if directory.exists() or not 1024 <= port <= 65535:
        raise RuntimeError('Use a new directory and valid own port')
    directory.mkdir(parents=True, mode=0o700)
    values = {
        'COMPOSE_PROJECT_NAME': 'portfolio-' + secrets.token_hex(6),
        'PORTFOLIO_DATABASE': database,
        'PORTFOLIO_MEDIA_ROOT': str(directory / 'media'),
        'PORTFOLIO_PG_PORT': str(port),
        'PORTFOLIO_BOOTSTRAP_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_DB_URL': f'jdbc:postgresql://127.0.0.1:{port}/{database}',
        'PORTFOLIO_DB_USER': 'portfolio_reader',
        'PORTFOLIO_DB_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_WRITER_USER': 'portfolio_writer',
        'PORTFOLIO_WRITER_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_CREDENTIALS_USER': 'portfolio_credentials',
        'PORTFOLIO_CREDENTIALS_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_IDENTITY_USER': 'portfolio_identity',
        'PORTFOLIO_IDENTITY_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_AUTH_USER': 'portfolio_auth',
        'PORTFOLIO_AUTH_PASSWORD': secrets.token_hex(24),
        'PORTFOLIO_MIGRATION_USER': 'portfolio_owner',
        'PORTFOLIO_MIGRATION_PASSWORD': secrets.token_hex(24),
    }
    path = directory / 'runtime.env'
    path.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
    path.chmod(0o600)


def configuration(directory):
    directory = private(directory)
    path = directory / 'runtime.env'
    if not path.is_file() or path.stat().st_mode & 0o077:
        raise RuntimeError('Private runtime.env required')
    values = dict(line.split('=', 1) for line in path.read_text().splitlines() if line)
    if not re.fullmatch(r'portfolio-[0-9a-f]{12}', values.get('COMPOSE_PROJECT_NAME', '')):
        raise RuntimeError('Explicit owned scope required')
    if values.get('PORTFOLIO_DATABASE') not in ['portfolio_demo', 'portfolio_test']:
        raise RuntimeError('Dedicated portfolio database required')
    port = int(values['PORTFOLIO_PG_PORT'])
    if values['PORTFOLIO_DB_URL'] != f"jdbc:postgresql://127.0.0.1:{port}/{values['PORTFOLIO_DATABASE']}":
        raise RuntimeError('Database URL does not match own loopback scope')
    return directory, path, values


def compose(path, *args):
    return subprocess.run(COMPOSE + ['--env-file', str(path), *args], env=environment(), check=True)


def container(values):
    ids = subprocess.check_output(DOCKER + ['ps', '-q', '--filter', 'label=com.docker.compose.project=' + values['COMPOSE_PROJECT_NAME'],
                                  '--filter', 'label=com.docker.compose.service=postgres'], env=environment(), text=True).splitlines()
    if len(ids) != 1:
        raise RuntimeError('One running owned PostgreSQL required')
    metadata = json.loads(subprocess.check_output(DOCKER + ['inspect', '--format', '{{json .NetworkSettings.Ports}}', ids[0]],
                                                   env=environment(), text=True))
    if metadata.get('5432/tcp') != [{'HostIp': '127.0.0.1', 'HostPort': values['PORTFOLIO_PG_PORT']}]:
        raise RuntimeError('Own loopback mapping required')
    return ids[0]


def sql(directory, values, statement):
    result = subprocess.run(DOCKER + ['exec', '-i', container(values), 'psql', '-U', 'portfolio_bootstrap',
                            '-d', values['PORTFOLIO_DATABASE'], '-XAt', '-v', 'ON_ERROR_STOP=1'], env=environment(),
                            input=statement.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    if result.returncode:
        log = directory / ('sql-failure-' + secrets.token_hex(4) + '.log')
        log.write_bytes(result.stderr)
        log.chmod(0o600)
        raise RuntimeError('Database operation failed; inspect private failure log')
    return result.stdout.decode().strip()


def sql_ready(directory, values):
    """Docker health may see the init-only Unix socket server. Wait for final TCP+SQL."""
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        ready = subprocess.run(DOCKER + ['exec', container(values), 'pg_isready', '-h', '127.0.0.1', '-U',
                               'portfolio_bootstrap', '-d', values['PORTFOLIO_DATABASE']], env=environment(),
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
        if ready.returncode == 0:
            try:
                if sql(directory, values, 'SELECT 1;') == '1': return
            except RuntimeError:
                pass  # Bounded read-only readiness only; never retries role writes.
        time.sleep(0.25)
    raise RuntimeError('Own final PostgreSQL TCP/SQL server was not ready within 35 seconds')


def prepare_roles(directory, values):
    sql_ready(directory, values)
    if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_owner');") == 't':
        prepare_auth_role(directory, values)
        prepare_writer_role(directory, values)
        prepare_identity_role(directory, values)
        prepare_credentials_role(directory, values)
        return  # Never reset an existing role/password/data on restart.
    if sql(directory, values, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r';") != '0':
        raise RuntimeError('Refuse role initialization on existing application data')
    owner, reader = values['PORTFOLIO_MIGRATION_PASSWORD'], values['PORTFOLIO_DB_PASSWORD']
    if not re.fullmatch(r'[0-9a-f]{48}', owner) or not re.fullmatch(r'[0-9a-f]{48}', reader):
        raise RuntimeError('Generated credentials required')
    database = values['PORTFOLIO_DATABASE']
    sql(directory, values, f"""BEGIN;
      CREATE ROLE portfolio_owner LOGIN PASSWORD '{owner}';
      CREATE ROLE portfolio_reader LOGIN PASSWORD '{reader}';
      ALTER DATABASE {database} OWNER TO portfolio_owner;
      REVOKE ALL ON DATABASE {database} FROM PUBLIC;
      GRANT CONNECT ON DATABASE {database} TO portfolio_reader;
      ALTER SCHEMA public OWNER TO portfolio_owner;
      REVOKE CREATE ON SCHEMA public FROM PUBLIC;
      GRANT USAGE ON SCHEMA public TO portfolio_reader;
      ALTER DEFAULT PRIVILEGES FOR ROLE portfolio_owner IN SCHEMA public GRANT SELECT ON TABLES TO portfolio_reader;
      COMMIT;""")
    prepare_auth_role(directory, values)
    prepare_writer_role(directory, values)
    prepare_identity_role(directory, values)
    prepare_credentials_role(directory, values)


def prepare_auth_role(directory, values):
    password = values.get('PORTFOLIO_AUTH_PASSWORD', '')
    if values.get('PORTFOLIO_AUTH_USER') != 'portfolio_auth' or not re.fullmatch(r'[0-9a-f]{48}', password):
        raise RuntimeError('Run enable-auth on this own scope before V3')
    if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_auth');") == 't':
        return
    database = values['PORTFOLIO_DATABASE']
    sql(directory, values, f"CREATE ROLE portfolio_auth LOGIN PASSWORD '{password}'; GRANT CONNECT ON DATABASE {database} TO portfolio_auth;")


def enable_auth(directory, path, values):
    container(values)
    if 'PORTFOLIO_AUTH_PASSWORD' not in values:
        if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_auth');") == 't':
            raise RuntimeError('Existing auth role requires its matching private configuration; never reset it')
        with path.open('a') as stream:
            stream.write('PORTFOLIO_AUTH_USER=portfolio_auth\nPORTFOLIO_AUTH_PASSWORD=' + secrets.token_hex(24) + '\n')
        values = configuration(directory)[2]
    prepare_auth_role(directory, values)


def prepare_writer_role(directory, values):
    password = values.get('PORTFOLIO_WRITER_PASSWORD', '')
    if values.get('PORTFOLIO_WRITER_USER') != 'portfolio_writer' or not re.fullmatch(r'[0-9a-f]{48}', password):
        raise RuntimeError('Run enable-writer on this own scope before V4')
    if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_writer');") == 't':
        return
    database = values['PORTFOLIO_DATABASE']
    sql(directory, values, f"CREATE ROLE portfolio_writer LOGIN PASSWORD '{password}'; GRANT CONNECT ON DATABASE {database} TO portfolio_writer;")


def enable_writer(directory, path, values):
    container(values)
    if 'PORTFOLIO_WRITER_PASSWORD' not in values:
        if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_writer');") == 't':
            raise RuntimeError('Existing writer requires matching private configuration; never reset it')
        with path.open('a') as stream:
            stream.write('PORTFOLIO_WRITER_USER=portfolio_writer\nPORTFOLIO_WRITER_PASSWORD=' + secrets.token_hex(24) + '\n')
        values = configuration(directory)[2]
    prepare_writer_role(directory, values)


def prepare_identity_role(directory, values):
    password = values.get('PORTFOLIO_IDENTITY_PASSWORD', '')
    if values.get('PORTFOLIO_IDENTITY_USER') != 'portfolio_identity' or not re.fullmatch(r'[0-9a-f]{48}', password):
        raise RuntimeError('Run enable-identity on this own scope before V7')
    if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_identity');") == 't':
        return
    database = values['PORTFOLIO_DATABASE']
    sql(directory, values, f"CREATE ROLE portfolio_identity LOGIN PASSWORD '{password}'; GRANT CONNECT ON DATABASE {database} TO portfolio_identity;")


def enable_identity(directory, path, values):
    container(values)
    if 'PORTFOLIO_IDENTITY_PASSWORD' not in values:
        if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_identity');") == 't':
            raise RuntimeError('Existing identity role requires matching private configuration; never reset it')
        with path.open('a') as stream:
            stream.write('PORTFOLIO_IDENTITY_USER=portfolio_identity\nPORTFOLIO_IDENTITY_PASSWORD=' + secrets.token_hex(24) + '\n')
        values = configuration(directory)[2]
    prepare_identity_role(directory, values)


def prepare_credentials_role(directory, values):
    password = values.get('PORTFOLIO_CREDENTIALS_PASSWORD', '')
    if values.get('PORTFOLIO_CREDENTIALS_USER') != 'portfolio_credentials' or not re.fullmatch(r'[0-9a-f]{48}', password):
        raise RuntimeError('Run enable-credentials on this own scope before V8')
    if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_credentials');") == 't':
        return
    database = values['PORTFOLIO_DATABASE']
    sql(directory, values, f"CREATE ROLE portfolio_credentials LOGIN PASSWORD '{password}'; GRANT CONNECT ON DATABASE {database} TO portfolio_credentials;")


def enable_credentials(directory, path, values):
    container(values)
    if 'PORTFOLIO_CREDENTIALS_PASSWORD' not in values:
        if sql(directory, values, "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='portfolio_credentials');") == 't':
            raise RuntimeError('Existing credentials role requires matching private configuration; never reset it')
        with path.open('a') as stream:
            stream.write('PORTFOLIO_CREDENTIALS_USER=portfolio_credentials\nPORTFOLIO_CREDENTIALS_PASSWORD=' + secrets.token_hex(24) + '\n')
        values = configuration(directory)[2]
    prepare_credentials_role(directory, values)


def enable_media(directory, path, values):
    desired = str(directory / 'media')
    if 'PORTFOLIO_MEDIA_ROOT' in values:
        if values['PORTFOLIO_MEDIA_ROOT'] != desired:
            raise RuntimeError('Existing media root differs; preserve and explicitly review it')
        return
    with path.open('a') as stream:
        stream.write('PORTFOLIO_MEDIA_ROOT=' + desired + '\n')


def provision_account(directory, values, username, role):
    if not re.fullmatch(r'[a-z][a-z0-9_-]{2,63}', username or ''):
        raise RuntimeError('Username must be 3–64 lowercase letters/digits/underscore/dash, starting with a letter')
    credentials = directory / ('account-' + username + '.env')
    if credentials.exists():
        raise RuntimeError('Keep existing credential file; account provisioning never resets passwords')
    password = secrets.token_hex(24)
    salt = secrets.token_bytes(16)
    encoded = (salt + hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 310000, 32)).hex()
    # INSERT fails on existing username. No UPSERT or password reset, and no stdout credentials.
    sql(directory, values, f"INSERT INTO admin_account(username,password_hash,role) VALUES ('{username}','{encoded}','{role}');")
    credentials.write_text(f'PORTFOLIO_LOGIN_USERNAME={username}\nPORTFOLIO_LOGIN_PASSWORD={password}\n')
    credentials.chmod(0o600)
    print('Created account; credentials stored only in ' + str(credentials.relative_to(ROOT)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['init', 'up', 'down', 'stop', 'start', 'exec', 'enable-auth', 'create-account', 'enable-writer', 'enable-media', 'enable-identity', 'enable-credentials'])
    parser.add_argument('--directory', required=True)
    parser.add_argument('--port', type=int, default=25486)
    parser.add_argument('--database', choices=['portfolio_demo', 'portfolio_test'], default='portfolio_demo')
    parser.add_argument('--username')
    parser.add_argument('--role', choices=['ADMIN', 'VIEWER'], default='ADMIN')
    parser.add_argument('--purge', action='store_true')
    arguments = sys.argv[1:]
    command = []
    if '--' in arguments:
        boundary = arguments.index('--')
        arguments, command = arguments[:boundary], arguments[boundary + 1:]
    args = parser.parse_args(arguments)
    if args.action == 'init':
        initialize(args.directory, args.port, args.database)
        print('Created private own-scope configuration')
        return
    directory, path, values = configuration(args.directory)
    if args.action == 'up':
        compose(path, 'up', '-d', '--wait')
        prepare_roles(directory, values)
    elif args.action == 'enable-auth':
        enable_auth(directory, path, values)
    elif args.action == 'enable-credentials':
        enable_credentials(directory, path, values)
    elif args.action == 'enable-identity':
        enable_identity(directory, path, values)
    elif args.action == 'enable-media':
        enable_media(directory, path, values)
    elif args.action == 'enable-writer':
        enable_writer(directory, path, values)
    elif args.action == 'create-account':
        provision_account(directory, values, args.username, args.role)
    elif args.action == 'down':
        compose(path, 'down', *(['-v'] if args.purge else []))
    elif args.action in ['stop', 'start']:
        compose(path, args.action, 'postgres')
    else:
        container(values)
        if not command:
            raise RuntimeError('Command required')
        process_env = environment()
        process_env.update(values)
        raise SystemExit(subprocess.run(command, cwd=ROOT, env=process_env).returncode)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        print('Own database operation refused or failed: ' + (str(error) if not isinstance(error, subprocess.SubprocessError) else type(error).__name__))
        raise SystemExit(1)
