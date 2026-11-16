// Run after extracting packages to .local/android-smoke/<id>/.
// PLAYWRIGHT_MODULE points to an installed @playwright/test (or playwright) module.
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {readFile,stat} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const catalog=JSON.parse(await readFile(process.env.CATALOG || 'android/packages/catalog.json','utf8'));
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--enable-webgl','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
let failures=0;
for(const game of catalog.games.filter(g=>g.kind==='web'&&(!process.env.GAME_ID||g.id===process.env.GAME_ID))){
 const root=resolve('.local/android-smoke',game.id);const host=game.id+'.games.anf.invalid';
 const context=await browser.newContext({viewport:{width:960,height:540},serviceWorkers:'block'});
 const page=await context.newPage();const errors=[],missing=[],external=[];
 page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/*',async route=>{
  const url=new URL(route.request().url());
  if(url.hostname!==host){external.push(url.href);return route.abort();}
  let path=resolve(root,'.'+decodeURIComponent(url.pathname));if(url.pathname.endsWith('/'))path=resolve(path,'index.html');
  if(!path.startsWith(root+'/'))return route.abort();
  try{await stat(path);const types={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.wasm':'application/wasm','.webp':'image/webp','.png':'image/png','.svg':'image/svg+xml'};await route.fulfill({path,contentType:types[extname(path)]||'application/octet-stream'});}
  catch{missing.push(url.pathname);await route.fulfill({status:404,body:'Not found'});}
 });
 await page.goto('https://'+host+'/');await page.waitForTimeout(4000);
 if(game.id==='dust-valley'&&!process.env.MCM_ONLY){
  await page.waitForFunction(()=>window.__game);
  for(const mode of ['race','free']){
   if(mode==='free'){await page.click('#pause');await page.click('#home');await page.click('[data-mode=free]');}
   await page.click('#start');
   if(await page.locator('#tutorial').isVisible())await page.click('#tutorial-start');
   await page.waitForFunction(()=>window.__game.snapshot().phase==='playing'&&window.__game.snapshot().countdown<=0);
   await page.keyboard.down('KeyW');
   await page.waitForFunction(()=>window.__game.snapshot().speed>6,null,{timeout:60000});
   await page.keyboard.up('KeyW');
   assert.equal(await page.evaluate(()=>window.__game.snapshot().mode),mode);
   await page.screenshot({path:'.local/android-smoke/dust-valley-'+mode+'.png'});
  }
 }
 if(game.id==='dust-valley'&&process.env.MCM_ONLY){
  await page.waitForFunction(()=>window.__game);
  await page.selectOption('#physics-mode','mcm-preview');
  await page.uncheck('#sand-mode');
  await page.click('[data-mode=free]');
  for(const [map,rider,bike] of [['quarries-dunes','rider01','bike01'],['season-week10','rider12','bike12']]){
   await page.selectOption('#mcm-map',map);
   await page.selectOption('#mcm-rider',rider);await page.selectOption('#mcm-bike',bike);
   await page.click('#start');
   await page.waitForFunction(()=>['playing','tutorial'].includes(window.__game.snapshot().phase),null,{timeout:120000});
   if(await page.locator('#tutorial').isVisible())await page.click('#tutorial-start');
   await page.waitForFunction(()=>window.__game.snapshot().frame>=5,null,{timeout:120000});
   const result=await page.evaluate(()=>{const s=window.__game.snapshot();return {assets:s.originalAssets,frame:s.frame,finite:[s.x,s.y,s.z].every(Number.isFinite)};});
   assert.equal(result.assets.map,map);assert.equal(result.assets.rider,rider);assert.equal(result.assets.bike,bike);
   assert.equal(result.assets.parts,21);assert.equal(result.assets.terrainTextures,true);
   assert.equal(result.assets.missingTerrainTextures,0);assert.ok(result.finite);
   console.log(JSON.stringify({mcmPreview:result}));
   await page.screenshot({path:'.local/android-smoke/mcm-'+map+'.png'});
   await page.click('#pause');await page.click('#home');
  }
 }
 await page.screenshot({path:'.local/android-smoke/'+game.id+'.png'});
 const result={game:game.id,title:await page.title(),errors,missing:[...new Set(missing)],blockedNetwork:[...new Set(external)]};
 console.log(JSON.stringify(result));if(errors.length||missing.length)failures++;
 await context.close();
}
await browser.close();process.exitCode=failures?1:0;
