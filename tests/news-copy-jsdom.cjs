// Offline interaction test using the actual local index.html and scripts.
// NODE_PATH=<directory containing jsdom> node tests/news-copy-jsdom.cjs
const {JSDOM,ResourceLoader,VirtualConsole}=require('jsdom');
const {fileURLToPath}=require('node:url');
const path=require('node:path');
const assert=require('node:assert/strict');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const attempts=[],errors=[];
class LocalAssetsOnly extends ResourceLoader {
 fetch(url,options){
  if(!url.startsWith('file:')){attempts.push(url);return null;}
  const resource=path.resolve(fileURLToPath(url));
  if(!resource.startsWith(assets+path.sep)||!['.js','.css'].includes(path.extname(resource)))throw Error('Unexpected local resource: '+resource);
  return super.fetch(url,options);
 }
}
(async()=>{
 const virtualConsole=new VirtualConsole();virtualConsole.on('jsdomError',error=>errors.push(error.message));
 const dom=await JSDOM.fromFile(path.join(assets,'index.html'),{
  resources:new LocalAssetsOnly(),runScripts:'dangerously',pretendToBeVisual:true,virtualConsole,
  beforeParse(window){
   window.matchMedia=()=>({matches:false,addListener(){},removeListener(){},addEventListener(){},removeEventListener(){}});
   window.scrollTo=()=>{};window.structuredClone=structuredClone;
   window.fetch=url=>{attempts.push(String(url));return Promise.reject(Error('Network is disabled in this test'));};
  }
 });
 try{
  const w=dom.window,d=w.document;
  if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
  const settle=()=>new Promise(resolve=>setImmediate(resolve));
  const click=async selector=>{const node=d.querySelector(selector);assert.ok(node,'Missing '+selector);node.click();await settle();};
  const originalUrl=w.location.href;
  w.eval("window.demoCalls=[];nativeCall=async(method,arg)=>{demoCalls.push({method,arg});throw Error('Demo must not call Native');};");
  await click('[data-tab="news"]');await click('[data-digest="2"]');
  assert.equal(d.querySelectorAll('[data-news-copy]').length,2);
  await click('[data-news-copy="0"]');
  assert.match(d.getElementById('toast').textContent,/没有真实原文链接/);assert.equal(w.demoCalls.length,0);
  await w.eval(`(async()=>{
   closeModal();demo=false;window.copyCalls=[];window.copied='';window.copyToasts=[];toast=x=>copyToasts.push(x);
   const vpn='https://webvpn2.hitwh.edu.cn/http/77726476706e69737468656265737421abcdefabcdefabcdefabcdef/2026/0921/c1a123/page.htm';
   window.savedNews={configured:true,messages:[{seq:8,day:'2026-09-28',title:'已缓存精选',articles:[{id:'hitwh-home:a123',title:'<img src=x onerror=alert(1)>',url:vpn,summary:'<script>alert(1)</script>第一条摘要'},{id:'hit-news:a123',title:'第二条',url:'https://news.hit.edu.cn/2026/0927/c1510a243604/page.htm',summary:'第二条摘要'}]}]};
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
  })()`);
  await click('[data-digest="8"]');
  assert.equal(d.querySelectorAll('#overlay img,#overlay script').length,0);
  assert.match(d.querySelector('.news-article h3').textContent,/<img src=x onerror=alert\(1\)>/);
  for(const [index,id] of [[0,'hitwh-home:a123'],[1,'hit-news:a123']]){
   await click('[data-news-copy="'+index+'"]');
   const request=w.copyCalls.filter(row=>row.method==='newsArticleCopy').at(-1);
   assert.deepEqual(JSON.parse(JSON.stringify(request.arg)),{seq:8,articleId:id});
   assert.equal(w.copied,w.savedNews.messages[0].articles[index].url);
   assert.equal(w.copyToasts.at(-1),'链接已复制');
  }
  assert.equal(w.copyCalls.filter(row=>['newsOriginal','newsCurate','portal','newsUnresolvedOpen'].includes(row.method)).length,0);
  assert.equal(d.getElementById('overlay').hidden,false);assert.equal(w.location.href,originalUrl);
  w.savedNews.messages[0].articles.shift();
  const before=w.copied;await click('[data-news-copy="0"]');
  assert.match(w.copyToasts.at(-1),/已不存在/);assert.equal(w.copied,before);
  assert.equal(d.querySelector('[data-news-copy="0"]').disabled,false);
  assert.deepEqual(attempts,[]);assert.deepEqual(errors,[]);
  console.log('PASS: real local-assets DOM click handlers copy cached seq/articleId; demo skips Native; two same-number site IDs resolve independently; VPN/public URLs preserved; no reader/navigation/crawl calls; stale deletion errors without clipboard change; HTML escaped; all external requests blocked and none attempted.');
 }finally{dom.window.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
