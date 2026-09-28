const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require.resolve('../app/src/main/assets/login-diagnostics.js'),'utf8');
const VPN='https://webvpn2.hitwh.edu.cn';
function heading(name,address,rendered=true){return{textContent:name,getClientRects:()=>rendered?[{}]:[],closest:()=>({querySelector:()=>({getAttribute:()=>address,textContent:address})})};}
function probe(url,headings=[],target='news',iframes=0){
 const parsed=new URL(url),location={origin:parsed.origin,pathname:parsed.pathname};
 Object.defineProperty(location,'search',{get(){throw Error('Query must never be read');}});
 const document={readyState:'complete',querySelectorAll(selector){if(selector==='iframe')return Array(iframes).fill({});assert.equal(selector,'h2');return headings;}};
 Object.defineProperty(document,'cookie',{get(){throw Error('Cookies must never be read');}});
 const fn=vm.runInNewContext(source,{location,document,window:{innerWidth:390}});
 return JSON.parse(fn({target}));
}
test('diagnostic counts real resource cards without reading login inputs, queries or cookies',()=>{
 const result=probe(VPN+'/?ticket=private-ticket&student=private-student',[heading('校主页','http://www.hitwh.edu.cn'),heading('校主页','http://www.hitwh.edu.cn',false),heading('新教务系统','http://jwts.hitwh.edu.cn/')]);
 assert.equal(result.route,'vpnRoot');assert.equal(result.headings,3);assert.equal(result.targetHeadings,2);assert.equal(result.renderedTargets,1);assert.equal(result.exactCards,2);
 assert.equal(result.probeError,undefined);assert.ok(!JSON.stringify(result).includes('private'));
});
test('diagnostic classifies known and common alternative portal paths while redacting unknown path contents',()=>{
 for(const path of ['/index.html','/resources','/portal/']){const result=probe(VPN+path);assert.equal(result.route,'vpnOther');assert.equal(result.path,path);}
 const result=probe(VPN+'/sensitive-user-id/access-token-value');assert.equal(result.path,'/[other]');assert.ok(!JSON.stringify(result).includes('sensitive'));
});
test('identity and known resource URLs are reduced to route categories without query or original route details',()=>{
 for(const [url,route] of [
  ['https://ids.hit.edu.cn/authserver/login?service=private-target','identity'],
  [VPN+'/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/kbcx/queryGrkb?student=private-student','academic'],
  [VPN+'/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b/?code=private-code','news']]){
  const result=probe(url);assert.equal(result.route,route);assert.ok(!JSON.stringify(result).includes('private'));assert.ok(!result.path.includes('777264'));
 }
});
test('diagnostic describes a missing rendered target without manufacturing a matched resource',()=>{
 const result=probe(VPN+'/',[heading('校主页使用说明','http://www.hitwh.edu.cn'),heading('新教务系统','http://example.org',false)],'academic');
 assert.equal(result.targetHeadings,1);assert.equal(result.renderedTargets,0);assert.equal(result.exactCards,0);
});
test('diagnostic reports iframe count without inspecting frame URLs or contents',()=>{const result=probe(VPN+'/',[],'news',3);assert.equal(result.iframes,3);});
test('mobile non-root dashboard evidence is reported without disclosing its route',()=>{const result=probe(VPN+'/synthetic-unknown-route',[heading('校主页','http://www.hitwh.edu.cn'),heading('校主页','http://www.hitwh.edu.cn')]);assert.equal(result.dashboard,true);assert.equal(result.renderedExactCards,2);assert.equal(result.path,'/[other]');});
