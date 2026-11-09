#!/usr/bin/env python3
"""Preview and CAS-apply curated text/architecture through the existing ADMIN API."""
import argparse
import hashlib
import http.cookiejar
import json
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import portfolio_deploy as deploy
import portfolio_db as pg

ROOT = pg.ROOT
BUNDLE = ROOT / 'content/showcase-2026-10-13/release.json'
PATCH_FIELDS = {'statusNote', 'duration', 'features', 'plannedGoals', 'verification', 'limitations', 'challenges',
                'architectureDescription', 'evidenceLinks', 'evidenceCheckedAt'}


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def bundle():
    data = json.loads(BUNDLE.read_text())
    deploy.require(data['schema'] == 1 and data['release'] == 'showcase-2026-10-13', 'Unknown release')
    targets = set()
    for project in data['projects']:
        deploy.require(project['slug'] not in targets and set(project['patch']) <= PATCH_FIELDS, 'Invalid release patch')
        targets.add(project['slug'])
        for media in project['media']:
            path = (BUNDLE.parent / media['file']).resolve()
            deploy.require(path.is_relative_to(BUNDLE.parent) and path.is_file() and not (BUNDLE.parent / media['file']).is_symlink(), 'Invalid release media')
            deploy.require(deploy.hash_file(path) == media['sha256'], 'Release media bytes differ')
    return data


def content(managed):
    return {key: value for key, value in managed['project'].items() if key not in {'coverImage', 'screenshots', 'localDemoUrl'}}


def proposed(managed, patch):
    return {**content(managed), **patch}


def preflight(current, target):
    deploy.require(fingerprint(current) == target['beforeSha256'] and current['version'] == target['expectedVersion'], 'Stale plan; create and review a new plan')


