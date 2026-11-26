/* Explorer page of the Layout Designer (service Explorer). Fed with window.LDX_RUN by ExplorerUserService: the user's
   explorer (their variant, without what their roles or EBX permissions hide) and the AJAX addresses. Every search, filter,
   count and page comes from the server; this script only draws them. Format: docs/EXPLORER-FORMAT.md. */
(function () {
  'use strict';
  var B = window.LDX_RUN, root = document.getElementById('ldx-run');
  if (!B || !root) return;
  var C = B.config || {}, CARD = C.card || {};
  var LOC = B.locale || 'en-US', LANG = LOC.slice(0, 2), DEF = B.defaultLocale || 'en-US';
  if (B.main) root.style.setProperty('--ldx-main', B.main);
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]; }); };
  /* a configured text: the user's locale, then the same language, then the default locale, then any */
  var T = function (o) {
    if (o == null) return '';
    if (typeof o === 'string') return o;
    if (o[LOC]) return o[LOC];
    for (var k in o) if (k.slice(0, 2) === LANG && o[k]) return o[k];
    if (o[DEF]) return o[DEF];
    for (var j in o) if (o[j]) return o[j];
    return '';
  };
  /* [French, English, Italian]; any other language reads English */
  var STR = {
    asOf: ['En vigueur au', 'In force on', 'In vigore al'], today: ['Aujourd’hui', 'Today', 'Oggi'],
    search: ['Rechercher…', 'Search…', 'Cerca…'], records: ['fiches', 'records', 'schede'], record: ['fiche', 'record', 'scheda'], more: ['Afficher plus', 'Show more', 'Mostra altro'],
    none: ['Aucune fiche ne correspond à ces critères.', 'No record matches these criteria.', 'Nessuna scheda corrisponde a questi criteri.'], clear: ['Tout effacer', 'Clear all', 'Cancella tutto'],
    hideF: ['Masquer les filtres', 'Hide filters', 'Nascondi i filtri'], showF: ['Afficher les filtres', 'Show filters', 'Mostra i filtri'], open: ['Ouvrir la fiche', 'Open', 'Apri la scheda'],
    fav: ['Favori', 'Favorite', 'Preferito'], addFav: ['Ajouter aux favoris', 'Add to favorites', 'Aggiungi ai preferiti'], rmFav: ['Retirer des favoris', 'Remove from favorites', 'Rimuovi dai preferiti'],
    myFav: ['Mes favoris', 'My favorites', 'I miei preferiti'], moreV: ['Afficher 10 de plus', 'Show 10 more', 'Mostra altri 10'], less: ['Réduire', 'Show less', 'Riduci'],
    findV: ['Chercher parmi {n} valeurs', 'Search {n} values', 'Cerca tra {n} valori'], findMany: ['Chercher une valeur', 'Search a value', 'Cerca un valore'],
    ofMany: ['{a} les plus fréquentes, d\'autres existent', '{a} most frequent, more exist', '{a} più frequenti, ne esistono altri'], foundMany: ['Plus de {n} trouvées', 'More than {n} found', 'Più di {n} trovati'],
    narrowMany: ['Pour les autres, cherchez.', 'For the others, search.', 'Per gli altri, cerca.'], found: ['{n} trouvée(s)', '{n} found', '{n} trovato/i'], ofTotal: ['{a} sur {b}, les plus fréquentes', '{a} of {b}, most frequent first', '{a} su {b}, i più frequenti'],
    narrow: ['Pour les {n} autres, cherchez.', 'For the other {n}, search.', 'Per gli altri {n}, cerca.'], all: ['Toutes', 'All', 'Tutti'], yes: ['Oui', 'Yes', 'Sì'], no: ['Non', 'No', 'No'],
    copy: ['Copier', 'Copy', 'Copia'], copied: ['Copié', 'Copied', 'Copiato'], compliant: ['Rien à signaler', 'Nothing to report', 'Nulla da segnalare'], error: ['Le serveur n’a pas répondu correctement.', 'The server answer could not be read.', 'Il server non ha risposto correttamente.'],
    sort: ['Trier', 'Sort', 'Ordina'], computed: ['Calculé à {t}', 'Computed at {t}', 'Calcolato alle {t}'], agoMin: ['il y a {n} min', '{n} min ago', '{n} min fa'], now: ['à l’instant', 'just now', 'proprio ora'],
    refresh: ['Actualiser', 'Refresh', 'Aggiorna'], asView: ['Affichage', 'View', 'Visualizzazione'], v_vertical: ['Cartes', 'Cards', 'Schede'], v_horizontal: ['Cartes larges', 'Wide cards', 'Schede larghe'], v_compact: ['Liste', 'List', 'Elenco'],
    asc: ['Croissant', 'Ascending', 'Crescente'], desc: ['Décroissant', 'Descending', 'Decrescente'], create: ['Créer', 'Create', 'Crea'], inGroup: ['{n} fiche(s)', '{n} record(s)', '{n} scheda/e'], noGroup: ['(vide)', '(empty)', '(vuoto)'], changed: ['Des données ont changé depuis.', 'Data changed since.', 'Da allora i dati sono cambiati.'], auto: ['Mis à jour automatiquement', 'Updated automatically', 'Aggiornato automaticamente'], ago: ['il y a {n}', '{n} ago', '{n} fa'], inT: ['dans {n}', 'in {n}', 'tra {n}'], today: ['aujourd’hui', 'today', 'oggi'], d: ['j', 'd', 'g'], mo: ['mois', 'mo', 'mesi'], y: ['an(s)', 'y', 'anno/i']
  };
  var S_ = function (k, args) { var s = STR[k] || [k, k, k], v = s[LANG === 'fr' ? 0 : LANG === 'it' ? 2 : 1] || s[1]; for (var a in args || {}) v = v.split('{' + a + '}').join(args[a]); return v; };
  var nf = function (n, opts) { try { return new Intl.NumberFormat(LOC, opts || {}).format(n); } catch (x) { return String(n); } };

  // --- state, kept per EBX user and explorer for the browser tab ---------------------------------------------------
  var STORE = 'ldx:' + B.user + ':' + B.explorer;
  var S = {q: '', sel: {}, tile: null, sort: 0, dir: '', lay: '', more: {}, fq: {}, collapsed: {}, noSide: false, selLabels: {}, asOf: ''};
  /* 1.25.0: the "in force on" date is shared by every explorer and form block of this user in the tab */
  var ASOF_KEY = 'ldfAsOf:' + B.user, isDay = function (s) { return /^\d{4}-\d{2}-\d{2}$/.test(s || ''); };
  var today = function () { var x = new Date(); return x.getFullYear() + '-' + ('0' + (x.getMonth() + 1)).slice(-2) + '-' + ('0' + x.getDate()).slice(-2); };
  try {
    for (var i = sessionStorage.length - 1; i >= 0; i--) { var k = sessionStorage.key(i); if (k && k.indexOf('ldx:') === 0 && k.indexOf('ldx:' + B.user + ':') !== 0) sessionStorage.removeItem(k); }
    var saved = JSON.parse(sessionStorage.getItem(STORE) || 'null');
    if (saved) { S.q = saved.q || ''; S.sel = saved.sel || {}; S.tile = saved.tile || null; S.sort = saved.sort || 0; S.dir = saved.dir || ''; S.collapsed = saved.collapsed || {}; S.selLabels = saved.selLabels || {}; }
    var savedLay = localStorage.getItem(STORE + ':lay'); if (savedLay) S.lay = savedLay;
    var sharedDay = sessionStorage.getItem(ASOF_KEY); if (C.asOf && isDay(sharedDay)) S.asOf = sharedDay;
  } catch (x) { /* no storage */ }
  (C.facets || []).forEach(function (f) { if (!(f.id in S.collapsed) && f.open === false) S.collapsed[f.id] = true; });
  var save = function () { try { if (C.asOf) { if (isDay(S.asOf)) sessionStorage.setItem(ASOF_KEY, S.asOf); else sessionStorage.removeItem(ASOF_KEY); } sessionStorage.setItem(STORE, JSON.stringify({q: S.q, sel: S.sel, tile: S.tile, sort: S.sort, dir: S.dir, collapsed: S.collapsed, selLabels: S.selLabels})); if (S.lay) localStorage.setItem(STORE + ':lay', S.lay); } catch (x) {} };
  /* the layouts the user may switch between (the configurator's list), and the one shown */
  var LAYS = (CARD.layouts || []).filter(function (l) { return l === 'vertical' || l === 'horizontal' || l === 'compact'; });
  var lay = function () { return S.lay && LAYS.indexOf(S.lay) >= 0 ? S.lay : CARD.layout || 'vertical'; };
  var sortDef = function () { return (C.sorts || []).filter(function (x) { return x.i === +S.sort; })[0] || (C.sorts || [])[0]; };
  var isDesc = function () { var d = sortDef(); return S.dir ? S.dir === 'desc' : !!(d && d.desc); };
  var on = function (m) { return Object.keys(m || {}).filter(function (k) { return m[k]; }); };

  // --- server calls -------------------------------------------------------------------------------------------------
  var escV = function (v) { return String(v).replace(/%/g, '%25').replace(/;/g, '%3B').replace(/\|/g, '%7C').replace(/=/g, '%3D').replace(/:/g, '%3A'); };
  var url = function (base, params) {
    var u = base;
    for (var k in params) if (params[k] !== '' && params[k] != null) u += (u.indexOf('?') < 0 ? '?' : '&') + encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    return u;
  };
  /* each request carries its number and the page's id: the server drops the work of a request a newer one replaced */
  var seq = 0, TAB = Math.random().toString(36).slice(2, 10);
  /* EBX answers a page's requests one after the other: while one runs, only the latest new one waits (the others are
     dropped), so typing never queues work nobody will read */
  var busy = false, pending = null;
  var ask = function (u, done) {
    var mine = ++seq;
    if (busy) { pending = {u: u, done: done, mine: mine}; return; }
    send(u, done, mine);
  };
  var send = function (u, done, mine) {
    busy = true;
    var h = new EBX_AJAXResponseHandler();
    var next = function () { busy = false; if (pending) { var p = pending; pending = null; send(p.u, p.done, p.mine); } };
    h.handleAjaxResponseSuccess = function (text) {
      next();
      if (mine !== seq) return;
      var d; try { d = JSON.parse(text); } catch (x) { d = null; }
      if (d && d.cancelled) return;
      if (!d || d.error) { R = R || {}; R.error = d && d.error ? d.error : S_('error'); draw(); return; }
      done(d);
    };
    h.handleAjaxResponseFailed = function () { next(); };
    h.sendRequest(u + (u.indexOf('?') < 0 ? '?' : '&') + 'gen=' + mine + '&tab=' + TAB);
  };
  /* collapsed facets are not counted: they are asked when opened */
  var closed = function () { return (C.facets || []).filter(function (f) { return S.collapsed[f.id]; }).map(function (f) { return f.id; }).join(';'); };
  var params = function (off, lim, part, force) {
    return {
      part: part || 'all', closed: closed(), refresh: force ? 1 : '', dir: S.dir,
      q: S.q, tile: S.tile || '', sort: S.sort, off: off, lim: lim, asOf: C.asOf && isDay(S.asOf) ? S.asOf : '',
      f: Object.keys(S.sel).map(function (id) { var v = on(S.sel[id]); return v.length ? id + '=' + v.map(escV).join('|') : null; }).filter(Boolean).join(';'),
      more: Object.keys(S.more).map(function (id) { return id + ':' + S.more[id]; }).join(';'),
      fq: Object.keys(S.fq).filter(function (id) { return S.fq[id]; }).map(function (id) { return id + ':' + escV(S.fq[id]); }).join(';')
    };
  };
  var R = null, CARDS = [], GROUPS = {};
  var PAGE = C.pageSize || 48;
  /* two steps: the count, the records and the tiles first, then the facets (the slow part on a big table) */
  var facetsOnly = function (keepFocus) {
    if (!(C.facets || []).length) return;
    ask(url(B.ajax.query, params(0, 1, 'facets')), function (d) {
      R = R || {}; R.facets = d.facets || {}; R.waiting = false; stamp(d); remember(d); draw(keepFocus);
    });
  };
  var askMain = function (off, lim, keepFocus, force, add) {
    ask(url(B.ajax.query, params(off, lim, 'main', force)), function (d) {
      var old = R && R.facets;
      R = d; R.facets = old || {}; R.waiting = !add && !!(C.facets || []).length;
      CARDS = add ? CARDS.concat(d.cards || []) : d.cards || [];
      if (!add) GROUPS = {};
      (d.groups || []).forEach(function (g) { GROUPS[g.v] = g; });
      if (R.computedAt) R.at = R.computedAt;
      draw(keepFocus);
      if (!add) facetsOnly(keepFocus);
    });
  };
  var stamp = function (d) { if (d.computedAt && R && (!R.at || d.computedAt < R.at)) R.at = d.computedAt; if (d.changed && R) R.changed = true; };
  var refresh = function (keepFocus) { askMain(0, PAGE, keepFocus); };
  var more = function () { askMain(CARDS.length, PAGE, false, false, true); };
  var reload = function (force) { askMain(0, Math.max(PAGE, CARDS.length), false, force); };
  /* the labels of selected values, for the chips (a long list does not send them all) */
  var remember = function (d) {
    for (var id in d.facets || {}) (d.facets[id].items || []).forEach(function (it) { if (it.sel) (S.selLabels[id] = S.selLabels[id] || {})[it.v] = it.l; });
  };

  // --- values with their widgets ------------------------------------------------------------------------------------
  var TONES = {ok: 1, warn: 1, bad: 1, info: 1, accent: 1, muted: 1};
  var tone = function (t) { return TONES[t] ? t : 'muted'; };
  var thr = function (x, o) { var g = o.green != null ? +o.green : 80, a = o.amber != null ? +o.amber : 50; return x >= g ? 'ok' : x >= a ? 'warn' : 'bad'; };
  var flagOf = function (cc) { if (!/^[A-Z]{2}$/.test(cc || '')) return ''; return String.fromCodePoint(0x1F1E6 + cc.charCodeAt(0) - 65, 0x1F1E6 + cc.charCodeAt(1) - 65); };
  var rel = function (iso) {
    var d = new Date(iso + 'T12:00:00'), now = new Date(), days = Math.round((d - now) / 86400000), a = Math.abs(days);
    var n = a < 31 ? a + ' ' + S_('d') : a < 365 ? Math.round(a / 30) + ' ' + S_('mo') : nf(Math.round(a / 36.5) / 10) + ' ' + S_('y');
    return {txt: days === 0 ? S_('today') : days < 0 ? S_('ago', {n: n}) : S_('inT', {n: n}), days: days};
  };
  var fmtDate = function (iso) { try { return new Date(iso + 'T12:00:00').toLocaleDateString(LOC, {day: 'numeric', month: 'short', year: 'numeric'}); } catch (x) { return iso; } };
  var money = function (n, o) {
    var unit = o.unit || '€';
    if (o.compact) { var a = Math.abs(n); return (a >= 1e6 ? nf(n / 1e6, {maximumFractionDigits: 1}) + ' M' : a >= 1e3 ? nf(Math.round(n / 1e3)) + ' k' : nf(n) + ' ') + unit; }
    return nf(n) + ' ' + unit;
  };
  var ring = function (x, max, o) {
    var pc = Math.max(0, Math.min(1, x / (max || 100))), C2 = 2 * Math.PI * 13, t = thr(pc * 100, o);
    return '<span class="ldx-ring"><svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true"><circle cx="17" cy="17" r="13" fill="none" stroke="var(--ldx-soft)" stroke-width="4.5"/>'
      + '<circle cx="17" cy="17" r="13" fill="none" stroke="var(--ldx-' + t + ')" stroke-width="4.5" stroke-linecap="round" stroke-dasharray="' + (C2 * pc).toFixed(1) + ' ' + C2.toFixed(1) + '" transform="rotate(-90 17 17)"/>'
      + '<text x="17" y="21" text-anchor="middle" font-size="9.5" font-weight="700" fill="currentColor">' + esc(nf(x)) + '</text></svg></span>';
  };
  /* one value with a widget; '' when there is nothing to show */
  var widget = function (val, w, o, src) {
    o = o || {};
    if (!val) return '';
    var k = val.k, l = val.l != null ? val.l : val.k, n = val.n;
    if (val.ls) l = val.ls.join(', ');
    var pre = esc(T(o.prefix)), suf = esc(T(o.suffix));
    switch (w) {
      case 'bold': return '<b>' + pre + esc(l) + suf + '</b>';
      case 'mono': return '<span class="ldx-mono">' + pre + esc(l) + suf + '</span>';
      case 'copy': return pre + esc(l) + suf + '<button type="button" class="ldx-copy" data-copy="' + esc(l) + '" aria-label="' + esc(S_('copy')) + '">⧉</button>';
      case 'tag': return (val.ls || [l]).map(function (x) { return '<span class="ldx-tag">' + esc(x) + '</span>'; }).join(' ');
      case 'link':
        if (val.ks && val.ls) return val.ls.map(function (x, i) { return B.pk === 1 ? '<a class="ldx-link" data-open="' + esc(JSON.stringify([val.ks[i]])) + '">' + esc(x) + '</a>' : esc(x); }).join(', ');
        return val.pk && B.links && B.links[src] ? '<a class="ldx-link" data-link="' + esc(src) + '" data-pk="' + esc(JSON.stringify(val.pk)) + '">' + esc(l) + '</a>' : esc(l);
      case 'pill': case 'dot': {
        var t = tone((o.colors || {})[k] || o['default'] || 'muted');
        return w === 'pill' ? '<span class="ldx-pill t-' + t + '">' + esc(l) + '</span>' : '<span class="ldx-dot" style="--tc:var(--ldx-' + t + ')">' + esc(l) + '</span>';
      }
      case 'country': { var sh = o.show || 'both'; return (sh !== 'code' ? flagOf(k) + ' ' : '') + (sh === 'flag' ? '' : esc(sh === 'code' ? k : l)); }
      case 'num': return n != null ? esc(nf(n)) : esc(l);
      case 'money': return n != null ? '<span class="ldx-money">' + esc(money(n, o)) + '</span>' : esc(l);
      case 'bar': { var mx = +o.max || 100; return '<span class="ldx-mini"><i><b style="width:' + Math.max(0, Math.min(100, 100 * (n || 0) / mx)) + '%"></b></i>' + esc(nf(n || 0)) + '</span>'; }
      case 'ring': return ring(n || 0, +o.max || 100, o);
      case 'level': return '<span class="ldx-pill t-' + thr(n || 0, o) + '">' + esc(nf(n || 0)) + '</span>';
      case 'stars': { var sm = +o.max || 5, sn = Math.max(0, Math.min(sm, Math.round((n || 0) * sm / (+o.scale || 100)))); return '<span class="ldx-stars">' + '★'.repeat(sn) + '<span>' + '★'.repeat(sm - sn) + '</span></span>'; }
      case 'alert': return (n || 0) > 0 ? '<span class="ldx-pill t-warn">' + esc(nf(n)) + '</span>' : '<span class="ldx-sub">0</span>';
      case 'date': return esc(val.d ? fmtDate(val.d) : l);
      case 'relative': {
        if (!val.d) return esc(l);
        var r = rel(val.d), wd = +o.warnDays || 0, rt = r.days < 0 && o.pastBad ? 'bad' : wd && r.days >= 0 && r.days <= wd ? 'warn' : '';
        return rt ? '<span class="ldx-pill t-' + rt + '" title="' + esc(fmtDate(val.d)) + '">' + esc(r.txt) + '</span>' : '<span title="' + esc(fmtDate(val.d)) + '">' + esc(r.txt) + '</span>';
      }
      default: return pre + esc(l) + suf;
    }
  };
  var src = function (p) { return p ? (p.derived ? 'derived:' + p.derived : p.field || null) : null; };
  var labelOf = function (p) { var s = src(p); return p && p.label ? T(p.label) : (B.labels && B.labels[s]) || ''; };
  var flagDefs = {};
  (C.facets || []).forEach(function (f) { if (f.kind === 'flags') (f.flags || []).forEach(function (fl) { flagDefs[f.id + '|' + fl.id] = fl; }); });
  var part = function (p, c) {
    if (p.flags) {
      var raised = (c.flags || {})[p.flags] || [];
      if (!raised.length) return p.options && p.options.none ? '<span class="ldx-pill t-ok">' + esc(T(p.options.none)) + '</span>' : '';
      return raised.map(function (id) { var fl = flagDefs[p.flags + '|' + id] || {}; return '<span class="ldx-pill t-' + tone(fl.tone || 'warn') + '">' + esc(T(fl.label) || id) + '</span>'; }).join(' ');
    }
    var s = src(p);
    return widget((c.v || {})[s], p.widget || 'text', p.options || {}, s);
  };
  var initials = function (s) { return String(s || '').split(/\s+/).filter(Boolean).map(function (w) { return w[0]; }).join('').slice(0, 2).toUpperCase(); };
  var image = function (c, where) {
    var im = CARD.image; if (!im) return '';
    var shape = im.shape || 'logo', at = shape === 'cover' ? 'top' : shape === 'side' ? 'side' : 'inline';
    if (at !== where) return '';
    var cls = 'ldx-img-' + (shape === 'cover' ? 'cover' : shape === 'side' ? 'side' : shape === 'avatar' ? 'avatar' : 'logo') + (im.fit === 'contain' ? ' contain' : '');
    var v = (c.v || {})[src(im)];
    if (!v || !v.url) return im.whenMissing === 'hide' ? '' : '<span class="' + cls + ' ldx-img-ph" aria-hidden="true">' + esc(initials(c.l)) + '</span>';
    return '<img class="' + cls + '" src="' + esc(v.url) + '" alt="" loading="lazy" title="' + esc(v.name || '') + '">';
  };
  var card = function (c) {
    var pill = CARD.pill, pv = pill ? (c.v || {})[src(pill)] : null;
    var pTone = pv ? tone((pill.colors || {})[pv.k] || pill['default'] || 'muted') : null;
    var over = (CARD.overline || []).map(function (p) { return part(p, c); }).filter(Boolean).map(function (h) { return '<span>' + h + '</span>'; }).join('');
    var h = '';
    if (over || pv) h += '<div class="ldx-top"><span class="ov">' + over + '</span>' + (pv ? '<span class="ldx-pill t-' + pTone + '">' + esc(pv.l) + '</span>' : '') + '</div>';
    var titleSrc = CARD.title ? (typeof CARD.title === 'string' ? CARD.title : src(CARD.title)) : null;
    var titleV = titleSrc ? (c.v || {})[titleSrc] : null;
    var title = '<h3><a href="#" data-open="' + esc(JSON.stringify(c.pk)) + '">' + esc(titleV ? titleV.l : c.l) + '</a></h3>';
    var sub = (CARD.subtitle || []).map(function (p) { return part(p, c); }).filter(Boolean).map(function (x) { return '<span>' + x + '</span>'; }).join('');
    var tb = title + (sub ? '<div class="ldx-sub">' + sub + '</div>' : '');
    var inl = image(c, 'inline');
    h += inl ? '<div class="ldx-title">' + inl + '<div>' + tb + '</div></div>' : tb;
    (CARD.lines || []).forEach(function (p) {
      var x = part(p, c); if (!x) return;
      var lb = p.options && p.options.nolabel ? '' : labelOf(p);
      h += '<div class="ldx-line">' + (lb ? '<span class="lb">' + esc(lb) + ' : </span>' : '') + x + '</div>';
    });
    var tags = (CARD.tags || []).map(function (p) { return part(p, c); }).filter(Boolean).join(' ');
    if (tags) h += '<div class="ldx-row">' + tags + '</div>';
    var kpis = (CARD.kpis || []).map(function (p) { var x = part(p, c); return x ? '<span class="fig"><b>' + x + '</b>' + esc(labelOf(p)) + '</span>' : ''; }).filter(Boolean).join('');
    if (kpis) h += '<div class="ldx-kpis">' + kpis + '</div>';
    var g = CARD.gauge, gv = g ? (c.v || {})[src(g)] : null;
    if (gv && gv.n != null) {
      var mx = +g.max || 100, gl = labelOf(g);
      h += g.widget === 'ring' ? '<div class="ldx-gauge">' + ring(gv.n, mx, g) + esc(gl) + '</div>'
        : '<div class="ldx-gauge"><i><b style="width:' + Math.max(0, Math.min(100, gv.n * 100 / mx)) + '%;background:var(--ldx-' + thr(gv.n * 100 / mx, g) + ')"></b></i>' + esc(gl) + ' ' + esc(nf(gv.n)) + '/' + esc(nf(mx)) + '</div>';
    }
    var acts = (CARD.actions || []).map(function (a, i) {
      if ((c.off || []).indexOf(i) >= 0) return '';
      var lb = T(a.label);
      if (a.action === 'open') return '<button type="button" class="ldx-btn ' + (a.style || 'primary') + '" data-open="' + esc(JSON.stringify(c.pk)) + '">' + esc(lb || S_('open')) + '</button>';
      if (a.action === 'favorite') return B.ajax.fav ? '<button type="button" class="ldx-btn star' + (c.fav ? ' on' : '') + '" data-star="' + esc(c.k) + '" aria-pressed="' + !!c.fav + '" title="' + esc(c.fav ? S_('rmFav') : S_('addFav')) + '">' + (c.fav ? '★' : '☆') + (lb ? ' ' + esc(lb) : '') + '</button>' : '';
      if (!B.actions || !B.actions[i]) return '';
      /* a linked-records button may show its count, and is left out when there is none */
      var cnt = a.count ? ((c.v || {})['derived:' + a.count] || {}).n : null;
      if (a.count && cnt != null && +cnt === 0 && a.hideWhenNone !== false) return '';
      return '<button type="button" class="ldx-btn ' + (a.style || 'ghost') + '" data-act="' + i + '" data-pk="' + esc(JSON.stringify(c.pk)) + '" data-k="' + esc(c.k) + '">' + esc(lb) + (cnt != null ? ' <span class="n">' + nf(cnt) + '</span>' : '') + '</button>';
    }).filter(Boolean).join('');
    if (acts) h += '<div class="ldx-actions">' + acts + '</div>';
    var ly = lay(), edge = CARD.edge === 'pill' && pTone;
    return '<article class="ldx-card ' + esc(ly) + (edge ? ' edge' : '') + (c.fav ? ' fav' : '') + '"' + (edge ? ' style="--edge:var(--ldx-' + pTone + ')"' : '') + '>'
      + image(c, 'top') + image(c, 'side') + '<div class="cin">' + h + '</div></article>';
  };

  // --- the page ------------------------------------------------------------------------------------------------------
  var facetBox = function (f) {
    var page = R && R.facets ? R.facets[f.id] : null;
    if (!page) return '';
    if (page.lazy && !S.collapsed[f.id]) page = null;
    if (!page || page.lazy) {
      var t0 = f.kind === 'favorites' ? (T(f.title) || S_('myFav')) : T(f.title);
      return '<div class="ldx-facet' + (S.collapsed[f.id] ? ' collapsed' : '') + '"><h4 data-fold="' + esc(f.id) + '"><span>' + esc(t0) + '</span><span aria-hidden="true">' + (S.collapsed[f.id] ? '▸' : '▾') + '</span></h4><div class="ldx-fb"><p class="ldx-fnote">…</p></div></div>';
    }
    var sel = S.sel[f.id] || {}, items = page.items || [], total = page.total || 0, many = !!page.more, h = '';
    var title = f.kind === 'favorites' ? (T(f.title) || S_('myFav')) : T(f.title);
    var long = f.kind !== 'flags' && f.kind !== 'ranges' && f.kind !== 'favorites' && (total > (f.top || 10) || S.fq[f.id]);
    if (long) h += '<input class="ldx-fsearch" type="search" id="ldx-fq-' + esc(f.id) + '" data-fq="' + esc(f.id) + '" value="' + esc(S.fq[f.id] || '') + '" placeholder="' + esc(many ? S_('findMany') : S_('findV', {n: nf(total)})) + '">';
    if (f.kind === 'favorites') {
      var it = items[0] || {n: 0};
      h += '<label class="ldx-opt"><input type="checkbox" data-f="' + esc(f.id) + '" data-v="1"' + (sel['1'] ? ' checked' : '') + '><span class="l">' + esc(S_('myFav')) + '</span><span class="n">' + nf(it.n) + '</span></label>';
    } else if (f.widget === 'select' && !long) {
      var cur = on(sel)[0] || '';
      h += '<select class="ldx-fselect" data-sf="' + esc(f.id) + '" aria-label="' + esc(title) + '"><option value="">' + esc(S_('all')) + '</option>'
        + items.map(function (x) { return '<option value="' + esc(x.v) + '"' + (x.v === cur ? ' selected' : '') + '>' + esc(x.l) + ' (' + nf(x.n) + ')</option>'; }).join('') + '</select>';
    } else if (f.widget === 'chips') {
      h += '<div class="ldx-chipsf">' + items.map(function (x) { return '<button type="button" class="ldx-fchip" data-chip="' + esc(f.id) + '" data-v="' + esc(x.v) + '" aria-pressed="' + !!sel[x.v] + '">' + esc(x.l) + '<span class="n">' + nf(x.n) + '</span></button>'; }).join('') + '</div>';
    } else {
      h += items.map(function (x) {
        return '<label class="ldx-opt' + (x.n ? '' : ' zero') + (x.sel ? ' sel' : '') + '"' + (x.d ? ' style="padding-left:' + (x.d * 14) + 'px"' : '') + '><input type="checkbox" data-f="' + esc(f.id) + '" data-v="' + esc(x.v) + '"' + (sel[x.v] ? ' checked' : '') + '><span class="l">' + esc(x.l) + '</span><span class="n">' + nf(x.n) + '</span></label>';
      }).join('');
    }
    if (long) {
      var shown = S.more[f.id] || f.top || 10, notSel = items.filter(function (x) { return !x.sel; }).length;
      /* a long list under a filter comes without its exact total ("more"): EBX would take seconds to count it */
      h += '<div class="ldx-fnote">' + esc(S.fq[f.id] ? (many ? S_('foundMany', {n: nf(total - 1)}) : S_('found', {n: nf(total)}))
        : many ? S_('ofMany', {a: nf(notSel)}) : S_('ofTotal', {a: nf(Math.min(notSel, total)), b: nf(total)})) + '</div>';
      if (total > shown && shown < 50) h += '<button type="button" class="ldx-fmore" data-fmore="' + esc(f.id) + '">' + esc(S_('moreV')) + '</button>';
      else if (total > shown) h += '<div class="ldx-fnote">' + esc(many ? S_('narrowMany') : S_('narrow', {n: nf(total - shown)})) + '</div>';
      if (S.more[f.id] && S.more[f.id] > (f.top || 10)) h += ' <button type="button" class="ldx-fmore" data-fless="' + esc(f.id) + '">' + esc(S_('less')) + '</button>';
    }
    return '<div class="ldx-facet' + (S.collapsed[f.id] ? ' collapsed' : '') + '"><h4 data-fold="' + esc(f.id) + '"><span>' + esc(title) + '</span><span aria-hidden="true">' + (S.collapsed[f.id] ? '▸' : '▾') + '</span></h4><div class="ldx-fb">' + h + '</div></div>';
  };
  var chips = function () {
    var c = [];
    if (S.q) c.push(['q', '', S.q]);
    if (S.tile) { var t = (C.tiles || []).filter(function (x) { return x.id === S.tile; })[0]; if (t) c.push(['tile', '', T(t.label)]); }
    (C.facets || []).forEach(function (f) { on(S.sel[f.id]).forEach(function (v) { c.push([f.id, v, (f.kind === 'favorites' ? S_('myFav') : ((S.selLabels[f.id] || {})[v] || v))]); }); });
    if (!c.length) return '';
    return '<div class="ldx-chips">' + c.map(function (x) { return '<span class="ldx-chip">' + esc(x[2]) + '<button type="button" data-rm="' + esc(x[0]) + '" data-v="' + esc(x[1]) + '" aria-label="✕">×</button></span>'; }).join('')
      + '<button type="button" class="ldx-clear" data-clear="1">' + esc(S_('clear')) + '</button></div>';
  };
  /* when the answers shown were computed, and the button to compute them again */
  var age = function () {
    var m = Math.floor((Date.now() - R.at) / 60000);
    return m < 1 ? S_('now') : S_('agoMin', {n: m});
  };
  var fresh = function () {
    if (!R || !R.at) return '';
    var t; try { t = new Date(R.at).toLocaleTimeString(LOC, {hour: '2-digit', minute: '2-digit'}); } catch (x) { t = ''; }
    return '<div class="ldx-fresh' + (R.changed ? ' changed' : '') + '">'
      + (R.mode === 'auto' ? '<span>' + esc(S_('auto')) + '</span>' : '<span>' + esc(S_('computed', {t: t})) + ' (<span class="ldx-age">' + esc(age()) + '</span>)</span>')
      + (R.changed ? '<span class="ldx-changed">' + esc(S_('changed')) + '</span>' : '')
      + (R.mode === 'auto' ? '' : '<button type="button" class="ldx-refresh" data-refresh="1">&#8635; ' + esc(S_('refresh')) + '</button>') + '</div>';
  };
  setInterval(function () { var a = root.querySelector('.ldx-age'); if (a && R && R.at) a.textContent = age(); }, 30000);
  var draw = function (keepFocus) {
    var active = document.activeElement, fid = active && active.id, pos = active && active.selectionStart;
    var tiles = (C.tiles || []).length ? '<div class="ldx-tiles">' + C.tiles.map(function (t) {
      var n = R && R.tiles && t.id in R.tiles ? nf(R.tiles[t.id]) : '…';
      return '<button type="button" class="ldx-tile ' + esc(t.tone || '') + (S.tile === t.id ? ' on' : '') + '" data-tile="' + esc(t.id) + '"><b>' + n + '</b><span>' + esc(T(t.label)) + '</span></button>';
    }).join('') + '</div>' : '';
    var side = '';
    if (C.search && C.search.on) side += '<label class="ldx-search">&#128269;<input id="ldx-q" type="search" value="' + esc(S.q) + '" placeholder="' + esc(T(C.search.placeholder) || S_('search')) + '" aria-label="' + esc(S_('search')) + '"></label>';
    side += (C.facets || []).map(facetBox).join('');
    var total = R ? R.total || 0 : 0, all = R ? R.all || 0 : 0;
    var main = '<div class="ldx-bar">' + (side ? '<button type="button" class="ldx-toggle" data-side="1">' + esc(S.noSide ? S_('showF') : S_('hideF')) + '</button>' : '')
      + '<span class="ldx-count">' + nf(total) + ' ' + esc(total > 1 ? S_('records') : S_('record')) + (R ? ' <small>/ ' + nf(all) + '</small>' : '') + '</span>'
      + (C.asOf ? '<label class="ldx-asof">&#128197; ' + esc(T(C.asOf.label) || S_('asOf')) + ' <input type="date" id="ldx-asof" value="' + esc(isDay(S.asOf) ? S.asOf : today()) + '" aria-label="' + esc(T(C.asOf.label) || S_('asOf')) + '">' + (isDay(S.asOf) && S.asOf !== today() ? '<button type="button" data-asof-today="1">' + esc(S_('today')) + '</button>' : '') + '</label>' : '')
      + ((C.sorts || []).length > 1 ? '<select id="ldx-sort" aria-label="' + esc(S_('sort')) + '">' + C.sorts.map(function (s) { return '<option value="' + s.i + '"' + (+S.sort === s.i ? ' selected' : '') + '>' + esc(T(s.label)) + '</option>'; }).join('') + '</select>' : '')
      + ((C.sorts || []).length ? '<button type="button" class="ldx-dir" data-dir="1" title="' + esc(isDesc() ? S_('desc') : S_('asc')) + '" aria-label="' + esc(isDesc() ? S_('desc') : S_('asc')) + '">' + (isDesc() ? '↓' : '↑') + '</button>' : '')
      + (LAYS.length > 1 ? '<span class="ldx-views" role="group" aria-label="' + esc(S_('asView')) + '">' + LAYS.map(function (l) { return '<button type="button" data-lay="' + l + '" aria-pressed="' + (lay() === l) + '" title="' + esc(S_('v_' + l)) + '" aria-label="' + esc(S_('v_' + l)) + '">' + (l === 'compact' ? '☰' : l === 'horizontal' ? '▤' : '▦') + '</button>'; }).join('') + '</span>' : '')
      + (B.header && B.header.create ? '<button type="button" class="ldx-btn primary ldx-create" data-create="1">+ ' + esc(T(B.header.create.label) || S_('create')) + '</button>' : '')
      + '</div>'
      + (B.header && (B.header.links || []).length ? '<div class="ldx-links">' + B.header.links.map(function (l, i) { return '<a href="#" data-hlink="' + i + '">' + esc(T(l.label)) + '</a>'; }).join('') + '</div>' : '')
      + chips();
    main += fresh();
    if (R && R.error) main += '<div class="ldx-error">' + esc(R.error) + '</div>';
    var ly = lay(), minW = +CARD.minWidth || 260, prevG = null;
    /* in groups: a heading before the first record of each group, with the group's count */
    var withGroups = function (c) {
      var h = '';
      if (C.group && c.g != null && c.g !== prevG) {
        prevG = c.g; var g = GROUPS[c.g] || {l: c.g, n: 0};
        h = '<h3 class="ldx-group">' + esc(g.l || T(C.group.emptyLabel) || S_('noGroup')) + ' <small>' + esc(S_('inGroup', {n: nf(g.n)})) + '</small></h3>';
      }
      return h + card(c);
    };
    main += CARDS.length ? '<div class="ldx-grid' + (ly === 'compact' ? ' compact' : '') + '" style="' + (ly === 'compact' ? '' : 'grid-template-columns:repeat(auto-fill,minmax(min(100%,' + minW + 'px),1fr))') + '">' + CARDS.map(withGroups).join('') + '</div>'
      + (total > CARDS.length ? '<button type="button" class="ldx-more">' + esc(S_('more')) + ' (' + nf(total - CARDS.length) + ')</button>' : '')
      : R ? '<div class="ldx-empty">' + esc(S_('none')) + '</div>' : '<p class="ldx-loading">…</p>';
    var oldSide = root.querySelector('.ldx-side'), scroll = oldSide ? oldSide.scrollTop : 0;
    root.innerHTML = tiles + '<div class="ldx-body' + (S.noSide || !side ? ' noside' : '') + '">' + (side ? '<aside class="ldx-side' + (R && R.waiting ? ' waiting' : '') + '">' + side + '</aside>' : '') + '<section class="ldx-main">' + main + '</section></div>';
    var ns = root.querySelector('.ldx-side'); if (ns) ns.scrollTop = scroll;
    if (keepFocus && fid) { var el = document.getElementById(fid); if (el) { el.focus(); try { el.setSelectionRange(pos, pos); } catch (x) {} } }
    save();
  };

  // --- record pop-ups ------------------------------------------------------------------------------------------------
  var enc = function (v) { return encodeURIComponent(String(v == null ? '' : v).replace(/'/g, "''")).replace(/'/g, '%27'); };
  var fill = function (cmd, pk, k) { return cmd.replace(/LDXMARK(\d+)/g, function (m, i) { return enc(pk[+i]); }).replace(/LDXKEY/g, function () { return enc(k); }); };
  var run = function (cmd, pk, k) {
    if (!cmd) return;
    try { new Function(fill(cmd, pk || [], k))(); watchPopup(); } catch (x) { if (window.console) console.warn('[explorer] pop-up not opened', x); }
  };
  /* EBX's record pop-up is an iframe whose address carries closeUniqueModal: once it is gone, the cards are read again */
  var watchPopup = function () {
    var seen = false, n = 0;
    var open = function () {
      var found = false;
      var look = function (w) {
        var d; try { d = w.document; } catch (x) { return; }
        var f = d.getElementsByTagName('iframe');
        for (var i = 0; i < f.length && !found; i++) if ((f[i].getAttribute('src') || '').indexOf('closeUniqueModal') >= 0) found = true;
        for (var k = 0; k < w.frames.length && !found; k++) look(w.frames[k]);
      };
      try { look(window.top); } catch (x) {}
      return found;
    };
    var t = setInterval(function () { n++; if (open()) { seen = true; return; } if (seen) { clearInterval(t); reload(); } else if (n > 20) clearInterval(t); }, 700);
  };
  /* a header link: another table of the dataset (in the current perspective when there is one) or an address */
  var goLink = function (l) {
    if (!l) return;
    if (l.url) { if (l.newTab) window.open(l.url, '_blank', 'noopener'); else { try { window.top.location.href = l.url; } catch (x) { window.location.href = l.url; } } return; }
    var key = null;
    try { var m = window.top.location.pathname.match(/\/ebx-ui\/ui\/custom\/([^/]+)\//); if (m) key = m[1]; } catch (x) { /* other origin */ }
    var u;
    if (key) {
      u = '/ebx-ui/ui/custom/' + encodeURIComponent(key) + '/data/B' + encodeURIComponent(l.ds) + '/' + encodeURIComponent(l.dataset) + l.table;
      var q = [];
      if (l.predicate) q.push('predicate=' + encodeURIComponent(l.predicate));
      if (l.service) q.push('service=' + encodeURIComponent(l.service), 'serviceMode=TABLE_VIEW');
      if (q.length) u += '?' + q.join('&');
    } else {
      u = '/ebx/?branch=' + encodeURIComponent(l.ds) + '&instance=' + encodeURIComponent(l.dataset) + '&xpath=' + encodeURIComponent(l.table + (l.predicate ? '[' + l.predicate + ']' : ''))
        + (l.service ? '&service=' + encodeURIComponent(l.service) : '');
    }
    try { window.top.location.href = u; } catch (x) { window.location.href = u; }
  };
  var toast = function (msg) {
    var t = document.createElement('div'); t.className = 'ldx-toast'; t.textContent = msg; document.body.appendChild(t);
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 2600);
  };

  // --- events --------------------------------------------------------------------------------------------------------
  var timer = null;
  var later = function (fn) { clearTimeout(timer); timer = setTimeout(fn, 250); };
  root.addEventListener('input', function (e) {
    var t = e.target;
    if (t.id === 'ldx-q') { S.q = t.value; later(function () { refresh(true); }); }
    else if (t.dataset.fq) { S.fq[t.dataset.fq] = t.value; delete S.more[t.dataset.fq]; later(function () { refresh(true); }); }
  });
  root.addEventListener('change', function (e) {
    var t = e.target;
    if (t.dataset.f) { (S.sel[t.dataset.f] = S.sel[t.dataset.f] || {})[t.dataset.v] = t.checked; refresh(); }
    else if (t.dataset.sf) { S.sel[t.dataset.sf] = {}; if (t.value) S.sel[t.dataset.sf][t.value] = true; refresh(); }
    else if (t.id === 'ldx-sort') { S.sort = +t.value; S.dir = ''; refresh(); }
    else if (t.id === 'ldx-asof') { if (isDay(t.value)) { S.asOf = t.value; refresh(); } }
  });
  root.addEventListener('click', function (e) {
    var t = e.target, el;
    if ((el = t.closest('[data-open]'))) { e.preventDefault(); run(B.open && B.open.cmd, JSON.parse(el.dataset.open)); return; }
    if ((el = t.closest('[data-link]'))) { e.preventDefault(); run(B.links[el.dataset.link], JSON.parse(el.dataset.pk)); return; }
    if ((el = t.closest('[data-act]'))) { run(B.actions[+el.dataset.act], JSON.parse(el.dataset.pk), el.dataset.k); return; }
    if (t.closest('[data-create]')) { run(B.header.create.cmd, []); return; }
    if ((el = t.closest('[data-hlink]'))) { e.preventDefault(); goLink((B.header.links || [])[+el.dataset.hlink]); return; }
    if ((el = t.closest('[data-lay]'))) { S.lay = el.dataset.lay; draw(); return; }
    if (t.closest('[data-dir]')) { S.dir = isDesc() ? 'asc' : 'desc'; refresh(); return; }
    if (t.closest('[data-asof-today]')) { S.asOf = today(); refresh(); return; }
    if ((el = t.closest('[data-star]'))) {
      var key = el.dataset.star;
      ask(url(B.ajax.fav, {k: key}), function (d) { CARDS.forEach(function (c) { if (c.k === key) c.fav = !!d.favorite; }); if ((C.facets || []).some(function (f) { return f.kind === 'favorites'; })) reload(); else draw(); });
      return;
    }
    if ((el = t.closest('[data-copy]'))) {
      var txt = el.dataset.copy;
      try { navigator.clipboard.writeText(txt).then(function () { toast(S_('copied') + ' : ' + txt); }, function () { toast(txt); }); } catch (x) { toast(txt); }
      return;
    }
    if ((el = t.closest('[data-tile]'))) { S.tile = S.tile === el.dataset.tile ? null : el.dataset.tile; refresh(); return; }
    if ((el = t.closest('[data-chip]'))) { var m = (S.sel[el.dataset.chip] = S.sel[el.dataset.chip] || {}); m[el.dataset.v] = !m[el.dataset.v]; refresh(); return; }
    if ((el = t.closest('[data-fold]'))) {
      var fid = el.dataset.fold; S.collapsed[fid] = !S.collapsed[fid]; draw();
      var pg = R && R.facets && R.facets[fid];
      if (!S.collapsed[fid] && (!pg || pg.lazy)) facetsOnly();
      return;
    }
    if (t.closest('[data-refresh]')) { R.changed = false; R.at = null; reload(true); return; }
    if ((el = t.closest('[data-fmore]'))) { var f = (C.facets || []).filter(function (x) { return x.id === el.dataset.fmore; })[0]; S.more[el.dataset.fmore] = Math.min(50, (S.more[el.dataset.fmore] || (f && f.top) || 10) + 10); refresh(); return; }
    if ((el = t.closest('[data-fless]'))) { delete S.more[el.dataset.fless]; refresh(); return; }
    if ((el = t.closest('[data-rm]'))) {
      var id = el.dataset.rm;
      if (id === 'q') S.q = ''; else if (id === 'tile') S.tile = null; else if (S.sel[id]) S.sel[id][el.dataset.v] = false;
      refresh(); return;
    }
    if (t.closest('[data-clear]')) { S.q = ''; S.tile = null; S.sel = {}; S.fq = {}; refresh(); return; }
    if (t.closest('[data-side]')) { S.noSide = !S.noSide; draw(); return; }
    if (t.closest('.ldx-more')) { more(); return; }
  });

  draw();
  refresh();
})();
