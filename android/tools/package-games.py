#!/usr/bin/env python3
"""Build immutable web-game ZIPs + catalog from clean Git checkouts. Never edits source repos."""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile
from catalog_versions import LIVE_CATALOG, load_catalog, merge_published, needs_build, remote_head
from package_manifest import write_manifest, verify_archive
from source_assets import verify_mcm_assets
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'android/app/src/main/assets/catalog.json'

def run(args, cwd, **kwargs):
    subprocess.run(args, cwd=cwd, check=True, **kwargs)

def package(source, output, game=None):
    if not (source / 'index.html').is_file():
        raise ValueError(f'Missing index.html in {source}')
    write_manifest(source, game or {})
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for p in sorted(source.rglob('*')):
            if p.is_symlink():
                raise ValueError(f'Symlink in package: {p}')
            if p.is_file():
                rel = p.relative_to(source)
                if any(part.startswith('.') for part in rel.parts):
                    continue
                info = zipfile.ZipInfo(str(rel), date_time=(2020, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with p.open('rb') as inp, archive.open(info, 'w') as out:
                    shutil.copyfileobj(inp, out)
    if output.stat().st_size > 1500 * 1024**2:
        raise ValueError(f'{output.name} exceeds Android download limit')
    with zipfile.ZipFile(output) as z:
        if sum(i.file_size for i in z.infolist()) > 3 * 1024**3:
            raise ValueError(f'{output.name} exceeds Android extraction limit')
    verify_archive(output)
    digest = hashlib.sha256()
    with output.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def require_current_local(local, repo):
    revision = subprocess.check_output(['git','rev-parse','HEAD'],cwd=local,text=True).strip()
    remote = subprocess.check_output(['git','ls-remote',f'https://github.com/rnaud/{repo}.git','HEAD'],text=True).split()
    if not remote or remote[0] != revision:
        raise ValueError(f'{repo}: local checkout {revision[:12]} is not the current remote HEAD. Build without --local, update the checkout, or explicitly use --allow-stale-local for a historical build.')
    return revision

def build_game(game, args):
    """Build one game; its catalog entry changes only after the package is complete and verified."""
    with tempfile.TemporaryDirectory(prefix='anf-package-') as tmp:
        src=Path(tmp)/'source';src.mkdir(); local=args.local/game['repo'] if args.local else None
        if args.repack_existing:
            old=args.repack_existing/game['download'].split('/')[-1]
            with old.open('rb') as stream:
                sha=hashlib.sha256()
                for block in iter(lambda:stream.read(1024*1024),b''):sha.update(block)
            if sha.hexdigest()!=game['sha256']:raise ValueError(f'Incorrect source ZIP: {old}')
            dist=src/'dist';dist.mkdir();revision=game['revision']
            with zipfile.ZipFile(old) as archive:
                for name in archive.namelist():
                    dest=(dist/name).resolve()
                    if dist.resolve() not in dest.parents:raise ValueError('Unsafe source ZIP path')
                archive.extractall(dist)
        else:
            if local and (local/'.git').exists():
                if not args.allow_stale_local:require_current_local(local,game['repo'])
                # Stream the snapshot to avoid a second tar-sized copy of large game repos.
                with subprocess.Popen(['git','archive','--format=tar','HEAD'],cwd=local,stdout=subprocess.PIPE) as snapshot:
                    run(['tar','-xf','-','-C',str(src)],src,stdin=snapshot.stdout)
                    snapshot.stdout.close()
                    if snapshot.wait()!=0:raise RuntimeError('Git snapshot failed')
                revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=local,text=True).strip()
                if (local/'node_modules').exists(): (src/'node_modules').symlink_to((local/'node_modules').resolve(),target_is_directory=True)
            else:
                run(['git','clone','--depth=1',f'https://github.com/rnaud/{game["repo"]}.git',str(src)],Path(tmp))
                revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=src,text=True).strip()
            dist=src/'dist'
            if game['id']=='openkart':
                dist.mkdir()
                for name in ['index.html','src','assets','vendor','sw.js','offline-manifest.js']:
                    f=src/name
                    if f.is_dir():shutil.copytree(f,dist/name)
                    elif f.is_file():shutil.copy2(f,dist/name)
            else:
                if not (src/'node_modules').exists():
                    run(['npm','ci','--ignore-scripts','--no-audit','--no-fund'],src)
                env=dict(os.environ,HIRAETH_PUBLIC='1',PAGES_BASE='/')
                run(['node',str(src/'node_modules/vite/bin/vite.js'),'build','--configLoader=runner','--base=/','--outDir',str(dist),'--emptyOutDir'],src,env=env)
        if game['id']=='dust-valley' and not args.repack_existing and not args.allow_stale_local:
            verify_mcm_assets(src,dist)
        output=args.output/f'{game["id"]}-{revision[:12]}.zip'
        digest=package(dist,output,game)
        # Hash in filename makes catalog replacement safe even when rebuilding the same commit.
        final=args.output/f'{game["id"]}-{digest[:16]}.zip';output.replace(final)
        inventory_name=f'{game["id"]}-{digest[:16]}.manifest.json'
        shutil.copy2(dist/'package-manifest.json', args.output/inventory_name)
        inventory=json.loads((dist/'package-manifest.json').read_text())
        game.update(manifest=f'https://github.com/rnaud/anf/releases/download/game-library/{inventory_name}',fileCount=len(inventory['files']),expandedBytes=inventory['totalBytes'],sha256=digest,bytes=final.stat().st_size,revision=revision,download=f'https://github.com/rnaud/anf/releases/download/game-library/{final.name}')
        print(f'{game["title"]}: {final.stat().st_size/1024**2:.1f} MB · {digest[:12]}',flush=True)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repack-existing',type=Path,help='Add manifests to already-published ZIPs without changing game versions')
    p.add_argument('--allow-stale-local',action='store_true',help='Explicitly permit historical local snapshots (never the default)')
    p.add_argument('--local',type=Path,help='Use clean HEAD snapshots from sibling repos; missing repos are cloned')
    p.add_argument('--output',type=Path,default=ROOT/'android/packages')
    p.add_argument('--only',nargs='+',help='Build selected game IDs for development')
    p.add_argument('--bundle-catalog',action='store_true',help='Also put generated catalog into the APK assets')
    p.add_argument('--published-catalog',nargs='?',const=LIVE_CATALOG,help=f'Start from the package versions in this catalog URL or path (default: {LIVE_CATALOG})')
    p.add_argument('--changed-only',action='store_true',help='Only rebuild games whose remote HEAD differs from the catalog revision; other games keep their packages')
    p.add_argument('--skip-private',action='store_true',help='Skip private-repo games instead of failing when no repository token is configured')
    args=p.parse_args(); args.output.mkdir(parents=True,exist_ok=True)
    catalog=json.loads(CATALOG.read_text())
    if args.published_catalog:merge_published(catalog,load_catalog(args.published_catalog))
    built,failed=[],[]
    for game in catalog['games']:
        if game['kind']!='web' or (args.only and game['id'] not in args.only):continue
        # Without a repository token these repos are unreadable by design, not broken: keep what is published.
        if args.skip_private and game.get('private'):
            print(f'{game["title"]}: private repo, kept at {game.get("revision","unpublished")[:12]}',flush=True);continue
        try:
            if args.changed_only and not args.repack_existing:
                head=remote_head(game['repo'])
                if not needs_build(game,head):
                    print(f'{game["title"]}: current at {head[:12]}',flush=True);continue
                print(f'{game["title"]}: {game.get("revision","unpublished")[:12]} -> {head[:12]}',flush=True)
            build_game(game,args);built.append(game['id'])
        except Exception as e:
            # In update mode one broken game must not block the others; it keeps its published package.
            if not args.changed_only:raise
            failed.append(game['id']);print(f'::error::{game["title"]} failed to build: {e}',file=sys.stderr,flush=True)
    data=json.dumps(catalog,indent=2)+'\n'
    (args.output/'catalog.json').write_text(data)
    if args.bundle_catalog:CATALOG.write_text(data)
    print(f'Built: {", ".join(built) or "none"}' + (f' · failed: {", ".join(failed)}' if failed else ''),flush=True)
    if failed:sys.exit(1)

if __name__=='__main__':main()
