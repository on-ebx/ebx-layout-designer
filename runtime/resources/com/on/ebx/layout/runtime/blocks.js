/* Layout Designer: the "Value over time" and "Sites on a map" blocks, drawn from the JSON the server put in each block. */
(function () {
  if (window.ldfBlocks) { window.ldfBlocks.scan(); return; }
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); };
  var json = function (box) { return JSON.parse(box.querySelector('script[type="application/json"]').textContent); };

  // ---------------------------------------------------------------- value over time
  function valueHistory(box, D) {
    var T = D.t, fi = 0, body = box.querySelector('.ldf-vh-body');
    var nf = new Intl.NumberFormat(D.locale);
    var dt = function (ms) { return new Date(ms).toLocaleString(D.locale, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }); };
    var ds = function (ms) { return new Date(ms).toLocaleDateString(D.locale, { day: 'numeric', month: 'short' }); };
    function render() {
      var F = D.fields[fi], P = F ? F.points : [], h = '';
      if (D.fields.length > 1) h += '<div class="ldf-oc-seg">' + D.fields.map(function (f, i) { return '<button type="button" data-f="' + i + '"' + (i === fi ? ' class="ldf-oc-on"' : '') + '>' + esc(f.label) + '</button>'; }).join('') + '</div>';
      if (!P.length) { body.innerHTML = h + '<div class="ldf-more">' + esc(T.noValue) + '</div>'; return; }
      var first = P[0], last = P[P.length - 1];
      h += '<div class="ldf-vh-top"><div class="ldf-vh-big"><b>' + esc(last.s) + '</b><span>' + esc(T.since.replace('{n}', dt(last.t))) + '</span></div>';
      if (F.numeric && P.length > 1) {
        var d = last.v - first.v, cls = d > 0 ? 'up' : d < 0 ? 'down' : 'flat';
        h += '<span class="ldf-vh-delta ldf-vh-' + cls + '">' + esc(d === 0 ? T.unchanged : (d > 0 ? '+' : '−') + nf.format(Math.abs(Math.round(d * 100) / 100)) + ' ' + T.changeSince.replace('{n}', ds(first.t))) + '</span>';
      }
      h += '<span class="ldf-more">' + esc(T.ofVersions.replace('{n}', P.length).replace('{m}', D.versions)) + '</span></div>';
      if (P.length < 2) { body.innerHTML = h + '<div class="ldf-more ldf-vh-note">' + esc(T.noChange.replace('{n}', dt(first.t))) + '</div>'; return; }
      if (F.numeric) h += chart(P);
      if (D.list) {
        h += '<div class="ldf-vh-list">' + P.slice().reverse().map(function (p, i, a) {
          var prev = a[i + 1];
          return '<div class="ldf-vh-row"><span class="ldf-vh-d">' + esc(dt(p.t)) + '</span><span>' + (prev ? '<s>' + esc(prev.s) + '</s> → ' : esc(T.first) + ' ') + '<b>' + esc(p.s) + '</b></span>' + (D.author ? '<span class="ldf-vh-u">' + esc(p.u) + '</span>' : '<span></span>') + '</div>';
        }).join('') + '</div>';
      }
      body.innerHTML = h;
    }
    function chart(P) {
      var W = 760, H = 220, L = 54, R = 24, Tp = 26, B = 30;
      var t0 = P[0].t, t1 = Math.max(D.now, P[P.length - 1].t + 1);
      var vs = P.map(function (p) { return p.v; }), lo0 = Math.min.apply(null, vs), hi0 = Math.max.apply(null, vs);
      // round ticks: a step of 1, 2 or 5 times a power of ten
      var span = hi0 - lo0 || Math.abs(hi0) || 1, raw = span * 1.5 / 4, mag = Math.pow(10, Math.floor(Math.log10(raw))), r = raw / mag;
      var step = (r <= 1 ? 1 : r <= 2 ? 2 : r <= 5 ? 5 : 10) * mag;
      var lo = Math.floor((lo0 - span * 0.25) / step) * step, hi = Math.ceil((hi0 + span * 0.25) / step) * step;
      if (lo0 >= 0 && lo < 0) lo = 0;
      var nt = Math.max(1, Math.round((hi - lo) / step));
      var x = function (t) { return L + (W - L - R) * (t - t0) / (t1 - t0); }, y = function (v) { return Tp + (H - Tp - B) * (1 - (v - lo) / (hi - lo)); };
      var g = '', i;
      for (i = 0; i <= nt; i++) { var v = lo + step * i; g += '<line class="ldf-vh-grid" x1="' + L + '" x2="' + (W - R) + '" y1="' + y(v) + '" y2="' + y(v) + '"/><text x="' + (L - 6) + '" y="' + (y(v) + 4) + '" text-anchor="end">' + esc(nf.format(Math.round(v * 1000) / 1000)) + '</text>'; }
      for (i = 0; i <= 4; i++) { var tt = t0 + (t1 - t0) * i / 4; g += '<text x="' + x(tt) + '" y="' + (H - 8) + '" text-anchor="' + (i === 0 ? 'start' : i === 4 ? 'end' : 'middle') + '">' + esc(ds(tt)) + '</text>'; }
      var p = '';
      P.forEach(function (q, j) { var X = x(q.t), Y = y(q.v); p += j === 0 ? 'M' + X + ',' + Y : D.shape === 'line' ? 'L' + X + ',' + Y : 'H' + X + 'V' + Y; });
      p += D.shape === 'line' ? '' : 'H' + (W - R);
      var area = p + 'V' + (H - B) + 'H' + x(P[0].t) + 'Z';
      var pts = P.map(function (q, j) { return '<circle class="ldf-vh-pt' + (j === P.length - 1 ? ' ldf-vh-last' : '') + '" cx="' + x(q.t) + '" cy="' + y(q.v) + '" r="5"><title>' + esc(dt(q.t) + ' · ' + q.u + ' : ' + q.s) + '</title></circle>'
        + (P.length <= 12 ? '<text class="ldf-vh-vl" x="' + x(q.t) + '" y="' + (y(q.v) - 11) + '" text-anchor="middle">' + esc(q.s) + '</text>' : ''); }).join('');
      return '<div class="ldf-vh-chart"><svg viewBox="0 0 ' + W + ' ' + H + '" role="img">' + g + '<line class="ldf-vh-axis" x1="' + L + '" x2="' + (W - R) + '" y1="' + (H - B) + '" y2="' + (H - B) + '"/><path class="ldf-vh-area" d="' + area + '"/><path class="ldf-vh-line" d="' + p + '"/>' + pts + '</svg></div>';
    }
    box.addEventListener('click', function (e) { var b = e.target.closest('[data-f]'); if (b) { fi = +b.getAttribute('data-f'); render(); } });
    render();
  }

  // ---------------------------------------------------------------- sites on a map (OpenStreetMap tiles)
  function sitesMap(box, D) {
    var T = D.t, S = D.sites, body = box.querySelector('.ldf-sm-body'), sel = -1, z = null, cx = 0, cy = 0;
    var placed = S.filter(function (s) { return s.lat != null; });
    var px = function (lat, lon, zz) { var n = 256 * Math.pow(2, zz), r = lat * Math.PI / 180;
      return [(lon + 180) / 360 * n, (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n]; };
    S.forEach(function (s, i) { if (s.hq && sel < 0) sel = i; });
    var H = D.tall ? 520 : 360;
    var list = function () {
      return S.map(function (s, i) {
        var tags = (s.hq ? '<span class="ldf-sm-tag ldf-sm-hq">' + esc(T.hq) + '</span>' : '') + (s.approx ? '<span class="ldf-sm-tag ldf-sm-ap">' + esc(T.approx) + '</span>' : '')
          + (s.pending ? '<span class="ldf-sm-tag">' + esc(T.pending) + '</span>' : '') + (s.miss ? '<span class="ldf-sm-tag">' + esc(T.notFound) + '</span>' : '') + (s.off ? '<span class="ldf-sm-tag">' + esc(T.inactive) + '</span>' : '');
        return '<div class="ldf-sm-site' + (i === sel ? ' ldf-sm-sel' : '') + '" data-i="' + i + '" tabindex="0"><span class="ldf-sm-row"><b>' + esc(s.n) + '</b>' + tags + '</span>'
          + (s.a ? '<span class="ldf-sm-a">' + esc(s.a) + '</span>' : '') + (s.d ? '<span class="ldf-sm-dd">' + esc(s.d) + '</span>' : '')
          + '<a class="ldf-sm-open" href="' + esc(s.u) + '" target="_blank" rel="noopener">' + esc(T.open) + '</a></div>';
      }).join('');
    };
    function fit(W) {
      if (!placed.length) return;
      var lats = placed.map(function (s) { return s.lat; }), lons = placed.map(function (s) { return s.lon; });
      var n = Math.max.apply(null, lats), so = Math.min.apply(null, lats), e = Math.max.apply(null, lons), w = Math.min.apply(null, lons);
      z = 15;
      while (z > 2) { var a = px(n, w, z), b = px(so, e, z); if (b[0] - a[0] <= W - 80 && b[1] - a[1] <= H - 80) break; z--; }
      if (placed.length === 1) z = 13;
      var c1 = px(n, w, z), c2 = px(so, e, z); cx = (c1[0] + c2[0]) / 2; cy = (c1[1] + c2[1]) / 2;
    }
    function drawMap() {
      var map = body.querySelector('.ldf-sm-map'); if (!map) return;
      var W = map.clientWidth || 600;
      if (z == null) fit(W);
      if (z == null) { map.innerHTML = '<div class="ldf-more" style="padding:12px">' + esc(T.notFound) + '</div>'; return; }
      var left = cx - W / 2, top = cy - H / 2, max = Math.pow(2, z), h = '';
      for (var tx = Math.floor(left / 256); tx <= Math.floor((left + W) / 256); tx++) {
        for (var ty = Math.floor(top / 256); ty <= Math.floor((top + H) / 256); ty++) {
          if (ty < 0 || ty >= max) continue;
          var wx = ((tx % max) + max) % max;
          h += '<img class="ldf-sm-tile" alt="" draggable="false" src="https://tile.openstreetmap.org/' + z + '/' + wx + '/' + ty + '.png" style="left:' + Math.round(tx * 256 - left) + 'px;top:' + Math.round(ty * 256 - top) + 'px">';
        }
      }
      S.forEach(function (s, i) {
        if (s.lat == null) return;
        var p = px(s.lat, s.lon, z), X = Math.round(p[0] - left), Y = Math.round(p[1] - top);
        h += (s.approx ? '<span class="ldf-sm-halo" style="left:' + X + 'px;top:' + Y + 'px"></span>' : '')
          + '<span class="ldf-sm-pin' + (s.hq ? ' ldf-sm-pinhq' : '') + (i === sel ? ' ldf-sm-pinsel' : '') + '" data-i="' + i + '" title="' + esc(s.n) + '" style="left:' + X + 'px;top:' + Y + 'px"></span>'
          + '<span class="ldf-sm-lbl" style="left:' + (X + 10) + 'px;top:' + (Y - 8) + 'px">' + esc(s.n) + '</span>';
      });
      h += '<div class="ldf-sm-zoom"><button type="button" data-z="1" aria-label="' + esc(T.zoomIn) + '">+</button><button type="button" data-z="-1" aria-label="' + esc(T.zoomOut) + '">−</button></div>'
        + '<div class="ldf-sm-attr">© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a></div>';
      map.innerHTML = h;
    }
    // "Locate now" (1.15.1): the addresses not known yet, asked one by one to the module (the service takes one a second)
    var left = 0;
    function ask(q) { return geoAsk(D.g, q, D.tk); }
    function locateAll() {
      if (left) return;
      var todo = S.filter(function (s) { return s.pending && s.q; });
      left = todo.length;
      var next = function () {
        var s = todo.shift();
        if (!s) { left = 0; return render(); }
        render();
        ask(s.q).then(function (a) {
          if (a.lat != null) { s.lat = a.lat; s.lon = a.lon; s.pending = false; return; }
          if (!s.tq) { s.pending = false; s.miss = true; return; }
          return ask(s.tq).then(function (b) { s.pending = false; if (b.lat != null) { s.lat = b.lat; s.lon = b.lon; s.approx = true; } else s.miss = true; });
        }).then(function () { left--; placed = S.filter(function (x) { return x.lat != null; }); z = null; next(); });
      };
      next();
    }
    function render() {
      var n = placed.length, hq = S.filter(function (s) { return s.hq; }).length, ap = S.filter(function (s) { return s.approx; }).length;
      var waiting = D.g ? S.filter(function (s) { return s.pending && s.q; }).length : 0;
      body.innerHTML = '<div class="ldf-more ldf-sm-sum">' + esc(T.sites.replace('{n}', S.length).replace('{m}', n)) + (hq ? ' · ' + hq + ' ' + esc(T.hq.toLowerCase()) : '') + (ap ? ' · ' + ap + ' ' + esc(T.approx.toLowerCase()) : '')
        + (waiting ? ' <button type="button" class="ldf-btn ldf-sm-locate" data-locate' + (left ? ' disabled' : '') + '>' + esc(left ? T.locating.replace('{n}', left) : T.locate) + '</button>' : '') + '</div>'
        + '<div class="ldf-sm-grid"><div class="ldf-sm-map" style="height:' + H + 'px"></div><div class="ldf-sm-list" style="max-height:' + H + 'px">' + list() + '</div></div>';
      drawMap();
    }
    box.addEventListener('click', function (e) {
      var t = e.target, b;
      if (t.closest('a')) return;
      if (t.closest('[data-locate]')) return locateAll();
      if ((b = t.closest('[data-z]'))) { var nz = Math.max(2, Math.min(18, z + +b.getAttribute('data-z'))), f = Math.pow(2, nz - z); cx *= f; cy *= f; z = nz; return drawMap(); }
      if ((b = t.closest('[data-i]'))) {
        sel = +b.getAttribute('data-i'); var s = S[sel];
        if (s.lat != null && z != null) { var p = px(s.lat, s.lon, z); cx = p[0]; cy = p[1]; }
        [].forEach.call(box.querySelectorAll('.ldf-sm-site'), function (el) { el.classList.toggle('ldf-sm-sel', +el.getAttribute('data-i') === sel); });
        drawMap();
      }
    });
    box.addEventListener('keydown', function (e) { if (e.key === 'Enter' && e.target.classList.contains('ldf-sm-site')) e.target.click(); });
    var drag = null;
    box.addEventListener('pointerdown', function (e) { if (!e.target.closest('.ldf-sm-map') || e.target.closest('button,a,[data-i]')) return; drag = [e.clientX, e.clientY, cx, cy]; e.preventDefault(); });
    window.addEventListener('pointermove', function (e) { if (!drag) return; cx = drag[2] - (e.clientX - drag[0]); cy = drag[3] - (e.clientY - drag[1]); drawMap(); });
    window.addEventListener('pointerup', function () { drag = null; });
    render();
    // the form's tab may be hidden at first: draw again once the map has a width
    if (window.ResizeObserver) { var last = 0; new ResizeObserver(function () { var m = body.querySelector('.ldf-sm-map'); if (m && m.clientWidth && m.clientWidth !== last) { if (!last) z = null; last = m.clientWidth; drawMap(); } }).observe(box); }
  }

  // one address asked to the module (1.23.3): 200 = answer, 202 = queued (asked again every 2 s, 60 times at most), 429 or an error = not found
  function geoAsk(url, q, t, tries) {
    tries = tries || 0;
    return fetch(url + '?q=' + encodeURIComponent(q) + '&t=' + encodeURIComponent(t), { credentials: 'same-origin' })
      .then(function (r) { return r.ok || r.status === 202 ? r.json().then(function (a) { return { s: r.status, a: a }; }) : { s: r.status, a: { found: false } }; })
      .catch(function () { return { s: 0, a: { found: false } }; })
      .then(function (x) {
        if (x.s !== 202) return x.a;
        if (tries >= 60) return { found: false };
        return new Promise(function (ok) { setTimeout(ok, 2000); }).then(function () { return geoAsk(url, q, t, tries + 1); });
      });
  }

  // the "Locate now" button of a map block placed by its address (1.15.1): asks the module, then draws the map
  function locateMap(btn) {
    if (btn.disabled) return;
    btn.disabled = true; btn.textContent = btn.getAttribute('data-wait');
    geoAsk(btn.getAttribute('data-g'), btn.getAttribute('data-q'), btn.getAttribute('data-t'))
      .then(function (a) {
        var box = btn.closest('.ldf-more');
        if (a.lat == null) { box.textContent = btn.getAttribute('data-miss'); return; }
        var bbox = (a.lon - 0.012) + ',' + (a.lat - 0.007) + ',' + (a.lon + 0.012) + ',' + (a.lat + 0.007);
        var m = document.createElement('div'); m.className = 'ldf-map'; m.style.setProperty('--ldf-map-h', btn.getAttribute('data-h') + 'px');
        m.innerHTML = '<iframe loading="lazy" title="map" src="https://www.openstreetmap.org/export/embed.html?bbox=' + bbox + '&amp;layer=mapnik&amp;marker=' + a.lat + ',' + a.lon + '"></iframe>';
        box.parentNode.replaceChild(m, box);
      });
  }
  document.addEventListener('click', function (e) { var b = e.target.closest && e.target.closest('.ldf-map-locate'); if (b) { e.preventDefault(); locateMap(b); } });

  window.ldfBlocks = { scan: function () {
    [].forEach.call(document.querySelectorAll('.ldf-vh[data-vh]:not([data-done])'), function (box) { box.setAttribute('data-done', '1'); try { valueHistory(box, json(box)); } catch (e) { box.querySelector('.ldf-vh-body').textContent = String(e); } });
    [].forEach.call(document.querySelectorAll('.ldf-sm[data-sm]:not([data-done])'), function (box) { box.setAttribute('data-done', '1'); try { sitesMap(box, json(box)); } catch (e) { box.querySelector('.ldf-sm-body').textContent = String(e); } });
  } };
  window.ldfBlocks.scan();
})();

