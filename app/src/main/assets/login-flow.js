/* Observed school forms only. Called in the isolated WebView; no native bridge. */
(function (arg) {
 'use strict';
 const reply=(state,extra={})=>JSON.stringify({state,...extra});
 const VPN='https://webvpn2.hitwh.edu.cn';
 const academic='/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667';
 // Observed after selecting 校主页 on the authenticated portal, 2026-09-21.
 const homepageResource='/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b';
 const identity='/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b';
 const origin=location.origin,path=location.pathname,mode=arg.mode||'inspect',target=arg.target||'academic';
 if(target!=='academic'&&target!=='news')return reply('unknown');
 const auth=(origin==='https://ids.hit.edu.cn'&&path.startsWith('/authserver/'))||
   (origin===VPN&&path.startsWith(identity+'/authserver/'));
 const vpn=origin===VPN&&path==='/login',school=origin===VPN&&path.startsWith(academic+'/');
 const proxy=origin===VPN&&/^\/(?:http|https)\/[a-f0-9]{32,}(?:\/|$)/i.test(path);
 let routePath=path;try{routePath=decodeURIComponent(path);}catch(_){return reply('unknown');}
 const dashboardCandidate=origin===VPN&&!/^\/(?:http|https)(?:\/|$)/i.test(routePath)&&!/(?:^|\/)(?:authserver|auth|login|logout|cas|sso|oauth|saml|mfa)(?:\/|$)/i.test(routePath);
 if(!auth&&!vpn&&!school&&!dashboardCandidate&&!(target==='news'&&proxy))return reply('unknown');
 const all=s=>Array.from(document.querySelectorAll(s));
 const visible=e=>e&&e.getClientRects().length>0;
 const shown=s=>all(s).filter(visible);
 const text=e=>(e.textContent||'').replace(/\s/g,'');
 const single=a=>a.length===1?a[0]:null;
 const login=()=>single(shown('a,button,input[type="submit"]').filter(e=>text(e)==='登录'||e.value==='登录'));
 const set=(e,v)=>{e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};
 // Check again at execution time, including after a redirect between inspect and act.
 if(arg.expected&&arg.expected!==origin+path)return reply('changed');
 const result=(state,extra={})=>reply(state,{page:origin+path,...extra});
 const click=(state,e)=>{if(mode===state){e.click();return result('acted');}return result(state);};
 // Silent jobs may sign in with saved credentials, but must never request or
 // submit a verification code or interact with a human challenge.
 if(arg.interactive===false&&['prefill','verifiedCredentials','chooseMethod','sendCode','submitCode'].includes(mode))return result('blocked');
 const proxyRoot=u=>{try{const x=new URL(u,location.href);const m=x.pathname.match(/^\/(?:http|https)\/[a-f0-9]{32,}(?:\/|$)/i);return x.origin===VPN&&m?VPN+m[0].replace(/\/$/,''):'';}catch(_){return '';}};
 if(target==='news'&&proxy&&!auth&&!school){
   const currentRoot=proxyRoot(location.href),learnedRoot=arg.resourceUrl?proxyRoot(arg.resourceUrl):'';
   const domain=all('meta[name="SiteDomain"]').map(e=>e.getAttribute('content')||'');
   const name=all('meta[name="SiteName"]').map(e=>e.getAttribute('content')||'');
   const official=domain.some(v=>{try{return new URL(/^https?:\/\//i.test(v)?v:'https://'+v).hostname==='www.hitwh.edu.cn';}catch(_){return false;}})&&name.some(v=>/哈尔滨工业大学/.test(v)&&/威海/.test(v));
   const title=document.title||'',schoolTitle=/哈尔滨工业大学/.test(title)&&/威海/.test(title)&&!/登录|认证|访问验证|安全验证/.test(title);
   const homepage=path.replace(currentRoot.slice(VPN.length),'').replace(/\/$/,'');
   // A user may finish verification in the real page, or the old navigation's
   // JS callback may be discarded. The observed homepage therefore must not
   // depend on an ephemeral value returned by the resource-click action.
   const articleLink=all('a').some(e=>{try{const u=new URL(e.getAttribute('href')||'',location.href);return proxyRoot(u.href)===currentRoot&&/\/20\d{2}\/\d{4}\/c\d+a\d+\/page\.htm$/.test(u.pathname);}catch(_){return false;}});
   const homeLabels=new Set(all('a,h1,h2,h3').map(text));
   const siteNavigation=['校区概况','院系部门','师资队伍','人才培养','科学研究','国际合作','人才招聘','招生就业'].filter(label=>homeLabels.has(label)).length>=3;
   const newsSection=['工大要闻','校区新闻','通知公告','学术讲座'].some(label=>homeLabels.has(label));
   const observedHome=currentRoot===VPN+homepageResource&&schoolTitle&&(articleLink||siteNavigation&&newsSection);
   if((observedHome||learnedRoot&&learnedRoot===currentRoot&&schoolTitle||official)&&['','/main.htm','/main.psp','/index.htm','/index.html','/index.php','/index.psp'].includes(homepage))return result('ready',{resourceUrl:currentRoot+'/'});
 }
 if(target==='academic'&&school&&path.endsWith('/kbcx/queryGrkb')&&document.querySelector('select#xnxq')&&shown('th').filter(e=>/^星期[一二三四五六日]$/.test(text(e))).length>=5)return result('ready');
 if(vpn){
   const cas=single(all('a').filter(e=>{try{const u=new URL(e.getAttribute('href'),location.href);return text(e)==='CAS统一身份认证登录'&&u.origin===VPN&&u.pathname==='/login'&&u.searchParams.get('cas_login')==='true';}catch(_){return false;}}));
   if(cas){if(mode==='vpnCas'){location.assign(cas.href);return result('acted');}return result('vpnCas');}
   const tab=single(shown('a,button,li,span,div').filter(e=>text(e)==='统一认证'&&!Array.from(e.children||[]).some(c=>text(c)==='统一认证')));
   if(tab)return click('vpnMethod',tab);
   return result('unknown');
 }
 if(target==='academic'&&school){
   const cas=single(shown('a').filter(e=>{try{const u=new URL(e.getAttribute('href'),location.href);return text(e)==='统一身份认证登录'&&u.origin===VPN&&(u.pathname===academic+'/loginCAS'||u.pathname==='/loginCAS');}catch(_){return false;}}));
   if(cas)return click('academicCas',cas);
   if(all('a').some(e=>/\/kbcx\/queryGrkb(?:$|[?#])/.test(e.getAttribute('href')||'')))return result('menu');
 }
 if(dashboardCandidate){
   // The mobile dashboard uses a non-root route. Its URL alone does not prove
   // it is a resource dashboard: require the exact rendered card name and
   // original address, outside all authentication and proxied-resource routes.
   const label=target==='news'?'校主页':'新教务系统';
   const resourceHost=target==='news'?'www.hitwh.edu.cn':'jwts.hitwh.edu.cn';
   const resourceUrl=VPN+(target==='news'?homepageResource:academic)+'/';
   for(const heading of shown('h2').filter(e=>text(e)===label)){
     const anchor=heading.closest?heading.closest('a[href]'):null;
     if(!anchor||!anchor.querySelector)continue;
     const desc=anchor.querySelector('.block-group__item__desc');
     const address=desc?String(desc.getAttribute('title')||desc.textContent||'').trim().replace(/\/$/,''):'';
     if(address!=='http://'+resourceHost)continue;
     const href=anchor.getAttribute('href');
     const inert=typeof href==='string'&&/^(?:#|javascript:void\(0\);?)$/.test(href.trim());
     if(!inert){try{const link=new URL(href,location.href);if(link.origin!==VPN||link.pathname.replace(/\/$/,'')!==(target==='news'?homepageResource:academic)||link.username||link.password)continue;}catch(_){continue;}}
     const details={dashboard:true,resourceName:label,resourceHost,resourceUrl};
     if(mode==='resource'){location.assign(resourceUrl);return result('acted',details);}
     return result('resource',details);
   }
 }
 if(!auth)return result('unknown');
 const once=single(shown('button').filter(e=>text(e)==='仅本次登录'));
 if(once)return click('trustOnce',once);
 const dynamic=single(shown('input[name="dynamicCode"]'));
 if(dynamic){
   const methods=['哈工大APP验证码','短信验证码'].filter(name=>shown('span[title]').some(e=>e.getAttribute('title')===name));
   const send=single(shown('button#getDynamicCode'));
   if(mode==='chooseMethod'){
     const method=single(shown('span[title]').filter(e=>e.getAttribute('title')===arg.method));
     if(!method)return result('unknown');method.click();return result('acted');
   }
   if(mode==='sendCode'){
     if(!send||send.disabled)return result('waitCode');send.click();return result('acted');
   }
   if(mode==='submitCode'){
     const button=login();if(!button||button.disabled||!String(arg.code||'').trim())return result('unknown');
     set(dynamic,String(arg.code).trim());button.click();return result('acted');
   }
   return result('otp',{methods,canSend:!!send&&!send.disabled});
 }
 const user=single(shown('input#username')),password=single(shown('input#password'));
 if(user&&password&&password.type==='password'){
   const button=login();if(!button)return result('unknown');
   const challenge=shown('input').some(e=>e!==user&&e!==password&&/captcha|verifycode|validatecode|验证码/i.test([e.id,e.name,e.placeholder].join(' ')))||
     shown('iframe').some(e=>/captcha|验证/i.test((e.title||'')+' '+(e.getAttribute('src')||'')));
   if(mode==='prefill'||mode==='credentials'||mode==='verifiedCredentials'){
     if(!arg.account||!arg.password)return result('missing');
     set(user,arg.account);set(password,arg.password);
     if(mode==='prefill')return result('filled');
     if(challenge&&mode!=='verifiedCredentials')return result('captcha');
     if(button.disabled)return result('unknown');button.click();return result('acted');
   }
   return result(challenge?'captcha':'credentials');
 }
 return result('unknown');
})
