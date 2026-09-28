const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync(require.resolve('../app/src/main/assets/news-scraper.js'),'utf8');
const VPN='https://webvpn2.hitwh.edu.cn',ROOT='/https/observed_home_123',HOME=VPN+ROOT+'/',NOW=Date.parse('2026-09-21T12:00:00Z');
// A small DOM fixture implements real clone/remove/text semantics; no network or credentials.
class Element{
 constructor(tag,attrs={},children=[]){this.tagName=tag;this.attrs=attrs;this.children=[];this.hidden=false;for(const child of children)this.append(child);}
 append(child){if(child instanceof Element)child.parent=this;this.children.push(child);}
 get textContent(){return this.children.map(x=>typeof x==='string'?x:x.textContent).join('');}
 get innerText(){return this.textContent;}
 getAttribute(key){return this.attrs[key]??null;}
 getClientRects(){return this.hidden?[]:[{}];}
 cloneNode(){return new Element(this.tagName,{...this.attrs},this.children.map(x=>typeof x==='string'?x:x.cloneNode()));}
 remove(){this.replaceWith();}
 replaceWith(...nodes){if(!this.parent)return;const i=this.parent.children.indexOf(this);this.parent.children.splice(i,1,...nodes);}
 matches(selector){const m=selector.match(/^([\w-]+)?(?:\.([\w-]+))?(?:\[([\w-]+)(?:="([^"]*)")?\])?$/);return !!m&&(!m[1]||this.tagName===m[1])&&(!m[2]||(this.attrs.class||'').split(/\s+/).includes(m[2]))&&(!m[3]||m[3] in this.attrs&&(!m[4]||this.attrs[m[3]]===m[4]));}
 querySelectorAll(selector){const selectors=selector.split(',');let out=[];for(const c of this.children){if(typeof c==='string')continue;if(selectors.some(s=>c.matches(s)))out.push(c);out.push(...c.querySelectorAll(selector));}return out;}
 querySelector(s){return this.querySelectorAll(s)[0]||null;}
}
const e=(tag,attrs={},...children)=>new Element(tag,attrs,children);
const link=(id,date='0921',attrs={})=>e('a',{href:ROOT+'/2026/'+date+'/c1024a'+id+'/page.htm',...attrs},'新闻 '+id);
function page(children=[],url=HOME,title='哈尔滨工业大学（威海）'){
 const body=e('body',{},...children),document={title,body,querySelectorAll:s=>body.querySelectorAll(s),querySelector:s=>body.querySelector(s)};
 const fn=vm.runInNewContext(script,{document,location:new URL(url),URL,Date,fetch:()=>{throw Error('No fetch allowed');},localStorage:new Proxy({},{get(){throw Error('No storage reads allowed');}})});
 return arg=>JSON.parse(fn({homeUrl:HOME,articleUrl:url,nowMs:NOW,...arg}));
}
test('homepage normalizes only verified original domains and explicit VPN article links',()=>{
 const run=page([link(1),link(2,'0920',{href:VPN+'/https/other/2026/0920/c1024a2/page.htm'}),link(3,'0921',{href:'https://today.hitwh.edu.cn/2026/0921/c1024a3/page.htm'}),link(4,'0921',{href:'https://www.hitwh.edu.cn/2026/0921/c1024a4/page.htm'}),link(5,'0921',{href:'https://webvpn2.hitwh.edu.cn.evil.test'+ROOT+'/2026/0921/c1024a5/page.htm'}),link(6,'0921',{href:'javascript:alert(1)'}),link(7,'0921',{href:ROOT+'/2026/0921/c1024a7/page.htm/extra'})]);
 const out=run({mode:'home'});assert.equal(out.state,'home');assert.deepEqual(out.articles.map(x=>x.id),['hitwh-home:a1','hitwh-home:a4','hitwh-home:a2']);assert.equal(out.articles[0].source,'校主页');
});
test('seven-day Beijing window excludes future and malformed dates and preserves date/title',()=>{
 const run=page([link(1,'0921'),link(2,'0915'),link(3,'0914'),link(4,'0922'),link(5,'0230')]);
 const out=run({mode:'home'});assert.deepEqual(out.articles.map(x=>x.id),['hitwh-home:a1','hitwh-home:a2']);assert.equal(out.articles[1].published,'2026-09-15');assert.equal(out.articles[1].title,'新闻 2');
 const midnight=page([link(6,'0922')]);assert.equal(midnight({mode:'home',nowMs:Date.parse('2026-09-21T16:00:00Z')}).articles.length,1);
});
test('deduplicates repeated home links, excludes known IDs, caps each scan at twenty',()=>{
 const links=Array.from({length:24},(_,i)=>link(i+1));links.push(link(2,'0921',{title:'完整且更长的新闻标题'}));
 const out=page(links)({mode:'home',knownIds:['hitwh-home:a1']});assert.equal(out.articles.length,20);assert.equal(out.limited,true);assert.equal(out.articles[0].title,'完整且更长的新闻标题');assert.ok(!out.articles.some(x=>x.id==='hitwh-home:a1'));
});
test('relative article URLs resolve against actually reached source without constructing a proxy',()=>{
 const run=page([e('a',{href:'2026/0921/c1024a9/page.htm?vpn-session=temporary#section'},'相对链接')]);assert.equal(run({mode:'home'}).articles[0].url,HOME+'2026/0921/c1024a9/page.htm');
 assert.equal(page([link(1,'0901')])({mode:'home'}).state,'home');assert.deepEqual(page([link(1,'0901')])({mode:'home'}).articles,[]);
 for(const url of ['https://www.hitwh.edu.cn/',VPN+'/',VPN+'/https/other/'])assert.notEqual(page([],url)({mode:'home'}).state,'home');
});
test('navigated document, login form and protected articles never become empty successful articles',()=>{
 const url=HOME+'2026/0921/c1024a1/page.htm';
 assert.equal(page([],url)({mode:'article',expected:url+'?old'}).state,'changed');
 assert.equal(page([e('input',{type:'password'})],url)({mode:'article'}).state,'login');
 assert.equal(page([],url,'统一身份认证')({mode:'article'}).state,'login');
 assert.equal(page([e('p',{},'仅限校内用户访问，请先登录')],url)({mode:'article'}).state,'login');
 assert.equal(page([e('div',{class:'wp_error_msg'},'当前ip并非校内地址，该信息仅允许校内地址访问')],url)({mode:'article'}).state,'login');
 assert.equal(page([e('p',{},'网站导航与页脚')],url)({mode:'article'}).state,'failed');
});
test('extracts article content, prioritizes article heading and removes scripts/forms/navigation',()=>{
 const body=e('div',{class:'wp_articlecontent'},e('p',{},'这是一条用于验证校园新闻正文提取行为的足够长通知。'),e('script',{},'DO NOT SEND'),e('input',{value:'secret'}),e('nav',{},'导航内容'),e('p',{},'第二段包含重要办理时间。'));
 const out=page([e('h1',{},'网站标志'),e('h2',{class:'arti_title'},'实际通知标题'),body],HOME+'2026/0921/c1024a8/page.htm')({mode:'article',id:'hitwh-home:a8',title:'列表标题'});
 assert.equal(out.state,'article');assert.equal(out.article.title,'实际通知标题');assert.equal(out.article.id,'hitwh-home:a8');assert.match(out.article.body,/第二段/);assert.doesNotMatch(out.article.body,/DO NOT SEND|secret|导航内容/);assert.match(body.textContent,/DO NOT SEND/,'original DOM remains untouched');
});
test('image and attachment notices report unread media honestly; text stays capped at 5500',()=>{
 const body=e('div',{class:'article-content'},e('p',{},'文'.repeat(6000)),e('img',{src:'qr.png'}),e('a',{href:'/file.pdf'},'附件'));
 const out=page([body],HOME+'2026/0921/c1024a8/page.htm')({mode:'article'});assert.equal(out.article.body.length,5500);assert.match(out.article.note,/未识别图片内容/);assert.match(out.article.note,/未读取附件内容/);assert.match(out.article.note,/5500/);
 const imageOnly=page([e('div',{class:'wp_articlecontent'},e('img',{src:'poster.jpg'}))],HOME+'2026/0921/c1024a9/page.htm')({mode:'article'});assert.equal(imageOnly.state,'article');assert.equal(imageOnly.article.body,'');assert.match(imageOnly.article.note,/图片/);
});
test('short empty article is rejected, and article-ID mismatch cannot store the wrong page',()=>{
 const url=HOME+'2026/0921/c1024a1/page.htm';
 assert.equal(page([e('div',{class:'wp_articlecontent'},'加载中')],url)({mode:'article'}).state,'failed');
 assert.equal(page([e('div',{class:'wp_articlecontent'},'文'.repeat(50))],url)({mode:'article',id:'hitwh-home:a2'}).state,'changed');
});

test('cross-proxy article parsing requires the exact homepage-discovered article URL',()=>{
 const cross=VPN+'/http/observed_other_456/2026/0921/c1040a220590/page.htm';
 const body=e('div',{class:'wp_articlecontent'},'这是校主页明确链接的另一代理路径新闻，其正文可以通过同一校园VPN会话读取。');
 const run=page([body],cross);
 assert.equal(run({mode:'article',articleUrl:cross,id:'hitwh-home:a220590'}).state,'article');
 assert.equal(run({mode:'article',articleUrl:HOME+'2026/0921/c1040a220590/page.htm',id:'hitwh-home:a220590'}).state,'changed');
 assert.equal(run({mode:'article',articleUrl:''}).state,'changed');
 assert.equal(run({mode:'home'}).state,'changed');
});

test('observed static-to-dynamic WebPlus redirect keeps exact article identity and saved htm URL',()=>{
 const staticUrl=HOME+'2026/0920/c5449a220579/page.htm',dynamicUrl=staticUrl.replace(/htm$/,'psp');
 const body=e('div',{class:'wp_articlecontent'},'这是校主页通过校园VPN打开的新闻正文，页面重定向为同一篇文章的动态路径。');
 const run=page([body],dynamicUrl);const out=run({mode:'article',articleUrl:staticUrl,id:'hitwh-home:a220579',expected:dynamicUrl});
 assert.equal(out.state,'article');assert.equal(out.article.url,staticUrl);
 assert.equal(run({mode:'article',articleUrl:staticUrl.replace('c5449','c1040'),id:'hitwh-home:a220579'}).state,'changed');
 assert.equal(run({mode:'article',articleUrl:staticUrl.replace('0920','0919'),id:'hitwh-home:a220579'}).state,'changed');
});

test('funnel separates recognized article links, recent dates, known items, candidates and skipped raw links',()=>{
 const run=page([link(1,'0921'),link(1,'0921'),link(2,'0920'),link(3,'0901'),link(4,'0922'),e('a',{href:'/2026/0921/c5449a99/page.htm'},'未改写原始链接')]);
 const out=run({mode:'home',knownIds:['hitwh-home:a1']});
 assert.equal(out.state,'home');assert.deepEqual(out.stats,{anchors:6,linked:4,recent:2,known:1,new:1,queued:1,unresolved:1,unresolvedRecent:1,missingTitle:0,read:0,failed:0});
 assert.equal(out.emptyReason,'');assert.equal(out.articles[0].id,'hitwh-home:a2');
});
test('empty valid home states distinguish all old from all known instead of implying model rejection',()=>{
 const old=page([link(1,'0901')])({mode:'home'});assert.equal(old.state,'home');assert.equal(old.emptyReason,'no_recent_articles');assert.equal(old.stats.linked,1);assert.equal(old.stats.recent,0);
 const known=page([link(1),link(2)])({mode:'home',knownIds:['hitwh-home:a1','hitwh-home:a2']});assert.equal(known.state,'home');assert.equal(known.emptyReason,'all_known');assert.equal(known.stats.recent,2);assert.equal(known.stats.known,2);assert.equal(known.stats.new,0);
});
test('unrecognized or incomplete homepage is pending, never a successful empty selection',()=>{
 const none=page([e('a',{href:'/index.htm'},'首页')])({mode:'home'});assert.equal(none.state,'homePending');assert.equal(none.stats.anchors,1);assert.equal(none.stats.linked,0);
 const untitled=page([e('a',{href:ROOT+'/2026/0921/c1024a2/page.htm'})])({mode:'home'});assert.equal(untitled.state,'homePending');assert.equal(untitled.stats.linked,1);assert.equal(untitled.stats.missingTitle,1);
});

test('recent unconverted links prevent false all-known or no-recent empty success',()=>{
 const raw=e('a',{href:'/2026/0921/c5449a99/page.htm'},'尚未转换的近期文章');
 for(const [resolved,known] of [[link(1,'0901'),[]],[link(1),['hitwh-home:a1']]]){
  const out=page([resolved,raw])({mode:'home',knownIds:known});
  assert.equal(out.state,'homePending');assert.equal(out.stats.unresolvedRecent,1);
  assert.equal(out.emptyReason,undefined);
 }
});

test('partial readable home preserves unresolved and untitled candidate counts',()=>{
 const raw=e('a',{href:'/2026/0921/c5449a99/page.htm'},'尚未转换的文章');
 const untitled=e('a',{href:ROOT+'/2026/0921/c5449a88/page.htm'});
 const out=page([link(1),raw,untitled])({mode:'home'});
 assert.equal(out.state,'home');assert.equal(out.articles.length,1);assert.equal(out.emptyReason,'');
 assert.equal(out.stats.unresolvedRecent,1);assert.equal(out.stats.missingTitle,1);
 assert.equal(out.stats.new,2);assert.equal(out.stats.queued,1);
});

test('old, future, invalid, known or duplicate unconverted links do not create missing recent work',()=>{
 const raw=(id,date)=>e('a',{href:'/2026/'+date+'/c5449a'+id+'/page.htm'},'未转换链接');
 const out=page([link(1),raw(1,'0921'),raw(2,'0901'),raw(3,'0922'),raw(4,'0230'),raw(5,'0921')])({mode:'home',knownIds:['hitwh-home:a1','hitwh-home:a5']});
 assert.equal(out.state,'home');assert.equal(out.emptyReason,'all_known');
 assert.equal(out.stats.unresolved,5);assert.equal(out.stats.unresolvedRecent,0);
 const duplicate=page([link(1),raw(1,'0921')])({mode:'home'});
 assert.equal(duplicate.stats.unresolvedRecent,0);assert.equal(duplicate.articles.length,1);
});

test('waiting homepage becomes usable after the school finishes rewriting its article link',()=>{
 const raw=e('a',{href:'/2026/0921/c5449a99/page.htm'},'动态新闻');
 const run=page([link(1,'0901'),raw]);
 assert.equal(run({mode:'home'}).state,'homePending');
 raw.attrs.href=ROOT+'/2026/0921/c5449a99/page.htm';
 const ready=run({mode:'home'});assert.equal(ready.state,'home');
 assert.equal(ready.stats.unresolvedRecent,0);assert.equal(ready.articles[0].id,'hitwh-home:a99');
});

const raw18=require('./fixtures/news-home-raw-18.json');
function fixtureAnchors(fixture){return fixture.articles.map(row=>{const a=e('a',{href:row.href,title:row.title},row.title);if(row.resolvedHref)a.href=row.resolvedHref;return a;});}
test('18 original absolute, root-relative and protocol-relative article links convert using verified VPN routes',()=>{
 const out=page(fixtureAnchors(raw18),raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl,nowMs:raw18.nowMs});
 assert.equal(out.state,'home');assert.equal(out.stats.unresolvedRecent,0);assert.equal(out.stats.linked,18);assert.equal(out.stats.queued,18);assert.equal(out.articles.length,18);
 assert.deepEqual(out.articles.map(a=>a.url),raw18.articles.map(a=>a.expectedUrl));
 assert.deepEqual(out.articles.map(a=>a.id),raw18.articles.map(a=>a.id));
});
test('original unverified host is used only when its exact rewritten VPN article property is present',()=>{
 const raw='https://today.hitwh.edu.cn/2026/0921/c1024a123/page.htm';
 const rewritten=VPN+'/http/observed_explicit_proxy/2026/0921/c1024a123/page.htm';
 const a=e('a',{href:raw},'首页明确链接的已改写文章');a.href=rewritten;
 const out=page([a],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl});assert.equal(out.articles[0].url,rewritten);
 a.href=raw;const unresolved=page([a],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl});assert.equal(unresolved.state,'homePending');assert.equal(unresolved.stats.queued,0);assert.equal(unresolved.stats.unresolvedRecent,1);
});
test('lookalikes, unverified originals, unsafe ports and userinfo are never repaired into accepted links',()=>{
 const path='/2026/0921/c5449a123/page.htm';
 const invalid=['https://www.hitwh.edu.cn.evil.test'+path,'https://evil.test'+path,'https://today.hitwh.edu.cn'+path,'https://www.hitwh.edu.cn:8443'+path,'http://news.hitwh.edu.cn:443'+path,'https://user@www.hitwh.edu.cn'+path,'https://@www.hitwh.edu.cn'+path,'javascript:alert(1)','https://www.hitwh.edu.cn/redirect?url='+encodeURIComponent(path)];
 for(const href of invalid){const a=e('a',{href},'不接受的链接');const out=page([a],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl});assert.equal(out.state,'homePending',href);assert.equal(out.stats.queued,0,href);}
 // A DOM property must not wash away forbidden raw URL userinfo or a nonstandard port.
 for(const href of ['https://@www.hitwh.edu.cn'+path,'https://www.hitwh.edu.cn:8443'+path]){const a=e('a',{href},'错误链接');a.href=raw18.homeUrl+path.slice(1);assert.equal(page([a],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl}).stats.queued,0);}
});
test('native URL anchors homepage and same-article validation when WebVPN virtualizes DOM location',()=>{
 const homeArgs={mode:'home',homeUrl:raw18.homeUrl,nativeUrl:raw18.homeUrl,expected:raw18.homeUrl,nowMs:raw18.nowMs};
 const home=page(fixtureAnchors(raw18),'http://www.hitwh.edu.cn/')({...homeArgs});assert.equal(home.stats.queued,18);assert.equal(home.homeUrl,raw18.homeUrl);
 const article=raw18.articles[6],dynamic=article.expectedUrl.replace(/htm$/,'psp'),original=article.href.replace(/htm$/,'psp');
 const body=e('div',{class:'wp_articlecontent'},'这是用于检查原生URL和虚拟DOM地址分离的校区新闻正文，必须对应同一篇文章。');
 const run=page([body],original),args={mode:'article',homeUrl:raw18.homeUrl,nativeUrl:dynamic,expected:dynamic,articleUrl:article.expectedUrl,id:article.id,nowMs:raw18.nowMs};
 const out=run(args);assert.equal(out.state,'article');assert.equal(out.article.url,article.expectedUrl);
 assert.equal(run({...args,articleUrl:raw18.articles[7].expectedUrl}).state,'changed');
 assert.equal(run({...args,expected:article.expectedUrl}).state,'changed');
});

