const test=require('node:test');
const assert=require('node:assert/strict');
const {escapeHtml,digestHtml,digestReport,digestSummaryHtml,unresolvedHtml,deleteSnapshot,curationView,normalizeSnapshot,configPayload}=require('../app/src/main/assets/news.js');
const form=values=>new Map(Object.entries(values));
const valid={baseUrl:'https://model.example/v1',model:'campus-summary',apiKey:'example-key',pushTime:'19:00'};

test('untrusted news text is escaped in rendered cards',()=>{
 const html=digestHtml({seq:1,day:'2026-09-18',read:false,context:{label:'<img src=x onerror=alert(1)>'},warnings:[],articles:[{title:'<script>alert(1)</script> & "标题"'}]});
 assert.ok(html.includes('&lt;script&gt;'));
 assert.ok(html.includes('&lt;img'));
 assert.ok(!html.includes('<script>'));
 assert.ok(!html.includes('<img'));
 assert.ok(html.includes('未读'));
});

test('local progress runs until collection is complete without a server receipt',()=>{
 assert.equal(curationView({status:'running'}).poll,true);
 assert.equal(curationView({status:'idle'},true).active,true);
 assert.equal(curationView({status:'running'},true,'正在读取校主页').message,'正在读取校主页');
 assert.equal(curationView({status:'completed',newsSeq:8}).poll,false);
 assert.equal(curationView({status:'completed',newsSeq:8}).label,'立即获取');
 assert.equal(curationView({status:'failed'}).active,false);
});

test('automatic failures stay quiet and manual failures show actionable detail',()=>{
 const job={status:'failed',error:'需要输入校园 VPN 验证码'};
 assert.match(curationView(job,false,'',false).message,/点击“立即获取”/);
 assert.doesNotMatch(curationView(job,false,'',false).message,/验证码/);
 assert.equal(curationView(job,false,'',true).message,job.error);
});

test('old daily publications remain readable without a configured model',()=>{
 const old={seq:12,day:'2026-09-18',read:false,articles:[{title:'已保存的学校通知',summary:'原有摘要'}]};
 const value=normalizeSnapshot({configured:false,messages:[old]});
 assert.equal(value.messages[0].articles[0].summary,'原有摘要');
 assert.equal(value.messages[0].warnings.length,0);
 assert.equal(value.unread,1);
 assert.match(digestHtml(value.messages[0]),/已保存的学校通知/);
 assert.equal(value.config.pushTime,'19:00');
});

test('manual publications are visually distinguishable on the same day',()=>{
 const html=digestHtml({seq:4,manual:true,createdAt:1789740000000,day:'2026-09-18',read:false,articles:[],context:{},warnings:[]});
 assert.match(html,/手动/);assert.match(html,/data-digest="4"/);
});

test('empty and partial digests do not claim all sources are up to date',()=>{
 const html=digestHtml({seq:2,day:'2026-09-18',read:true,articles:[],context:{},warnings:['synthetic failure']});
 assert.ok(html.includes('本次暂无入选消息'));
 assert.ok(html.includes('部分处理未完成'));
 assert.ok(html.includes('已读'));
 assert.equal(escapeHtml("&<>'\""),'&amp;&lt;&gt;&#39;&quot;');
});

test('an existing model key can be preserved but never silently reused for a new endpoint',()=>{
 const current={hasApiKey:true,baseUrl:valid.baseUrl};
 assert.equal(configPayload(form({...valid,apiKey:''}),current).apiKey,'');
 assert.throws(()=>configPayload(form({...valid,apiKey:''})),/密钥/);
 assert.throws(()=>configPayload(form({...valid,baseUrl:'https://other.example/v1',apiKey:''}),current),/密钥/);
 assert.equal(configPayload(form({...valid,baseUrl:'https://other.example/v1'}),current).baseUrl,'https://other.example/v1');
});

test('model config rejects credential-bearing or insecure URLs and invalid scheduled time',()=>{
 for(const baseUrl of ['http://model.example/v1','https://user:pass@model.example/v1','https://model.example/v1?key=secret','https://model.example/v1#key']){
  assert.throws(()=>configPayload(form({...valid,baseUrl})),/HTTPS/);
 }
 for(const pushTime of ['24:00','19:60','7:30',''])assert.throws(()=>configPayload(form({...valid,pushTime})),/北京时间/);
 assert.equal(configPayload(form({...valid,pushTime:'00:00'})).pushTime,'00:00');
 assert.equal(configPayload(form({...valid,pushTime:'23:59',autoEnabled:'on',notifyEnabled:'on',profile:'  关注科创  '})).profile,'关注科创');
 assert.equal(configPayload(form(valid)).autoEnabled,false);
});

const collection=(stats={},extra={})=>({seq:9,day:'2026-09-22',read:false,articles:[],warnings:[],stats:{recent:12,known:7,read:5,modelProcessed:5,selected:0,failed:0,...stats},...extra});

