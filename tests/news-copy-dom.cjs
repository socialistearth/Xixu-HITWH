// Offline real DOM verification; Native is mocked and every external request is blocked.
// NODE_PATH=<playwright modules> node tests/news-copy-dom.cjs <chromium executable>
const {chromium}=require('playwright');
const path=require('node:path');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.argv[2],args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:393,height:852}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',route=>route.request().url().startsWith('file:')?route.continue():route.abort());
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  await page.locator('[data-tab="news"]').click();await page.locator('[data-digest="2"]').click();
  assert.equal(await page.locator('[data-news-copy]').count(),2);
  await page.locator('[data-news-copy="0"]').click();assert.match(await page.locator('#toast').textContent(),/没有真实原文链接/);
  await page.evaluate(async()=>{
   closeModal();demo=false;window.copyCalls=[];window.copied='';window.copyToasts=[];toast=x=>copyToasts.push(x);
   const vpn='https://webvpn2.hitwh.edu.cn/http/77726476706e69737468656265737421abcdefabcdefabcdefabcdef/2026/0921/c1a123/page.htm';
   window.savedNews={configured:true,messages:[{seq:8,day:'2026-09-28',title:'已缓存精选',articles:[{id:'one',title:'<img src=x onerror=alert(1)>',url:vpn,summary:'第一条摘要'},{id:'two',title:'第二条',url:'https://news.hit.edu.cn/2026/0927/c1510a243604/page.htm',summary:'第二条摘要'}]}]};
   nativeCall=async(method,arg)=>{
    copyCalls.push({method,arg});
    if(method==='newsArticleCopy'){
     const message=savedNews.messages.find(m=>m.seq===arg.seq),article=message?.articles.find(a=>a.id===arg.articleId);
     if(!article)throw Error('这条新闻已不存在，请更新列表');
     copied=article.url;return {ok:true};
    }
    return structuredClone(savedNews);
   };
   NewsUI.reset();await NewsUI.load();NewsUI.render();
  });
  await page.locator('[data-digest="8"]').click();assert.equal(await page.locator('#overlay img').count(),0);
  for(const [index,id] of [[0,'one'],[1,'two']]){
   await page.locator('[data-news-copy="'+index+'"]').click();
   assert.deepEqual(await page.evaluate(()=>copyCalls.filter(x=>x.method==='newsArticleCopy').at(-1).arg),{seq:8,articleId:id});
   assert.equal(await page.evaluate(()=>copied),await page.evaluate(i=>savedNews.messages[0].articles[i].url,index));
   assert.equal(await page.evaluate(()=>copyToasts.at(-1)),'链接已复制');
  }
  assert.equal(await page.evaluate(()=>copyCalls.filter(x=>['newsOriginal','newsCurate','portal','newsUnresolvedOpen'].includes(x.method)).length),0);
  assert.equal(await page.locator('#overlay').isVisible(),true);
  await page.evaluate(()=>savedNews.messages[0].articles.shift());
  await page.locator('[data-news-copy="0"]').click();
  assert.match(await page.evaluate(()=>copyToasts.at(-1)),/已不存在/);
  assert.equal(await page.locator('[data-news-copy="0"]').isEnabled(),true);
  assert.equal(await page.evaluate(()=>document.body.scrollWidth<=innerWidth+1),true);
  assert.deepEqual(errors,[]);
  console.log('PASS: selected article copy buttons; demo no Native calls; cached seq/articleId copied without navigation or retrieval; VPN and public URLs preserved; stale/deleted article rejected; HTML escaped and mobile actions wrap.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
