"""Additional real API content-release checks after the existing deployment suite."""
import copy
import json
from pathlib import Path
import sys
import showcase_release as release
import portfolio_deploy as deploy
import portfolio_db as pg


def run(directory, output, chromium, environment, command):
    root = output / 'showcase'; root.mkdir(mode=0o700)
    checks = []
    def check(name, ok):
        deploy.require(ok, name); checks.append(name); print('PASS showcase ' + name, flush=True)
    command([sys.executable, 'scripts/portfolio_deploy.py', 'create-account', '--directory', str(directory), '--username', 'showcase_admin'], 'showcase-admin-provision')
    credentials = directory / 'account-showcase_admin.env'
    values, client = release.connection(directory, credentials)
    entries = release.bundle()['projects']
    before = {p['slug']: client.call('/api/admin/projects/' + p['slug']) for p in entries}
    audit_before = {s: len(client.call('/api/admin/projects/' + s + '/audit')) for s in before}
    runner = [sys.executable, 'scripts/showcase_release.py']
    def cli(action, plan, name, expected=0, viewer=False):
        argv = runner + [action, '--directory', str(directory), '--credentials', str(directory / ('account-deploy_viewer.env' if viewer else credentials.name)), '--plan', str(plan)]
        if action == 'apply': argv += ['--plan-sha256', deploy.hash_file(plan)]
        command(argv, name, expected=expected)
    try:
        # Make a reviewed plan, then simulate a legitimate concurrent editor.
        stale = root / 'stale.json'; cli('plan', stale, 'showcase-stale-plan')
        current = copy.deepcopy(before['developer-portfolio'])
        changed = release.content(current); changed['role'] = '合成資料：人工維護的工作範圍'
        saved = client.write('/api/admin/projects/developer-portfolio', {'content': changed, 'expectedVersion': current['version'], 'published': current['published'], 'sortOrder': current['sortOrder']})
        cli('apply', stale, 'showcase-stale-refusal', expected=1)
        check('stale plan refuses before any other target mutation', all(client.call('/api/admin/projects/' + s) == before[s] for s in before if s != 'developer-portfolio'))
        plan = root / 'reviewed.json'; cli('plan', plan, 'showcase-plan')
        planned = json.loads(plan.read_text())
        check('plan preserves manually maintained role and writes no catalog changes', client.call('/api/admin/projects/developer-portfolio') == saved and next(t for t in planned['targets'] if t['slug'] == 'developer-portfolio')['after']['role'] == changed['role'])
        cli('apply', plan, 'showcase-viewer-refusal', expected=1, viewer=True)
        check('VIEWER cannot apply reviewed release', client.call('/api/admin/projects/developer-portfolio') == saved)
        cli('apply', plan, 'showcase-apply')
        after = {s: client.call('/api/admin/projects/' + s) for s in before}
        for entry in entries:
            slug = entry['slug']; old = before[slug]; new = after[slug]
            check('patch/old media/publish/order and transaction audit preserved ' + slug,
                  all(new['project'][k] == v for k, v in entry['patch'].items())
                  and new['published'] == old['published'] and new['sortOrder'] == old['sortOrder']
                  and new['project']['title'] == old['project']['title']
                  and all(image in new['project']['screenshots'] for image in old['project']['screenshots'])
                  and len(client.call('/api/admin/projects/' + slug + '/audit')) >= audit_before[slug] + 2)
            image = [s for s in new['project']['screenshots'] if s['title'] == entry['media'][0]['title']]
            check('one normalized architecture image published ' + slug, len(image) == 1 and image[0]['src'].startswith('/api/public/media/'))
        cli('apply', plan, 'showcase-replay-refusal', expected=1)
        check('replaying attempted plan does not duplicate writes/media', all(client.call('/api/admin/projects/' + s) == after[s] for s in after))
        again = root / 'again.json'; cli('plan', again, 'showcase-noop-plan'); cli('apply', again, 'showcase-noop-apply')
        check('new reviewed equivalent plan is no-op', all(client.call('/api/admin/projects/' + s) == after[s] for s in after))
        browser_env = {**environment, 'PORTFOLIO_E2E_SHOWCASE': '1', 'PORTFOLIO_E2E_DEPLOYED_URL': 'http://127.0.0.1:' + values['PORTFOLIO_GATEWAY_PORT'],
                       'PORTFOLIO_E2E_PORT': values['PORTFOLIO_GATEWAY_PORT'], 'PORTFOLIO_E2E_OUTPUT': str(root / 'browser'), 'PORTFOLIO_CHROMIUM_PATH': chromium}
        command(['npm', 'run', 'test:e2e'], 'showcase-browser', browser_env, timeout=180)
        browser = json.loads((root / 'browser/report.json').read_text())['stats']
        check('four PG-backed showcase cases and one explicit Session-429 UI stub', browser['expected'] == 5 and not any(browser[k] for k in ('unexpected', 'skipped', 'flaky')))
        # Existing API session and all references must survive an app restart.
        _, env_file, _ = deploy.configuration(directory)
        command(deploy.compose(directory, env_file, 'restart', 'api'), 'showcase-api-restart')
        import time
        deadline = time.monotonic() + 45
        while True:
            try:
                if client.call('/api/public/health'): break
            except Exception:
                deploy.require(time.monotonic() < deadline, 'Showcase restart timeout'); time.sleep(.25)
        check('curated content/media references survive API restart', all(client.call('/api/admin/projects/' + s) == after[s] for s in after))
        return {'outcome': 'PASS', 'checksPassed': len(checks), 'browser': {k: browser[k] for k in ('expected', 'unexpected', 'skipped', 'flaky')}}
    finally:
        # Parent acceptance owns volume cleanup; these snapshots/logs stay private.
        deploy.save(root / 'checks.json', {'passed': len(checks), 'checks': checks})
