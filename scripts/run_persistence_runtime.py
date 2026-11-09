#!/usr/bin/env python3
"""Packaged API, real PostgreSQL restart/failure and production browser acceptance."""
import argparse
import base64
import hashlib
import http.client
from http.cookies import SimpleCookie
from urllib.parse import urlencode
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import portfolio_db as pg


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def get(path):
    connection = http.client.HTTPConnection('127.0.0.1', 19186, timeout=10)
    try:
        connection.request('GET', '/api/public/' + path)
        response = connection.getresponse()
        return response.status, json.loads(response.read())
    finally:
        connection.close()


class AuthClient:
    def __init__(self):
        self.cookie = ''

    def call(self, method, path, body=None, csrf=None, json_body=False, extra_headers=None):
        connection = http.client.HTTPConnection('127.0.0.1', 19186, timeout=10)
        headers = {'Cookie': 'PORTFOLIO_SESSION=' + self.cookie} if self.cookie else {}
        if extra_headers: headers.update(extra_headers)
        if csrf: headers['X-CSRF-TOKEN'] = csrf
        if body is not None: headers['Content-Type'] = 'application/json' if json_body else 'application/x-www-form-urlencoded'
        try:
            connection.request(method, '/api/admin/' + path, (json.dumps(body) if json_body else urlencode(body)) if body is not None else None, headers)
            response = connection.getresponse()
            cookies = SimpleCookie()
            for key, value in response.getheaders():
                if key.lower() == 'set-cookie': cookies.load(value)
            if 'PORTFOLIO_SESSION' in cookies:
                self.cookie = cookies['PORTFOLIO_SESSION'].value
            raw = response.read()
            return response.status, json.loads(raw) if raw else None, cookies
        finally: connection.close()

    def session(self):
        status, data, _ = self.call('GET', 'session')
        require(status == 200, 'Real session endpoint must succeed')
        return data


def binary_image(path):
    connection = http.client.HTTPConnection('127.0.0.1', 19186, timeout=10)
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        return response.status, response.read(), response.getheader('Content-Type')
    finally: connection.close()