test('zero selected after completed model judgments is distinguished from collection failure',()=>{
 const message=collection();
 assert.equal(digestReport(message).empty,'本次模型未选中任何消息');
 assert.match(digestHtml(message),/发现近期 12 篇 · 已处理 7 篇 · 读取 5 篇 · 模型判断 5 篇 · 入选 0 篇/);
 assert.match(digestSummaryHtml(message),/仅反映本次校主页扫描范围/);
});

test('all known articles and no recent discoveries have distinct zero labels',()=>{
 const known=collection({recent:7,known:7,read:0,modelProcessed:0},{emptyReason:'all_known'});
 const absent=collection({recent:0,known:0,read:0,modelProcessed:0},{emptyReason:'no_recent_articles'});
 assert.equal(digestReport(known).empty,'近期文章均已处理');
 assert.equal(digestReport(absent).empty,'本次扫描未发现近七天文章');
 assert.doesNotMatch(digestHtml(known),/模型未选中/);
 assert.doesNotMatch(digestHtml(absent),/学校没有更新/);
});

test('zero successful reads never claims the model rejected messages',()=>{
 const message=collection({read:0,modelProcessed:0,failed:5});
 assert.match(digestReport(message).empty,/未成功读取文章正文/);
 assert.match(digestHtml(message),/读取失败 5 篇/);
 assert.doesNotMatch(digestHtml(message),/模型未选中/);
 const inconsistent=collection({read:0,modelProcessed:5});
 assert.doesNotMatch(digestReport(inconsistent).empty,/模型未选中/);
});

test('successful reads without model completion are not represented as rejected articles',()=>{
 assert.equal(digestReport(collection({modelProcessed:0})).empty,'已读取文章，本次未完成模型判断。');
 assert.doesNotMatch(digestReport(collection({modelProcessed:0})).empty,/模型未选中/);
});

test('partial model failure with zero recommendations names only the completed judgments',()=>{
 const message=collection({recent:8,known:0,new:8,read:8,modelProcessed:5},{warnings:['3篇模型判断未完成']});
 const report=digestReport(message);
 assert.match(report.empty,/已完成判断的 5 篇未入选/);
 assert.match(report.empty,/部分处理未完成/);
 assert.doesNotMatch(report.empty,/本次模型未选中任何消息/);
 assert.match(report.summary,/模型判断 5 篇/);assert.match(report.summary,/模型未完成 3 篇/);
});

test('partial body failure cannot make zero recommendations look like a fully assessed scan',()=>{
 const message=collection({recent:8,known:0,new:8,read:5,modelProcessed:5,failed:3});
 const report=digestReport(message);
 assert.match(report.empty,/已完成判断的 5 篇未入选/);
 assert.match(report.empty,/部分处理未完成/);assert.match(report.summary,/读取失败 3 篇/);
 assert.doesNotMatch(report.empty,/本次模型未选中任何消息/);
});

test('unresolved recent links or missing titles override stale definitive empty reasons',()=>{
 for(const reason of ['all_known','no_recent_articles']){
  const message=collection({recent:1,known:1,new:0,read:0,modelProcessed:0,unresolvedRecent:1},{emptyReason:reason});
  const report=digestReport(message);
  assert.match(report.summary,/近期链接未识别 1 篇/);assert.match(report.empty,/未完整读取或处理/);
  assert.doesNotMatch(report.empty,/均已处理|未发现近七天/);
 }
 const untitled=collection({recent:6,known:0,new:6,read:5,modelProcessed:5,missingTitle:1});
 assert.match(digestReport(untitled).summary,/缺少标题 1 篇/);
 assert.match(digestReport(untitled).empty,/部分处理未完成/);
 assert.doesNotMatch(digestReport(untitled).summary,/另有 1 篇尚未读取/,'same missing-title article is not counted twice');
});

test('bounded reading leaves unvisited new candidates visible without claiming they were assessed',()=>{
 const message=collection({recent:25,known:0,new:25,read:20,modelProcessed:20});
 assert.match(digestReport(message).summary,/另有 5 篇尚未读取/);
 assert.match(digestReport(message).empty,/已完成判断的 20 篇未入选/);
});

test('carried recommendations remain separate from model processing in this run',()=>{
 const message=collection({recent:3,known:3,new:0,read:0,modelProcessed:0,selected:3,carried:3},{emptyReason:'all_known',articles:[{title:'待展示1'},{title:'待展示2'},{title:'待展示3'}]});
 const report=digestReport(message),html=digestHtml(message);
 assert.match(report.summary,/模型判断 0 篇 · 入选 3 篇/);
 assert.match(report.summary,/其中 3 篇来自此前待展示精选/);
 assert.match(html,/待展示1/);assert.doesNotMatch(html,/近期文章均已处理/);
});

