// Run after extracting packages to .local/android-smoke/<id>/.
// PLAYWRIGHT_MODULE points to an installed @playwright/test (or playwright) module.
import {createRequire} from 'node:module';
import {readFile,stat} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const catalog=JSON.parse(await readFile('android/packages/catalog.json','utf8'));
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--enable-unsafe-swiftshader']});
let failures=0;
for(const game of catalog.games.filter(g=>g.kind==='web')){
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
 await page.screenshot({path:'.local/android-smoke/'+game.id+'.png'});
 const result={game:game.id,title:await page.title(),errors,missing:[...new Set(missing)],optionalNetwork:[...new Set(external)]};
 console.log(JSON.stringify(result));if(errors.length||missing.length)failures++;
 await context.close();
}
await browser.close();process.exitCode=failures?1:0;
