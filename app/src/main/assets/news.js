(function(root){
 'use strict';
 const e=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const defaults={configured:false,messages:[],unread:0,lastSync:0,lastError:'',busy:false,needsVerification:false,progress:-1,stage:'',curation:{status:'idle'},failedReview:{},config:{baseUrl:'',model:'',hasApiKey:false,profile:'',autoEnabled:false,pushTime:'19:00',notifyEnabled:true}};
 let model=normalizeSnapshot({}),inDemo=false,onlyUnread=false,visible=12,settingsDirty=false,generation=0;
 let fetching=false,submitting=false,verifying=false,checking=false,saving=false,deleting=false,curationTimer=null,curationFailures=0,manualFlow=false;
 const byId=id=>document.getElementById(id);
 function normalizeSnapshot(data={}){
  const messages=Array.isArray(data.messages)?data.messages.map(m=>({...m,day:String(m.day||''),articles:Array.isArray(m.articles)?m.articles:[],warnings:Array.isArray(m.warnings)?m.warnings:[]})):[];
  return {...defaults,...data,config:{...defaults.config,...data.config},curation:{...defaults.curation,...data.curation},messages,unread:messages.filter(m=>!m.read).length};
 }
 function merge(data){model=normalizeSnapshot(data);}
 function setContext(){
  if(demo&&!inDemo){
   generation++;clearTimeout(curationTimer);curationFailures=0;manualFlow=false;inDemo=true;
   merge({configured:true,lastSync:Date.now(),config:{...defaults.config,model:'示例精选模型'},messages:[
    {seq:2,day:'2026-09-18',title:'校园日报 2026-09-18｜2条推荐',read:false,context:{label:'2026级本科生 · 大一 · 秋季学期',phase:'秋季开学阶段'},warnings:['示例：有一条链接待人工查看。'],unresolvedLinks:[{id:'sample-link',title:'待核对的通知 · 示例',url:'https://www.hitwh.edu.cn/example.htm',published:'2026-09-18',reason:'示例：页面日期未能自动确认。'}],articles:[{id:'sample1',title:'校园科创体验活动 · 示例',source:'校主页',published:'2026-09-18',campus:'威海',summary:'这是界面演示消息，展示手机通过校园 VPN 获取后整理的简介；不是学校发布的真实活动通知。',note:'',url:''},{id:'sample2',title:'图书馆服务安排 · 示例',source:'校主页',published:'2026-09-18',campus:'威海',summary:'精选会保存在手机里。标题、来源和摘要均可离线查看，原文通过校园 VPN 打开。',note:'',url:''}]},
    {seq:1,day:'2026-09-17',title:'校园日报 2026-09-17｜1条推荐',read:false,context:{label:'2026级本科生 · 大一 · 秋季学期',phase:'秋季开学阶段'},warnings:[],articles:[{id:'sample3',title:'学术讲座报名 · 示例',source:'校主页',published:'2026-09-17',campus:'线上',summary:'新精选显示未读标记，打开详情即可标记为已读。',note:'示例数据，没有真实报名入口。',url:''}]}]});
  }else if(!demo&&inDemo){inDemo=false;generation++;manualFlow=false;merge({});}
 }
 function badge(){const n=byId('news-badge');if(n){n.hidden=!model.unread;n.textContent=model.unread>99?'99+':String(model.unread);}}
 function clock(value){return value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'尚未获取';}
 function curationView(job={},busy=false,stage='',manual=false,lastError='',needsVerification=false){
  const active=needsVerification||busy||['queued','running'].includes(job.status);
  const complete=['completed','succeeded'].includes(job.status);
  const error=job.error||lastError;
  let message='通过校园 VPN 进入校主页，获取消息并在手机上完成精选。';
  if(needsVerification)message=stage||'需要手动验证，点击按钮后继续。';
  else if(active)message=stage||'正在登录并获取消息，完成后会保存在这里。';
  else if(job.status==='failed')message=manual?(error||'本次获取未完成，请重新尝试。'):'上次自动获取未完成，可点击“立即获取”处理。';
  else if(complete)message='本次获取已完成，精选已保存在手机。';
  return {active,complete,message,label:needsVerification?'手动验证':active?'获取进行中…':'立即获取',poll:active};
 }
 function curationHtml(){
  const v=curationView(model.curation,model.busy,model.stage,manualFlow,model.lastError,model.needsVerification);
  const task={busy:submitting||v.active,needsVerification:model.needsVerification,stage:model.stage||v.message,progress:submitting?-1:model.progress};
  const button=root.TaskUI?.button(task,'立即获取')||e(v.label),progress=root.TaskUI?.progress(task)||'';
  return `<section class="curation-panel"><div class="row between"><div><b>获取校园消息</b><div class="tiny muted">手动获取不受每日定时限制</div></div><button class="btn sm" id="news-curate" aria-busy="${task.busy&&!task.needsVerification}" ${deleting||submitting||verifying||(v.active&&!model.needsVerification)||!model.configured?'disabled':''}>${button}</button></div>${progress}<p class="curation-status" role="status" ${task.busy||task.progress>=0?'hidden':''}>${e(v.message)}</p>${curationFailures>=3?'<button class="text-btn" id="news-curation-check">查看最新进度</button>':''}</section>`;
 }
 function monitorCuration(){
  clearTimeout(curationTimer);curationTimer=null;
  if(demo||document.hidden||deleting||submitting||verifying||checking||curationFailures>=3||!curationView(model.curation,model.busy,'',false,'',model.needsVerification).poll)return;
  curationTimer=setTimeout(()=>checkCuration(),2000);
 }
 async function checkCuration(retry=false){
  if(demo||deleting||checking||submitting||verifying)return;if(retry)curationFailures=0;
  checking=true;const mine=generation;
  try{const value=await nativeCall('newsCurationStatus');if(mine!==generation)return;merge(value);curationFailures=0;}
  catch(error){if(mine===generation){curationFailures++;if(manualFlow)model.lastError=error.message;}}
  finally{checking=false;paint();monitorCuration();}
 }
 async function verifyNews(){
  if(demo||deleting||verifying||!model.needsVerification)return;
  verifying=true;const mine=generation;paint();
  try{const value=await nativeCall('newsVerify');if(mine===generation)merge(value);}
  finally{verifying=false;paint();monitorCuration();}
 }
 async function curate(){
  if(demo){toast('示例模式不登录学校或调用模型');return;}
  if(model.needsVerification){await verifyNews();return;}
  if(deleting||submitting||verifying||curationView(model.curation,model.busy).active)return;
  if(!model.configured){toast('请先在用户页保存精选模型配置和学校登录资料');return;}
  submitting=true;manualFlow=true;curationFailures=0;clearTimeout(curationTimer);const mine=generation;paint();
  try{const value=await nativeCall('newsCurate');if(mine!==generation)return;merge(value);if(value.status==='setup')toast('请先保存精选模型配置和学校登录资料');else if(value.status==='error')toast(value.lastError||value.curation?.error||'本次获取未能开始');}
  catch(error){if(mine===generation){model.curation={status:'failed',error:error.message};model.lastError=error.message;toast(error.message);}}
  finally{submitting=false;paint();monitorCuration();}
 }
 function digestReport(message={}){
  const stats=message.stats&&typeof message.stats==='object'?message.stats:null;
  const count=key=>stats&&Number.isSafeInteger(stats[key])&&stats[key]>=0?stats[key]:null;
  const articles=Array.isArray(message.articles)?message.articles:[];
  const deleted=Number.isSafeInteger(message.deletedArticles)&&message.deletedArticles>0?message.deletedArticles:0;
  const deletedSummary=deleted?' · 已删除 '+deleted+' 篇':'';
  const deletedEmpty='本份精选中的新闻缓存已全部删除。';
  if(!stats)return {summary:'旧记录未保存抓取统计，无法据此判断是否漏抓。'+deletedSummary,empty:deleted&&!articles.length?deletedEmpty:'本次暂无入选消息',legacy:true};
  const labels=[['recent','发现近期'],['known','已处理'],['read','读取'],['modelProcessed','模型判断'],['selected','入选']];
  let summary=labels.map(([key,label])=>label+' '+(count(key)===null?'未记录':count(key)+' 篇')).join(' · ');
  const unresolved=count('unresolvedRecent')||0,missingTitle=count('missingTitle')||0;
  const modelUnfinished=count('read')!==null&&count('modelProcessed')!==null?Math.max(0,count('read')-count('modelProcessed')):0;
  const notRead=count('new')!==null&&count('read')!==null?Math.max(0,count('new')-count('read')-(count('failed')||0)):0;
  if(count('failed')>0)summary+=' · 读取失败 '+count('failed')+' 篇';
  if(modelUnfinished>0)summary+=' · 模型未完成 '+modelUnfinished+' 篇';
  if(unresolved>0)summary+=' · 近期链接未识别 '+unresolved+' 篇';
  if(missingTitle>0)summary+=' · 缺少标题 '+missingTitle+' 篇';
  if(notRead>missingTitle)summary+=' · 另有 '+(notRead-missingTitle)+' 篇尚未读取';
  if(count('carried')>0)summary+=' · 其中 '+count('carried')+' 篇来自此前待展示精选';
  let empty='本次暂无入选消息，请结合抓取统计和处理提示判断。';
  const incomplete=count('failed')>0||modelUnfinished>0||unresolved>0||missingTitle>0||notRead>0;
  if(count('read')===0&&count('failed')>0)empty='本次未成功读取文章正文，请查看处理提示。';
  else if(count('read')>0&&count('modelProcessed')===0)empty='已读取文章，本次未完成模型判断。';
  else if(incomplete&&count('modelProcessed')>0&&count('selected')===0&&!articles.length)empty='已完成判断的 '+count('modelProcessed')+' 篇未入选；另有部分处理未完成，请查看统计和提示。';
  else if(incomplete&&!articles.length)empty='本次新闻未完整读取或处理，请查看统计和提示。';
  else if(message.emptyReason==='all_known')empty='近期文章均已处理';
  else if(message.emptyReason==='no_recent_articles')empty='本次扫描未发现近七天文章';
  else if(count('read')>0&&count('modelProcessed')===count('read')&&count('selected')===0&&!articles.length)empty='本次模型未选中任何消息';
  else if(count('read')===0&&count('modelProcessed')===0)empty='本次没有进入模型精选，请查看抓取统计。';
  summary+=deletedSummary;
  if(deleted&&!articles.length)empty=deletedEmpty;
  return {summary,empty,legacy:false};
 }
 function digestSummaryHtml(message){const report=digestReport(message);return `<div class="notice news-stats">${e(report.summary)}${report.legacy?'':'<br><span class="tiny muted">仅反映本次校主页扫描范围；“已处理”为此前已判断的文章。</span>'}</div>`;}
 function digestHtml(message){
  const articles=Array.isArray(message.articles)?message.articles:[],day=String(message.day||''),report=digestReport(message);
  return `<button class="news-digest ${message.read?'read':'unread'}" data-digest="${Number(message.seq)}"><span class="news-day"><b>${e(day.slice(5).replace('-','.'))}</b><small>${e(day.slice(0,4))}</small></span><span class="news-digest-body"><span class="news-digest-top"><b>${message.manual?'手动 · ':''}${articles.length} 则精选</b><span class="tag ${message.read?'':'solid'}">${message.read?'已读':'未读'}</span></span><span class="news-headlines">${articles.length?articles.slice(0,3).map(a=>`<span>${e(a.title)}</span>`).join(''):e(report.empty)}${articles.length>3?`<small>还有 ${articles.length-3} 则消息 →</small>`:''}</span><span class="news-digest-foot news-stats">${e(report.summary)}</span>${message.warnings?.length?'<span class="tiny news-warning">部分处理未完成，请查看详情</span>':''}<span class="news-digest-foot">${message.manual?e(clock(message.createdAt))+' · ':''}${e(message.context?.label||'校园精选')} · 查看精选 →</span></span></button>`;
 }
 function unresolvedHtml(message={},seq=0){
  message=message||{};
  const links=Array.isArray(message.unresolvedLinks)?message.unresolvedLinks:[];
  if(!links.length){
   return !Array.isArray(message.unresolvedLinks)&&message.warnings?.length?'<section class="news-unresolved"><h3>待人工查看的链接</h3><p class="tiny muted">这份旧记录未保存具体链接。下次获取时，会列出可供人工查看的未识别链接。</p></section>':'';
  }
  return `<section class="news-unresolved"><h3>待人工查看的链接</h3><p class="tiny muted">以下链接未能完成自动处理，可打开原文核对；不计作已完成的精选。</p>${links.map((value,index)=>{const link=value||{};return `<article class="news-unresolved-item"><h4>${e(link.title||'未识别标题的链接')}</h4><p class="tiny muted">${e(link.published||'日期未识别')}</p><p class="news-unresolved-url">${e(link.url||'未保存链接地址')}</p><p class="tiny">${e(link.reason||'未能自动处理，请打开原文核对。')}</p><div class="actions"><button class="text-btn" data-unresolved-open="${index}" data-review-seq="${Number(seq)}" data-review-id="${e(link.id||'')}" ${deleting?'disabled':''}>打开链接 ↗</button><button class="text-btn" data-unresolved-copy="${index}" data-review-seq="${Number(seq)}" data-review-id="${e(link.id||'')}" ${deleting?'disabled':''}>复制链接</button></div></article>`;}).join('')}</section>`;
 }
 function wireUnresolved(container){
  for(const [attribute,method] of [['data-unresolved-open','newsUnresolvedOpen'],['data-unresolved-copy','newsUnresolvedCopy']]){
   container.querySelectorAll('['+attribute+']').forEach(button=>button.onclick=run(async()=>{
    if(deleting)return;
    if(demo){toast('示例模式没有真实链接');return;}
    await nativeCall(method,{seq:Number(button.dataset.reviewSeq),index:Number(button.getAttribute(attribute)),linkId:button.dataset.reviewId||''});
    if(method==='newsUnresolvedCopy')toast('链接已复制');
   }));
  }
 }
 function deleteSnapshot(snapshot,payload){
  const next=normalizeSnapshot(snapshot),{scope,seq,articleId}=payload;
  if(scope==='all'){next.messages=[];next.failedReview={};next.unread=0;return next;}
  const index=next.messages.findIndex(message=>message.seq===seq);
  if(index<0)throw Error('这份精选已不存在，请更新列表');
  if(scope==='digest')next.messages=next.messages.filter(message=>message.seq!==seq);
  else if(scope==='article'){
   const message=next.messages[index],matches=message.articles.filter(article=>article.id===articleId);
   if(!articleId||matches.length!==1)throw Error('这条新闻已不存在，请更新列表');
   next.messages[index]={...message,articles:message.articles.filter(article=>article.id!==articleId),deletedArticles:(Number.isSafeInteger(message.deletedArticles)?message.deletedArticles:0)+1};
  }else throw Error('未知的缓存删除范围');
  next.unread=next.messages.filter(message=>!message.read).length;return next;
 }
 function confirmDelete(payload){
  if(deleting)return;
  const labels={article:'删除这条新闻缓存',digest:'删除这份精选',all:'清空新闻缓存'};
  const descriptions={article:'仅删除这台手机上的这条新闻缓存。',digest:'仅删除这台手机上的这份精选及其新闻缓存。',all:'删除这台手机上的全部新闻缓存和待人工查看记录。'};
  ask(labels[payload.scope],descriptions[payload.scope]+(model.busy?'当前新闻获取会停止。':'')+'保留新闻设置与已处理标记，避免后续重复精选。',()=>deleteNews(payload));
 }
 async function deleteNews(payload){
  if(deleting)return;
  deleting=true;generation++;const mine=generation;clearTimeout(curationTimer);paint();
  try{
   const value=demo?deleteSnapshot(model,payload):await nativeCall('newsDelete',payload);
   if(mine!==generation)return;
   if(value.status==='error')throw Error(value.lastError||'新闻缓存删除失败');
   merge(value);closeModal();toast(demo?'示例新闻缓存已删除':'新闻缓存已删除');
  }finally{deleting=false;paint();monitorCuration();}
 }
 function render(){
  setContext();const messages=model.messages.filter(m=>!onlyUnread||!m.read);
  const status=fetching?'正在更新本机列表…':(demo?'示例获取时间：':'上次获取：')+clock(model.lastSync);
  document.querySelector('#main').innerHTML=`<div class="hero news-hero"><div><div class="eyebrow">CAMPUS / DISPATCH</div><h1>校园消息</h1><p class="subtitle">从校主页出发，与你有关的消息。<br>手机获取与精选，离线也能查看。</p></div><div class="code-block">UNREAD<strong>${String(model.unread).padStart(2,'0')}</strong>份未读精选</div></div><div class="news-toolbar"><div class="row"><button class="btn sm ${onlyUnread?'secondary':'light'}" id="news-all">全部</button><button class="btn sm ${onlyUnread?'light':'secondary'}" id="news-unread">未读</button></div><button class="btn secondary sm" id="news-refresh" ${fetching?'disabled':''}>${fetching?'更新中…':'更新列表'}</button></div>${curationHtml()}${unresolvedHtml(model.failedReview,0)}<div class="startup-status" role="status">${e(status)}</div>${!model.configured&&!demo?'<section class="panel news-setup"><h3>设置手机精选</h3><p class="tiny muted">在用户页保存学校登录资料和精选模型配置，即可通过校园 VPN 进入“校主页”获取消息。</p><button class="btn secondary sm" id="news-configure">前往配置</button></section>':''}${messages.length?`<div class="news-list">${messages.slice(0,visible).map(digestHtml).join('')}</div>${messages.length>visible?'<button class="btn secondary full" id="news-more">显示更早的精选</button>':''}`:`<section class="panel empty"><h3>${onlyUnread?'未读已清空':'这里还没有精选'}</h3><p>${onlyUnread?'新消息到来时，会出现在这里。':'完成配置后点击“立即获取”，也可以开启每日自动获取。'}</p></section>`}${model.messages.length||model.failedReview?.unresolvedLinks?.length?`<div class="actions news-cache-actions">${model.messages.length?`<button class="text-btn" id="news-read-all" ${deleting?'disabled':''}>全部标为已读</button>`:''}<button class="text-btn news-delete" id="news-delete-all" ${deleting?'disabled':''}>${deleting?'正在删除…':'清空新闻缓存'}</button></div>`:''}<div class="footer-code">SELECTED ON YOUR PHONE / READ AT YOUR PACE</div>`;
  byId('news-all').onclick=()=>{onlyUnread=false;visible=12;render();};byId('news-unread').onclick=()=>{onlyUnread=true;visible=12;render();};byId('news-refresh').onclick=run(()=>sync(true));
  byId('news-curate').onclick=run(curate);byId('news-curation-check')?.addEventListener('click',run(()=>checkCuration(true)));
  byId('news-configure')?.addEventListener('click',()=>{tab='user';root.render?.();});
  byId('news-more')?.addEventListener('click',()=>{visible+=12;render();});byId('news-read-all')?.addEventListener('click',run(()=>read(0)));
  byId('news-delete-all')?.addEventListener('click',()=>confirmDelete({scope:'all'}));wireUnresolved(byId('main'));
  document.querySelectorAll('[data-digest]').forEach(b=>b.onclick=run(()=>detail(+b.dataset.digest)));
  badge();monitorCuration();
 }
 function settings(){setContext();settingsDirty=false;const c=model.config;
  return `<section class="panel" id="news-config-panel"><div class="section-heading"><h3>校园消息</h3><small>NEWS / ON DEVICE</small></div><p class="tiny muted">使用上方的学校账号登录校园 VPN，从“校主页”获取消息。文章正文会发送给你设置的模型 API 进行精选；学校账号和密码不会发送给模型。</p><form id="news-config-form"><div class="field"><label for="news-url">模型 API 地址（HTTPS）</label><input id="news-url" name="baseUrl" type="url" value="${e(c.baseUrl)}" placeholder="https://api.example.com/v1" maxlength="500" ${demo?'disabled':''} required><small class="tiny muted">填写兼容 Chat Completions 的 API 根地址。</small></div><div class="field"><label for="news-model">模型名称</label><input id="news-model" name="model" value="${e(c.model)}" placeholder="填写服务商提供的模型名称" maxlength="160" autocomplete="off" ${demo?'disabled':''} required></div><div class="field"><label for="news-key">模型 API 密钥</label><input id="news-key" name="apiKey" type="password" autocomplete="off" maxlength="500" placeholder="${c.hasApiKey?'已加密保存；地址不变时可留空':'填写模型服务商提供的 API Key'}" ${demo?'disabled':''}></div><div class="field"><label for="news-profile">关注方向（可选）</label><textarea id="news-profile" name="profile" rows="3" maxlength="1000" placeholder="默认按 2026 年 9 月入学的本科生筛选；可补充专业、兴趣或重点关注的消息。" ${demo?'disabled':''}>${e(c.profile)}</textarea></div><label class="check-row"><input id="news-auto" name="autoEnabled" type="checkbox" ${c.autoEnabled?'checked':''} ${demo?'disabled':''}><span>每日自动获取</span></label><div class="field"><label for="news-time">自动获取时间（北京时间）</label><input id="news-time" name="pushTime" type="time" value="${e(c.pushTime)}" ${demo?'disabled':''} required></div><p class="tiny muted">手机系统可能因省电或后台限制延后执行。请允许汐序后台运行；遇到验证码、登录确认或获取失败时，自动任务会安静结束。需要处理时可点击“立即获取”。</p><label class="check-row"><input name="notifyEnabled" type="checkbox" ${c.notifyEnabled?'checked':''} ${demo?'disabled':''}><span>获取成功后通知我</span></label><div class="actions"><button class="btn" type="submit" ${demo||saving?'disabled':''}>${saving?'保存中…':'保存新闻设置'}</button><button class="btn secondary" type="button" id="news-permission" ${demo?'disabled':''}>通知权限</button></div></form><p class="tiny muted news-config-status">${demo?'示例模式 · 不登录学校或调用模型':!model.configured?'配置完成后即可立即获取；保存设置不会立刻获取新闻。':'设置已保存在本机 · 上次获取：'+e(clock(model.lastSync))}${model.configured&&model.notificationAllowed===false?' · 系统通知尚未开启':''}</p></section>`;
 }
 function configPayload(f,current={}){
  const baseUrl=String(f.get('baseUrl')||'').trim(),name=String(f.get('model')||'').trim(),apiKey=String(f.get('apiKey')||'').trim(),pushTime=String(f.get('pushTime')||'').trim();
  let url;try{url=new URL(baseUrl);}catch(_){throw Error('请填写有效的模型 API HTTPS 地址');}
  if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash)throw Error('模型 API 地址必须使用 HTTPS，且不能包含账号、查询参数或片段');
  if(!name)throw Error('请填写模型名称');
  if(!apiKey&&(!current.hasApiKey||baseUrl.replace(/\/$/,'')!==String(current.baseUrl||'').replace(/\/$/,'')))throw Error('首次设置或更换 API 地址时，请填写模型密钥');
  if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(pushTime))throw Error('请选择有效的北京时间');
  return {baseUrl,model:name,apiKey,profile:String(f.get('profile')||'').trim(),autoEnabled:f.has('autoEnabled'),pushTime,notifyEnabled:f.has('notifyEnabled')};
 }
 function wireSettings(){const form=byId('news-config-form');if(!form)return;
  form.addEventListener('input',()=>{settingsDirty=true;});
  form.onsubmit=run(async event=>{
   if(demo||deleting||saving)return;
   const payload=configPayload(new FormData(event.target),model.config);saving=true;generation++;const mine=generation;
   const submit=event.target.querySelector('[type="submit"]');submit.disabled=true;submit.textContent='保存中…';
   try{const value=await nativeCall('newsSaveConfig',payload);if(mine!==generation)return;if(value.status==='error')throw Error(value.lastError||'保存失败，请检查配置');merge(value);settingsDirty=false;byId('news-key').value='';paint();toast('新闻设置已保存');}
   finally{saving=false;if(document.contains(submit)){submit.disabled=false;submit.textContent='保存新闻设置';}paint();}
  });
  byId('news-permission').onclick=run(async()=>{if(!demo){await nativeCall('newsPermission');await load();}});
 }
 function paint(){badge();if(tab==='news')render();else if(tab==='user'&&!settingsDirty){const panel=byId('news-config-panel');if(panel&&!panel.contains(document.activeElement)){panel.outerHTML=settings();wireSettings();}}}
 async function load(){if(deleting)return;setContext();if(demo){paint();return;}const mine=generation;const value=await nativeCall('newsState');if(mine===generation){merge(value);paint();}}
 async function sync(manual=false){
  setContext();if(deleting)return;if(demo){if(manual)toast('示例模式 · 列表已更新');paint();return;}if(fetching)return;
  fetching=true;const mine=generation;paint();
  try{const value=await nativeCall('newsSync');if(mine!==generation)return;merge(value);if(manual)toast('本机列表已更新；获取新消息请点击“立即获取”');}
  catch(error){if(mine===generation&&manual)toast(error.message);}
  finally{fetching=false;paint();}
 }
 async function read(seq){
  if(deleting)return;const mine=generation;
  if(demo){model.messages.forEach(m=>{if(!seq||m.seq===seq)m.read=true;});model.unread=model.messages.filter(m=>!m.read).length;}
  else{const value=await nativeCall('newsRead',{seq});if(mine!==generation)return;merge(value);}
  paint();
 }
 async function detail(seq){if(deleting)return;const message=model.messages.find(m=>m.seq===seq);if(!message)return;
  modal(message.title||'校园精选 · '+message.day,`<p class="tiny muted">${e(message.context?.label||'校园精选')}<br>${e(message.context?.phase||'')}</p>${digestSummaryHtml(message)}<div class="news-detail">${message.articles.length?message.articles.map((a,i)=>`<article class="news-article"><div class="eyebrow">${String(i+1).padStart(2,'0')} / ${e(a.source)} · ${e(a.published)}</div><h3>${e(a.title)}</h3><span class="tag">${e(a.campus||'适用范围待核实')}</span><p>${e(a.summary)}</p>${a.note?`<p class="news-limit">${e(a.note)}</p>`:''}<div class="actions"><button class="text-btn" data-news-link="${i}">查看学校原文 ↗</button><button class="text-btn" data-news-copy="${i}" ${!a.id?'disabled':''}>复制链接</button><button class="text-btn news-delete" data-news-delete-article="${i}" ${!a.id?'disabled':''}>删除这条新闻缓存</button></div></article>`).join(''):`<div class="notice">${e(digestReport(message).empty)}</div>`}</div>${message.warnings.map(w=>`<div class="notice warn">${e(w)}</div>`).join('')}${unresolvedHtml(message,seq)}<p class="tiny muted">AI 精选与简介供参考，日期、资格和办理要求以学校原文为准。</p><div class="actions news-cache-actions"><button class="text-btn news-delete" id="news-delete-digest">删除这份精选</button></div>`);
  document.querySelectorAll('[data-news-link]').forEach(b=>b.onclick=run(()=>{if(deleting)return;if(demo){toast('这条消息是演示内容，没有真实原文');return;}return nativeCall('newsOriginal',{url:message.articles[+b.dataset.newsLink].url});}));
  document.querySelectorAll('[data-news-copy]').forEach(button=>button.onclick=run(async()=>{
   if(deleting)return;
   if(demo){toast('这条消息是演示内容，没有真实原文链接');return;}
   button.disabled=true;
   try{await nativeCall('newsArticleCopy',{seq,articleId:message.articles[Number(button.dataset.newsCopy)].id});toast('链接已复制');}
   finally{if(document.contains(button))button.disabled=false;}
  }));
  document.querySelectorAll('[data-news-delete-article]').forEach(b=>b.onclick=()=>confirmDelete({scope:'article',seq,articleId:message.articles[+b.dataset.newsDeleteArticle].id}));
  byId('news-delete-digest').onclick=()=>confirmDelete({scope:'digest',seq});wireUnresolved(byId('overlay'));
  await read(seq);
 }
 const api={render,settings,wireSettings,load,sync,reset:()=>{generation++;clearTimeout(curationTimer);curationFailures=0;manualFlow=false;merge({});settingsDirty=false;badge();},badge,escapeHtml:e,digestHtml};
 if(typeof module==='object'&&module.exports)module.exports={escapeHtml:e,digestHtml,digestReport,digestSummaryHtml,unresolvedHtml,deleteSnapshot,curationView,normalizeSnapshot,configPayload};
 else{root.NewsUI=api;document.addEventListener('visibilitychange',()=>{if(!document.hidden){curationFailures=0;monitorCuration();}else clearTimeout(curationTimer);});}
})(typeof window==='object'?window:globalThis);