test('partial homepage exports each safe unread official article for manual review without inventing a proxy',()=>{
 const hosts=['today.hitwh.edu.cn','jwc.hitwh.edu.cn','today.hit.edu.cn','notice.department.hitwh.edu.cn'];
 const rows=hosts.map((host,i)=>e('a',{href:'https://'+host+'/2026/0921/c1024a'+(600+i)+'/page.htm?ticket=temporary-secret#session-fragment',title:'待检查的校园通知 '+i},'通知'));
 const out=page([link(1),...rows])({mode:'home'});
 assert.equal(out.state,'home');assert.equal(out.stats.unresolvedRecent,4);assert.equal(out.unresolvedLinks.length,4);
 for(let i=0;i<4;i++){const row=out.unresolvedLinks[i];assert.equal(row.id,'hitwh-home:a'+(600+i));assert.equal(row.title,'待检查的校园通知 '+i);assert.equal(row.published,'2026-09-21');assert.equal(row.url,'https://'+hosts[i]+'/2026/0921/c1024a'+(600+i)+'/page.htm');assert.match(row.reason,/未能转换/);}
 assert.doesNotMatch(JSON.stringify(out),/temporary-secret|session-fragment/);
});
test('all-unresolved homepage stays pending but retains manual links, and URL-only titles lose session query too',()=>{
 const raw='http://today.hitwh.edu.cn/2026/0921/c1024a666/page.psp?session=private-token#private-fragment';
 const out=page([e('a',{href:raw},raw)])({mode:'home'});
 assert.equal(out.state,'homePending');assert.equal(out.unresolvedLinks.length,1);assert.equal(out.unresolvedLinks[0].url,'http://today.hitwh.edu.cn/2026/0921/c1024a666/page.psp');
 assert.doesNotMatch(JSON.stringify(out),/private-token|private-fragment/);assert.equal(out.articles,undefined);
});
test('unresolved review removes already known or successfully parsed IDs regardless of link order',()=>{
 const raw=id=>e('a',{href:'https://today.hitwh.edu.cn/2026/0921/c1024a'+id+'/page.htm'},'待查看新闻 '+id);
 for(const order of [[raw(1),link(1)],[link(1),raw(1)]]){const out=page([...order,raw(2),raw(3),raw(3)])({mode:'home',knownIds:['hitwh-home:a2']});assert.deepEqual(out.unresolvedLinks.map(x=>x.id),['hitwh-home:a3']);assert.equal(out.stats.unresolvedRecent,1);}
});
test('manual review links remain within safe official HTTP origins and recent valid article dates',()=>{
 const path='/2026/0921/c1024a700/page.htm';
 const unsafe=['https://outside.example'+path,'https://hitwh.edu.cn.evil.test'+path,'https://user@today.hitwh.edu.cn'+path,'https://@today.hitwh.edu.cn'+path,'https://today.hitwh.edu.cn:8443'+path,'http://today.hitwh.edu.cn:443'+path,'javascript:alert(1)','https://today.hitwh.edu.cn/2026/0901/c1024a701/page.htm','https://today.hitwh.edu.cn/2026/0922/c1024a702/page.htm','https://today.hitwh.edu.cn/2026/0230/c1024a703/page.htm'];
 const out=page(unsafe.map(href=>e('a',{href},'不应保留的链接')))({mode:'home'});assert.deepEqual(out.unresolvedLinks,[]);
 const valid=page([e('a',{href:'http://today.hitwh.edu.cn:80'+path},'允许默认端口')])({mode:'home'});assert.equal(valid.unresolvedLinks[0].url,'http://today.hitwh.edu.cn'+path);
});
test('manual review exports at most forty unique unresolved articles while retaining total funnel count',()=>{
 const rows=Array.from({length:55},(_,i)=>e('a',{href:'https://today.hitwh.edu.cn/2026/0921/c1024a'+(800+i)+'/page.htm'},'近期文章 '+i));
 const out=page(rows)({mode:'home'});assert.equal(out.state,'homePending');assert.equal(out.stats.unresolvedRecent,55);assert.equal(out.unresolvedLinks.length,40);assert.equal(new Set(out.unresolvedLinks.map(x=>x.id)).size,40);
});

