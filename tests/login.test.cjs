const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync(require.resolve('../app/src/main/assets/login-flow.js'),'utf8');
const VPN='https://webvpn2.hitwh.edu.cn',BASE='/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667',IDS='/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b';
function element(props={}){return Object.assign({textContent:'',value:'',type:'text',children:[],events:[],clicks:0,disabled:false,getClientRects(){return this.hidden?[]:[{}];},getAttribute(name){return this[name]??null;},closest(){return this.anchor||null;},dispatchEvent(e){this.events.push(e.type);},click(){this.clicks++;}},props);}
function card(name,address){return element({textContent:name,anchor:element({href:'javascript:void(0)',querySelector:()=>element({title:address})})});}
function page(url,map={}){const location=new URL(url);location.assign=url=>location.assigned=url;const document={title:'',querySelectorAll:s=>map[s]||[],querySelector:s=>(map[s]||[])[0]||null};const window={open:()=>{throw new Error('Unexpected unhandled popup');}};const fn=vm.runInNewContext(script,{document,location,window,URL,Event:class{constructor(type){this.type=type;}}});return{run:(arg={})=>JSON.parse(fn(arg)),location,map,document,window};}
function credentials(url='https://ids.hit.edu.cn/authserver/login'){
 const user=element({id:'username'}),password=element({id:'password',type:'password'}),button=element({textContent:'登 录'});
 const p=page(url,{'input#username':[user],'input#password':[password],'a,button,input[type="submit"]':[button],'input':[user,password]});return{...p,user,password,button};
}
test('VPN switches to CAS and follows the observed CAS link without using a VPN password form',()=>{
 const tab=element({textContent:'统一认证'}),p=page(VPN+'/login',{'a,button,li,span,div':[tab]});assert.equal(p.run().state,'vpnMethod');p.run({mode:'vpnMethod'});assert.equal(tab.clicks,1);
 const cas=element({textContent:'CAS统一身份认证登录',href:VPN+'/login?cas_login=true',hidden:true});p.map.a=[cas];assert.equal(p.run().state,'vpnCas');p.run({mode:'vpnCas'});assert.equal(p.location.assigned,cas.href);
});
test('academic entry chooses unified identity and ignores other-user login',()=>{
 const cas=element({textContent:'统一身份认证登录',href:VPN+BASE+'/loginCAS'}),legacy=element({textContent:'其他用户',href:VPN+BASE+'/loginNOCAS'}),p=page(VPN+BASE+'/',{a:[cas,legacy]});assert.equal(p.run().state,'academicCas');p.run({mode:'academicCas'});assert.equal(cas.clicks,1);assert.equal(legacy.clicks,0);
});
test('both verified identity origins fill account/password and submit once per action',()=>{
 for(const url of ['https://ids.hit.edu.cn/authserver/login',VPN+IDS+'/authserver/login']){const p=credentials(url);assert.equal(p.run().state,'credentials');assert.equal(p.button.clicks,0);assert.equal(p.run({mode:'credentials',account:'synthetic-student',password:'test-only"\\value'}).state,'acted');assert.equal(p.user.value,'synthetic-student');assert.equal(p.password.value,'test-only"\\value');assert.deepEqual(p.password.events,['input','change']);assert.equal(p.button.clicks,1);}
});
test('credentials never fill on lookalike, other school, or arbitrary VPN routes',()=>{
 for(const url of ['https://ids.hit.edu.cn.example.org/authserver/login','https://other.hit.edu.cn/authserver/login',VPN+'/https/other/authserver/login','http://ids.hit.edu.cn/authserver/login']){const p=credentials(url);assert.equal(p.run({mode:'credentials',account:'synthetic-student',password:'test-only'}).state,'unknown');assert.equal(p.password.value,'');assert.equal(p.button.clicks,0);}
});
test('document navigation between inspection and submission cancels filling',()=>{const p=credentials();const r=p.run({mode:'credentials',expected:'https://ids.hit.edu.cn/authserver/old',account:'sample',password:'sample'});assert.equal(r.state,'changed');assert.equal(p.password.value,'');});
test('missing saved credentials and ambiguous submit controls do not submit',()=>{
 const p=credentials();assert.equal(p.run({mode:'credentials'}).state,'missing');p.map['a,button,input[type="submit"]'].push(element({textContent:'登录'}));assert.equal(p.run({mode:'credentials',account:'sample',password:'sample'}).state,'unknown');assert.equal(p.button.clicks,0);
});
test('visible CAPTCHA requires a human action; prefill does not submit',()=>{
 const p=credentials(),captcha=element({id:'captcha',placeholder:'验证码'});p.map.input.push(captcha);assert.equal(p.run().state,'captcha');assert.equal(p.run({mode:'credentials',account:'sample',password:'sample'}).state,'captcha');assert.equal(p.button.clicks,0);assert.equal(p.run({mode:'prefill',account:'sample',password:'sample'}).state,'filled');assert.equal(p.button.clicks,0);captcha.value='human-entered';assert.equal(p.run({mode:'verifiedCredentials',account:'sample',password:'sample'}).state,'acted');assert.equal(p.button.clicks,1);
});
function otp(){const code=element({name:'dynamicCode'}),send=element(),button=element({textContent:'登录'}),app=element({title:'哈工大APP验证码'}),sms=element({title:'短信验证码'});return{...page(VPN+IDS+'/authserver/mfa',{'input[name="dynamicCode"]':[code],'button#getDynamicCode':[send],'a,button,input[type="submit"]':[button],'span[title]':[app,sms]}),code,send,button,app,sms};}
test('OTP inspection never sends a code; explicit method selection and request are separate',()=>{const p=otp();assert.deepEqual(p.run().methods,['哈工大APP验证码','短信验证码']);assert.equal(p.send.clicks,0);p.run({mode:'chooseMethod',method:'短信验证码'});assert.equal(p.sms.clicks,1);assert.equal(p.send.clicks,0);p.run({mode:'sendCode'});assert.equal(p.send.clicks,1);p.send.disabled=true;assert.equal(p.run({mode:'sendCode'}).state,'waitCode');assert.equal(p.send.clicks,1);});
test('OTP submit requires input, never saves code, and targets the observed form',()=>{const p=otp();assert.equal(p.run({mode:'submitCode',code:''}).state,'unknown');assert.equal(p.button.clicks,0);assert.equal(p.run({mode:'submitCode',code:'123456'}).state,'acted');assert.equal(p.code.value,'123456');assert.equal(p.button.clicks,1);});
test('trust prompt chooses this login only and never trusts the device',()=>{const once=element({textContent:'仅本次登录'}),trust=element({textContent:'信任此设备'}),p=page('https://ids.hit.edu.cn/authserver/trust',{button:[once,trust]});assert.equal(p.run().state,'trustOnce');p.run({mode:'trustOnce'});assert.equal(once.clicks,1);assert.equal(trust.clicks,0);});
test('resource dashboard and authenticated timetable are distinct states',()=>{
 const p=page(VPN+'/',{h2:[card('新教务系统','http://jwts.hitwh.edu.cn/')]});assert.equal(p.run().state,'resource');const q=page(VPN+BASE+'/kbcx/queryGrkb',{'select#xnxq':[element()],th:['一','二','三','四','五','六','日'].map(x=>element({textContent:'星期'+x}))});assert.equal(q.run().state,'ready');assert.notEqual(q.run({target:'news'}).state,'ready');
});
test('portal chooses exact target title and never favourite, delete or another resource',()=>{
 const academic=card('新教务系统','http://jwts.hitwh.edu.cn/'),duplicate=card('新教务系统','http://jwts.hitwh.edu.cn/'),news=card('校主页','http://www.hitwh.edu.cn'),old=element({textContent:'旧教务系统'}),favourite=element({textContent:'收藏'}),remove=element({textContent:'删除'});
 const p=page(VPN+'/',{h2:[old,academic,duplicate,news],button:[favourite,remove]});
 assert.equal(p.run().state,'resource');assert.equal(academic.clicks,0);assert.equal(p.run({mode:'resource'}).state,'acted');
 assert.equal(p.location.assigned,VPN+BASE+'/');assert.equal(academic.clicks,0);assert.equal(duplicate.clicks,0);assert.equal(p.run({target:'news',mode:'resource'}).state,'acted');assert.equal(news.clicks,0);
 assert.equal(old.clicks,0);assert.equal(favourite.clicks,0);assert.equal(remove.clicks,0);
});
test('hidden or similarly named portal resources are never opened',()=>{
 const hidden=element({textContent:'校主页',hidden:true}),similar=element({textContent:'校主页使用说明'}),p=page(VPN+'/',{h2:[hidden,similar]});
 assert.equal(p.run({target:'news',mode:'resource'}).state,'unknown');assert.equal(hidden.clicks,0);assert.equal(similar.clicks,0);
});
const NEWS='/https/'+ 'a'.repeat(64);
const OBSERVED_HOME='/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b';
test('0.7.2 diagnostic regression: non-root official mobile dashboard with two exact cards is detected',()=>{
 // These paths are synthetic variants: the private diagnostic deliberately
 // redacted the actual mobile route, and implementation must not guess it.
 for(const route of ['/index','/mobile/portal-view','/arbitrary-resource-route'])for(const target of ['news','academic']){
   const cards=[['校主页','http://www.hitwh.edu.cn'],['新教务系统','http://jwts.hitwh.edu.cn/']].flatMap(([name,address])=>[0,1].map(()=>element({textContent:name,anchor:element({href:'javascript:void(0)',querySelector:()=>element({title:address})})})));
   const p=page(VPN+route,{h2:cards}),result=p.run({target});
   assert.equal(result.state,'resource');assert.equal(result.dashboard,true);assert.equal(result.resourceUrl,VPN+(target==='news'?OBSERVED_HOME:BASE)+'/');
   assert.equal(cards.reduce((sum,e)=>sum+e.clicks,0),0);
 }
});
test('regression: real homepage is ready after manual navigation without learned URL or optional site metadata',()=>{
 const p=page(VPN+OBSERVED_HOME+'/?wrdrecordvisit=12345',{a:[element({textContent:'学校新闻',href:VPN+OBSERVED_HOME+'/xxgk/list.htm'}),element({textContent:'通知公告',href:VPN+OBSERVED_HOME+'/2026/0921/c1a220600/page.htm'})]});p.document.title='哈尔滨工业大学（威海）';
 assert.equal(p.run({target:'news'}).state,'ready');
});
test('observed homepage recognition requires actual school structure and rejects article or login pages',()=>{
 const newsLink=element({href:VPN+OBSERVED_HOME+'/2026/0920/c5449a220579/page.htm'});
 for(const suffix of ['/', '/main.htm', '/main.psp','/index.psp']){
   const p=page(VPN+OBSERVED_HOME+suffix,{a:[newsLink]});p.document.title='哈尔滨工业大学（威海）';assert.equal(p.run({target:'news'}).state,'ready');
 }
 for(const [path,title] of [['/',''],['/','统一身份认证 - 哈尔滨工业大学（威海）'],['/2026/0920/c5449a220579/page.htm','哈尔滨工业大学（威海）']]){
   const p=page(VPN+OBSERVED_HOME+path,{a:[newsLink]});p.document.title=title;assert.notEqual(p.run({target:'news'}).state,'ready');
 }
 const blank=page(VPN+OBSERVED_HOME+'/');blank.document.title='哈尔滨工业大学（威海）';assert.equal(blank.run({target:'news'}).state,'unknown');
 const structural=page(VPN+OBSERVED_HOME+'/',{'a,h1,h2,h3':['校区概况','院系部门','师资队伍','通知公告'].map(textContent=>element({textContent}))});structural.document.title='哈尔滨工业大学（威海）';assert.equal(structural.run({target:'news'}).state,'ready');
});
test('regression: exact resource card yields stable destination during inspect before popup click',()=>{
 for(const [target,label,destination,expected] of [['news','校主页','http://www.hitwh.edu.cn',VPN+OBSERVED_HOME+'/'],['academic','新教务系统','http://jwts.hitwh.edu.cn/',VPN+BASE+'/']]){
   const desc=element({title:destination}),anchor=element({href:'javascript:void(0)',querySelector:()=>desc});
   const heading=element({textContent:label,anchor}),p=page(VPN+'/',{h2:[heading]});const result=p.run({target});
   assert.equal(result.state,'resource');assert.equal(result.resourceUrl,expected);assert.equal(heading.clicks,0);
 }
});
test('exact resource card cannot redirect to unrelated proxy, direct or script URLs',()=>{
 for(const href of [VPN+OBSERVED_HOME+'/', VPN+NEWS+'/', 'https://example.org/', 'javascript:alert(1)', 'https://www.hitwh.edu.cn/',VPN+'/delete']){
   const anchor=element({href,querySelector:()=>element({title:'http://www.hitwh.edu.cn'})}),heading=element({textContent:'校主页',anchor}),p=page(VPN+'/',{h2:[heading]});
   const result=p.run({target:'news',mode:'resource'});assert.equal(result.state,href===VPN+OBSERVED_HOME+'/'?'acted':'unknown');assert.equal(heading.clicks,0);
   if(result.state==='acted')assert.equal(p.location.assigned,href);
 }
});
test('news ready uses observed resource prefix and never accepts dashboard, a different proxy or article page',()=>{
 const resourceUrl=VPN+NEWS+'/';
 const home=page(resourceUrl);home.document.title='哈尔滨工业大学（威海）';assert.equal(home.run({target:'news',resourceUrl}).state,'ready');
 assert.equal(page(resourceUrl).run({target:'news',resourceUrl}).state,'unknown');
 for(const url of [VPN+'/',VPN+'/login',VPN+'/https/'+'b'.repeat(64)+'/',resourceUrl+'2026/0920/c1a123/page.htm','https://www.hitwh.edu.cn/'])assert.notEqual(page(url).run({target:'news',resourceUrl}).state,'ready');
});
test('news homepage metadata can identify the target after a card without href; generic title cannot',()=>{
 const p=page(VPN+NEWS+'/main.htm',{'meta[name="SiteDomain"]':[element({content:'www.hitwh.edu.cn'})],'meta[name="SiteName"]':[element({content:'哈尔滨工业大学（威海）'})]});
 assert.equal(p.run({target:'news'}).state,'ready');p.map['meta[name="SiteDomain"]'][0].content='www.hitwh.edu.cn.example.org';assert.equal(p.run({target:'news'}).state,'unknown');
 const q=page(VPN+NEWS+'/');q.document.title='哈尔滨工业大学（威海）';assert.equal(q.run({target:'news'}).state,'unknown');
});
test('silent mode never requests or submits OTP even when called explicitly',()=>{
 const p=otp();assert.equal(p.run({interactive:false}).state,'otp');
 for(const mode of ['chooseMethod','sendCode','submitCode'])assert.equal(p.run({interactive:false,mode,method:'短信验证码',code:'123456'}).state,'blocked');
 assert.equal(p.send.clicks,0);assert.equal(p.sms.clicks,0);assert.equal(p.button.clicks,0);assert.equal(p.code.value,'');
});
test('silent mode allows ordinary credential login but cannot submit CAPTCHA',()=>{
 const p=credentials();assert.equal(p.run({interactive:false,mode:'credentials',account:'sample',password:'sample'}).state,'acted');assert.equal(p.button.clicks,1);
 const q=credentials();q.map.input.push(element({id:'captcha'}));assert.equal(q.run({interactive:false,mode:'credentials',account:'sample',password:'sample'}).state,'captcha');assert.equal(q.run({interactive:false,mode:'verifiedCredentials',account:'sample',password:'sample'}).state,'blocked');assert.equal(q.button.clicks,0);
});
test('verified cards use same-window navigation without invoking popup, favourite or delete handlers',()=>{
 for(const href of ['javascript:void(0)','javascript:void(0);','#']){
   const p=page(VPN+'/mobile-fixture'),anchor=element({href,querySelector:()=>element({title:'http://www.hitwh.edu.cn'})}),favourite=element({title:'收藏'}),remove=element({title:'删除'});
   const heading=element({textContent:'校主页',anchor,click(){throw new Error('Popup handler must not run');}});p.map.h2=[heading];p.map.svg=[favourite,remove];
   assert.equal(p.run({target:'news'}).state,'resource');assert.equal(p.run({target:'news',mode:'resource'}).state,'acted');assert.equal(p.location.assigned,VPN+OBSERVED_HOME+'/');assert.equal(favourite.clicks,0);assert.equal(remove.clicks,0);
 }
});
test('card-shaped content on external, authentication and proxied resource pages is never a dashboard',()=>{
 for(const url of ['https://example.org/mobile', 'http://webvpn2.hitwh.edu.cn/mobile', VPN+'/login',VPN+'/authserver/login',VPN+'/account/mfa/verify', VPN+IDS+'/authserver/login',VPN+OBSERVED_HOME+'/',VPN+'/https/not-a-hex-proxy/',VPN+BASE+'/']){
   const p=page(url,{h2:[card('校主页','http://www.hitwh.edu.cn')]});assert.notEqual(p.run({target:'news'}).state,'resource');assert.notEqual(p.run({target:'news',mode:'resource'}).state,'acted');
 }
});
test('non-root dashboard still requires complete exact rendered card evidence',()=>{
 for(const h of [element({textContent:'校主页'}),card('校主页使用说明','http://www.hitwh.edu.cn'),card('校主页','http://www.hitwh.edu.cn.example.org'),Object.assign(card('校主页','http://www.hitwh.edu.cn'),{hidden:true})]){
  const p=page(VPN+'/synthetic-mobile-route',{h2:[h]});assert.equal(p.run({target:'news'}).state,'unknown');
 }
});
test('observed resource fallback requires both exact label and exact card destination',()=>{
 for(const [target,label,destination,expected] of [
   ['news','校主页','http://www.hitwh.edu.cn',VPN+'/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b/'],
   ['academic','新教务系统','http://jwts.hitwh.edu.cn/',VPN+BASE+'/']]){
   const p=page(VPN+'/'),anchor=element({href:'javascript:void(0)',querySelector:()=>element({title:destination})});
   p.map.h2=[element({textContent:label,anchor,click(){p.window.open('');}})];
   assert.equal(p.run({target,mode:'resource'}).state,'acted');assert.equal(p.location.assigned,expected);
 }
 const p=page(VPN+'/'),anchor=element({href:'javascript:void(0)',querySelector:()=>element({title:'http://www.hitwh.edu.cn.example.org'})});p.map.h2=[element({textContent:'校主页',anchor,click(){p.window.open('');}})];
 assert.equal(p.run({target:'news',mode:'resource'}).state,'unknown');assert.equal(p.location.assigned,undefined);
});
