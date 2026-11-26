/* "Arrange my form": the user's own order of the tabs, and the blocks they hide, fold or pin. Fed by window.LDP_BOOT
   (PersonalizeUserService); the arrangement is posted through EBX's own form (hidden fields + a hidden submit button). */
(function(){
'use strict';
const B = window.LDP_BOOT, T = B.t;
const root = document.getElementById('ldp-app');
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const P = B.prefs || {};
const arr = k => Array.isArray(P[k]) ? P[k].slice() : [];
const hidden = new Set(arr('hidden')), folded = new Set(arr('collapsed')), hiddenTabs = new Set(arr('hiddenTabs'));
let pinned = arr('pinned');
let first = P.defaultTab || '';
/* the tabs in the user's order: listed ones take the places of those same tabs, the others keep theirs */
let tabs = B.tabs.slice();
(function(){
  const order = arr('tabs'); const slots = [], moved = [];
  order.forEach(id => { const i = tabs.findIndex(t => t.id === id); if (i >= 0 && !moved.includes(tabs[i])){ moved.push(tabs[i]); slots.push(i); } });
  slots.sort((a, b) => a - b); const out = tabs.slice(); slots.forEach((s, k) => { out[s] = moved[k]; }); tabs = out;
})();
const known = new Set(); tabs.forEach(t => t.blocks.forEach(b => known.add(b.id)));
pinned = pinned.filter(id => known.has(id));

const style = document.createElement('style');
style.textContent = `
.ldp{font-family:inherit;color:#2E2340;max-width:860px;padding:4px 4px 24px;display:flex;flex-direction:column;gap:12px}
.ldp p{margin:0}.ldp .intro{color:#6B6575;font-size:13px;line-height:1.5}
.ldp .note{font-size:12.5px;color:#8A4B00;background:#FFF4E0;border-radius:8px;padding:8px 10px}
.ldp .ok{font-size:13px;color:#0B7A4B;background:#E7F8EF;border-radius:8px;padding:8px 10px}.ldp .err{font-size:13px;color:#A3001E;background:#FDE8EC;border-radius:8px;padding:8px 10px}
.ldp .tab{border:1px solid #E2DEE6;border-radius:12px;background:#fff}
.ldp .tab.off{background:repeating-linear-gradient(135deg,#F7F5FA 0 8px,#fff 8px 16px)}
.ldp .th{display:flex;align-items:center;gap:10px;padding:9px 12px;border-bottom:1px solid #EFECF3;flex-wrap:wrap}
.ldp .th b{font-size:14px;color:#07073C;flex:1;min-width:160px}
.ldp .mv{display:inline-flex;gap:4px}.ldp button.sm{font:inherit;font-size:12px;border:1px solid #E2DEE6;background:#fff;border-radius:6px;padding:2px 8px;cursor:pointer;color:#2E2340}
.ldp button.sm:disabled{opacity:.35;cursor:default}
.ldp label.o{display:inline-flex;align-items:center;gap:5px;font-size:12.5px;color:#4A4458;cursor:pointer;white-space:nowrap}
.ldp label.o input:disabled+span{color:#B9B3C2}
.ldp .bl{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px 14px;align-items:center;padding:7px 12px;border-top:1px solid #F4F2F7;font-size:13px}
.ldp .bl:first-of-type{border-top:0}.ldp .bl .n{min-width:0;overflow-wrap:anywhere}.ldp .bl .n.off{color:#B9B3C2;text-decoration:line-through}
.ldp .bl .opts{display:flex;gap:12px;flex-wrap:wrap}
.ldp .lock{font-size:11px;font-weight:600;color:#6B6575;background:#F1EFF4;border-radius:999px;padding:1px 7px;margin-left:6px}
.ldp .pinn{font-size:11px;font-weight:600;color:#5B12C7;background:#F1E8FF;border-radius:999px;padding:1px 7px;margin-left:6px}
.ldp .acts{display:flex;gap:8px;flex-wrap:wrap}
.ldp .btn{font:inherit;font-size:13px;border:1px solid #E2DEE6;background:#fff;color:#2E2340;border-radius:8px;padding:6px 14px;cursor:pointer}
.ldp .btn.primary{background:#7F17FF;border-color:#7F17FF;color:#fff;font-weight:600}
.ldp .mine{border:1px dashed #7F17FF;border-radius:12px;padding:9px 12px;font-size:13px;color:#5B12C7;background:#FBF8FF}
@media (max-width:640px){.ldp .bl{grid-template-columns:minmax(0,1fr)}}`;
document.head.appendChild(style);

function render(){
  let h = `<div class="ldp">`;
  if (B.notice) h += `<div class="${B.noticeError ? 'err' : 'ok'}" role="status">${esc(B.notice)}${B.saved && !B.noticeError ? ' ' + esc(T.savedReload) : ''}</div>`;
  h += `<p class="intro">${esc(T.intro)}</p>`;
  if (pinned.length) h += `<div class="mine"><b>${esc(T.mine)}</b> · ${pinned.map(id => esc(nameOf(id))).join(' · ')}</div>`;
  tabs.forEach((t, i) => {
    const off = hiddenTabs.has(t.id);
    h += `<section class="tab${off ? ' off' : ''}" aria-label="${esc(t.label)}"><div class="th"><span class="mv"><button type="button" class="sm" data-up="${i}" ${i === 0 ? 'disabled' : ''} aria-label="${esc(T.up)}">↑</button><button type="button" class="sm" data-down="${i}" ${i === tabs.length - 1 ? 'disabled' : ''} aria-label="${esc(T.down)}">↓</button></span>
      <b>${esc(t.label)}${t.locked ? `<span class="lock">${esc(T.locked)}</span>` : ''}</b>
      <label class="o"><input type="radio" name="ldp-first" data-first="${esc(t.id)}"${first === t.id ? ' checked' : ''}${off ? ' disabled' : ''}><span>${esc(T.first)}</span></label>
      <label class="o"><input type="checkbox" data-htab="${esc(t.id)}"${off ? ' checked' : ''}${t.locked ? ' disabled' : ''}><span>${esc(T.hideTab)}</span></label></div>`;
    if (!t.blocks.length) h += `<div class="bl"><span class="n">${esc(T.noBlock)}</span></div>`;
    t.blocks.forEach(b => {
      const hid = hidden.has(b.id), pin = pinned.includes(b.id);
      h += `<div class="bl"><span class="n${hid || off ? ' off' : ''}">${esc(b.title)}${b.locked ? `<span class="lock">${esc(T.locked)}</span>` : ''}${pin ? `<span class="pinn">${esc(T.mine)}</span>` : ''}</span>
        <span class="opts"><label class="o"><input type="checkbox" data-hide="${esc(b.id)}"${hid ? ' checked' : ''}${b.locked || pin ? ' disabled' : ''}><span>${esc(T.hide)}</span></label>
        <label class="o"><input type="checkbox" data-fold="${esc(b.id)}"${folded.has(b.id) ? ' checked' : ''}${hid ? ' disabled' : ''}><span>${esc(T.fold)}</span></label>
        <label class="o"><input type="checkbox" data-pin="${esc(b.id)}"${pin ? ' checked' : ''}${b.locked || hid ? ' disabled' : ''}><span>${esc(T.pin)}</span></label></span></div>`;
    });
    h += `</section>`;
  });
  h += `<p class="note">${esc(T.roleNote)}</p><div class="acts"><button type="button" class="btn primary" id="ldp-save">${esc(T.save)}</button>${B.hasPrefs ? `<button type="button" class="btn" id="ldp-reset">${esc(T.reset)}</button>` : ''}</div></div>`;
  root.innerHTML = h;
}
function nameOf(id){ for (const t of tabs) for (const b of t.blocks) if (b.id === id) return b.title; return id; }
function prefs(){
  return {tabs: tabs.map(t => t.id), defaultTab: first || undefined, hiddenTabs: [...hiddenTabs], hidden: [...hidden], collapsed: [...folded].filter(id => !hidden.has(id)), pinned: pinned.slice()};
}
function submit(action){
  document.getElementById('ldpAction').value = action;
  document.getElementById('ldpPrefs').value = JSON.stringify(prefs());
  const holder = document.getElementById('ldpSubmit');
  const btn = holder && (/^(BUTTON|INPUT|A)$/.test(holder.tagName) ? holder : holder.querySelector('button,input[type=submit],a'));
  if (btn) btn.click();
}
root.addEventListener('click', e => {
  const t = e.target;
  const up = t.closest('[data-up]'); if (up){ const i = +up.dataset.up; [tabs[i - 1], tabs[i]] = [tabs[i], tabs[i - 1]]; return render(); }
  const dn = t.closest('[data-down]'); if (dn){ const i = +dn.dataset.down; [tabs[i + 1], tabs[i]] = [tabs[i], tabs[i + 1]]; return render(); }
  if (t.closest('#ldp-save')) return submit('save');
  if (t.closest('#ldp-reset')){ if (confirm(T.resetConfirm)) submit('reset'); }
});
root.addEventListener('change', e => {
  const t = e.target;
  if (t.dataset.first){ first = t.dataset.first; return render(); }
  if (t.dataset.htab){ if (t.checked){ hiddenTabs.add(t.dataset.htab); if (first === t.dataset.htab) first = ''; } else hiddenTabs.delete(t.dataset.htab); return render(); }
  if (t.dataset.hide){ if (t.checked) hidden.add(t.dataset.hide); else hidden.delete(t.dataset.hide); return render(); }
  if (t.dataset.fold){ if (t.checked) folded.add(t.dataset.fold); else folded.delete(t.dataset.fold); return render(); }
  if (t.dataset.pin){ pinned = pinned.filter(x => x !== t.dataset.pin); if (t.checked) pinned.push(t.dataset.pin); return render(); }
});
/* Enter in a field would submit EBX's form */
root.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.tagName === 'INPUT') e.preventDefault(); });
render();
/* saved from the form's pop-up: reload the form behind it, which closes the pop-up and shows the new arrangement */
if (B.saved && !B.noticeError) setTimeout(() => {
  try {
    const top = window.top.location;
    if (/[?&]service=[^&]*PersonalizeForm/.test(top.search)){
      // opened from the Actions menu: back to the record, without the service
      const q = top.search.slice(1).split('&').filter(x => !/^(service|serviceMode)=/.test(x)).join('&');
      top.href = top.pathname + (q ? '?' + q : '') + top.hash;
    } else top.reload();
  } catch (x) { /* another origin */ }
}, 900);
})();
