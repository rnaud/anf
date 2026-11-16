import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from source_assets import verify_mcm_assets

spec=importlib.util.spec_from_file_location('pack',Path(__file__).with_name('package-games.py'))
pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)

class SourceAssetsTest(unittest.TestCase):
    def fixture(self):
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup)
        root=Path(tmp.name);source=root/'source';dist=root/'dist';dist.mkdir()
        maps=source/'src/assets/mcm-maps';maps.mkdir(parents=True)
        for i in range(33):
            data=f'map{i}'.encode();(maps/f'map{i}.bin').write_bytes(data);(dist/f'map{i}-hashed.bin').write_bytes(data)
        for name in ['mcm-original-models.bin','mcm-core-scene.bin']:
            (source/'src/assets'/name).write_text(name);(dist/name).write_text(name)
        audio=source/'public/mcm-audio';audio.mkdir(parents=True);(audio/'bike.wav').write_bytes(b'audio')
        (dist/'mcm-audio').mkdir();(dist/'mcm-audio/bike.wav').write_bytes(b'audio')
        return source,dist
    def test_late_loaded_assets_present(self):
        s,d=self.fixture();self.assertEqual(verify_mcm_assets(s,d)['terrains'],33)
    def test_missing_map_rejected(self):
        s,d=self.fixture();(d/'map32-hashed.bin').unlink()
        with self.assertRaisesRegex(ValueError,'Missing'):verify_mcm_assets(s,d)
    def test_classic_only_source_rejected(self):
        s,d=self.fixture();(s/'src/assets/mcm-maps/map32.bin').unlink()
        with self.assertRaisesRegex(ValueError,'all 33'):verify_mcm_assets(s,d)
    def test_missing_audio_rejected(self):
        s,d=self.fixture();(d/'mcm-audio/bike.wav').unlink()
        with self.assertRaisesRegex(ValueError,'audio'):verify_mcm_assets(s,d)
    @patch.object(pack.subprocess,'check_output',side_effect=['a'*40+'\n','b'*40+'\tHEAD\n'])
    def test_stale_checkout_rejected(self,_):
        with self.assertRaisesRegex(ValueError,'not the current remote HEAD'):pack.require_current_local(Path('.'),'dust-valley-motocross')
    @patch.object(pack.subprocess,'check_output',side_effect=['a'*40+'\n','a'*40+'\tHEAD\n'])
    def test_current_checkout_accepted(self,_):
        self.assertEqual(pack.require_current_local(Path('.'),'dust-valley-motocross'),'a'*40)

if __name__=='__main__':unittest.main()
