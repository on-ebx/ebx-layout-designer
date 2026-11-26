/* "Record form layouts" page, fed by window.LD_HOME (LayoutsHomeUserService). Opening the designer navigates the top
   window, inside the current perspective when there is one. */
(function(){
'use strict';
const H = window.LD_HOME;
const root = document.getElementById('ld-home');
if (!H || !root) return;
const FR = (H.userLocale || 'en').slice(0,2) === 'fr';
const t = (fr, en) => FR ? fr : en;
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

/* the designer of one table: an ebx-ui route of the current perspective, else a web component */
function designerUrl(ds, dataset, table){
  let key = null;
  try { const m = window.top.location.pathname.match(/\/ebx-ui\/ui\/custom\/([^/]+)\//); if (m) key = m[1]; } catch (_){ /* other origin */ }
  if (key) return `/ebx-ui/ui/custom/${encodeURIComponent(key)}/data/B${encodeURIComponent(ds)}/${encodeURIComponent(dataset)}${table}?service=${encodeURIComponent(H.service)}&serviceMode=TABLE_VIEW`;
  return `/ebx/?branch=${encodeURIComponent(ds)}&instance=${encodeURIComponent(dataset)}&xpath=${encodeURIComponent(table)}&service=${encodeURIComponent(H.service)}`;
}
function go(url){ try { window.top.location.href = url; } catch (_){ window.location.href = url; } }
/* a dataset of the layout's model holding its table: the first one met */
function datasetOf(model, table){
  for (const s of H.dataspaces) for (const d of s.datasets) if (d.model === model && d.tables.some(x => x.path === table)) return {ds:s.key, dataset:d.name, label:`${s.label} › ${d.label}`};
  return null;
}
const tableLabel = (model, table) => { for (const s of H.dataspaces) for (const d of s.datasets) if (d.model === model){ const x = d.tables.find(x => x.path === table); if (x) return x.label; } return table; };

const rows = H.layouts.map((l, i) => { const at = datasetOf(l.model, l.table);
  return `<tr><td><b>${esc(tableLabel(l.model, l.table))}</b><div class="hint mono">${esc(l.table)}</div></td><td class="mono" style="font-size:11.5px;max-width:360px;overflow-wrap:anywhere">${esc(l.model.replace(/^urn:ebx:(module|publication):/, ''))}</td>
    <td>${l.version}</td><td>${l.active ? `<span class="badge done">${t('active','active')}</span>` : `<span class="badge todo">${t('inactive','inactive')}</span>`}</td><td>${esc(l.updatedOn)} ${esc(l.updatedBy || '')}</td>
    <td>${at ? `<button type="button" class="btn small" data-open="${i}" title="${esc(at.label)}">${t('Ouvrir le designer','Open the designer')}</button>` : `<span class="hint">${t('Aucun dataset ouvert sur ce modèle','No open dataset on this model')}</span>`}</td></tr>`; }).join('');

root.innerHTML = `<div class="wrap" style="max-width:1200px">
    ${H.problem ? `<div class="explain" role="status">${esc(H.problem)}</div>` : ''}
  <section class="out"><div class="out-hd"><h2>${t('Mises en page enregistrées','Saved layouts')} · ${H.layouts.length}</h2></div>
    <div style="padding:8px 16px 16px;overflow-x:auto">${H.layouts.length ? `<table class="ld-h-table" style="width:100%;border-collapse:collapse;font-size:13px"><thead><tr><th>${t('Table','Table')}</th><th>${t('Modèle','Model')}</th><th>Version</th><th>${t('État','State')}</th><th>${t('Modifiée','Changed')}</th><th></th></tr></thead><tbody>${rows}</tbody></table>` : `<p class="hint">${t('Aucune mise en page enregistrée pour l’instant.','No saved layout yet.')}</p>`}</div></section>
</div>`;

if (H.go){ go(designerUrl(H.go.dataspace, H.go.dataset, H.go.table)); }
root.addEventListener('click', e => { const b = e.target.closest('[data-open]'); if (!b) return; const l = H.layouts[+b.dataset.open]; const at = datasetOf(l.model, l.table); if (at) go(designerUrl(at.ds, at.dataset, l.table)); });
root.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.tagName === 'INPUT') e.preventDefault(); });
})();
