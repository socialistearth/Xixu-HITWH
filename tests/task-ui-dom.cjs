// Offline mobile DOM flow. Run: NODE_PATH=<playwright modules> node tests/task-ui-dom.cjs <chromium executable>
const {chromium}=require('playwright');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.argv[2],args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:393,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>r.request().url().startsWith('file:')?r.continue():r.abort());
  await page.goto('file://'+root+'/app/src/main/assets/index.html');
  const originalUrl=page.url();
  await page.evaluate(async()=>{
   demo=false;window.calls=[];window.toasts=[];toast=m=>toasts.push(m);
   state.studentId='test-student';state.hasPassword=true;window.saved=structuredClone(state);
   window.academicStatus={busy:false,needsVerification:false,progress:-1,stage:''};
   window.newsSnapshot={configured:true,busy:false,needsVerification:false,progress:-1,curation:{status:'idle'},config:{baseUrl:'https://model.example/v1',model:'test',hasApiKey:true},messages:[]};
   nativeCall=async(method,arg)=>{
    calls.push({method,arg});
    if(method==='syncNow'||method==='startupSync'){
     academicStatus={busy:true,needsVerification:false,progress:-1,stage:'正在登录校园 VPN'};
     return new Promise((resolve,reject)=>{window.finishAcademic=resolve;window.failAcademic=reject;});
    }
    if(method==='syncStatus')return structuredClone(academicStatus);
    if(method==='syncVerify'){academicStatus={busy:true,needsVerification:false,stage:'正在读取课表',progress:40};return structuredClone(academicStatus);}
    if(method==='applyStartupSync')return {...structuredClone(saved),syncReport:arg.report};
    if(method==='bootstrap')return {...structuredClone(saved),syncTask:structuredClone(academicStatus)};
    if(method==='newsCurate')newsSnapshot={...newsSnapshot,busy:true,needsVerification:false,progress:-1,stage:'正在打开校主页',curation:{status:'running'}};
    if(method==='newsVerify')newsSnapshot={...newsSnapshot,busy:true,needsVerification:false,progress:55,stage:'正在读取校主页新闻 3/6'};
    return structuredClone(newsSnapshot);
   };
   NewsUI.reset();await NewsUI.load();render();
  });
  await page.locator('#sync').click();assert.equal(page.url(),originalUrl);
  assert.equal(await page.locator('#sync .task-spinner').count(),1);assert.equal(await page.locator('#sync').isDisabled(),true);
  assert.equal(await page.locator('.task-track.indeterminate').count(),1);
  await page.locator('[data-tab="user"]').click();assert.equal(await page.locator('#open-vpn .task-spinner').count(),1);
  await page.evaluate(()=>academicStatus={busy:true,needsVerification:true,progress:-1,stage:'需要填写验证码'});
  await page.waitForFunction(()=>document.getElementById('open-vpn').textContent==='手动验证',{},{timeout:4000});
  assert.equal(await page.locator('#open-vpn').isEnabled(),true);
  assert.equal(await page.locator('.needs-verification .task-track span').evaluate(el=>getComputedStyle(el).animationName),'none');
  assert.equal(await page.evaluate(()=>calls.filter(x=>['portal','syncVerify'].includes(x.method)).length),0);
  await page.locator('#open-vpn').click();assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='syncVerify').length),1);
  await page.locator('[data-tab="query"]').click();assert.match(await page.locator('.task-step').textContent(),/正在读取课表/);
  assert.equal(await page.locator('.task-track').getAttribute('aria-valuenow'),'40');
  await page.evaluate(()=>academicStatus={busy:true,needsVerification:true,progress:-1,stage:'验证尚未完成'});
  await page.waitForFunction(()=>document.getElementById('sync').textContent==='手动验证',{},{timeout:4000});
  await page.locator('#sync').click();assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='syncVerify').length),2);
  await page.evaluate(()=>{academicStatus={busy:false,needsVerification:false,progress:100,stage:'完成'};finishAcademic({status:'read',revision:1,results:[],message:'教务已读取'});});
  await page.waitForFunction(()=>!document.getElementById('sync').disabled);
  assert.equal(await page.locator('#sync .task-spinner').count(),0);assert.equal(await page.locator('.task-track').getAttribute('aria-valuenow'),'100');
  const terminalCalls=await page.evaluate(()=>calls.filter(x=>x.method==='syncStatus').length);await page.waitForTimeout(1200);assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='syncStatus').length),terminalCalls);
  await page.locator('#sync').click();await page.evaluate(()=>finishAcademic({status:'cancelled',results:[]}));await page.waitForFunction(()=>!document.getElementById('sync').disabled);assert.match(await page.locator('[data-startup-status]').textContent(),/同步已停止/);
  await page.locator('#sync').click();await page.evaluate(()=>failAcademic(Error('测试连接失败')));await page.waitForFunction(()=>!document.getElementById('sync').disabled);assert.match(await page.locator('[data-startup-status]').textContent(),/测试连接失败/);
  await page.locator('[data-tab="news"]').click();await page.locator('#news-curate').click();
  assert.equal(await page.locator('#news-curate .task-spinner').count(),1);assert.equal(await page.locator('#news-curate').isDisabled(),true);assert.equal(page.url(),originalUrl);
  await page.locator('[data-tab="query"]').click();
  await page.evaluate(()=>newsSnapshot={...newsSnapshot,needsVerification:true,progress:-1,stage:'新闻需要手动验证'});
  await page.waitForTimeout(2200);
  await page.locator('[data-tab="news"]').click();await page.waitForFunction(()=>document.getElementById('news-curate').textContent==='手动验证');
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='newsVerify').length),0);
  await page.locator('#news-curate').click();assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='newsVerify').length),1);
  assert.equal(await page.locator('.curation-panel .task-track').getAttribute('aria-valuenow'),'55');
  await page.evaluate(()=>newsSnapshot={...newsSnapshot,needsVerification:true,progress:-1,stage:'验证尚未完成'});
  await page.waitForFunction(()=>document.getElementById('news-curate').textContent==='手动验证',{},{timeout:4000});
  await page.locator('#news-curate').click();
  await page.evaluate(()=>newsSnapshot={...newsSnapshot,busy:false,needsVerification:false,progress:100,stage:'新闻获取完成',curation:{status:'completed'}});
  await page.waitForFunction(()=>!document.getElementById('news-curate').disabled,{},{timeout:4000});
  assert.equal(await page.locator('#news-curate .task-spinner').count(),0);
  const finishedNews=await page.evaluate(()=>calls.filter(x=>x.method==='newsCurationStatus').length);await page.waitForTimeout(2200);assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='newsCurationStatus').length),finishedNews);
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.method==='portal').length),0);
  assert.equal(page.url(),originalUrl);assert.equal(await page.evaluate(()=>document.body.scrollWidth<=innerWidth+1),true);assert.deepEqual(errors,[]);
  console.log('PASS: actual academic/news clicks stay on page; spinners and determinate/indeterminate steps; tab switching retains tasks; explicit-only verification and retry after dismissal; success/cancellation/failure reset controls; terminal polling stops; no automatic portal requests; mobile width fits.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
