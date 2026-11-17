#!/usr/bin/env python3
"""Emulator-only UI smoke test. Seeds a tiny fixture in the separate debug app."""
import argparse, io, json, re, subprocess, tarfile, time, xml.etree.ElementTree as ET
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('--adb',default='adb');parser.add_argument('--serial',default='emulator-5554');args=parser.parse_args()
assert args.serial.startswith('emulator-'), 'This test only seeds the isolated debug app on an emulator.'
ADB=[args.adb,'-s',args.serial];PACKAGE='io.anf.games.debug'
def adb(*args,**kw):return subprocess.check_output([*ADB,*args],timeout=30,**kw)
def tree():
    adb('shell','uiautomator','dump','/sdcard/anf-debug-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/anf-debug-ui.xml'))
def find(value):
    for node in tree().iter('node'):
        if value.casefold()==(node.get('text') or '').casefold() or (node.get('content-desc') or '').casefold().startswith(value.casefold()+', '):return node
    return None
def tap(value):
    deadline=time.monotonic()+20;node=find(value)
    while node is None and time.monotonic()<deadline:
        time.sleep(.25);node=find(value)
    assert node is not None, f'Missing UI: {value}'
    a=list(map(int,re.findall(r'\d+',node.get('bounds'))))
    adb('shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2))
def screenshot(name):
    Path('.local/cartridge-qa').mkdir(parents=True,exist_ok=True)
    data=adb('exec-out','screencap','-p');Path('.local/cartridge-qa',name+'.png').write_bytes(data)

game=json.loads(Path('android/app/src/main/assets/catalog.json').read_text())['games'][0]
html=b'''<!doctype html><meta name="viewport" content="width=device-width"><style>body{background:#203e32;color:white;font:22px sans-serif;padding:20px}button{padding:20px}</style><h1>Navigation fixture</h1><button onclick="this.textContent='Control clicked'">Game control</button><p id="status">Session retained</p><script>history.pushState({},'', '?game=running');window.addEventListener('keydown',e=>{if(e.key==='Escape'||e.key==='BrowserBack'){e.preventDefault();e.stopPropagation();}});</script>'''
buffer=io.BytesIO()
with tarfile.open(fileobj=buffer,mode='w') as archive:
    for name,body in [(f'files/games/{game["id"]}/{game["sha256"]}/index.html',html),(f'files/games/{game["id"]}/current',game['sha256'].encode())]:
        info=tarfile.TarInfo(name);info.size=len(body);archive.addfile(info,io.BytesIO(body))
adb('shell','settings','put','system','accelerometer_rotation','0')
adb('shell','settings','put','system','user_rotation','0')
adb('shell','am','force-stop',PACKAGE)
adb('shell','run-as',PACKAGE,'tar','-xf','-',input=buffer.getvalue())
adb('shell','am','start','-n',PACKAGE+'/io.anf.games.MainActivity');time.sleep(1)
screenshot('portrait-shelf');tap(game['title']);tap('Play');time.sleep(1)
tap('Game control');assert find('Control clicked') is not None
# System/physical Back with focus in a WebView which has navigation history.
adb('shell','input','keyevent','4');assert find('Exit '+game['title']+'?') is not None
screenshot('exit-dialog');tap('Keep playing');assert find('Control clicked') is not None
# Explicit library control offers the same choice and tears down the game.
tap('‹ Library');assert find('Exit game') is not None;tap('Exit game');assert find('Pick a cartridge.') is not None
# Downloaded filtering and details survive navigating back to the shelf.
tap('Downloaded');tap(game['title']);tap('Play');time.sleep(1)
adb('shell','input','keyevent','4');tap('Exit game');assert find('Pick a cartridge.') is not None
# Some tablet emulator images expose gesture navigation but detach the edge handler.
# Do not mistake that system-level limitation for either an app pass or an app failure.
system_ui=adb('shell','dumpsys','activity','service','com.android.systemui/.SystemUIService').decode()
handler=system_ui.split('EdgeBackGestureHandler:',1)[-1][:500]
if 'mIsAttached=true' in handler and 'mIsEnabled=true' in handler:
    tap(game['title']);tap('Play');tap('Game control')
    adb('shell','input','swipe','1','350','140','350','350');tap('Exit game')
    assert find('Pick a cartridge.') is not None
    print('PASS: edge Back gesture')
else:
    print('SKIP: emulator SystemUI edge Back handler is detached; physical Back and toolbar are tested.')
tap('All games')
adb('shell','settings','put','system','user_rotation','1');time.sleep(1)
visible=[n for n in tree().iter('node') if 'Open game details.' in (n.get('content-desc') or '')]
assert len(visible)>=4, 'Landscape must show at least four cartridges'
screenshot('landscape-shelf')
adb('shell','settings','put','system','user_rotation','0');time.sleep(1)
screenshot('portrait-shelf')
print('PASS: cartridge details, Play, focused WebView Back key, keep-playing session, explicit exit, downloaded filter, portrait/landscape grid.')
