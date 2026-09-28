(function(root,factory){const api=factory(typeof module==='object'?require('./core.js'):root.Academic);if(typeof module==='object')module.exports=api;else root.Startup=api;})(typeof globalThis==='object'?globalThis:this,function(A){
'use strict';
const pool=[
{text:'苟日新，日日新，又日新。',from:'礼记·大学',author:''},
{text:'千里之行，始于足下。',from:'道德经',author:''},
{text:'行到水穷处，坐看云起时。',from:'终南别业',author:'王维'},
{text:'长风破浪会有时，直挂云帆济沧海。',from:'行路难·其一',author:'李白'},
{text:'不积跬步，无以至千里。',from:'劝学',author:'荀子'},
{text:'欲穷千里目，更上一层楼。',from:'登鹳雀楼',author:'王之涣'},
{text:'山重水复疑无路，柳暗花明又一村。',from:'游山西村',author:'陆游'},
{text:'纸上得来终觉浅，绝知此事要躬行。',from:'冬夜读书示子聿',author:'陆游'},
{text:'问渠那得清如许？为有源头活水来。',from:'观书有感·其一',author:'朱熹'},
{text:'知之者不如好之者，好之者不如乐之者。',from:'论语·雍也',author:''}];
function fallback(random=Math.random){return{...pool[Math.min(pool.length-1,Math.max(0,Math.floor(random()*pool.length)))],provider:'内置句库'};}
function quote(raw,local){if(!raw||typeof raw.text!=='string'||!raw.text.trim()||raw.text.length>90)return local;return{text:raw.text.trim(),from:String(raw.from||''),author:String(raw.author||''),provider:String(raw.provider||'一言'),url:/^https:\/\/hitokoto\.cn\?uuid=[0-9a-fA-F-]{36}$/.test(raw.url||'')?raw.url:''};}
function planUpdate(existing,result){
 if(result.error)throw Error(result.error);const{kind,term,sourceKey,snapshot}=result;
 if(!['courses','exams','grades'].includes(kind)||!term||!sourceKey)throw Error('同步目标不完整');
 if(snapshot.loginRequired||snapshot.truncated||snapshot.possiblePagination||snapshot.blockedFrames)throw Error('当前页面不完整或需要登录');
 const parsed=A.parseSnapshot(snapshot,kind);if(!parsed.records.length){if(result.verifiedSchool&&parsed.recognized)return{kind,term,sourceKey,data:existing,meta:{at:result.capturedAt,host:result.host,partial:false,empty:true,automatic:true}};throw Error('没有识别到数据，保留原有缓存');}
 if(parsed.records.some(r=>!A.complete(r,kind)&&!r.unscheduled))throw Error('时间字段不完整，需要手动核对');
 const old=existing.filter(x=>x.term===term&&(result.verifiedSchool||x.syncSourceKey===sourceKey));if(!result.verifiedSchool&&old.some(x=>x.manualOverride))throw Error('此页有手动修正的条目，需要核对后同步');if(!old.length&&!result.verifiedSchool)throw Error('缺少上次导入基准，需要重新手动导入');
 const before=existing.filter(x=>!(x.term===term&&(result.verifiedSchool||x.syncSourceKey===sourceKey)));
 const manual=result.verifiedSchool?old.filter(x=>x.manualOverride):[];before.push(...manual);
 const sameSlot=(x,y)=>x.name===y.name&&x.day===y.day&&x.startPeriod===y.startPeriod&&x.endPeriod===y.endPeriod;
 const next=parsed.records.filter(r=>!manual.some(x=>sameSlot(x,r))).map((r,i)=>{const previous=old.find(x=>A.key(x)===A.key(r));return{...r,term,syncSourceKey:sourceKey,id:previous?.id||'sync-'+sourceKey+'-'+result.capturedAt+'-'+i};});
 const keys=new Set(before.map(x=>x.term+'|'+A.key(x)));for(const r of next){const k=r.term+'|'+A.key(r);if(!keys.has(k)){keys.add(k);before.push(r);}}
 if(before.length>2500)throw Error('条目数超出本机上限');return{kind,term,sourceKey,data:before,meta:{at:result.capturedAt,host:result.host,partial:false,officialGpa:parsed.officialGpa||'',automatic:true,manualPreserved:manual.length}};
}
return{fallback,quote,planUpdate,poolSize:pool.length};
});
