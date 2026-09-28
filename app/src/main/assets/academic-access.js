(function(){
'use strict';
// Return one boolean only: no field values, response text, credentials or query data.
const visible=e=>!!(e&&e.getClientRects().length);
if([...document.querySelectorAll('input[type="password"]')].some(visible))return true;
const notices=[...document.querySelectorAll('.wp_error_msg,[role="alert"],.alert,.error,.error-message')].filter(visible);
if(!notices.length&&document.body)notices.push(document.body);
return notices.some(e=>/(?:登录(?:状态|会话|信息)?(?:已经?|已)?(?:过期|失效)|(?:请|需要|必须)重新(?:登录|认证)|未登录|尚未登录|session\s+(?:has\s+)?(?:expired|timed\s*out))/i.test(String(e.innerText||e.textContent||'').slice(0,12000)));
})()
