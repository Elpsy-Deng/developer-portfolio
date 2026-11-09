import json
from pathlib import Path
import tempfile
import unittest
import ci_evidence as evidence


class EvidenceTests(unittest.TestCase):
    def test_https_evidence_requires_verified_tls_and_cleanup_and_omits_raw(self):
        path=self.root/'tls-result.json'
        raw={'outcome':'PASS','checksPassed':3,'certificateVerification':'ENABLED','cleanup':dict(containers=0,volumes=0,networks=0),'secret':'SECRET','checks':['SECRET1','SECRET2','SECRET3']}
        path.write_text(json.dumps(raw));self.assertNotIn('SECRET',json.dumps(evidence.https_summary(path)))
        for change in ({'outcome':'FAIL'},{'checksPassed':0},{'checksPassed':True},{'checks':[]},{'checks':['x','x','x']},{'certificateVerification':'DISABLED'},{'cleanup':{'containers':1}}):
            path.write_text(json.dumps({**raw,**change}))
            with self.assertRaises(ValueError):evidence.https_summary(path)
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def report(self, **counts):
        values = dict(tests=3, failures=0, errors=0, skipped=0)
        values.update(counts)
        attributes = ' '.join(f'{k}="{v}"' for k, v in values.items())
        (self.root / 'TEST-synthetic.xml').write_text(f'<testsuite {attributes}><system-out>SECRET-cookie-hash</system-out></testsuite>')

    def runtime(self, **changes):
        raw = {'outcome': 'PASS', 'passed': 1, 'checks': ['SECRET-check'],
               'browser': {'expected': 43, 'unexpected': 0, 'skipped': 0, 'flaky': 0, 'password': 'SECRET'},
               'cleanup': {'containers': 0, 'volumes': 0, 'networks': 0}, 'error': 'SECRET', 'env': 'SECRET',
               'showcase': {'outcome': 'PASS', 'checksPassed': 1, 'browser': {'expected': 5, 'unexpected': 0, 'skipped': 0, 'flaky': 0}}}
        raw.update(changes)
        path = self.root / 'result.json'
        path.write_text(json.dumps(raw))
        return path

    def test_raw_details_are_never_projected(self):
        self.report()
        projected = {'backend': evidence.backend_summary(self.root), 'runtime': evidence.runtime_summary(self.runtime())}
        self.assertNotIn('SECRET', json.dumps(projected))
        self.assertEqual(projected['backend']['tests'], 3)
        self.assertEqual(projected['runtime']['browser']['expected'], 43)
        runtime=self.root/'runtime';(runtime/'browser').mkdir(parents=True)
        (runtime/'result.json').write_text(json.dumps({'passed':3,'error':'SECRET'}))
        (runtime/'browser/report.json').write_text(json.dumps({'stats':{'expected':42,'unexpected':1,'skipped':0,'flaky':0,'password':'SECRET'},'suites':[{'title':'SECRET','specs':[{'ok':False,'line':42,'title':'SECRET','errors':['SECRET']},{'ok':False,'line':'SECRET'}]}]}))
        progress=evidence.failure_progress(self.root)
        self.assertNotIn('SECRET',json.dumps(progress));self.assertEqual(progress['browser']['failedSourceLines'],[42])

    def test_backend_missing_empty_or_malformed_cannot_pass(self):
        for create in (lambda: None, lambda: self.report(tests=0), lambda: self.report(tests='invalid')):
            create()
            with self.assertRaises(ValueError): evidence.backend_summary(self.root)

    def test_backend_failure_error_skip_or_negative_cannot_pass(self):
        for key in ('failures', 'errors', 'skipped'):
            for count in (1, -1):
                self.report(**{key: count})
                with self.assertRaises(ValueError): evidence.backend_summary(self.root)

    def test_failed_or_incomplete_runtime_cannot_pass(self):
        for change in ({'outcome': 'FAIL'}, {'passed': 2}, {'passed': True}, {'checks': []}, {'checks': [None]}, {'cleanup': {}}):
            with self.assertRaises(ValueError): evidence.runtime_summary(self.runtime(**change))

    def test_leftover_resource_or_boolean_count_cannot_pass(self):
        for value in (1, False):
            with self.assertRaises(ValueError):
                evidence.runtime_summary(self.runtime(cleanup={'containers': value, 'volumes': 0, 'networks': 0}))

    def test_browser_failure_skip_flaky_or_missing_cannot_pass(self):
        for key in ('unexpected', 'skipped', 'flaky'):
            values = dict(expected=43, unexpected=0, skipped=0, flaky=0)
            values[key] = 1
            with self.assertRaises(ValueError): evidence.runtime_summary(self.runtime(browser=values))
        with self.assertRaises(ValueError): evidence.runtime_summary(self.runtime(browser={}))

    def test_missing_or_failed_showcase_cannot_pass(self):
        for value in ({}, {'outcome': 'FAIL'}):
            with self.assertRaises(ValueError): evidence.runtime_summary(self.runtime(showcase=value))


if __name__ == '__main__': unittest.main()
