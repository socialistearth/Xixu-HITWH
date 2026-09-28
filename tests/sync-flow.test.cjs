const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require.resolve('../app/src/main/assets/app.js'),'utf8').split('\nif(isNative)loadNative().then(runStartup)')[0];
function app(reply){
 const nodes=new Map(),get=s=>{if(!nodes.has(s))nodes.set(s,{hidden:true,textContent:'',innerHTML:'',classList:{add(){},remove(){},toggle(){}},setAttribute(){}});return nodes.get(s);};
 const calls=[],saved={studentId:'synthetic-student',hasPassword:true,settings:{termName:'2026秋季'},courses:[],exams:[],grades:[]};
 const ctx=vm.createContext({Academic:require('../app/src/main/assets/core.js'),Startup:require('../app/src/main/assets/startup.js'),Native:{},document:{querySelector:get,querySelectorAll:()=>[],addEventListener(){}},window:{addEventListener(){},scrollTo(){}},matchMedia:()=>({matches:false}),setTimeout,clearTimeout,Intl,
  invoke:async(method,arg)=>{calls.push({method,arg});return reply?reply(method,arg,saved):(method==='bootstrap'||method==='applyStartupSync'?saved:{});}});
 vm.runInContext(source,ctx);vm.runInContext("nativeCall=invoke;render=()=>{};renderQuery=()=>{};updateQuoteDom=()=>{};toast=()=>{};state=normalize({studentId:'synthetic-student',hasPassword:true});globalThis.api={runStartup,syncAcademic,pollAcademic,verifyAcademic,academicAction,task:()=>syncTask,busy:()=>busy,clearQueryCache,deleteQueryRecord,courses:()=>state.courses,refresh:window.refreshNative,message:()=>syncMessage,setPassword:v=>state.hasPassword=v};",ctx);
 return{api:ctx.api,calls,saved};
}
test('disabled startup performs no import or login, and launch runs once',async()=>{
 const x=app(m=>m==='startupSync'?{status:'disabled',message:'已关闭'}:{});await x.api.runStartup();await x.api.runStartup();assert.deepEqual(x.calls.map(x=>x.method),['startupQuote','startupSync']);assert.equal(x.api.message(),'已关闭');
});
test('cooldown does not overwrite the last report, manual sync remains available',async()=>{
 const x=app((m,a,s)=>m==='startupSync'?{status:'cooldown',message:'冷却中'}:m==='syncNow'?{status:'read',results:[],message:'已读取'}:m==='applyStartupSync'?s:{});await x.api.runStartup();assert.equal(x.api.message(),'冷却中');assert.ok(!x.calls.some(x=>x.method==='applyStartupSync'));await x.api.syncAcademic(false,true);assert.equal(x.calls.filter(x=>x.method==='syncNow').length,1);assert.equal(x.calls.filter(x=>x.method==='portal').length,0);
});
test('a legacy login response never opens a school page automatically',async()=>{
 const x=app((m,a,s)=>m==='syncNow'?{status:'login',message:'需要登录',results:[]}:m==='applyStartupSync'?s:{});
 await x.api.syncAcademic(false,true);assert.equal(x.calls.filter(x=>x.method==='portal').length,0);assert.equal(x.api.message(),'需要登录');assert.equal(x.api.busy(),false);
});
test('status polling reveals verification without opening it, explicit action verifies and original sync completes',async()=>{
 let release;const completed=new Promise(resolve=>release=resolve);
 let status={busy:true,needsVerification:true,stage:'等待手动验证',progress:-1};
 const x=app(async(m,a,s)=>{
  if(m==='syncNow')return completed;
  if(m==='syncStatus')return status;
  if(m==='syncVerify'){status={busy:true,needsVerification:false,stage:'正在读取课表',progress:40};return status;}
  return m==='applyStartupSync'?s:{};
 });
 const syncing=x.api.syncAcademic(false,true);await x.api.pollAcademic();
 assert.equal(x.api.busy(),true);assert.equal(x.api.task().needsVerification,true);assert.equal(x.api.task().stage,'等待手动验证');
 assert.ok(!x.calls.some(c=>c.method==='portal'||c.method==='syncVerify'));
 await x.api.academicAction();assert.equal(x.calls.filter(c=>c.method==='syncVerify').length,1);assert.equal(x.api.task().needsVerification,false);
 release({status:'read',revision:0,results:[],message:'已读取'});await syncing;
 assert.equal(x.calls.filter(c=>c.method==='syncNow').length,1);assert.ok(!x.calls.some(c=>c.method==='portal'));assert.equal(x.api.busy(),false);assert.equal(x.api.task().progress,100);
});
test('cancelling login refreshes cached state without another crawl',async()=>{
 const x=app((m,a,s)=>m==='bootstrap'?{...s,resumeSync:false}:{});await x.api.refresh();assert.deepEqual(x.calls.map(x=>x.method),['bootstrap']);
});
test('recreation after login consumes continuation instead of starting another automatic cycle',async()=>{
 const x=app((m,a,s)=>m==='syncNow'?{status:'login',results:[]}:m==='applyStartupSync'?s:{});await x.api.runStartup(true);assert.equal(x.calls.filter(x=>x.method==='syncNow').length,1);assert.ok(!x.calls.some(x=>x.method==='startupSync'||x.method==='portal'));
});
test('no saved password means no automatic CAS window',async()=>{
 const x=app((m,a,s)=>m==='startupSync'?{status:'login',results:[]}:m==='applyStartupSync'?{...s,hasPassword:false}:{});x.api.setPassword(false);await x.api.runStartup();assert.ok(!x.calls.some(x=>x.method==='portal'));assert.match(x.api.message(),/保存学号/);
});
test('overlapping sync clicks share one running operation',async()=>{
 let release;const wait=new Promise(resolve=>release=resolve);const x=app(async(m,a,s)=>m==='syncNow'?(await wait,{status:'read',results:[]}):m==='applyStartupSync'?s:{});const first=x.api.syncAcademic(false,true);await x.api.syncAcademic(false,true);release();await first;assert.equal(x.calls.filter(x=>x.method==='syncNow').length,1);
});

