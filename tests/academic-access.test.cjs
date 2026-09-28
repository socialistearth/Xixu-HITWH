const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require.resolve('../app/src/main/assets/academic-access.js'),'utf8');
function probe({passwords=[],notices=[],text=''}={}){
 const el=(innerText,visible=true)=>({innerText,getClientRects:()=>visible?[{}]:[]});
 const document={body:el(text),querySelectorAll:s=>s==='input[type="password"]'?passwords.map(visible=>el('',visible)):notices.map(x=>el(x))};
 return vm.runInNewContext(source,{document});
}
test('HTTP error probe recognizes visible login form without reading its input values',()=>{assert.equal(probe({passwords:[true]}),true);assert.equal(probe({passwords:[false],text:'服务器内部错误'}),false);});
test('expired session error pages are recognized despite their HTTP response status',()=>{
 for(const text of ['登录状态已失效，请重新登录','登录会话已经过期','必须重新认证后访问','尚未登录','Your session has expired'])assert.equal(probe({notices:[text]}),true,text);
});
test('genuine server failures, denied permissions and rate limits remain errors',()=>{
 for(const text of ['500 Internal Server Error','Service unavailable','没有访问此功能的权限','请求过于频繁，请稍后再试','学校统一身份认证系统维护中'])assert.equal(probe({notices:[text]}),false,text);
});
test('an ordinary school page is not mistaken for an authentication failure',()=>{assert.equal(probe({text:'个人课表查询 2026秋季 登录帮助 数学 教师甲 A楼-101'}),false);});
test('plain login-expired document without a styled alert is recognized',()=>{assert.equal(probe({text:'页面提示：请重新登录。'}),true);});
