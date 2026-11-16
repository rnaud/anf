"""Complete file inventory for a game package; independent of its website's PWA manifest."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

NAME = 'package-manifest.json'

def included_files(root):
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError(f'Symlink in package: {path}')
        if path.is_file():
            rel = path.relative_to(root).as_posix()
            if rel != NAME and not any(p.startswith('.') for p in PurePosixPath(rel).parts):
                yield path, rel

def digest(stream):
    sha = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        sha.update(block)
    return sha.hexdigest()

def write_manifest(root, game):
    files = []
    for path, rel in included_files(root):
        with path.open('rb') as stream:
            files.append({'path': rel, 'bytes': path.stat().st_size, 'sha256': digest(stream)})
    if not any(f['path'] == 'index.html' for f in files):
        raise ValueError('Missing package entrypoint')
    manifest = {'schema': 1, 'id': game.get('id', ''), 'entrypoint': 'index.html',
                'totalBytes': sum(f['bytes'] for f in files), 'files': files}
    (root / NAME).write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest

def verify_archive(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate ZIP entries')
        manifest = json.loads(archive.read(NAME))
        if manifest.get('schema') != 1 or manifest.get('entrypoint') != 'index.html':
            raise ValueError('Invalid package manifest')
        records = manifest['files']
        paths = [f['path'] for f in records]
        if len(paths) != len(set(paths)) or set(names) != set(paths) | {NAME}:
            raise ValueError('Package file list does not match the manifest')
        if 'index.html' not in paths:
            raise ValueError('Missing package entrypoint')
        total = 0
        for item in records:
            rel = PurePosixPath(item['path'])
            if rel.is_absolute() or '..' in rel.parts or '\\' in str(rel):
                raise ValueError('Unsafe manifest path')
            info = archive.getinfo(str(rel))
            if info.file_size != item['bytes']:
                raise ValueError(f'Incorrect size: {rel}')
            with archive.open(info) as stream:
                if digest(stream) != item['sha256']:
                    raise ValueError(f'Incorrect checksum: {rel}')
            total += info.file_size
        if total != manifest['totalBytes']:
            raise ValueError('Incorrect expanded package size')
    return manifest
