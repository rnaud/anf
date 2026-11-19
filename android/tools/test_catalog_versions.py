import unittest
from catalog_versions import merge_published, needs_build

def web(id, **fields):
    return {'id': id, 'kind': 'web', 'title': id.title(), **fields}

class CatalogVersionsTest(unittest.TestCase):
    def test_uses_published_package_and_repo_metadata(self):
        repo = {'games': [web('siege', title='New title', revision='stale', sha256='a' * 64, download='old.zip')]}
        live = {'games': [web('siege', title='Old title', revision='live', sha256='b' * 64, download='live.zip', bytes=5)]}
        game = merge_published(repo, live)['games'][0]
        self.assertEqual((game['title'], game['revision'], game['download'], game['bytes']), ('New title', 'live', 'live.zip', 5))

    def test_unpublished_game_keeps_repo_entry(self):
        repo = {'games': [web('new', revision='r', download='x.zip')]}
        self.assertEqual(merge_published(repo, {'games': []})['games'][0]['download'], 'x.zip')

    def test_native_games_are_untouched(self):
        repo = {'games': [{'id': 'app', 'kind': 'native', 'packageName': 'a.b'}]}
        merge_published(repo, {'games': [{'id': 'app', 'kind': 'native', 'download': 'bad'}]})
        self.assertNotIn('download', repo['games'][0])

    def test_rebuilds_only_when_behind(self):
        self.assertFalse(needs_build(web('g', revision='abc', download='g.zip'), 'abc'))
        self.assertTrue(needs_build(web('g', revision='abc', download='g.zip'), 'def'))
        self.assertTrue(needs_build(web('g'), 'abc'))
        self.assertFalse(needs_build({'id': 'n', 'kind': 'native'}, 'abc'))

if __name__ == '__main__':
    unittest.main()
