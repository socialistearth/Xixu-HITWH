(function (arg) {
'use strict';
// Read the current rendered DOM only. Navigation, limits and cancellation belong to Java.
// Never read cookies, input values or storage; never follow links or call fetch here.
arg = arg || {};
const clean = value => String(value || '').replace(/\u00a0/g, ' ').replace(/[ \t\r\f\v]+/g, ' ').trim();
const result = value => JSON.stringify(value);
// Android's WebView URL is the authority: WebVPN may virtualize DOM location to the original site.
const current = new URL(arg.nativeUrl || location.href);
let home;
try { home = new URL(arg.homeUrl || current.href); } catch (_) { return result({state:'outside'}); }
const root = home.pathname.match(/^\/(?:http|https)\/[A-Za-z0-9_-]+(?=\/|$)/);
if (!root || home.protocol !== 'https:' || home.hostname !== 'webvpn2.hitwh.edu.cn' || home.username || home.password || home.port && home.port !== '443') return result({state:'outside'});
const prefix = root[0];
const sameVpn = u => u.origin === home.origin && !u.username && !u.password;
const inside = u => sameVpn(u) && (u.pathname === prefix || u.pathname.startsWith(prefix + '/'));
const publicNewsHosts = new Set(['news.hit.edu.cn','news.hitwh.edu.cn']);
const publicNews = u => ['http:','https:'].includes(u.protocol) && publicNewsHosts.has(u.hostname) && !u.username && !u.password && !u.port;
if (!(arg.mode === 'article' ? sameVpn(current) || publicNews(current) : inside(current)) || arg.expected && current.href !== arg.expected) return result({state:'changed'});
const visible = e => !e.getClientRects || !!e.getClientRects().length;
const pageTitle = clean(document.title);
const accessTitle = /统一身份认证|用户登录|访问验证|安全验证|访问受限|无权访问|权限不足|禁止访问|Access Denied|Forbidden/i;
const passwordForm = Array.from(document.querySelectorAll('input[type="password"]')).some(visible);
const accessError = document.querySelector('.wp_error_msg');
const campusOnly = /当前\s*ip\s*并非校内地址|仅允许校内地址访问|仅限校内|校内(?:用户|IP)|没有(?:访问)?权限|无权访问|权限不足|访问被拒绝/i;
if (accessError && campusOnly.test(clean(accessError.textContent))) return result({state:'login',message:'该文章仅允许校内地址访问，当前校园 VPN 会话未获得访问权限。'});
if (passwordForm || accessTitle.test(pageTitle)) return result({state:'login',message:'学校需要重新登录或访问验证，请主动获取后完成验证。'});
const now = Number.isFinite(arg.nowMs) ? arg.nowMs : Date.now();
const beijingDay = Math.floor((now + 8 * 3600000) / 86400000);
// These exact routes were observed by opening 校主页 and its 校区新闻 links through this VPN.
// No encryption, guessing, request, or expansion to an unverified original host is performed.
const vpnOrigin = 'https://webvpn2.hitwh.edu.cn';
const routes = {
    'www.hitwh.edu.cn':'/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b',
    'news.hitwh.edu.cn':'/http/77726476706e69737468656265737421fef2568f693861446900c7a99c406d3633'
};
const originalHost = Object.keys(routes).find(host => current.pathname === routes[host] || current.pathname.startsWith(routes[host]+'/'));
const originalBase = originalHost ? 'http://'+originalHost+(current.pathname.slice(routes[originalHost].length)||'/') : current.href;
const articlePath = /^\/(20\d{2})\/(\d{2})(\d{2})\/c\d+a(\d+)\/page\.(?:htm|psp)$/i;
const vpnArticlePath = /^\/(?:http|https)\/[A-Za-z0-9_-]+(\/20\d{2}\/\d{4}\/c\d+a\d+\/page\.(?:htm|psp))$/i;
const canonicalPath = path => path.replace(/\/page\.(?:htm|psp)$/i,'/page.htm');
function safeUrl(value, base) {
    const raw = String(value || '').trim();
    if (!raw || raw.length>2048 || /[\\\u0000-\u0020\u007f]/.test(raw) || /^(?:[a-z][a-z0-9+.-]*:)?\/\/[^/?#]*@/i.test(raw)) return null;
    try {
        const u=new URL(raw,base);
        if (!['http:','https:'].includes(u.protocol) || u.username || u.password || u.port) return null;
        return u;
    } catch (_) { return null; }
}
const anchors = arg.mode === 'home' ? Array.from(document.querySelectorAll('a[href]')).slice(0,2000) : [];
function explicitVpn(value) {
    const u=safeUrl(value,current.href);
    return u && sameVpn(u) && vpnArticlePath.test(u.pathname) ? u : null;
}
function pairedProxy(raw, resolved) {
    const original=safeUrl(raw,originalBase), proxy=explicitVpn(resolved);
    if (!original || !proxy) return null;
    const originalPath=articlePath.test(original.pathname) ? original.pathname : (sameVpn(original) && original.pathname.match(vpnArticlePath) || [])[1];
    const official=original.hostname==='hit.edu.cn'||original.hostname.endsWith('.hit.edu.cn')||original.hostname==='hitwh.edu.cn'||original.hostname.endsWith('.hitwh.edu.cn');
    return official && originalPath && canonicalPath(originalPath)===canonicalPath(proxy.pathname.match(vpnArticlePath)[1]) ? proxy : null;
}
// Prefer the page's actual rewrite to a historical route. Learn only explicit same-article
// original/VPN pairs already present on this homepage; never derive encrypted proxy tokens.
const observedRoutes=new Map(), conflictingRoutes=new Set();
for (const a of anchors) {
    let resolved='';try { resolved=typeof a.href==='string'?a.href:''; } catch (_) {}
    const original=safeUrl(a.getAttribute('href'),originalBase), proxy=pairedProxy(a.getAttribute('href'),resolved);
    if (!original || !proxy || original.hostname===home.hostname || !articlePath.test(original.pathname)) continue;
    const path=proxy.pathname.match(vpnArticlePath), route=proxy.pathname.slice(0,-path[1].length);
    if (observedRoutes.has(original.hostname) && observedRoutes.get(original.hostname)!==route) conflictingRoutes.add(original.hostname);
    else observedRoutes.set(original.hostname,route);
}
for (const [host, route] of observedRoutes) if (!conflictingRoutes.has(host)) routes[host]=route;
function toArticle(value) {
    // A rewritten link must be an explicit HTTPS VPN article, even if its original host is unknown.
    const nativeResolved=safeUrl(value,current.href);
    if (nativeResolved && sameVpn(nativeResolved) && vpnArticlePath.test(nativeResolved.pathname)) return nativeResolved;
    // Raw root-relative links refer to the verified original homepage, not the VPN dashboard root.
    const original=safeUrl(value,originalBase);
    if (!original || !articlePath.test(original.pathname)) return null;
    if (arg.mode==='article' && publicNews(original)) return new URL('https://'+original.hostname+original.pathname);
    if (Object.prototype.hasOwnProperty.call(routes,original.hostname)) return new URL(vpnOrigin+routes[original.hostname]+original.pathname);
    // These two official news sites were checked over HTTPS. Only articles explicitly
    // linked by the authenticated homepage enter the native navigation allowlist.
    if (publicNews(original)) return new URL('https://'+original.hostname+original.pathname);
    return null;
}
function articleInfo(value, requireRecent = true) {
    const u=toArticle(value);
    if (!u) return null;
    const vpnMatch=u.pathname.match(vpnArticlePath);
    const match=(vpnMatch ? vpnMatch[1] : publicNews(u) ? u.pathname : '').match(articlePath);
    if (!match) return null;
    const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
    const date = new Date(Date.UTC(year, month-1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month-1 || date.getUTCDate() !== day) return null;
    const age = beijingDay - Math.floor(date.getTime() / 86400000);
    if (requireRecent && (age < 0 || age >= 7)) return null;
    u.hash = '';
    // WebPlus page identity is its path; omit transient VPN/session query flags from saved links.
    u.search = '';
    // The school redirects static article links to the same WebPlus page.psp identity.
    u.pathname = canonicalPath(u.pathname);
    let host=publicNews(u)?u.hostname:Object.keys(routes).find(host=>u.pathname.startsWith(routes[host]+'/'));
    const hint=safeUrl(arg.publicUrl);
    if (!host && hint && publicNews(hint) && canonicalPath(hint.pathname)===canonicalPath(vpnMatch&&vpnMatch[1]||'')) host=hint.hostname;
    return {id:(host==='news.hit.edu.cn'?'hit-news:a':'hitwh-home:a') + match[4],url:u.href,published:match[1]+'-'+match[2]+'-'+match[3],source:'校主页'};
}
function publicArticleUrl(value) {
    const original=safeUrl(value,originalBase);
    if (original && publicNews(original) && articlePath.test(original.pathname)) return 'https://'+original.hostname+canonicalPath(original.pathname);
    const proxy=explicitVpn(value);
    if (!proxy) return '';
    const match=proxy.pathname.match(vpnArticlePath), route=proxy.pathname.slice(0,-match[1].length);
    const host=Object.keys(routes).find(host=>publicNewsHosts.has(host)&&routes[host]===route);
    return host ? 'https://'+host+canonicalPath(match[1]) : '';
}
// Manual review links keep their original official host; an unknown VPN route is never invented.
function manualArticleLink(value, title) {
    const u=safeUrl(value,originalBase);
    if (!u || !['hit.edu.cn','hitwh.edu.cn'].some(host=>u.hostname===host||u.hostname.endsWith('.'+host))) return null;
    const match=u.pathname.match(/\/(20\d{2})\/(\d{2})(\d{2})\/c\d+a(\d+)\/page\.(?:htm|psp)$/i);
    if (!match) return null;
    const year=Number(match[1]),month=Number(match[2]),day=Number(match[3]);
    const date=new Date(Date.UTC(year,month-1,day)),age=beijingDay-Math.floor(date.getTime()/86400000);
    if(date.getUTCFullYear()!==year||date.getUTCMonth()!==month-1||date.getUTCDate()!==day||age<0||age>=7)return null;
    u.search='';u.hash='';
    // A title occasionally consists of the URL itself; do not re-export its transient query there.
    const label=clean(title).replace(/https?:\/\/[^\s<>"']+/gi,value=>{
        try{const url=new URL(value);url.search='';url.hash='';return url.href;}catch(_){return '原文链接';}
    }).replace(/\n+/g,' ').slice(0,180)||'待手动查看的文章';
    return {id:(u.hostname==='news.hit.edu.cn'?'hit-news:a':'hitwh-home:a')+match[4],title:label,url:u.href,published:match[1]+'-'+match[2]+'-'+match[3],reason:'未能转换为可自动读取的校园 VPN 链接'};
}
if (arg.mode === 'home') {
    const known = new Set(Array.isArray(arg.knownIds) ? arg.knownIds.slice(0,20000).map(String) : []);
    const found = new Map(), linkedIds = new Set(), recentIds = new Set(), knownFound = new Set(), unresolved = new Set(), unresolvedRecent = new Set(), unresolvedDetails = new Map();
    for (const a of anchors) {
        const raw = a.getAttribute('href');
        // WebVPN can expose a rewritten property while keeping the raw HTML attribute unchanged.
        let resolved = '';
        try { const property = a.href; if (typeof property === 'string') resolved = property; } catch (_) {}
        // Do not let a normalized property hide forbidden credentials, ports or schemes in the raw link.
        const paired=pairedProxy(raw,resolved);
        const item = safeUrl(raw,current.href) && (paired ? articleInfo(paired.href,false) : articleInfo(raw, false));
        if (!item) {
            const candidate = String(raw || '').match(/\/(20\d{2})\/(\d{2})(\d{2})\/c\d+a(\d+)\/page\.(?:htm|psp)(?:$|[?#])/i);
            if (candidate) {
                unresolved.add(raw);
                // Diagnose only unread recent articles as missing work. Old, already judged,
                // or duplicated raw links must not make an otherwise complete scan fail.
                const year=Number(candidate[1]),month=Number(candidate[2]),day=Number(candidate[3]);
                const original=safeUrl(raw,originalBase);
                const date=new Date(Date.UTC(year,month-1,day)),id=(original&&original.hostname==='news.hit.edu.cn'?'hit-news:a':'hitwh-home:a')+candidate[4];
                const age=beijingDay-Math.floor(date.getTime()/86400000);
                if(date.getUTCFullYear()===year&&date.getUTCMonth()===month-1&&date.getUTCDate()===day&&age>=0&&age<7&&!known.has(id)){
                    unresolvedRecent.add(id);
                    const manual=manualArticleLink(raw,a.getAttribute('title')||a.textContent);
                    if(manual){const previous=unresolvedDetails.get(id);if(!previous||previous.title.length<manual.title.length)unresolvedDetails.set(id,manual);}
                }
            }
            continue;
        }
        linkedIds.add(item.id);
        const publishedDay = Math.floor(Date.parse(item.published+'T00:00:00Z') / 86400000);
        const age = beijingDay-publishedDay;
        if (age < 0 || age >= 7) continue;
        recentIds.add(item.id);
        if (known.has(item.id)) { knownFound.add(item.id); continue; }
        const title = clean(a.getAttribute('title') || a.textContent).replace(/\n+/g,' ').slice(0,180);
        if (!title) continue;
        item.title = title;
        const publicUrl=publicArticleUrl(raw)||publicArticleUrl(item.url);
        if (publicUrl && publicUrl!==item.url) item.publicUrl=publicUrl;
        const old = found.get(item.id);
        if (!old || old.title.length < title.length) found.set(item.id,item);
    }
    const articles = Array.from(found.values()).sort((a,b) => b.published.localeCompare(a.published));
    for(const id of linkedIds){unresolvedRecent.delete(id);unresolvedDetails.delete(id);}
    const unresolvedLinks=Array.from(unresolvedDetails.values()).sort((a,b)=>b.published.localeCompare(a.published)).slice(0,40);
    const stats = {anchors:anchors.length,linked:linkedIds.size,recent:recentIds.size,known:knownFound.size,new:recentIds.size-knownFound.size,queued:Math.min(20,articles.length),unresolved:unresolved.size,unresolvedRecent:unresolvedRecent.size,missingTitle:recentIds.size-knownFound.size-found.size,read:0,failed:0};
    if (!stats.linked) return result({state:'homePending',message:'已进入校主页，但尚未识别到新闻链接',stats,unresolvedLinks});
    if (!articles.length && (stats.missingTitle || stats.unresolvedRecent)) return result({state:'homePending',message:'校主页近期新闻链接或标题尚未读取完整',stats,unresolvedLinks});
    const emptyReason = articles.length ? '' : stats.recent ? 'all_known' : 'no_recent_articles';
    return result({state:'home',homeUrl:current.href,articles:articles.slice(0,20),linked:stats.linked,limited:articles.length>20,stats,emptyReason,unresolvedLinks});
}
if (arg.mode !== 'article') return result({state:'unknown'});
const item = articleInfo(current.href);
const expectedArticle = arg.articleUrl ? articleInfo(arg.articleUrl) : null;
if (!item || !expectedArticle || item.url !== expectedArticle.url || arg.id && item.id !== arg.id) return result({state:'changed'});
const body = document.querySelector('.wp_articlecontent,.field--name-body,.article-content,.article_content');
if (!body) {
    const visibleText = clean(document.body && document.body.innerText).slice(0,5000);
    if (campusOnly.test(visibleText) || /请(?:先)?登录|需要.*(?:身份认证|访问验证)|资源访问控制系统/.test(visibleText)) return result({state:'login',message:'校主页文章需要进一步登录或访问授权，请主动获取后完成验证。'});
    return result({state:'failed',message:'未识别到这篇文章的正文结构'});
}
const copy = body.cloneNode(true);
copy.querySelectorAll('script,style,noscript,iframe,input,textarea,select,button,nav,header,footer').forEach(n=>n.remove());
const notes = [];
if (copy.querySelector('img')) notes.push('含图片/二维码，未识别图片内容');
if (Array.from(copy.querySelectorAll('a[href]')).some(a => /\.(?:pdf|docx?|xlsx?|pptx?|zip|rar|7z|wps)(?:$|[?#])/i.test(a.getAttribute('href')||'') || /附件|下载/.test(a.textContent||''))) notes.push('含附件，未读取附件内容');
copy.querySelectorAll('br').forEach(n=>n.replaceWith('\n'));
copy.querySelectorAll('p,div,li,section,tr,h2,h3,h4').forEach(n=>n.append('\n'));
let text = clean(copy.textContent).replace(/ *\n */g,'\n').replace(/\n{3,}/g,'\n\n');
if (text.length < 25 && !copy.querySelector('img,a[href]')) return result({state:'failed',message:'这篇文章正文为空或不完整'});
const titleNode = ['.arti_title','.article-title','.article_title','h1.title','h1'].map(s=>document.querySelector(s)).find(Boolean);
item.title = clean(titleNode && titleNode.textContent || arg.title || pageTitle).replace(/\n+/g,' ').slice(0,180);
if (text.length > 5500) { text = text.slice(0,5500); notes.push('正文较长，仅读取前5500字'); }
item.body = text;
item.note = notes.join('；');
return result({state:'article',article:item});
})