class Client:
    def __init__(self, origin, credentials, opener=None):
        self.origin = origin
        self.opener = opener or urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        csrf = self.call('/api/admin/session')['csrf']['token']
        self.call('/api/admin/login', urllib.parse.urlencode({'username': credentials['PORTFOLIO_LOGIN_USERNAME'], 'password': credentials['PORTFOLIO_LOGIN_PASSWORD']}).encode(), 'POST',
                  {'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': csrf})
        deploy.require(self.call('/api/admin/session')['role'] == 'ADMIN', 'ADMIN required')

    def call(self, path, body=None, method=None, headers=None):
        headers = headers or {}
        if isinstance(body, dict):
            body = json.dumps(body, ensure_ascii=False).encode(); headers = {**headers, 'Content-Type': 'application/json'}
        try:
            with self.opener.open(urllib.request.Request(self.origin + path, data=body, method=method, headers=headers), timeout=25) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            # Never dump response bodies/session/credentials; an ambiguous write is not retried.
            raise RuntimeError('API HTTP ' + str(error.code)) from None

    def write(self, path, body, method='PUT', headers=None):
        csrf = self.call('/api/admin/session')['csrf']['token']
        return self.call(path, body, method, {**(headers or {}), 'X-CSRF-TOKEN': csrf})

    def upload(self, slug, version, media):
        boundary = 'showcase-release-boundary'
        fields = {'expectedVersion': str(version), 'kind': 'SCREENSHOT', 'title': media['title'], 'description': media['description'], 'alt': media['alt']}
        body = b''.join((f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n').encode() for key, value in fields.items())
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="architecture.png"\r\nContent-Type: image/png\r\n\r\n').encode()
        body += (BUNDLE.parent / media['file']).read_bytes() + (f'\r\n--{boundary}--\r\n').encode()
        return self.write('/api/admin/projects/' + slug + '/media', body, 'POST', {'Content-Type': 'multipart/form-data; boundary=' + boundary})


def connection(scope, credential_file, cafile=None):
    directory, _, values = deploy.configuration(scope)
    pg.container(values)
    gateway = deploy.ids(values, 'gateway')
    deploy.require(len(gateway) == 1 and len(deploy.ids(values, 'api')) == 1, 'Own running gateway/API required')
    ports = json.loads(subprocess.check_output(pg.DOCKER + ['inspect', '--format', '{{json .NetworkSettings.Ports}}', gateway[0]], env=pg.environment(), text=True))
    deploy.require(ports.get('8080/tcp') == [{'HostIp': '127.0.0.1', 'HostPort': values['PORTFOLIO_GATEWAY_PORT']}], 'Own loopback gateway required')
    credential_file = deploy.private(credential_file)
    deploy.require(credential_file.parent == directory and credential_file.is_file() and not credential_file.stat().st_mode & 0o077, 'Private scope-local credential file required')
    credentials = dict(line.split('=', 1) for line in credential_file.read_text().splitlines() if line)
    if (directory/'.portfolio-https-v1').exists():
        import portfolio_https as tls
        settings=json.loads((directory/'.portfolio-https-v1').read_text())
        deploy.require(settings['scope']==values['COMPOSE_PROJECT_NAME'] and settings['bindAddress']=='127.0.0.1'
                       and ports.get('8443/tcp')==[{'HostIp':'127.0.0.1','HostPort':str(settings['httpsPort'])}], 'Own loopback TLS gateway required')
        jar=http.cookiejar.CookieJar();origin='https://'+settings['hostname']+':'+str(settings['httpsPort'])
        return values,Client(origin,credentials,tls.opener(settings,cafile,jar))
    return values, Client('http://127.0.0.1:' + values['PORTFOLIO_GATEWAY_PORT'], credentials)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['plan', 'apply'])
    parser.add_argument('--directory', required=True)
    parser.add_argument('--credentials', required=True)
    parser.add_argument('--plan', required=True)
    parser.add_argument('--plan-sha256')
    parser.add_argument('--cafile',help='Explicit trusted CA for a private TLS scope; certificate verification stays enabled')
    args = parser.parse_args()
    directory, _, _ = deploy.configuration(args.directory)
    with deploy.lock(directory):
        operate(args)


def operate(args):
    data = bundle()
    values, client = connection(args.directory, args.credentials,args.cafile)
    path = deploy.private(args.plan)
    deploy.require(path.parent.exists() and not path.parent.stat().st_mode & 0o077, 'Private plan parent required')
    if args.action == 'plan':
        deploy.require(not path.exists(), 'Preserve old plan')
        targets = []
        for entry in data['projects']:
            slug = entry['slug']; before = client.call('/api/admin/projects/' + slug)
            media = client.call('/api/admin/projects/' + slug + '/media')
            text = proposed(before, entry['patch'])
            missing = [item for item in entry['media'] if not any(m['kind'] == 'SCREENSHOT' and m['title'] == item['title'] and m['description'] == item['description'] and m['alt'] == item['alt'] for m in media)]
            deploy.require(sum(m['kind'] == 'SCREENSHOT' for m in media) + len(missing) <= 20, 'Screenshot capacity exceeded')
            targets.append({'slug': slug, 'expectedVersion': before['version'], 'beforeSha256': fingerprint(before),
                            'before': content(before), 'after': text, 'published': before['published'], 'sortOrder': before['sortOrder'], 'media': missing})
        deploy.save(path, {'schema': 1, 'scope': values['COMPOSE_PROJECT_NAME'], 'releaseSha256': deploy.hash_file(BUNDLE), 'targets': targets})
        print('Private review plan created; no catalog writes. SHA256=' + deploy.hash_file(path))
        return
    deploy.require(path.is_file() and not path.stat().st_mode & 0o077 and deploy.hash_file(path) == args.plan_sha256, 'Reviewed private plan hash required')
    plan = json.loads(path.read_text())
    deploy.require(plan['schema'] == 1 and plan['scope'] == values['COMPOSE_PROJECT_NAME'] and plan['releaseSha256'] == deploy.hash_file(BUNDLE), 'Plan/release scope changed')
    entries = {entry['slug']: entry for entry in data['projects']}
    deploy.require([t['slug'] for t in plan['targets']] == list(entries), 'Plan targets changed')
    # All targets must still match before the first catalog write.
    for target in plan['targets']:
        current = client.call('/api/admin/projects/' + target['slug']); preflight(current, target)
        entry = entries[target['slug']]
        existing = client.call('/api/admin/projects/' + target['slug'] + '/media')
        missing = [item for item in entry['media'] if not any(m['kind'] == 'SCREENSHOT' and m['title'] == item['title'] and m['description'] == item['description'] and m['alt'] == item['alt'] for m in existing)]
        deploy.require(target['after'] == proposed(current, entry['patch']) and target['media'] == missing
                       and target['published'] == current['published'] and target['sortOrder'] == current['sortOrder'], 'Plan proposal changed')
    journal = path.with_name(path.stem + '-result.json')
    deploy.require(not journal.exists(), 'This plan was already attempted; review a new plan')
    result = {'outcome': 'IN_PROGRESS', 'completed': [], 'current': None}
    deploy.save(journal, result)
    try:
        for target in plan['targets']:
            slug = target['slug']; result['current'] = slug; deploy.save(journal, result)
            current = client.call('/api/admin/projects/' + slug); preflight(current, target)
            if content(current) != target['after']:
                current = client.write('/api/admin/projects/' + slug, {'content': target['after'], 'expectedVersion': current['version'],
                                       'published': target['published'], 'sortOrder': target['sortOrder']})
            for media in target['media']:
                current = client.upload(slug, current['version'], media)
            result['completed'].append({'slug': slug, 'version': current['version']}); deploy.save(journal, result)
        result.update(outcome='PASS', current=None)
    except Exception:
        result['outcome'] = 'PARTIAL_OR_UNCONFIRMED'
        raise
    finally:
        deploy.save(journal, result)
    print('Release applied through ADMIN API; per-project audit/CAS preserved')


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Showcase release refused/failed: ' + (str(error) if type(error) is RuntimeError else type(error).__name__))
        sys.exit(1)
