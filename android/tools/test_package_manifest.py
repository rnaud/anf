import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from package_manifest import write_manifest, verify_archive

class PackageManifestTest(unittest.TestCase):
    def archive(self, mutation=None):
        tmp = tempfile.TemporaryDirectory(); self.addCleanup(tmp.cleanup)
        root = Path(tmp.name); source = root / 'game'; source.mkdir()
        (source / 'index.html').write_text('<script src="late-level.js"></script>')
        (source / 'late-level.js').write_text('export const level = 2;')
        (source / 'sound.ogg').write_bytes(bytes(range(256)))
        write_manifest(source, {'id': 'test-game'})
        if mutation: mutation(source)
        archive = root / 'game.zip'
        with zipfile.ZipFile(archive, 'w') as z:
            for path in source.iterdir(): z.write(path, path.name)
        return archive
    def test_covers_late_assets(self):
        result = verify_archive(self.archive())
        self.assertEqual({f['path'] for f in result['files']}, {'index.html','late-level.js','sound.ogg'})
    def test_rejects_missing_asset(self):
        with self.assertRaisesRegex(ValueError,'file list'): verify_archive(self.archive(lambda p:(p/'sound.ogg').unlink()))
    def test_rejects_unlisted_asset(self):
        with self.assertRaisesRegex(ValueError,'file list'): verify_archive(self.archive(lambda p:(p/'extra').write_text('extra')))
    def test_rejects_corrupted_asset(self):
        with self.assertRaisesRegex(ValueError,'checksum'): verify_archive(self.archive(lambda p:(p/'sound.ogg').write_bytes(b'X'*256)))
    def test_rejects_wrong_size(self):
        with self.assertRaisesRegex(ValueError,'size'): verify_archive(self.archive(lambda p:(p/'sound.ogg').write_bytes(b'X')))
    def test_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'index.html').write_text('ok');(root/'bad').symlink_to(root/'index.html')
            with self.assertRaisesRegex(ValueError,'Symlink'):write_manifest(root,{})

if __name__ == '__main__': unittest.main()
