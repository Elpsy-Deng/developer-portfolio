import copy
import unittest
import showcase_release as release


class ReleaseTests(unittest.TestCase):
    def current(self):
        return {'project': {'slug': 'test', 'title': 'My edited title', 'role': 'Private role field',
                            'description': 'Original', 'features': ['Old'], 'coverImage': {'src': '/old.png'},
                            'screenshots': [{'title': 'My image'}], 'localDemoUrl': 'http://localhost'},
                'version': 8, 'published': False, 'sortOrder': 99}

    def test_declared_patch_preserves_edited_fields_and_separates_media(self):
        current = self.current()
        after = release.proposed(current, {'features': ['New']})
        self.assertEqual(after['title'], 'My edited title')
        self.assertEqual(after['role'], 'Private role field')
        self.assertEqual(after['description'], 'Original')
        self.assertNotIn('screenshots', after)
        self.assertNotIn('coverImage', after)
        self.assertEqual(current['project']['features'], ['Old'])

    def test_stale_version_text_publish_sort_and_media_refuse_before_writes(self):
        original = self.current()
        target = {'beforeSha256': release.fingerprint(original), 'expectedVersion': 8}
        release.preflight(original, target)
        for change in ('version', 'published', 'sortOrder', 'text', 'media'):
            current = copy.deepcopy(original)
            if change == 'version': current['version'] += 1
            elif change == 'published': current['published'] = True
            elif change == 'sortOrder': current['sortOrder'] = 1
            elif change == 'text': current['project']['title'] = 'Other editor'
            else: current['project']['screenshots'].append({'title': 'New upload'})
            with self.assertRaises(RuntimeError): release.preflight(current, target)

    def test_fingerprint_ignores_key_order_but_not_value_changes(self):
        self.assertEqual(release.fingerprint({'b': 1, 'a': 2}), release.fingerprint({'a': 2, 'b': 1}))
        self.assertNotEqual(release.fingerprint({'a': 2}), release.fingerprint({'a': 3}))

    def test_committed_release_hashes_and_patch_allowlist_are_valid(self):
        data = release.bundle()
        self.assertEqual(len(data['projects']), 5)
        self.assertEqual(len({p['slug'] for p in data['projects']}), 5)
        for project in data['projects']:
            self.assertNotIn('role', project['patch'])
            self.assertNotIn('published', project['patch'])
            self.assertNotIn('title', project['patch'])
            self.assertTrue(project['media'])


if __name__ == '__main__': unittest.main()
