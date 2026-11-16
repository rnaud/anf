"""Ensure late-loaded MCM inputs survive the production build."""
from pathlib import Path
from package_manifest import digest

def verify_mcm_assets(source, dist):
    maps = sorted((source / 'src/assets/mcm-maps').glob('*.bin'))
    if len(maps) < 33:
        raise ValueError(f'MCM preview requires all 33 terrain files; found {len(maps)}')
    required = maps + [source / 'src/assets/mcm-original-models.bin', source / 'src/assets/mcm-core-scene.bin']
    output_hashes = set()
    for path in dist.rglob('*'):
        if path.is_file():
            with path.open('rb') as stream: output_hashes.add(digest(stream))
    for path in required:
        with path.open('rb') as stream: sha = digest(stream)
        if sha not in output_hashes: raise ValueError(f'Missing or changed MCM build asset: {path.name}')
    audio = sorted((source / 'public/mcm-audio').glob('*'))
    if not audio: raise ValueError('MCM audio is missing')
    for path in audio:
        target = dist / 'mcm-audio' / path.name
        if not target.is_file() or target.read_bytes() != path.read_bytes():
            raise ValueError(f'Missing or changed MCM audio: {path.name}')
    return {'terrains':len(maps),'modelAndSceneBundles':2,'audioFiles':len(audio)}
