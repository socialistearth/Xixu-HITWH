const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

// Source guards for the Android-only dialog lifecycle. They deliberately check
// the ordering of user-consent and disposal gates rather than mock Android UI.
const source = fs.readFileSync(require.resolve('../app/src/main/java/edu/hitwh/fieldnote/SchoolLogin.java'), 'utf8');
function method(signature) {
  const start = source.indexOf(signature);
  assert.ok(start >= 0, `missing ${signature}`);
  const open = source.indexOf('{', start);
  let depth = 1, quote = '', comment = '';
  for (let i = open + 1; i < source.length; i++) {
    const c = source[i], n = source[i + 1];
    if (comment === '//') { if (c === '\n') comment = ''; continue; }
    if (comment === '/*') { if (c === '*' && n === '/') { comment = ''; i++; } continue; }
    if (quote) { if (c === '\\') i++; else if (c === quote) quote = ''; continue; }
    if (c === '/' && (n === '/' || n === '*')) { comment = c + n; i++; continue; }
    if (c === '"' || c === "'") { quote = c; continue; }
    if (c === '{') depth++;
    if (c === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  throw Error(`unclosed ${signature}`);
}
const handle = method('private void handle(');
const official = method('private void showOfficialVerification(');
const open = method('void openVerification(');
const inspect = method('private void inspect(');
const pause = method('void pause(');

test('verification deferral is opt-in and only explicit opening grants page visibility', () => {
  assert.match(method('SchoolLogin deferVerification('), /deferredVerification=true;return this;/);
  assert.equal((source.match(/deferredVerification\s*=\s*true/g) || []).length, 1);
  assert.equal((source.match(/verificationRequested\s*=\s*true/g) || []).length, 1);
  assert.match(open, /verificationRequested=true;waitingForVerification=false;resume\(\);/);
  assert.match(official, /^\s*if\(deferredVerification&&!verificationRequested\)\{pause\([^;]+;return;\}/);
});

test('automatic captcha and OTP paths wait before any verification UI or prefill', () => {
  for (const state of ['captcha', 'otp']) {
    const branch = handle.slice(handle.indexOf(`case "${state}":`));
    assert.match(branch, /^case "(?:captcha|otp)":\s*if\(deferredVerification\)\{pause\([^;]+;return;\}/);
  }
  assert.match(method('private void showCode('), /^\s*if\(deferredVerification\)\{pause\([^;]+;return;\}/);
  assert.match(method('private void submitCredentials('), /"captcha"\.equals\(state\)\).*if\(deferredVerification\)pause\([^;]+;else showOfficialVerification/s);
});

test('unknown pages and timeouts pause the task without opening a page', () => {
  const unknown = handle.slice(handle.indexOf('default:'));
  assert.match(unknown, /pause\(/);
  assert.doesNotMatch(unknown, /showOfficialVerification|showCode|openVerification/);
  assert.match(inspect, /SystemClock\.elapsedRealtime\(\)>deadline\).*pause\(/s);
  assert.doesNotMatch(inspect, /showOfficialVerification|showCode|openVerification/);
  assert.match(pause, /if\(deferredVerification\)\{waitingForVerification=true;verificationRequested=false;\}/);
  assert.match(pause, /status\.accept\(visible\);blocked\.accept\(visible\);/);
});

test('silent jobs still terminate instead of opening verification UI', () => {
  assert.match(pause, /if\(!interactive\)\{ended=true;web\.stopLoading\(\);handler\.removeCallbacksAndMessages\(null\);\}/);
  assert.match(open, /if\(!interactive\)\{pause\([^;]+;return;\}/);
  assert.ok(open.indexOf('if(!interactive)') < open.indexOf('verificationRequested=true'));
});

test('opening is protected from duplicate taps and both new and already running inspections', () => {
  const gate = 'deferredVerification&&verificationRequested&&!officialVisible&&dialog==null';
  assert.match(open, /if\(ended\|\|dialog!=null\|\|\(deferredVerification&&verificationRequested\)\)return;/);
  assert.ok(inspect.includes(`if(${gate}){schedule(1000);return;}`));
  assert.ok(inspect.includes(`&&!(${gate}))handle(result)`));
  assert.match(open, /if\(ended\|\|!verificationRequested\)return;/);
});

test('explicit credential submission disables its button before sending', () => {
  const deferredPositive = official.slice(official.indexOf('if(deferredVerification)dialog.getButton'));
  assert.match(deferredPositive, /if\(credentials\)\{v\.setEnabled\(false\);submitCredentials\(page,true\);return;\}/);
  assert.match(official, /setPositiveButton\("完成验证，继续",deferredVerification\?null:/);
});

test('deferred verification keeps the authorized page open when captcha advances to OTP', () => {
  assert.match(handle, /if\(isContinuation\(state\)\|\|\(!deferredVerification&&"otp"\.equals\(state\)&&!codeOnPage\)\)/);
});

test('closing unfinished verification waits again without finishing the main activity', () => {
  assert.match(official, /setNegativeButton\(deferredVerification\?"关闭网页":"取消登录",\(d,w\)->\{if\(!deferredVerification\)activity\.finish\(\);\}\)/);
  assert.match(official, /setOnCancelListener\(d->\{if\(!deferredVerification\)activity\.finish\(\);\}\)/);
  assert.match(official, /verificationContinues=false;verificationRequested=false;/);
  assert.match(official, /if\(deferredVerification&&!continues\)\{codeOnPage=false;pause\(/);
  assert.doesNotMatch(official, /setOnDismissListener[^]*showOfficialVerification/);
});

test('successful continuation dismisses verification without reporting a fresh block', () => {
  assert.match(handle, /verificationContinues=true;verificationRequested=false;waitingForVerification=false;verificationSurface\.accept\(false\);dialog\.dismiss\(\)/);
  assert.match(official, /boolean continues=verificationContinues\|\|ended;/);
  assert.match(handle, /ended=true;waitingForVerification=false;verificationRequested=false;[^]*dialog\.dismiss\(\);\}ready\.run\(\);return;/);
  assert.match(handle, /if\(deferredVerification&&waitingForVerification&&isContinuation\(state\)\)\{waitingForVerification=false;auto=true;/);
});

test('a delayed dismiss detaches the dialog but cannot reattach a disposed WebView', () => {
  assert.match(method('void close('), /^closed=true;ended=true;/);
  const dismiss = official.slice(official.indexOf('dialog.setOnDismissListener'));
  const detach = dismiss.indexOf('holder.removeView(web)');
  const guard = dismiss.indexOf('if(closed||activity.isFinishing()||activity.isDestroyed())return;');
  const attach = dismiss.indexOf('parent.addView(web');
  assert.ok(detach >= 0 && guard > detach && attach > guard);
});
