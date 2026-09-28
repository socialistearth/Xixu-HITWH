/* Optional real-DOM regression. Usage: NODE_PATH=<playwright node_modules>
   node tests/login-dom.cjs <chromium executable>. All requests are fixtures. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..'),script=fs.readFileSync(path.join(root,'app/src/main/assets/login-flow.js'),'utf8');
const fixture=fs.readFileSync(path.join(__dirname,'fixtures/vpn-resources.html'),'utf8');
const homepage=fs.readFileSync(path.join(__dirname,'fixtures/vpn-homepage.html'),'utf8');
const diagnostic=fs.readFileSync(path.join(root,'app/src/main/assets/login-diagnostics.js'),'utf8');
const previous=process.argv[3]?fs.readFileSync(process.argv[3],'utf8'):null;
const VPN='https://webvpn2.hitwh.edu.cn';
const destinations={news:VPN+'/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b/',academic:VPN+'/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/'};
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[2],headless:true,args:['--no-sandbox']});
 try{
  // The actual mobile route was redacted in diagnostics. These are synthetic
  // different paths proving recognition is based on cards, not guessed names.
  for(const routePath of ['/','/index','/mobile/synthetic-resource-panel'])for(const target of ['academic','news']){
   const context=await browser.newContext({viewport:{width:360,height:640}});
   await context.route('**/*',route=>route.fulfill({status:200,contentType:'text/html; charset=utf-8',body:/^\/(?:http|https)\//.test(new URL(route.request().url()).pathname)?homepage:fixture}));
   const page=await context.newPage();await page.goto(VPN+routePath);
   await page.evaluate(()=>{
    window.__controlClicks=0;window.__resourceClicks=0;
    document.querySelectorAll('h1')[1].style.marginTop='1000px';
    for(let i=0;i<25;i++){const heading=document.createElement('h2');heading.textContent='其他资源'+i;document.body.appendChild(heading);}
    for(const svg of document.querySelectorAll('svg'))svg.addEventListener('click',event=>{window.__controlClicks++;event.stopPropagation();});
    for(const anchor of document.querySelectorAll('a.block-group__item'))anchor.addEventListener('click',()=>{
     window.__resourceClicks++;throw new Error('Native resource navigation must not depend on this popup handler');
    });
   });
   if(target==='news')assert.ok((await page.locator('h2[title="校主页"]').last().boundingBox()).y>640,'duplicate school homepage card is below the mobile viewport');
   const counts=await page.evaluate(({diagnostic,target})=>JSON.parse((0,eval)(diagnostic)({target})),{diagnostic,target});
   assert.equal(counts.headings,29);assert.equal(counts.targetHeadings,2);assert.equal(counts.renderedTargets,2);assert.equal(counts.exactCards,2);assert.equal(counts.iframes,0);assert.equal(counts.viewport,360);
   if(previous&&routePath!=='/'){
    const old=await page.evaluate(({previous,target})=>JSON.parse((0,eval)(previous)({target,mode:'inspect'})),{previous,target});
    assert.equal(old.state,'unknown');
   }
   const result=await page.evaluate(({script,target})=>JSON.parse((0,eval)(script)({target})),{script,target});
   assert.equal(result.state,'resource');assert.equal(result.dashboard,true);assert.equal(result.resourceUrl,destinations[target]);
   assert.equal(await page.evaluate(()=>window.__resourceClicks),0);assert.equal(await page.evaluate(()=>window.__controlClicks),0);
   // Android calls WebView.loadUrl with the validated inspect result. The page
   // does not need to emit a popup or return a JS value while navigating away.
   await page.goto(result.resourceUrl);
   assert.equal(context.pages().length,1,'resource remains in the existing WebView');
   if(target==='news'){
    for(const path of ['', 'main.psp','index.psp']){
     await page.goto(destinations[target]+path+'?wrdrecordvisit=12345');
     assert.equal((await page.evaluate(({script,target})=>JSON.parse((0,eval)(script)({target})),{script,target})).state,'ready','manual/home navigation has no remembered resourceUrl or SiteDomain metadata');
    }
   }
   await context.close();
  }
  console.log('Real DOM login regression passed: 360px root and synthetic non-root routes, 29 headings/2 exact cards/0 iframes, both destinations, no popup clicks; '+(previous?'0.7.2 stays unknown on same non-root DOM while new script returns resource.':'known homepage recognition also passed.'));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
