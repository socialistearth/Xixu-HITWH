(function(kind,term){
'use strict';
const paths={courses:'/kbcx/queryGrkb',exams:'/kscx/queryKcForXs',grades:'/cjcx/queryQmcj'};
const reply=state=>JSON.stringify({state});
const visible=e=>!!e.getClientRects().length;
if([...document.querySelectorAll('input[type="password"]')].some(visible))return reply('login');
if(!paths[kind]||!location.pathname.endsWith(paths[kind]))return reply('login');
const m=String(term).match(/(20\d{2})(?:\s*[-–—/]\s*(20\d{2}))?.*?([春夏秋冬])/);if(!m)return reply('term');
const expected=(m[2]&&m[3]!=='秋'?m[2]:m[1])+m[3]+'季';
const matches=[...document.querySelectorAll('select')].filter(visible).map(s=>({s,o:[...s.options].find(o=>o.textContent.replace(/\s/g,'')===expected)})).filter(x=>x.o);
if(matches.length!==1)return reply('term');
const {s,o}=matches[0];const changed=s.value!==o.value;
if(kind==='courses'){
 if(changed){s.value=o.value;s.dispatchEvent(new Event('change',{bubbles:true}));return reply('submitted');}
 return reply('ready');
}
const query=[...document.querySelectorAll('a,button')].filter(e=>visible(e)&&e.textContent.replace(/\s/g,'')==='查询');if(query.length!==1)return reply('structure');
s.value=o.value;query[0].click();return reply('submitted');
})
