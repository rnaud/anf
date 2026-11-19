#!/usr/bin/env bash
# Upload the packages referenced by android/packages/catalog.json, then switch the catalog.
# Usage: publish-release.sh [extra release files...]
set -euo pipefail
dir=android/packages
[ -f "$dir/catalog.json" ] || { echo 'No catalog was built.'; exit 0; }
files=()
while IFS= read -r line; do files+=("$line"); done < <(python3 - "$dir" <<'EOF'
import json, os, sys
d = sys.argv[1]
for game in json.load(open(os.path.join(d, 'catalog.json')))['games']:
    for key in ('download', 'manifest'):
        path = os.path.join(d, game.get(key, '').rsplit('/', 1)[-1])
        if game.get(key) and os.path.isfile(path):
            print(path)
EOF
)
[ $# -gt 0 ] && files+=("$@")
if [ ${#files[@]} -eq 0 ]; then echo 'All game packages are already published.'; exit 0; fi
gh release view game-library >/dev/null 2>&1 || gh release create game-library --title 'anf games for Android' --notes 'Install anf-games.apk. Browser games download inside the app. Jumpman and Alexandria open separately.'
# Immutable packages go up first; the catalog is the last file switched, so the app never sees a missing package.
gh release upload game-library "${files[@]}" --clobber
gh release upload game-library "$dir/catalog.json" --clobber
printf 'Published %s\n' "${files[@]}"
