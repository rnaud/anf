"""Track which published game packages are behind their source repositories."""
import json
import subprocess
import urllib.request

LIVE_CATALOG = 'https://github.com/rnaud/anf/releases/download/game-library/catalog.json'
PACKAGE_FIELDS = ('sha256', 'bytes', 'revision', 'download', 'manifest', 'fileCount', 'expandedBytes')

def load_catalog(location):
    if location.startswith('https://'):
        request = urllib.request.Request(location, headers={'User-Agent': 'anf-games-packager'})
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    with open(location) as stream:
        return json.load(stream)

def merge_published(catalog, published):
    """Keep game metadata from the repo, but package versions from what is actually published."""
    live = {g['id']: g for g in published.get('games', [])}
    for game in catalog['games']:
        old = live.get(game['id'])
        if game['kind'] != 'web' or not old or not old.get('download'):
            continue
        for field in PACKAGE_FIELDS:
            if field in old:
                game[field] = old[field]
            else:
                game.pop(field, None)
    return catalog

def remote_head(repo):
    out = subprocess.check_output(['git', 'ls-remote', f'https://github.com/rnaud/{repo}.git', 'HEAD'], text=True).split()
    if not out:
        raise ValueError(f'{repo}: no remote HEAD')
    return out[0]

def needs_build(game, head):
    return game['kind'] == 'web' and (not game.get('download') or game.get('revision') != head)
