(function(){
'use strict';
// DOM capture only. Reads rendered academic tables and selected term labels; never fetches URLs,
// reads form values/cookies/storage, clicks links, or submits a form.
const result={pages:[],blockedFrames:0,truncated:false,possiblePagination:false,loginRequired:false,termToken:''};
let cellCount=0,totalChars=0;const termParts=[];
const visible=e=>!!(e.getClientRects().length)&&getComputedStyle(e).visibility!=='hidden';
function cellText(e){const copy=e.cloneNode(true);copy.querySelectorAll('input,select,textarea,script,style,button').forEach(n=>n.remove());copy.querySelectorAll('br').forEach(n=>n.replaceWith('\n'));copy.querySelectorAll('div,p,li,section').forEach(n=>n.append('\n'));const text=(copy.textContent||'').replace(/\u00a0/g,' ').trim();if(text.length>4000)result.truncated=true;return text.slice(0,4000);}
function read(doc,depth){if(depth>3){result.truncated=true;return;}
 const p={title:doc.title,tables:[],summary:''};
 const login=Array.from(doc.querySelectorAll('input[type="password"]')).some(visible);
 if(login){result.loginRequired=true;return;}
 for(const select of doc.querySelectorAll('select')){if(!visible(select))continue;const selected=Array.from(select.selectedOptions||[]).map(o=>o.textContent.trim()).join('/');const label=(select.name||'')+' '+(select.id||'')+' '+(select.getAttribute('aria-label')||'');if(/(?:20\d{2}\s*[-–—/至]\s*20\d{2}|学期|秋季|春季|夏季)/.test(selected)||/(?:xnxq|xndm|xqdm|semester|学年|学期)/i.test(label))termParts.push(selected.replace(/\s+/g,''));}
 for(const table of doc.querySelectorAll('table,[role="table"],[role="grid"]')){
  if(!visible(table))continue;
  const rows=[];
  for(const row of table.querySelectorAll('tr,[role="row"]')){
   if(row.closest('table,[role="table"],[role="grid"]')!==table||!visible(row))continue;
   const cells=[];
   for(const c of row.querySelectorAll(':scope > th,:scope > td,:scope > [role="cell"],:scope > [role="columnheader"],:scope > [role="gridcell"]')){
    if(++cellCount>12000){result.truncated=true;break;}
    const text=cellText(c);totalChars+=text.length;if(totalChars>650000){result.truncated=true;break;}cells.push({text,rowSpan:c.rowSpan||1,colSpan:c.colSpan||1});
   }
   if(cells.length)rows.push(cells);
   if(result.truncated)break;
  }
  if(rows.length)p.tables.push({rows});
  if(result.truncated)break;
 }
 // Only retain GPA summary lines, not the whole page/profile.
 p.summary=(doc.body?.innerText||'').split('\n').filter(s=>/(平均学分绩点|平均绩点|GPA)\s*[:：=]\s*\d/i.test(s)).join('\n').slice(0,1000);
 result.possiblePagination ||= /(?:共\s*\d+\s*页|下一页|每页\s*\d+\s*条)/.test(doc.body?.innerText||'');
 if(p.tables.length)result.pages.push(p);
 for(const frame of doc.querySelectorAll('iframe,frame')){try{if(frame.contentDocument)read(frame.contentDocument,depth+1);else result.blockedFrames++;}catch(_){result.blockedFrames++;}}
}
read(document,0);const unique=[...new Set(termParts.filter(Boolean))].sort();if(unique.some(s=>/20\d{2}/.test(s)))result.termToken=unique.join('|');return JSON.stringify(result);
})();
