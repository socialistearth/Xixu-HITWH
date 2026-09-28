/* Offline-only academic-data parser. No HTTP requests, endpoints or credentials. */
(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.Academic=api;})(typeof globalThis==='object'?globalThis:this,function(){
'use strict';
const clean=s=>String(s??'').replace(/\u00a0/g,' ').trim();
const flat=s=>clean(s).replace(/\s/g,'').replace(/[()（）:：]/g,'');
const uniq=xs=>[...new Set(xs)];
function weeks(text){
 let s=clean(text).replace(/[第周次（）()\s]/g,'').replace(/[，、；;]/g,',').replace(/[至到~～—–]/g,'-');
 const odd=/单/.test(s),even=/双/.test(s);s=s.replace(/[单双]/g,'');
 if(odd&&even||!s||/[^0-9,\-]/.test(s))return [];
 const out=[];for(const part of s.split(',')){if(!part)continue;const m=part.match(/^(\d{1,2})(?:-(\d{1,2}))?$/);if(!m)return [];let a=+m[1],b=+(m[2]||a);if(a<1||b>40||b<a)return [];for(let i=a;i<=b;i++)if((!odd||i%2===1)&&(!even||i%2===0))out.push(i);}
 return uniq(out).sort((a,b)=>a-b);
}
function periods(text){const s=clean(text).replace(/[第节次（）()\s]/g,'').replace(/[至到~～—–]/g,'-');const m=s.match(/^(\d{1,2})(?:[-,，](\d{1,2}))?$/);if(!m)return null;const a=+m[1],b=+(m[2]||a);return a>=1&&b>=a&&b<=16?[a,b]:null;}
function weekday(text){const s=clean(text).replace(/星期|周|礼拜/g,'');return ({一:1,二:2,三:3,四:4,五:5,六:6,日:7,天:7})[s]||(/^[1-7]$/.test(s)?+s:null);}
function dateISO(text){const m=clean(text).match(/^(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?$/);if(!m)return '';const d=new Date(Date.UTC(+m[1],+m[2]-1,+m[3]));return d.getUTCFullYear()===+m[1]&&d.getUTCMonth()===+m[2]-1&&d.getUTCDate()===+m[3]?`${m[1]}-${m[2].padStart(2,'0')}-${m[3].padStart(2,'0')}`:'';}
function clock(s){const m=clean(s).match(/^(\d{1,2}):(\d{2})$/);return m&&+m[1]<24&&+m[2]<60?`${m[1].padStart(2,'0')}:${m[2]}`:'';}
function timeRange(s){const m=clean(s).match(/(\d{1,2}:\d{2})\s*[-~～—–至]\s*(\d{1,2}:\d{2})/);if(!m)return null;const a=clock(m[1]),b=clock(m[2]);return a&&b&&b>a?[a,b]:null;}
function number(s){const x=clean(s);return /^\d+(\.\d+)?$/.test(x)?Number(x):null;}
function expand(rows){const grid=[],origins=[];for(let r=0;r<Math.min(rows.length,800);r++){grid[r]||=[];let c=0;for(const cell of rows[r]){while(grid[r][c]!==undefined)c++;if(c>=40)break;const t=clean(typeof cell==='string'?cell:cell.text);const rs=Math.max(1,Math.min(40,+cell.rowSpan||1)),cs=Math.max(1,Math.min(30,+cell.colSpan||1));origins.push({r,c,text:t,rowSpan:rs,colSpan:cs});for(let a=0;a<rs;a++){grid[r+a]||=[];for(let b=0;b<cs;b++)grid[r+a][c+b]=t;}c+=cs;}}return{grid,origins};}
const labels={name:/^(课程名称|课程名|课程|考试科目|科目名称|科目)$/,day:/^(星期|星期几|上课星期|周几)$/,period:/^(节次|上课节次|上课节数)$/,weeks:/^(周次|上课周次|教学周)$/,teacher:/^(教师|任课教师|授课教师|教师姓名|主讲教师)$/,room:/^(教室|上课地点|上课教室|考试地点|考试教室|地点|考场)$/,date:/^(考试日期|日期)$/,time:/^(上课时间|考试时间|考试具体时间|时间|起止时间)$/,score:/^(总评成绩|总成绩|最终成绩|成绩)$/,credit:/^(学分|课程学分)$/,point:/^(绩点|课程绩点|学分绩点)$/,seat:/^(座位号|座号|座位)$/,term:/^(学年学期|学期)$/,type:/^(课程性质|课程类别|性质)$/};
function columns(row){const map={};row.forEach((cell,i)=>{for(const [key,re]of Object.entries(labels))if(re.test(flat(cell))&&(map[key]===undefined||key==='score'&&flat(cell)==='最终成绩'))map[key]=i;});return map;}

// Observed HITWH cell format: course <br> teacher[weeks]周[, more weeks]room.
function schoolCell(group){
 const ls=group.split(/\n/).map(clean).filter(Boolean);if(ls.length<2)return null;
 const text=ls.slice(1).join('\n'),matches=[...text.matchAll(/[\[【]([^\]】]+)[\]】]\s*周(?:\s*[（(]([单双])[)）])?/g)];if(!matches.length)return null;
 const teacher=clean(text.slice(0,matches[0].index)).replace(/^(?:教师|任课教师)[:：]/,'');
 if(!teacher||/[\d\[\]【】]/.test(teacher))return null;
 const w=[];for(const m of matches){const part=weeks(m[1]+(m[2]||''));if(!part.length)return null;w.push(...part);}
 // Do not combine distinct teacher/location blocks into one invented meeting.
 for(let i=1;i<matches.length;i++){const between=text.slice(matches[i-1].index+matches[i-1][0].length,matches[i].index);if(!/^[\s，,、；;]*$/.test(between))return null;}
 const last=matches.at(-1),room=clean(text.slice(last.index+last[0].length)).replace(/^(?:教室|地点|上课地点)[:：]/,'').replace(/\s+/g,'');
 return {name:ls[0],teacher,room,weeks:uniq(w).sort((a,b)=>a-b)};
}
function parseTable(table,kind){const{grid,origins}=expand(table.rows||[]);const out=[],warnings=[];let header=-1,map={};for(let r=0;r<Math.min(8,grid.length);r++){const m=columns(grid[r]);if(m.name!==undefined&&(kind==='grades'?m.score!==undefined:kind==='exams'?m.date!==undefined||m.time!==undefined:m.day!==undefined||m.period!==undefined)){header=r;map=m;break;}}
 if(header>=0){for(let r=header+1;r<grid.length;r++){const row=grid[r],v=k=>clean(row[map[k]]);const name=v('name');if(!name||labels.name.test(flat(name))||/^(合计|总计)/.test(name))continue;const base={name,room:v('room'),teacher:v('teacher'),raw:row.filter(Boolean).join(' · '),sourceTerm:v('term')};
 if(kind==='courses'){const p=periods(v('period')),d=weekday(v('day')),w=weeks(v('weeks')),tm=timeRange(v('time'));out.push({...base,day:d||0,startPeriod:p?p[0]:0,endPeriod:p?p[1]:0,weeks:w,start:tm?tm[0]:'',end:tm?tm[1]:''});}
 if(kind==='exams'){const all=v('date')+' '+v('time');const dm=all.match(/\d{4}[-/.年]\d{1,2}[-/.月]\d{1,2}日?/);const tm=timeRange(v('time'));out.push({...base,date:dm?dateISO(dm[0]):'',start:tm?tm[0]:'',end:tm?tm[1]:'',seat:v('seat')});}
 if(kind==='grades')out.push({...base,score:v('score'),credit:number(v('credit')),point:number(v('point')),type:v('type')});}
 return{records:out,warnings,recognized:true};}
 // Matrix timetable: weekdays must come from the actual header, not column position.
 if(kind==='courses'){let hr=-1,days={};for(let r=0;r<Math.min(grid.length,6);r++){const cols={};grid[r].forEach((s,c)=>{if(/^(星期|周|礼拜)[一二三四五六日天]$/.test(flat(s)))cols[c]=weekday(flat(s));});if(Object.keys(cols).length>=5){hr=r;days=cols;break;}}
 if(hr>=0){for(const cell of origins){if(cell.r<=hr||!days[cell.c]||!cell.text||cell.colSpan!==1)continue;const lines=cell.text.split(/\n+/).map(clean).filter(Boolean);if(!lines.length||/^(无|暂无|休息|午休|晚休|—|-)$/.test(lines[0]))continue;
 // Separate course groups only at explicit blank lines. Ambiguous mixed cells require review.
 const groups=cell.text.split(/\n\s*\n/).filter(clean);for(const group of groups){const ls=group.split(/\n/).map(clean).filter(Boolean);const label=(re)=>ls.find(x=>re.test(x))||'';
 const wm=group.match(/((?:\d{1,2}\s*(?:[-~～—–至]\s*\d{1,2})?\s*[,，、]?\s*)+)(?:周|\(周\)|（周）)(?:\s*[（(]([单双])[)）])?/);
 const pm=group.match(/(\d{1,2}\s*(?:[-~～—–至]\s*\d{1,2})?)\s*节/);
 let p=pm?periods(pm[1]):null;if(!p){for(let c=0;c<cell.c;c++){p=periods(grid[cell.r][c]);if(p)break;}}
 // A rowspan is not automatically a duration: it may span morning/afternoon groups.
 const compact=schoolCell(group);
 const name=(label(/^课程(?:名称)?[:：]/).replace(/^课程(?:名称)?[:：]/,'')||ls[0]).replace(/^\[[^\]]+\]\s*/,'');
 out.push({name,day:days[cell.c],startPeriod:p?p[0]:0,endPeriod:p?p[1]:0,weeks:wm?weeks(wm[1]+(wm[2]||'')):[],teacher:label(/^(?:教师|任课教师)[:：]/).replace(/^[^:：]+[:：]/,''),room:label(/^(?:教室|地点|上课地点)[:：]/).replace(/^[^:：]+[:：]/,''),start:'',end:'',raw:group,sourceTerm:'',...(compact||{})});}
 }
 for(const cell of origins){if(cell.colSpan>1&&/^其它课程[：:]/.test(cell.text)){const other=cell.text.replace(/^其它课程[：:]\s*/,'').split(/[；;\n]+/).filter(clean);for(const entry of other){const fields=entry.split('◇');if(fields.length>=3)out.push({name:clean(fields[0]),teacher:'',room:clean(fields[1]),weeks:weeks(fields[2]),day:0,startPeriod:0,endPeriod:0,start:'',end:'',unscheduled:true,raw:entry,sourceTerm:''});}}}
 warnings.push('网格课表按可见文字提取：请核对课程名称、教室、教师、周次和跨节范围。');return{records:out,warnings,recognized:true};}
 }
 return{records:out,warnings};
}
function key(x){return JSON.stringify([x.name,x.day,x.startPeriod,x.endPeriod,x.weeks,x.date,x.start,x.room,x.score,x.credit,x.point,x.sourceTerm]);}
function complete(x,kind){return kind==='grades'?!!x.name:kind==='courses'?!!(x.name&&x.day>=1&&x.day<=7&&x.startPeriod>0&&x.endPeriod>=x.startPeriod&&x.weeks?.length):!!(x.name&&dateISO(x.date)&&clock(x.start)&&clock(x.end)&&x.end>x.start);}
function parseSnapshot(snapshot,kind){if(!['courses','exams','grades'].includes(kind))throw Error('未知查询类型');const records=[],warnings=[];let officialGpa='',recognized=false;for(const page of snapshot.pages||[]){for(const table of page.tables||[]){const r=parseTable(table,kind);recognized ||= !!r.recognized;records.push(...r.records);warnings.push(...r.warnings);}if(kind==='grades'){const m=clean(page.summary).match(/(?:平均学分绩点|平均绩点|GPA)\s*[:：=]\s*(\d+(?:\.\d+)?)/i);if(m)officialGpa=m[1];}}
 const seen=new Set(),result=records.filter(x=>{const k=key(x);if(seen.has(k))return false;seen.add(k);return true;});
 if(snapshot.blockedFrames)warnings.push('有跨域框架无法读取；请将结果页面在本窗口单独打开后重试。');
 if(snapshot.truncated)warnings.push('页面超出读取上限，当前结果不完整。');
 if(snapshot.possiblePagination)warnings.push('页面可能存在分页；只读取了当前显示页。');
 if(result.some(x=>!complete(x,kind)&&!x.unscheduled))warnings.push('部分课程或考试的时间字段未识别，补全前不会安排提醒。');
 return{records:result,warnings:uniq(warnings),officialGpa,recognized};}
function addDays(iso,n){const s=dateISO(iso);if(!s)throw Error('日期格式应为 YYYY-MM-DD');const d=new Date(s+'T00:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);}
function eventFor(course,week,settings){if(!complete(course,'courses')||!course.weeks.includes(week))throw Error('本周没有这门课');if(!settings.termConfirmed||!dateISO(settings.firstMonday)||new Date(settings.firstMonday+'T00:00:00Z').getUTCDay()!==1)throw Error('请先确认第一教学周的周一日期');let start=clock(course.start),end=clock(course.end);if(!start||!end){if(!settings.timesConfirmed)throw Error('请先在用户页确认学校作息时间');start=clock(settings.periods?.[course.startPeriod-1]?.start);end=clock(settings.periods?.[course.endPeriod-1]?.end);}if(!start||!end||end<=start)throw Error('该课程的上课时间未补全');return{name:course.name,room:course.room,date:addDays(settings.firstMonday,(week-1)*7+course.day-1),start,end};}
function currentWeek(settings,today){if(!settings.termConfirmed||!dateISO(settings.firstMonday))return 1;return Math.max(1,Math.min(settings.weekCount||20,Math.floor((Date.parse(dateISO(today)+'T00:00:00Z')-Date.parse(settings.firstMonday+'T00:00:00Z'))/604800000)+1));}
function weighted(grades){const valid=grades.filter(x=>x.point!==null&&x.credit!==null&&x.credit>0&&Number.isFinite(x.point)&&Number.isFinite(x.credit));const credits=valid.reduce((s,x)=>s+x.credit,0);return{value:credits?valid.reduce((s,x)=>s+x.credit*x.point,0)/credits:null,count:valid.length,credits,all:grades.length};}
return{weeks,periods,weekday,dateISO,clock,timeRange,expand,parseTable,parseSnapshot,complete,eventFor,addDays,currentWeek,weighted,key};
});
