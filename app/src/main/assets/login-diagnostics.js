/* Local DOM counts only. Never reads inputs, cookies, queries, or page text. */
(function (arg) {
 'use strict';
 var out={version:1,ready:'unknown',route:'unknown',path:'/[other]',headings:0,targetHeadings:0,renderedTargets:0,exactCards:0,renderedExactCards:0,dashboard:false,iframes:0,viewport:0};
 try {
  var vpn='https://webvpn2.hitwh.edu.cn',path=String(location.pathname||''),origin=String(location.origin||'');
  var academic='/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667';
  var home='/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b';
  var identity='/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b/authserver/';
  if(origin===vpn){
   if(path==='/'){out.route='vpnRoot';out.path='/';}
   else if(path==='/login'){out.route='vpnLogin';out.path='/login';}
   else if(path.indexOf(identity)===0){out.route='identity';out.path='/proxy/identity';}
   else if(path===academic||path.indexOf(academic+'/')===0){out.route='academic';out.path='/proxy/academic';}
   else if(path===home||path.indexOf(home+'/')===0){out.route='news';out.path='/proxy/news';}
   else {out.route='vpnOther';if(/^\/(?:index|main|portal|resource|resources|dashboard)(?:\.(?:htm|html|psp))?\/?$/.test(path))out.path=path;}
  }else if(origin==='https://ids.hit.edu.cn'&&path.indexOf('/authserver/')===0){out.route='identity';out.path='/authserver';}
  else out.route='other';
  out.ready=/^(?:loading|interactive|complete)$/.test(document.readyState)?document.readyState:'unknown';
  out.viewport=Math.max(0,Math.min(10000,Number(window.innerWidth)||0));
  out.iframes=Math.min(2000,document.querySelectorAll('iframe').length);
  var headings=document.querySelectorAll('h2'),label=arg.target==='news'?'校主页':'新教务系统';
  out.headings=Math.min(2000,headings.length);
  for(var i=0;i<headings.length&&i<2000;i++){
   var h=headings[i];if(String(h.textContent||'').replace(/\s/g,'')!==label)continue;
   out.targetHeadings++;
   var rendered=h.getClientRects().length>0;if(rendered)out.renderedTargets++;
   var a=h.closest?h.closest('a[href]'):null,d=a&&a.querySelector('.block-group__item__desc');
   var description=d?String(d.getAttribute('title')||d.textContent||'').trim().replace(/\/$/,''):'';
   if(description===(arg.target==='news'?'http://www.hitwh.edu.cn':'http://jwts.hitwh.edu.cn')){out.exactCards++;if(rendered)out.renderedExactCards++;}
  }
  var decodedPath=decodeURIComponent(path);
  out.dashboard=origin===vpn&&!/^\/(?:http|https)(?:\/|$)/i.test(decodedPath)&&!/(?:^|\/)(?:authserver|auth|login|logout|cas|sso|oauth|saml|mfa)(?:\/|$)/i.test(decodedPath)&&out.renderedExactCards>0;
 }catch(error){out.probeError=String(error&&error.name||'Error').replace(/[^A-Za-z]/g,'').slice(0,32);}
 return JSON.stringify(out);
})