def upload_image(auth, slug, version, fixture):
    boundary = 'portfolio-image-boundary'
    fields = {'expectedVersion': str(version), 'kind': 'SCREENSHOT', 'title': 'Runtime image', 'description': 'Own synthetic screenshot', 'alt': 'Runtime synthetic image'}
    body = b''.join((f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n').encode() for key,value in fields.items())
    body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="untrusted.png"\r\nContent-Type: image/png\r\n\r\n').encode() + fixture.read_bytes() + (f'\r\n--{boundary}--\r\n').encode()
    connection = http.client.HTTPConnection('127.0.0.1',19186,timeout=10)
    try:
        connection.request('POST','/api/admin/projects/'+slug+'/media',body,{'Cookie':'PORTFOLIO_SESSION='+auth.cookie,'X-CSRF-TOKEN':auth.session()['csrf']['token'],'Content-Type':'multipart/form-data; boundary='+boundary})
        response=connection.getresponse()
        return response.status,json.loads(response.read())
    finally:connection.close()


def ready(process):
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        require(process.poll() is None, 'Own API exited; see private log')
        try:
            if get('health') == (200, {'status': 'UP', 'catalog': 'POSTGRESQL'}):
                return
        except (OSError, ValueError, http.client.HTTPException):
            pass
        time.sleep(0.25)
    raise RuntimeError('Own PostgreSQL API did not become ready')


def auth_recovered(process, client):
    """Bounded readiness probing after real PG recovery; never retries a login/write/assertion."""
    deadline = time.monotonic() + 35
    observations = []
    while time.monotonic() < deadline:
        require(process.poll() is None, 'Own API exited during auth recovery')
        status, body, _ = client.call('GET', 'session')
        observations.append(status)
        if status == 200:
            require(isinstance(body, dict) and isinstance(body.get('authenticated'), bool), 'Invalid recovered identity response')
            return observations
        require(status == 503 and body.get('code') == 'AUTH_UNAVAILABLE', 'Unexpected auth recovery response')
        time.sleep(0.25)
    raise RuntimeError('Own auth database pool did not recover within 35 seconds')


def identity_recovered(process, client):
    deadline=time.monotonic()+35
    observations=[]
    while time.monotonic()<deadline:
        require(process.poll() is None,'Own API exited during identity recovery')
        status,body,_=client.call('GET','accounts')
        observations.append(status)
        if status==200:
            require(isinstance(body,list),'Invalid recovered account directory');return observations
        require(status==503 and body.get('code')=='AUTH_UNAVAILABLE','Unexpected identity recovery response')
        time.sleep(.25)
    raise RuntimeError('Own identity pool did not recover within 35 seconds')


def credentials_recovered(process, client):
    deadline=time.monotonic()+35
    observations=[]
    while time.monotonic()<deadline:
        require(process.poll() is None,'Own API exited during credential recovery')
        status,body,_=client.call('GET','password')
        observations.append(status)
        if status==200:
            require(isinstance(body,dict) and 'version' in body and 'password' not in body,'Invalid recovered password profile');return observations
        require(status==503 and body.get('code')=='AUTH_UNAVAILABLE','Unexpected credential recovery response')
        time.sleep(.25)
    raise RuntimeError('Own credential pool did not recover within 35 seconds')


def fingerprint(directory, values):
    result = {}
    for table in ['category', 'project', 'technology', 'project_technology', 'project_media', 'project_evidence', 'flyway_schema_history']:
        raw = pg.sql(directory, values, f"SELECT COALESCE(jsonb_agg(to_jsonb(x) ORDER BY to_jsonb(x)::text),'[]'::jsonb)::text FROM {table} x;")
        result[table] = {'rows': len(json.loads(raw)), 'sha256': hashlib.sha256(raw.encode()).hexdigest()}
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--java-home', required=True)
    args = parser.parse_args()
    output = pg.private(args.output)
    require(not output.exists(), 'Preserve old evidence; choose a new output directory')
    for port in [19186, 15186, 25487]:
        with socket.socket() as probe:
            # Permit the previous disposable listener's TIME_WAIT, never a live TCP listener.
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            probe.bind(('127.0.0.1', port))  # Refuse occupied ports, never stop others.
    output.mkdir(parents=True, mode=0o700)
    dbdir = output / 'database'
    pg.initialize(dbdir, 25487, 'portfolio_demo')
    directory, envfile, values = pg.configuration(dbdir)
    environment = pg.environment()
    environment.update(values)
    environment.update(JAVA_HOME=str(Path(args.java_home).resolve()), PORTFOLIO_PORT='19186',
                       PORTFOLIO_COOKIE_SECURE='false', PORTFOLIO_E2E_OUTPUT=str(output.relative_to(pg.ROOT) / 'browser'))
    if not environment.get('PORTFOLIO_CHROMIUM_PATH') and Path('/usr/bin/chromium').is_file():
        environment['PORTFOLIO_CHROMIUM_PATH'] = '/usr/bin/chromium'
    processes = []
    handles = []
    result = {'outcome': 'RUNNING', 'checks': [], 'transport': 'packaged JAR / own PostgreSQL17 / production dist', 'automaticRetries': 0}
    artifact = pg.ROOT / 'backend/target/developer-portfolio-0.2.0.jar'
    result['artifactSha256'] = hashlib.sha256(artifact.read_bytes()).hexdigest()

    def check(name, condition):
        require(condition, name)
        result['checks'].append(name)

    def stop(process):
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=8)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

    def start(name):
        log = (output / (name + '.log')).open('xb')
        os.chmod(log.name, 0o600)
        handles.append(log)
        process = subprocess.Popen([str(Path(args.java_home) / 'bin/java'), '-Xmx256m', '-XX:+UseSerialGC',
                                   '-jar', 'backend/target/developer-portfolio-0.2.0.jar'], cwd=pg.ROOT,
                                   env=environment, stdout=log, stderr=subprocess.STDOUT)
        processes.append(process)
        ready(process)
        return process

    try:
        pg.compose(envfile, 'up', '-d', '--wait')
        pg.prepare_roles(directory, values)
        # Default HTTPS-only cookie is verified independently; local preview opts out explicitly.
        environment.pop('PORTFOLIO_COOKIE_SECURE', None)
        secure_process = start('api-secure-default')
        _, _, secure_cookies = AuthClient().call('GET', 'session')
        check('default session cookie is Secure/HttpOnly/Strict and scoped',
              bool(secure_cookies['PORTFOLIO_SESSION']['secure'])
              and bool(secure_cookies['PORTFOLIO_SESSION']['httponly'])
              and secure_cookies['PORTFOLIO_SESSION']['samesite'] == 'Strict'
              and secure_cookies['PORTFOLIO_SESSION']['path'] == '/api/admin')
        stop(secure_process)
        environment['PORTFOLIO_COOKIE_SECURE'] = 'false'
        first = start('api-first')
        for role in ['ADMIN', 'VIEWER']:
            username = 'runtime_' + role.lower()
            pg.provision_account(directory, values, username, role)
            credentials = dict(line.split('=', 1) for line in (directory / ('account-' + username + '.env')).read_text().splitlines())
            environment['PORTFOLIO_E2E_' + role + '_USERNAME'] = credentials['PORTFOLIO_LOGIN_USERNAME']
            environment['PORTFOLIO_E2E_' + role + '_PASSWORD'] = credentials['PORTFOLIO_LOGIN_PASSWORD']
        fixture = output / 'synthetic-image.png'
        fixture.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABQAAAAMCAIAAADtbgqsAAAAFklEQVR4nGNw7IkhGzGMah7VPHg1AwAU/haAot1VCgAAAABJRU5ErkJggg=='))
        environment['PORTFOLIO_E2E_IMAGE_FILE'] = str(fixture)
        identities = pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(a) ORDER BY username)::text FROM admin_account a WHERE username IN ('runtime_admin','runtime_viewer');")
        auth = AuthClient()
        anonymous_csrf = auth.session()['csrf']['token']
        anonymous_cookie = auth.cookie
        status, _, _ = auth.call('POST', 'login', {'username': environment['PORTFOLIO_E2E_ADMIN_USERNAME'],
                                 'password': environment['PORTFOLIO_E2E_ADMIN_PASSWORD']}, anonymous_csrf)
        check('provisioned PBKDF2 account logs in and rotates session', status == 204
              and auth.cookie != anonymous_cookie and auth.session()['role'] == 'ADMIN')
        authenticated_cookie = auth.cookie
        login_attribute_sql = "SELECT encode(a.attribute_bytes,'hex') FROM spring_session_attributes a JOIN spring_session s ON s.primary_id=a.session_primary_id WHERE s.principal_name='runtime_admin' AND a.attribute_name='portfolio.auth.login-time';"
        login_attribute = pg.sql(directory, values, login_attribute_sql)
        check('default absolute lifetime and rate limits are active',
              auth.call('GET', 'security')[1]['absoluteTimeoutSeconds'] == 28800
              and auth.call('GET', 'security')[1]['loginAccountLimit'] == 30)
        check('successful authentication persists a password-free login time',
              bool(login_attribute) and b'java.lang.Long' in bytes.fromhex(login_attribute))
        check('real admin overview and security authorization', auth.call('GET', 'overview')[1]['publishedProjects'] == 5
              and auth.call('GET', 'security')[1]['idleTimeoutMinutes'] == 30)
        baseline = fingerprint(directory, values)
        result['baseline'] = baseline
        check('fresh V1 through V9 and normalized seed', baseline['project']['rows'] == 5
              and baseline['category']['rows'] == 3 and baseline['technology']['rows'] == 32
              and baseline['project_evidence']['rows'] == 14 and baseline['flyway_schema_history']['rows'] == 9)
        check('V9 updates only untouched platform introduction to taxonomy and pagination',
              get('projects/developer-portfolio')[1]['statusNote'].find('分類／技術治理') >= 0)
        check('real public HTTP contracts', get('projects')[1]['total'] == 5 and len(get('categories')[1]) == 3
              and get('projects/unknown')[0] == 404)
        status, runtime_category, _ = auth.call('POST','taxonomy/categories',{'label':'Runtime own category','sortOrder':100},auth.session()['csrf']['token'],True)
        check('real dynamic category created with version and audit',status==201 and runtime_category['version']==1)
        status, runtime_technology, _ = auth.call('POST','taxonomy/technologies',{'label':'Runtime own technology'},auth.session()['csrf']['token'],True)
        check('real dynamic technology created with version and audit',status==201 and runtime_technology['version']==1)
        core = auth.call('GET', 'projects/developer-portfolio')[1]['project']
        for key in ['coverImage', 'screenshots', 'localDemoUrl']: core.pop(key, None)
        core.update(slug='runtime-crud-proof', title='Runtime CRUD proof', featured=False,category=runtime_category['label'])
        core['technologies'].append(runtime_technology['label'])
        command = {'content': core, 'published': False, 'sortOrder': None, 'expectedVersion': None}
        status, created, _ = auth.call('POST', 'projects', command, auth.session()['csrf']['token'], True)
        check('real admin creates draft and public404', status == 201 and created['version'] == 1
              and get('projects/runtime-crud-proof')[0] == 404)
        status, image_project = upload_image(auth, 'runtime-crud-proof', 1, fixture)
        image_url = image_project['project']['screenshots'][0]['src']
        media_rows = auth.call('GET','projects/runtime-crud-proof/media')[1]
        asset_file = Path(values['PORTFOLIO_MEDIA_ROOT']) / 'objects' / (image_url.rsplit('/',1)[-1]+'.png')
        check('real PNG upload increments aggregate version and is private while draft',status == 200
              and image_project['version'] == 2 and binary_image(image_url)[0] == 404 and asset_file.is_file())
        stored_image = asset_file.read_bytes()
        command.update(expectedVersion=2, published=True)
        status, edited, _ = auth.call('PUT', 'projects/runtime-crud-proof', command, auth.session()['csrf']['token'], True)
        check('real published write and transactional audit', status == 200 and edited['version'] == 3
              and get('projects/runtime-crud-proof')[1]['title'] == 'Runtime CRUD proof'
              and len(auth.call('GET', 'projects/runtime-crud-proof/audit')[1]) == 3)
        status, _, _ = auth.call('PUT', 'projects/runtime-crud-proof', command, auth.session()['csrf']['token'], True)
        check('real stale expectedVersion409 has no duplicate audit', status == 409
              and len(auth.call('GET', 'projects/runtime-crud-proof/audit')[1]) == 3)
        status, runtime_category, _ = auth.call('PUT','taxonomy/categories/'+runtime_category['id'],{'label':'Runtime renamed category','sortOrder':100,'expectedVersion':1},auth.session()['csrf']['token'],True)
        check('category rename propagates project version and full audit',status==200 and runtime_category['version']==2 and auth.call('GET','projects/runtime-crud-proof')[1]['version']==4)
        status, runtime_technology, _ = auth.call('PUT','taxonomy/technologies/'+runtime_technology['id'],{'label':'Runtime renamed technology','expectedVersion':1},auth.session()['csrf']['token'],True)
        check('technology rename propagates project version and full audit',status==200 and runtime_technology['version']==2 and auth.call('GET','projects/runtime-crud-proof')[1]['version']==5 and len(auth.call('GET','projects/runtime-crud-proof/audit')[1])==5)
        status,body,_=auth.call('DELETE','taxonomy/categories/'+runtime_category['id'],{'expectedVersion':2},auth.session()['csrf']['token'],True)
        check('referenced taxonomy delete is rejected by real HTTP',status==409 and body['code']=='TERM_IN_USE')
        page=get('projects?'+urlencode({'category':runtime_category['label'],'page':2,'pageSize':1}))[1]
        check('public bounded page keeps filtered count on empty trailing page',page['items']==[] and page['total']==1 and page['totalPages']==1 and page['catalogTotal']==6)
        taxonomy_sql="SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY created_at,id),'[]'::jsonb)::text FROM taxonomy_audit t WHERE term_id IN ('"+runtime_category['id']+"','"+runtime_technology['id']+"');"
        taxonomy_snapshot=pg.sql(directory,values,taxonomy_sql)
        check('real published image serves verified canonical bytes',binary_image(image_url) == (200,stored_image,'image/png'))
        managed_name = 'runtime_managed_probe'
        command_account = {'username':managed_name,'password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD'],'role':'VIEWER','enabled':True}
        status, account_created, _ = auth.call('POST','accounts',command_account,auth.session()['csrf']['token'],True)
        check('real account creation returns only safe fields and transactional audit',status == 201 and account_created['version'] == 1
              and set(account_created) == {'username','role','enabled','version'} and auth.call('GET','accounts/audit')[1][0]['action'] == 'CREATE')
        managed = AuthClient()
        status, _, _ = managed.call('POST','login',{'username':managed_name,'password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD']},managed.session()['csrf']['token'])
        check('created account authenticates with initial password but cannot read account directory',status == 204 and managed.call('GET','accounts')[0] == 403)
        status, account_changed, _ = auth.call('PUT','accounts/'+managed_name,{'expectedVersion':1,'role':'VIEWER','enabled':False},auth.session()['csrf']['token'],True)
        check('real account disable increments version and revokes prior session',status == 200 and account_changed['version'] == 2 and managed.call('GET','overview')[0] == 401)
        identity_sql = "SELECT json_build_object('account',(SELECT to_jsonb(a) FROM admin_account a WHERE username='runtime_managed_probe'),'audit',(SELECT jsonb_agg(to_jsonb(a) ORDER BY created_at,id) FROM admin_identity_audit a WHERE username='runtime_managed_probe'))::text;"
        identity_snapshot = pg.sql(directory,values,identity_sql)
        changed_user='runtime_password_probe'
        changed_secret=environment['PORTFOLIO_E2E_VIEWER_PASSWORD']+'changed'
        status, _, _ = auth.call('POST','accounts',{'username':changed_user,'password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD'],'role':'VIEWER','enabled':True},auth.session()['csrf']['token'],True)
        require(status==201,'Own password fixture creation must succeed')
        password_client=AuthClient()
        status, _, _ = password_client.call('POST','login',{'username':changed_user,'password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD']},password_client.session()['csrf']['token'])
        require(status==204,'Own password fixture must authenticate')
        status, _, _ = password_client.call('POST','password',{'expectedVersion':1,'currentPassword':environment['PORTFOLIO_E2E_VIEWER_PASSWORD'],'newPassword':changed_secret},password_client.session()['csrf']['token'],True)
        check('real VIEWER self password change revokes old session',status==204 and password_client.call('GET','overview')[0]==401)
        rejected_password=AuthClient()
        status, _, _ = rejected_password.call('POST','login',{'username':changed_user,'password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD']},rejected_password.session()['csrf']['token'])
        check('old password is rejected after committed change',status==401)
        status, _, _ = password_client.call('POST','login',{'username':changed_user,'password':changed_secret},password_client.session()['csrf']['token'])
        check('new password authenticates with incremented version',status==204 and password_client.call('GET','password')[1]['version']==2)
        password_sql="SELECT json_build_object('account',(SELECT to_jsonb(a) FROM admin_account a WHERE username='runtime_password_probe'),'audit',(SELECT jsonb_agg(to_jsonb(a) ORDER BY created_at,id) FROM admin_identity_audit a WHERE username='runtime_password_probe'))::text;"
        password_snapshot=pg.sql(directory,values,password_sql)
        status, _, _ = auth.call('POST','accounts',{'username':'runtime_password_limit','password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD'],'role':'VIEWER','enabled':True},auth.session()['csrf']['token'],True)
        require(status==201,'Own password limit fixture creation must succeed')
        password_quota=AuthClient()
        status, _, _ = password_quota.call('POST','login',{'username':'runtime_password_limit','password':environment['PORTFOLIO_E2E_VIEWER_PASSWORD']},password_quota.session()['csrf']['token'])
        require(status==204,'Own quota fixture must authenticate')
        for i in range(5):
            status, body, _=password_quota.call('POST','password',{'expectedVersion':1,'currentPassword':'wrong','newPassword':changed_secret},password_quota.session()['csrf']['token'],True)
            require(status==400 and body['code']=='PASSWORD_INVALID','Admitted wrong current password must fail without mutation')
        def password_blocked():
            status,body,_=password_quota.call('POST','password',{'expectedVersion':1,'currentPassword':environment['PORTFOLIO_E2E_VIEWER_PASSWORD'],'newPassword':changed_secret},password_quota.session()['csrf']['token'],True)
            return status==429 and body['code']=='PASSWORD_RATE_LIMITED'
        check('real self password quota exhausts without resetting successful login',password_blocked())
        old = json.loads(pg.sql(directory, values, "SELECT json_build_object('title',title,'version',version)::text FROM project WHERE slug='developer-portfolio';"))
        pg.sql(directory, values, "UPDATE project SET title='PG restart proof',version=version+1 WHERE slug='developer-portfolio';")
        check('API reads changed PostgreSQL rather than packaged JSON', get('projects/developer-portfolio')[1]['title'] == 'PG restart proof')
        changed = fingerprint(directory, values)
        stop(first)
        second = start('api-restarted')
        check('JAR restart preserves session and never resets accounts', auth.session()['authenticated']
              and auth.cookie == authenticated_cookie
              and pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(a) ORDER BY username)::text FROM admin_account a WHERE username IN ('runtime_admin','runtime_viewer');") == identities)
        check('JAR restart preserves absolute login time without renewal', pg.sql(directory, values, login_attribute_sql) == login_attribute)
        check('JAR restart preserves password hash/version/audit and new authenticated session',pg.sql(directory,values,password_sql)==password_snapshot and password_client.call('GET','password')[1]['version']==2)
        check('JAR restart does not reset exhausted password quota',password_blocked())
        check('JAR restart preserves governed account version/hash and identity audit',pg.sql(directory,values,identity_sql) == identity_snapshot)
        check('JAR restart preserves taxonomy version and audit',pg.sql(directory,values,taxonomy_sql)==taxonomy_snapshot and auth.call('GET','taxonomy/categories')[1][-1]['version']==2)
        check('JAR restart preserves CRUD version and audit', auth.call('GET', 'projects/runtime-crud-proof')[1]['version'] == 5
              and len(auth.call('GET', 'projects/runtime-crud-proof/audit')[1]) == 5)
        check('JAR restart preserves physical image and media metadata',binary_image(image_url) == (200,stored_image,'image/png')
              and auth.call('GET','projects/runtime-crud-proof/media')[1] == media_rows)
        check('API restart preserves all table rows and versions without reseed', fingerprint(directory, values) == changed
              and get('projects/developer-portfolio')[1]['title'] == 'PG restart proof')
        pg.compose(envfile, 'stop', 'postgres')
        check('real PG outage gives503 health', get('health')[0] == 503)
        status, body = get('projects')
        check('real PG outage gives503 catalog without JSON fallback', status == 503 and body.get('code') == 'CATALOG_UNAVAILABLE')
        check('real PG outage does not serve image by bypassing publication check',binary_image(image_url)[0] == 503)
        status, data, _ = auth.call('GET', 'session')
        check('real PG outage gives503 auth without authenticated fallback', status == 503 and data.get('code') == 'AUTH_UNAVAILABLE')
        pg.compose(envfile, 'start', 'postgres')
        ready(second)
        result.setdefault('authRecoveryProbes', []).append(auth_recovered(second, auth))
        check('auth pool recovers after first real PG restart', result['authRecoveryProbes'][-1][-1] == 200)
        result['identityRecoveryProbes']=identity_recovered(second,auth)
        check('identity pool recovers independently after PG restart',result['identityRecoveryProbes'][-1] == 200)
        result['credentialsRecoveryProbes']=credentials_recovered(second,password_client)
        check('credential pool recovers independently after PG restart',result['credentialsRecoveryProbes'][-1]==200)
        check('PG restart preserves password hash/version/audit and rejects no-data fallback',pg.sql(directory,values,password_sql)==password_snapshot and password_client.call('GET','password')[1]['version']==2)
        check('PG restart does not reset exhausted password quota',password_blocked())
        check('same-volume PG restart recovers original durable data', fingerprint(directory, values) == changed
              and get('projects/developer-portfolio')[1]['title'] == 'PG restart proof')
        check('same-volume PG restart preserves login session', auth.session()['authenticated'] and auth.cookie == authenticated_cookie)
        check('PG restart preserves absolute login time without renewal', pg.sql(directory, values, login_attribute_sql) == login_attribute)
        check('PG restart preserves governed account and identity audit',pg.sql(directory,values,identity_sql) == identity_snapshot)
        check('PG restart preserves taxonomy audit and technology version',pg.sql(directory,values,taxonomy_sql)==taxonomy_snapshot and next(t for t in auth.call('GET','taxonomy/technologies')[1] if t['id']==runtime_technology['id'])['version']==2)
        check('PG restart preserves written project and audit', get('projects/runtime-crud-proof')[1]['title'] == 'Runtime CRUD proof'
              and len(auth.call('GET', 'projects/runtime-crud-proof/audit')[1]) == 5)
        check('PG restart restores identical canonical image bytes',binary_image(image_url) == (200,stored_image,'image/png'))
        status, _, _ = auth.call('DELETE', 'projects/runtime-crud-proof', {'expectedVersion': 5}, auth.session()['csrf']['token'], True)
        audit = auth.call('GET', 'projects/runtime-crud-proof/audit')[1]
        check('real delete keeps prior snapshots and append-only audit', status == 204
              and get('projects/runtime-crud-proof')[0] == 404 and len(audit) == 6 and audit[0]['action'] == 'DELETE')
        check('deleted project revokes public image immediately',binary_image(image_url)[0] == 404)
        status, cleanup, _ = auth.call('POST','media/cleanup',csrf=auth.session()['csrf']['token'])
        check('durable retirement queue reclaims physical file without deleting audit',status == 200 and not asset_file.exists()
              and len(auth.call('GET','projects/runtime-crud-proof/audit')[1]) == 6)
        for kind,term in [('categories',runtime_category),('technologies',runtime_technology)]:
            status,_,_=auth.call('DELETE','taxonomy/'+kind+'/'+term['id'],{'expectedVersion':2},auth.session()['csrf']['token'],True)
            require(status==204,'Unused own runtime taxonomy cleanup must succeed')
        check('dictionary deletion retains all six audit tombstones',len(json.loads(pg.sql(directory,values,taxonomy_sql)))==6)
        token = auth.session()['csrf']['token']
        status, _, _ = auth.call('POST', 'logout', csrf=token)
        replay = AuthClient()
        replay.cookie = authenticated_cookie
        check('real logout revokes persisted session and cookie replay', status == 204 and replay.call('GET', 'overview')[0] == 401)
        # Restore only this disposable probe before UI; neither restart altered it.
        title = old['title'].replace("'", "''")
        pg.sql(directory, values, f"UPDATE project SET title='{title}',version={int(old['version'])} WHERE slug='developer-portfolio';")
        check('only own temporary probe restored; seed fingerprint unchanged', fingerprint(directory, values) == baseline)
        stop(second)
        browser_log = output / 'browser.log'
        with browser_log.open('xb') as stream:
            browser_log.chmod(0o600)
            completed = subprocess.run(['npm', 'run', 'test:e2e'], cwd=pg.ROOT, env=environment,
                                       stdout=stream, stderr=subprocess.STDOUT, timeout=240)
        require(completed.returncode == 0, 'Browser acceptance failed; preserve private report')
        browser = json.loads((output / 'browser/report.json').read_text())
        check('production browser43 with real PostgreSQL and taxonomy/pagination UI', browser['stats']['expected'] == 43
              and browser['stats']['unexpected'] == 0 and browser['stats']['skipped'] == 0 and browser['stats']['flaky'] == 0)
        result['browser'] = browser['stats']
        check('browser/API startup made no changes to domain or history', fingerprint(directory, values) == baseline)
        check('browser never resets account hashes or roles',
              pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(a) ORDER BY username)::text FROM admin_account a WHERE username IN ('runtime_admin','runtime_viewer');") == identities)
        # A second explicit, stricter configuration on the same real volume proves quota exhaustion quickly.
        # Time-age only the disposable counters before this phase, never disable rate limiting for the browser.
        pg.sql(directory, values, "UPDATE admin_login_bucket SET window_start=window_start-301;")
        environment.update(PORTFOLIO_LOGIN_ACCOUNT_LIMIT='3', PORTFOLIO_LOGIN_PEER_LIMIT='5', PORTFOLIO_LOGIN_GLOBAL_LIMIT='8')
        limited_api = start('api-tight-limits')
        quota = AuthClient()
        for i in range(3):
            status, body, _ = quota.call('POST','login',{'username':'runtime_quota_probe','password':'wrong'}, quota.session()['csrf']['token'],
                                         extra_headers={'X-Forwarded-For':f'203.0.113.{i+1}', 'Forwarded':f'for=203.0.113.{i+1}'})
            check(f'real admitted wrong attempt {i+1} remains generic401', status == 401 and body['code'] == 'INVALID_CREDENTIALS')
        def blocked():
            status, body, _ = quota.call('POST','login',{'username':'runtime_quota_probe','password':'wrong'}, quota.session()['csrf']['token'],
                                         extra_headers={'X-Forwarded-For':'198.51.100.8'})
            return status == 429 and body['code'] == 'LOGIN_RATE_LIMITED'
        check('real quota exhaustion429 ignores changing forwarding headers',blocked())
        counters = pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(b) ORDER BY bucket_key)::text FROM admin_login_bucket b;")
        stop(limited_api)
        limited_restart = start('api-tight-restarted')
        check('JAR restart does not reset exhausted durable quota', blocked() and pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(b) ORDER BY bucket_key)::text FROM admin_login_bucket b;") == counters)
        pg.compose(envfile,'stop','postgres')
        check('real limiter phase PG outage fails closed503', quota.call('GET','session')[0] == 503)
        pg.compose(envfile,'start','postgres'); ready(limited_restart)
        result.setdefault('authRecoveryProbes', []).append(auth_recovered(limited_restart, quota))
        check('auth pool recovers after quota-phase real PG restart', result['authRecoveryProbes'][-1][-1] == 200)
        check('same-volume PG restart does not reset exhausted quota', blocked() and pg.sql(directory, values, "SELECT jsonb_agg(to_jsonb(b) ORDER BY bucket_key)::text FROM admin_login_bucket b;") == counters)
        pg.sql(directory,values,"UPDATE admin_login_bucket SET window_start=window_start-301;")
        pg.sql(directory,values,"DELETE FROM spring_session WHERE principal_name='runtime_admin';")
        fresh = AuthClient()
        status, _, _ = fresh.call('POST','login', {'username':environment['PORTFOLIO_E2E_ADMIN_USERNAME'], 'password':environment['PORTFOLIO_E2E_ADMIN_PASSWORD']},fresh.session()['csrf']['token'])
        check('expired fixed window permits fresh real authentication', status == 204 and fresh.session()['authenticated'])
        issued_hex = pg.sql(directory,values,login_attribute_sql)
        require(len(issued_hex.splitlines()) == 1 and b'java.lang.Long' in bytes.fromhex(issued_hex), 'One fresh disposable session with serialized Long required')
        # Preserve Java serialization metadata, changing only this test fixture Long's eight-byte timestamp.
        old_time = int(time.time()*1000)-28801000
        expired_hex = issued_hex[:-16] + old_time.to_bytes(8,'big',signed=True).hex()
        pg.sql(directory,values, "UPDATE spring_session_attributes a SET attribute_bytes=decode('"+expired_hex+"','hex') FROM spring_session s WHERE a.session_primary_id=s.primary_id AND s.principal_name='runtime_admin' AND a.attribute_name='portfolio.auth.login-time';")
        saved_cookie = fresh.cookie
        check('real absolute expiry blocks protected reads despite recent activity', fresh.call('GET','overview')[0] == 401)
        old_session=AuthClient(); old_session.cookie=saved_cookie
        check('real absolute expiry revokes old cookie and yields anonymous fresh CSRF',old_session.call('GET','overview')[0] == 401 and not fresh.session()['authenticated'])
        status, _, _ = fresh.call('POST','login', {'username':environment['PORTFOLIO_E2E_ADMIN_USERNAME'], 'password':environment['PORTFOLIO_E2E_ADMIN_PASSWORD']}, fresh.session()['csrf']['token'])
        check('real reauthentication renews absolute lifetime after expiry',status == 204 and fresh.session()['authenticated'])
        check('login protection phase preserves catalog and account hashes',fingerprint(directory,values) == baseline
              and pg.sql(directory, values,"SELECT jsonb_agg(to_jsonb(a) ORDER BY username)::text FROM admin_account a WHERE username IN ('runtime_admin','runtime_viewer');") == identities)
        result['ratePhase'] = {'accountLimit':3,'peerLimit':5,'globalLimit':8,'windowSeconds':300,'fixtureClockAging':True,'browserUsesDefaults':True}
        require(hashlib.sha256(artifact.read_bytes()).hexdigest() == result['artifactSha256'], 'Runtime artifact changed')
        result['passed'] = len(result['checks'])
        result['outcome'] = 'PASS'
    finally:
        for process in processes: stop(process)
        for handle in handles: handle.close()
        pg.compose(envfile, 'down', '-v')  # This fresh, generated scope is disposable.
        if result['outcome'] != 'PASS': result['outcome'] = 'FAIL'
        result['cleanup'] = {'ownProcessesStopped': all(p.poll() is not None for p in processes)}
        path = output / 'result.json'
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        path.chmod(0o600)
    print('Own runtime persistence checks passed:', result['passed'])


if __name__ == '__main__':
    main()