test('homepage keeps the two real news domains distinct and upgrades new public links to HTTPS',()=>{
 const path='/2026/0921/c1510a243604/page.htm';
 const out=page([e('a',{href:'http://news.hit.edu.cn'+path+'?ticket=private#temporary'},'工大要闻'),e('a',{href:'http://news.hitwh.edu.cn'+path},'校区新闻')],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl});
 assert.equal(out.stats.queued,2);assert.equal(out.stats.unresolvedRecent,0);
 assert.equal(out.articles[0].url,'https://news.hit.edu.cn'+path);assert.equal(out.articles[0].id,'hit-news:a243604');
 assert.equal(out.articles[1].id,'hitwh-home:a243604');assert.equal(out.articles[1].publicUrl,'https://news.hitwh.edu.cn'+path);
 assert.match(out.articles[1].url,/webvpn2\.hitwh\.edu\.cn\/http\//);
 const known=page([e('a',{href:'http://news.hit.edu.cn'+path},'工大要闻'),e('a',{href:'http://news.hitwh.edu.cn'+path},'校区新闻')],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl,knownIds:['hitwh-home:a243604']});
 assert.deepEqual(known.articles.map(a=>a.id),['hit-news:a243604']);assert.doesNotMatch(JSON.stringify(out),/private|temporary/);
});
test('explicit same-article VPN rewrite wins over historical route and teaches only an observed mapping',()=>{
 const root='/https/observed_news_route_123',path='/2026/0921/c1040a700/page.htm';
 const first=e('a',{href:'https://news.hitwh.edu.cn'+path},'已改写的新闻');first.href=VPN+root+path;
 const second=e('a',{href:'http://news.hitwh.edu.cn/2026/0921/c1040a701/page.htm'},'同站另一新闻');
 const out=page([second,first],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl});
 assert.equal(out.articles.length,2);assert.ok(out.articles.every(a=>a.url.startsWith(VPN+root+'/')));
 assert.equal(out.articles[1].publicUrl,'https://news.hitwh.edu.cn'+path);
 const main=e('a',{href:'http://news.hit.edu.cn'+path},'另一个真实站点');main.href=VPN+'/https/observed_hit_789'+path;
 const home=page([main],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl});assert.equal(home.articles[0].id,'hit-news:a700');
 const current=home.articles[0],body=e('div',{class:'wp_articlecontent'},'此段合成正文验证从首页学到的代理路由在正文页面仍保留真实站点身份。');
 assert.equal(page([body],current.url)({mode:'article',articleUrl:current.url,publicUrl:current.publicUrl,id:current.id}).state,'article');
});
test('different article properties and external originals cannot teach or replace a VPN mapping',()=>{
 const path='/2026/0921/c1040a700/page.htm',original='https://news.hitwh.edu.cn'+path;
 for(const changed of ['c1041a700','c1040a701']){
  const a=e('a',{href:original},'不同文章不可配对');a.href=VPN+'/http/unrelated_proxy'+path.replace('c1040a700',changed);
  const out=page([a],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl});assert.equal(out.articles.length,1);assert.doesNotMatch(out.articles[0].url,/unrelated_proxy/);
 }
 const evil=e('a',{href:'https://unrelated.example'+path},'非学校链接');evil.href=VPN+'/http/unrelated_proxy'+path;
 assert.equal(page([evil],raw18.homeUrl)({mode:'home',homeUrl:raw18.homeUrl}).stats.queued,0);
});
test('public HTTPS article structures match both observed news sites and preserve exact article identity',()=>{
 const fixture=require('./fixtures/news-public-structures.json');
 for(const row of fixture.pages){
  const body=e('div',{class:row.bodyClass},e('p',{},'此处为合成的校园新闻正文，用于验证真实页面结构，不包含学校原文的完整内容。'));
  const elements=[e('h1',{},row.precedingH1),e(row.headingTag,{class:row.headingClass},row.headingText),row.wrapperClass?e('div',{class:row.wrapperClass},body):body];
  const dynamic=row.url.replace(/htm$/,'psp')+'?vpn=temporary#section';
  const out=page(elements,dynamic)({mode:'article',articleUrl:row.url,id:row.id,nowMs:fixture.nowMs});
  assert.equal(out.state,'article');assert.equal(out.article.title,row.headingText);assert.equal(out.article.url,row.url);assert.equal(out.article.id,row.id);assert.match(out.article.body,/合成的校园新闻/);
  assert.equal(page(elements,dynamic)({mode:'article',articleUrl:row.url.replace(/c\d+a/,'c9999a'),id:row.id,nowMs:fixture.nowMs}).state,'changed');
  assert.equal(page(elements,dynamic)({mode:'article',articleUrl:row.url.replace('/2026/','/2025/'),id:row.id,nowMs:fixture.nowMs}).state,'changed');
 }
});
test('public fallback does not generalize to campus-only notices or untrusted news lookalikes',()=>{
 const path='/2026/0921/c1040a700/page.htm';
 for(const host of ['today.hitwh.edu.cn','news.hit.edu.cn.evil.test','news.hitwh.edu.cn.evil.test']){
  const out=page([e('a',{href:'https://'+host+path},'不允许公开替代')])({mode:'home'});assert.equal(out.stats.queued,0);
 }
 const article='https://news.hit.edu.cn'+path;
 assert.equal(page([e('input',{type:'password'})],article)({mode:'article',articleUrl:article}).state,'login');
 assert.equal(page([e('div',{class:'wp_error_msg'},'当前ip并非校内地址，该信息仅允许校内地址访问')],article)({mode:'article',articleUrl:article}).state,'login');
});