/* 1.24.0: the "Hierarchy at a date" block. Every state of every entity is in the page; the chosen date decides which one shows. */
(function () {
  if (window.ldfAsOf) { window.ldfAsOf.scan(); return; }
  var init = function (box) {
    if (box.ldfInit) return; box.ldfInit = 1;
    var cfg = JSON.parse(box.querySelector('script[type="application/json"]').textContent);
    var inp = box.querySelector('input[type=date]'), cnt = box.querySelector('.ldf-aot-cnt'), bounds = cfg.bounds || [];
    /* 1.25.0: the date chosen in an explorer ("in force on") or in another block follows the user; a date read from a field of the record wins */
    var key = cfg.user ? 'ldfAsOf:' + cfg.user : null, isDay = function (s) { return /^\d{4}-\d{2}-\d{2}$/.test(s || ''); };
    try { var shared = key ? sessionStorage.getItem(key) : null; if (!cfg.fixed && isDay(shared)) inp.value = shared; } catch (x) {}
    var share = function () { try { if (key && isDay(inp.value)) sessionStorage.setItem(key, inp.value); } catch (x) {} };
    var lv0 = function (li) { return li.getAttribute('data-lv') === '0'; };
    var visit = function (li, d, counts) {
      var on = false, kids = false, h = li.querySelector(':scope > .ldf-aot-h');
      Array.prototype.forEach.call(h.querySelectorAll(':scope > .ldf-aot-s'), function (a) {
        var f = a.getAttribute('data-f'), t = a.getAttribute('data-t'), ok = (!f || f <= d) && (!t || d <= t);
        a.style.display = ok ? '' : 'none'; if (ok) on = true;
      });
      if (on || lv0(li)) Array.prototype.forEach.call(li.querySelectorAll(':scope > ul > li'), function (c) { if (visit(c, d, counts)) kids = true; });
      var lv = +li.getAttribute('data-lv');
      /* an entity with no state on the date does not exist under this parent on that date: its subtree goes with it
         (its children name the entity, not this parent); only the root stays, greyed, when it has no state */
      if (on) counts[lv] = (counts[lv] || 0) + 1;
      li.classList.toggle('ldf-aot-off', !on);
      li.style.display = (on || lv === 0) ? '' : 'none';
      return on;
    };
    var apply = function () {
      var d = inp.value || cfg.def, counts = [];
      Array.prototype.forEach.call(box.querySelectorAll(':scope > ul.ldf-aot-tree > li'), function (li) { visit(li, d, counts); });
      cnt.textContent = (cfg.levels || []).map(function (l, i) { return (counts[i + 1] || 0) + ' ' + l; }).join(' \u00b7 ');
    };
    box.addEventListener('click', function (e) {
      var b = e.target.closest('[data-aot]'); if (!b) return;
      var d = inp.value || cfg.def, k = b.getAttribute('data-aot'), x;
      if (k === 'today') { x = new Date(); inp.value = x.getFullYear() + '-' + ('0' + (x.getMonth() + 1)).slice(-2) + '-' + ('0' + x.getDate()).slice(-2); }
      else if (k === 'prev') { for (var i = bounds.length - 1; i >= 0; i--) if (bounds[i] < d) { inp.value = bounds[i]; break; } }
      else if (k === 'next') { for (var j = 0; j < bounds.length; j++) if (bounds[j] > d) { inp.value = bounds[j]; break; } }
      share(); apply();
    });
    inp.addEventListener('change', function () { share(); apply(); });
    apply();
  };
  window.ldfAsOf = { scan: function () { Array.prototype.forEach.call(document.querySelectorAll('.ldf-aot'), init); } };
  window.ldfAsOf.scan();
})();