test('clearing cache rejects an earlier login result without reopening the school page',async()=>{
 let release;const wait=new Promise(resolve=>release=resolve);const x=app(async(m,a,s)=>m==='syncNow'?(await wait,{status:'login',revision:1,results:[]}):m==='clearQueryCache'?{...s,courses:[],syncReport:{message:'已清除课表缓存'}}:s);
 const sync=x.api.syncAcademic(false,true);await x.api.clearQueryCache({kind:'courses',scope:'all',term:''});release();await sync;
 assert.ok(!x.calls.some(x=>x.method==='applyStartupSync'||x.method==='portal'));assert.equal(x.api.message(),'已清除课表缓存');
});
test('clearing cache also rejects a previously queued apply response in the UI',async()=>{
 let release,started;const waiting=new Promise(resolve=>release=resolve),applied=new Promise(resolve=>started=resolve);const x=app(async(m,a,s)=>{if(m==='syncNow')return{status:'read',revision:1,results:[]};if(m==='applyStartupSync'){started();await waiting;return{...s,courses:[{name:'stale-course'}]};}if(m==='clearQueryCache')return{...s,courses:[],syncReport:{message:'已清除课表缓存'}};return s;});
 const sync=x.api.syncAcademic(false,true);await applied;await x.api.clearQueryCache({kind:'courses',scope:'all',term:''});release();await sync;assert.equal(x.api.courses().length,0);assert.equal(x.api.message(),'已清除课表缓存');
});
test('single deletion carries ID, term and revision and blocks an older sync result',async()=>{
 let release;const waiting=new Promise(resolve=>release=resolve);
 const x=app(async(m,a,s)=>m==='syncNow'?(await waiting,{status:'read',revision:3,results:[]}):m==='deleteQueryRecord'?{...s,courses:[{id:'another'}],syncReport:{message:'已删除此条'}}:s);
 const syncing=x.api.syncAcademic(false,true);
 await x.api.deleteQueryRecord({id:'chosen',term:'2026秋季',name:'数学'},'courses',3);
 release();await syncing;
 const deletion=x.calls.find(c=>c.method==='deleteQueryRecord');
 assert.equal(deletion.arg.id,'chosen');assert.equal(deletion.arg.term,'2026秋季');assert.equal(deletion.arg.revision,3);
 assert.equal(x.api.courses()[0].id,'another');assert.ok(!x.calls.some(c=>c.method==='applyStartupSync'));
});


test('a late running status cannot revive an already finished sync',async()=>{
 let finishSync,finishStatus;const completed=new Promise(resolve=>finishSync=resolve),status=new Promise(resolve=>finishStatus=resolve);
 const x=app((m,a,s)=>m==='syncNow'?completed:m==='syncStatus'?status:m==='applyStartupSync'?s:{});
 const sync=x.api.syncAcademic(false,true),poll=x.api.pollAcademic();
 finishSync({status:'read',revision:1,results:[],message:'已读取'});await sync;
 assert.equal(x.api.busy(),false);assert.equal(x.api.task().progress,100);
 finishStatus({busy:true,needsVerification:true,stage:'过期登录状态',progress:-1});await poll;
 assert.equal(x.api.busy(),false);assert.equal(x.api.task().needsVerification,false);assert.equal(x.api.task().progress,100);
});
