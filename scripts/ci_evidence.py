"""Strict projection of private test reports into safe, numeric CI evidence."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def backend_summary(directory):
    reports = sorted(Path(directory).glob('TEST-*.xml'))
    if not reports:
        raise ValueError('Missing backend reports')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        root = ET.parse(report).getroot()
        if root.tag != 'testsuite':
            raise ValueError('Expected suite report')
        for key in totals:
            value = int(root.attrib[key])
            if value < 0:
                raise ValueError('Negative report count')
            totals[key] += value
    if totals['tests'] <= 0 or any(totals[k] for k in ('failures', 'errors', 'skipped')):
        raise ValueError('Backend did not fully pass')
    return {'suites': len(reports), **totals}


def runtime_summary(path):
    raw = json.loads(Path(path).read_text())
    checks = raw.get('checks')
    passed = raw.get('passed')
    if raw.get('outcome') != 'PASS' or type(passed) is not int or not isinstance(checks, list) or passed <= 0 or passed != len(checks):
        raise ValueError('Incomplete container acceptance')
    if not all(isinstance(check, str) and check for check in checks) or len(set(checks)) != len(checks):
        raise ValueError('Invalid acceptance checks')
    clean = raw.get('cleanup', {})
    if any(type(clean.get(k)) is not int or clean[k] != 0 for k in ('containers', 'volumes', 'networks')):
        raise ValueError('Incomplete resource cleanup')
    browser = raw.get('browser', {})
    if type(browser.get('expected')) is not int or browser['expected'] <= 0:
        raise ValueError('Missing browser acceptance')
    if any(type(browser.get(k)) is not int or browser[k] != 0 for k in ('unexpected', 'skipped', 'flaky')):
        raise ValueError('Browser did not fully pass')
    showcase = raw.get('showcase', {})
    showcase_browser = showcase.get('browser', {})
    if showcase.get('outcome') != 'PASS' or type(showcase.get('checksPassed')) is not int or showcase['checksPassed'] <= 0:
        raise ValueError('Missing showcase acceptance')
    if showcase_browser.get('expected') != 5 or any(type(showcase_browser.get(k)) is not int or showcase_browser[k] != 0 for k in ('unexpected', 'skipped', 'flaky')):
        raise ValueError('Showcase browser did not fully pass')
    # No arbitrary strings, environment, report details, fixture data or paths.
    return {'checksPassed': passed, 'browser': {k: browser[k] for k in ('expected', 'unexpected', 'skipped', 'flaky')},
            'cleanup': {k: clean[k] for k in ('containers', 'volumes', 'networks')},
            'showcase': {'checksPassed': showcase['checksPassed'], 'browser': {k: showcase_browser[k] for k in ('expected', 'unexpected', 'skipped', 'flaky')}}}


def https_summary(path):
    raw=json.loads(Path(path).read_text())
    if raw.get('outcome')!='PASS' or type(raw.get('checksPassed')) is not int or raw['checksPassed']<=0:
        raise ValueError('Incomplete HTTPS acceptance')
    checks=raw.get('checks')
    if not isinstance(checks,list) or len(checks)!=raw['checksPassed'] or not all(isinstance(c,str) and c for c in checks) or len(set(checks))!=len(checks):
        raise ValueError('Incomplete HTTPS assertion list')
    if raw.get('certificateVerification')!='ENABLED' or any(type(raw.get('cleanup',{}).get(k)) is not int or raw['cleanup'][k]!=0 for k in ('containers','volumes','networks')):
        raise ValueError('Unsafe/incomplete HTTPS acceptance')
    return {'checksPassed':raw['checksPassed'],'certificateVerification':'ENABLED','cleanup':{k:raw['cleanup'][k] for k in ('containers','volumes','networks')}}


def failure_progress(root):
    """Diagnostics select only bounded numbers and test source line numbers, never raw errors/titles/paths."""
    result={}
    for name,relative,count in [('container','runtime/result.json','passed'),('showcase','runtime/showcase/checks.json','passed'),('https','https-runtime/result.json','checksPassed')]:
        path=Path(root)/relative
        if path.exists():
            try:
                value=json.loads(path.read_text()).get(count)
                if type(value) is int and 0<=value<=1000:result[name+'ChecksCompleted']=value
                if name=='https':
                    raw=json.loads(path.read_text())
                    for key in ('failureOperation','failureCode','failureTypeCode','lastHttpStatus','failedHttpStatus','cookiePolicyBits'):
                        value=raw.get(key)
                        if type(value) is int and 0<=value<=1000:result[key]=value
                    frames=raw.get('sourceFrames',[])
                    if isinstance(frames,list):result['sourceFrames']=[{'fileCode':f['fileCode'],'line':f['line']} for f in frames if isinstance(f,dict) and type(f.get('fileCode')) is int and 1<=f['fileCode']<=5 and type(f.get('line')) is int and 1<=f['line']<=10000]
            except (OSError,ValueError):pass
    for name,relative in [('browser','runtime/browser/report.json'),('showcaseBrowser','runtime/showcase/browser/report.json')]:
        path=Path(root)/relative
        if not path.exists():continue
        try:
            raw=json.loads(path.read_text());stats=raw.get('stats',{})
            if all(type(stats.get(k)) is int and 0<=stats[k]<=1000 for k in ('expected','unexpected','skipped','flaky')):
                result[name]={k:stats[k] for k in ('expected','unexpected','skipped','flaky')}
                lines=[];assertions=[];session_statuses=[]
                def visit(suites):
                    for suite in suites:
                        for spec in suite.get('specs',[]):
                            if spec.get('ok') is False:
                                if type(spec.get('line')) is int and 1<=spec['line']<=10000:lines.append(spec['line'])
                                for test in spec.get('tests',[]):
                                    for annotation in test.get('annotations',[]):
                                        value=annotation.get('description')
                                        if annotation.get('type')=='session-http-status' and isinstance(value,str) and len(value)==3 and value.isascii() and value.isdigit() and 400<=int(value)<=599:session_statuses.append(int(value))
                                    for run in test.get('results',[]):
                                        for error in [run.get('error',{}),*run.get('errors',[])]:
                                            line=error.get('location',{}).get('line')
                                            if type(line) is int and 1<=line<=10000:assertions.append(line)
                        visit(suite.get('suites',[]))
                visit(raw.get('suites',[]));result[name]['failedSourceLines']=sorted(set(lines));result[name]['failedAssertionLines']=sorted(set(assertions));result[name]['failedSessionHttpStatuses']=sorted(set(session_statuses))
        except (OSError,ValueError,TypeError,AttributeError):pass
    return result