test('legacy records without statistics explicitly preserve diagnostic uncertainty',()=>{
 const old={seq:1,day:'2026-09-18',articles:[],warnings:[]};
 for(const html of [digestHtml(old),digestSummaryHtml(old)])assert.match(html,/旧记录未保存抓取统计，无法据此判断是否漏抓/);
 assert.doesNotMatch(digestHtml(old),/模型未选中|近期文章均已处理/);
});

test('missing or malicious counts are not silently converted into zero or HTML',()=>{
 const message=collection({recent:'<img onerror=x>',known:-1,read:NaN,modelProcessed:null,selected:Infinity});
 const html=digestHtml(message);
 assert.match(html,/发现近期 未记录 · 已处理 未记录 · 读取 未记录 · 模型判断 未记录 · 入选 未记录/);
 assert.doesNotMatch(html,/<img|模型未选中/);
});


test('deleting one article preserves sibling records, settings and original selection counts',()=>{
 const snapshot=normalizeSnapshot({configured:true,config:{model:'retain-model'},processedIds:['a','b'],messages:[collection({selected:2},{articles:[{id:'a',title:'one'},{id:'b',title:'two'}]}),{seq:10,day:'2026-09-22',articles:[{id:'a',title:'different digest'}]}]});
 const next=deleteSnapshot(snapshot,{scope:'article',seq:9,articleId:'a'});
 assert.equal(next.messages[0].articles.length,1);assert.equal(next.messages[0].articles[0].id,'b');
 assert.equal(next.messages[1].articles[0].id,'a');assert.equal(next.messages[0].deletedArticles,1);
 assert.equal(next.messages[0].stats.selected,2);assert.equal(next.config.model,'retain-model');
 assert.deepEqual(next.processedIds,['a','b']);assert.equal(snapshot.messages[0].articles.length,2);
});

test('an empty digest after deletion never claims the model selected nothing',()=>{
 const snapshot={messages:[collection({selected:1},{articles:[{id:'a'}]})]};
 const next=deleteSnapshot(snapshot,{scope:'article',seq:9,articleId:'a'});
 assert.match(digestReport(next.messages[0]).empty,/新闻缓存已全部删除/);
 assert.match(digestReport(next.messages[0]).summary,/入选 1 篇/);
 assert.match(digestReport(next.messages[0]).summary,/已删除 1 篇/);
 assert.doesNotMatch(digestHtml(next.messages[0]),/模型未选中/);
 assert.match(digestReport({articles:[],deletedArticles:1}).empty,/新闻缓存已全部删除/);
});

test('digest and all deletion retain settings and processing markers but clear requested records',()=>{
 const snapshot={config:{model:'retain'},processedIds:['a'],messages:[{seq:1,articles:[]},{seq:2,articles:[]}],failedReview:{unresolvedLinks:[{url:'https://www.hitwh.edu.cn/path'}]}};
 const one=deleteSnapshot(snapshot,{scope:'digest',seq:1});assert.deepEqual(one.messages.map(m=>m.seq),[2]);
 const all=deleteSnapshot(snapshot,{scope:'all'});assert.equal(all.messages.length,0);assert.equal(all.unread,0);assert.deepEqual(all.failedReview,{});
 assert.equal(all.config.model,'retain');assert.deepEqual(all.processedIds,['a']);
 assert.throws(()=>deleteSnapshot(snapshot,{scope:'article',seq:1,articleId:'unknown'}),/已不存在/);
});

test('unresolved links display escaped full text and route by saved index, never direct href',()=>{
 const html=unresolvedHtml({unresolvedLinks:[{title:'<img src=x onerror=x>',url:'https://www.hitwh.edu.cn/a" onclick="x',published:'<b>today</b>',reason:'<script>x</script>'}]},42);
 assert.match(html,/待人工查看的链接/);assert.match(html,/&lt;img/);assert.match(html,/&lt;script/);
 assert.match(html,/data-unresolved-open="0"/);assert.match(html,/data-review-seq="42"/);
 assert.match(html,/data-unresolved-copy="0"/);assert.doesNotMatch(html,/<img|<script|<[^>]* href=|<[^>]* onclick=/);
 assert.match(unresolvedHtml({unresolvedLinks:[{url:'https://www.hitwh.edu.cn/path'}]},0),/data-review-seq="0"/);
});

test('legacy warnings explain absent concrete links without fabricating links',()=>{
 const html=unresolvedHtml({warnings:['旧版未识别提示']},3);
 assert.match(html,/旧记录未保存具体链接/);assert.doesNotMatch(html,/data-unresolved-open/);
 assert.equal(unresolvedHtml({warnings:[],unresolvedLinks:[]},3),'');assert.equal(unresolvedHtml(null,0),'');
});


test('verification state keeps a task active but exposes only the explicit manual-verification action',()=>{
 const v=curationView({status:'running'},true,'需要输入验证码',true,'',true);
 assert.equal(v.active,true);assert.equal(v.poll,true);assert.equal(v.label,'手动验证');assert.equal(v.message,'需要输入验证码');
});
