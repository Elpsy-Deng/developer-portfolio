#!/usr/bin/env python3
"""Run the same scoped validation locally and on GitHub; publish no raw reports."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import ci_evidence
import portfolio_db as pg
import portfolio_deploy as deploy

MARKER = 'portfolio-ci-v1\n'


def directory(path):
    result = deploy.private(path)
    if result.exists():
        deploy.require(result.is_dir() and result.stat().st_uid == os.getuid() and not result.stat().st_mode & 0o077,
                       'Private owned CI directory required')
    return result


def execute(root, command, stage, env=None, timeout=600):
    print('CI stage: ' + stage, flush=True)
    deploy.run(root, command, stage, env, timeout)


def scopes(root):
    return [(root / 'test-db', False), (root / 'deploy-db', True)]


def cleanup(root):
    if not root.exists(): return  # Dependency installation may fail before CI init.
    deploy.require((root / '.portfolio-ci-v1').read_text() == MARKER, 'Owned CI marker required')
    failed = False
    for scope, deployed in scopes(root):
        if not scope.exists(): continue
        try:
            _, _, values = pg.configuration(scope)
            deploy.require(values['PORTFOLIO_DATABASE'] == ('portfolio_demo' if deployed else 'portfolio_test'), 'Wrong CI scope')
            tool = 'portfolio_deploy.py' if deployed else 'portfolio_db.py'
            try:
                execute(root, [sys.executable, 'scripts/' + tool, 'down', '--directory', str(scope), '--purge'], 'cleanup-scope')
            except Exception:
                # Even a full filesystem must not prevent cleanup by requiring a new private log.
                # This is the same idempotent down of the already validated disposable own scope.
                env=pg.environment()
                if deployed:
                    _,env_file,values=deploy.configuration(scope)
                    active=scope/'active-release.json'
                    if active.exists():
                        images=json.loads(active.read_text());env.update(PORTFOLIO_APP_IMAGE=images['app'],PORTFOLIO_GATEWAY_IMAGE=images['gateway'])
                    argv=deploy.compose(scope,env_file,'down','-v')
                else:
                    _,env_file,values=pg.configuration(scope)
                    argv=pg.DOCKER+['compose','-f',str(pg.ROOT/'infra/compose.pg.yml'),'--env-file',str(env_file),'down','-v']
                result=subprocess.run(argv,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=60)
                deploy.require(result.returncode==0,'Disposable cleanup without log failed')
            for args in (['ps', '-aq'], ['volume', 'ls', '-q'], ['network', 'ls', '-q']):
                remaining = subprocess.check_output(pg.DOCKER + args + ['--filter', 'label=com.docker.compose.project=' + values['COMPOSE_PROJECT_NAME']], env=pg.environment(), text=True)
                deploy.require(not remaining.strip(), 'CI resources remain')
        except Exception:
            failed = True
    deploy.require(not failed, 'CI cleanup incomplete; inspect private logs')


def immutable_artifacts():
    import zipfile
    jar = pg.ROOT / 'backend/target/developer-portfolio-0.2.0.jar'
    with zipfile.ZipFile(jar) as archive:
        for sql in (pg.ROOT / 'backend/src/main/resources/db/migration').glob('V*.sql'):
            relative = str(sql.relative_to(pg.ROOT))
            tracked = subprocess.check_output(['git', 'show', 'HEAD:' + relative], cwd=pg.ROOT)
            deploy.require(sql.read_bytes() == tracked == archive.read('BOOT-INF/classes/db/migration/' + sql.name), 'Migration bytes differ')
    files = subprocess.check_output(['git', 'ls-files', 'public'], cwd=pg.ROOT, text=True).splitlines()
    for name in files:
        source = pg.ROOT / name
        tracked = subprocess.check_output(['git', 'show', 'HEAD:' + name], cwd=pg.ROOT)
        deploy.require(source.read_bytes() == tracked == (pg.ROOT / 'dist' / source.relative_to(pg.ROOT / 'public')).read_bytes(), 'Public asset bytes differ')
    digest = hashlib.sha256()
    for path in sorted(p for p in (pg.ROOT / 'dist').rglob('*') if p.is_file()):
        deploy.require(not path.is_symlink(), 'Symlink in build output')
        digest.update(str(path.relative_to(pg.ROOT / 'dist')).encode() + b'\0')
        digest.update(bytes.fromhex(deploy.hash_file(path)))
    return {'jarSha256': deploy.hash_file(jar), 'distTreeSha256': digest.hexdigest(),
            'migrationCount': len(list((pg.ROOT / 'backend/src/main/resources/db/migration').glob('V*.sql'))), 'publicAssetCount': len(files)}


def verify(args, root):
    deploy.require(not root.exists(), 'Use a new CI output; preserve old evidence')
    deploy.require(os.getuid() != 0, 'Run CI as nonroot')
    root.mkdir(parents=True, mode=0o700)
    (root / '.portfolio-ci-v1').write_text(MARKER)
    evidence = root / 'evidence'; evidence.mkdir(mode=0o700)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=pg.ROOT, text=True).strip()
    deploy.require(re.fullmatch('[a-f0-9]{40}', commit), 'Valid checkout commit required')
    summary = {'schema': 1, 'commit': commit, 'outcome': 'FAIL', 'completedStages': []}
    stage = 'initialize'
    try:
        stage = 'python-guards'
        for pattern in ('test_portfolio_backup.py', 'test_ci_evidence.py', 'test_showcase_release.py', 'test_portfolio_https.py'):
            execute(root, [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts', '-p', pattern], stage)
        summary['completedStages'].append(stage)
        stage = 'test-database'
        test = root / 'test-db'
        pg.initialize(test, 25486, 'portfolio_test')
        execute(root, [sys.executable, 'scripts/portfolio_db.py', 'up', '--directory', str(test)], stage)
        stage = 'backend-package'
        maven = ['mvn']
        if args.maven_settings: maven += ['-s', str(Path(args.maven_settings).resolve())]
        cache = deploy.private(args.maven_cache)
        maven += ['-Dmaven.repo.local=' + str(cache), '-f', 'backend/pom.xml',
                  '-DargLine=-Xmx384m -XX:+UseSerialGC -XX:ActiveProcessorCount=2',
                  '-DforkCount=1', '-DreuseForks=false', 'clean', 'package']
        environment = pg.environment()
        environment.setdefault('MAVEN_OPTS', '-Xmx256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2')
        execute(root, [sys.executable, 'scripts/portfolio_db.py', 'exec', '--directory', str(test), '--', *maven], stage, environment)
        summary['backend'] = ci_evidence.backend_summary(pg.ROOT / 'backend/target/surefire-reports')
        summary['completedStages'].append(stage)
        execute(root, [sys.executable, 'scripts/portfolio_db.py', 'down', '--directory', str(test), '--purge'], 'test-database-down')
        stage = 'frontend-build'
        execute(root, ['npm', 'run', 'build'], stage)
        summary['completedStages'].append(stage)
        artifacts = immutable_artifacts()
        stage = 'deployment-initialize'
        deployed = root / 'deploy-db'
        execute(root, [sys.executable, 'scripts/portfolio_deploy.py', 'init', '--directory', str(deployed), '--loopback-http'], stage)
        execute(root, [sys.executable, 'scripts/portfolio_deploy.py', 'database', '--directory', str(deployed)], 'deployment-database')
        stage = 'docker-build'
        execute(root, [sys.executable, 'scripts/portfolio_deploy.py', 'build', '--directory', str(deployed), '--release', 'ci'], stage)
        summary['completedStages'].append(stage)
        candidate='ci'
        if args.compact:
            execute(root,[sys.executable,'scripts/portfolio_deploy.py','compact','--directory',str(deployed),'--release','ci','--to-release','ci-flat'],'owned-vfs-compact',timeout=600)
            _,_,values=deploy.configuration(deployed)
            original=deploy.release(deployed,values,'ci')
            for kind in ('app','gateway'):
                metadata=json.loads(subprocess.check_output(pg.DOCKER+['image','inspect',original[kind]],env=pg.environment(),text=True))[0]
                deploy.require(not subprocess.check_output(pg.DOCKER+['ps','-aq','--filter','ancestor='+original[kind]],env=pg.environment(),text=True).strip(),'Original verification image still used')
                for tag in metadata['RepoTags']:
                    deploy.require(tag=='portfolio-'+kind+':'+values['COMPOSE_PROJECT_NAME']+'-ci','Only own verification tag may be retired')
                    execute(root,pg.DOCKER+['image','rm',tag],'retire-own-original-image')
            candidate='ci-flat'
        summary['deploymentImages']='OWNED_COMPACT' if args.compact else 'STANDARD'
        stage = 'container-browser-acceptance'
        runtime = root / 'runtime'
        execute(root, [sys.executable, 'scripts/run_deployment_runtime.py', '--directory', str(deployed), '--output', str(runtime),
                       '--release', candidate, '--chromium', args.chromium], stage, timeout=600)
        summary['runtime'] = ci_evidence.runtime_summary(runtime / 'result.json')
        summary['completedStages'].append(stage)
        stage = 'verified-https-acceptance'
        tls_runtime = root / 'https-runtime'
        execute(root, [sys.executable, 'scripts/run_https_runtime.py', '--directory', str(deployed), '--output', str(tls_runtime),
                       '--release', candidate], stage, timeout=600)
        summary['https'] = ci_evidence.https_summary(tls_runtime / 'result.json')
        deploy.require(artifacts == immutable_artifacts(), 'Artifacts changed during acceptance')
        summary['artifacts'] = artifacts
        summary['completedStages'].append(stage)
        summary['outcome'] = 'PASS'
    except Exception as error:
        # Exceptions/logs may contain secret values. Export only our enum + type.
        summary.update(outcome='FAIL', failedStage=stage, errorType=type(error).__name__)
        summary['failureProgress']=ci_evidence.failure_progress(root)
        print('CI failed at ' + stage + '; private logs retained', flush=True)
    finally:
        try:
            cleanup(root); summary['cleanup'] = 'PASS'
        except Exception:
            summary.update(outcome='FAIL', cleanup='FAIL')
        deploy.save(evidence / 'summary.json', summary)
    print('CI outcome: ' + summary['outcome'], flush=True)
    return 0 if summary['outcome'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['verify', 'cleanup'])
    parser.add_argument('--output', required=True)
    parser.add_argument('--chromium')
    parser.add_argument('--maven-settings')
    parser.add_argument('--maven-cache', default='work/m2')
    parser.add_argument('--compact',action='store_true',help='Explicit VFS override: compact only newly built own verification images, never PG/media')
    args = parser.parse_args()
    root = directory(args.output)
    if args.action == 'cleanup': cleanup(root); return 0
    deploy.require(args.chromium and Path(args.chromium).is_file(), 'Existing Chromium executable required')
    return verify(args, root)


if __name__ == '__main__':
    try: sys.exit(main())
    except Exception as error:
        print('CI refused: ' + type(error).__name__)
        sys.exit(1)
