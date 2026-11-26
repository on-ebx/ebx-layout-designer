/* Layout Designer: the "Hierarchy chart" block (org chart, tree, rings) drawn from the JSON the server put in the block. */
(function () {
  if (window.ldfOrgChart) { window.ldfOrgChart.scan(); return; }
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); };
  function chart(box, D) {
    var T = D.t, byKey = {}, kids = {};
    D.nodes.forEach(function (d) { byKey[d.k] = d; });
    D.nodes.forEach(function (d) { if (d.p != null && byKey[d.p]) (kids[d.p] = kids[d.p] || []).push(d.k); });
    Object.keys(kids).forEach(function (k) { kids[k].sort(function (x, y) { return byKey[x].n.localeCompare(byKey[y].n); }); });
    var focus = D.focus, root = D.root, view = D.present === 'auto' ? 'org' : D.present, q = '';
    var openKids = {}, treeOpen = {}, totals = {};
    var total = function (k) { if (totals[k] != null) return totals[k]; var n = 0; (kids[k] || []).forEach(function (c) { n += 1 + total(c); }); return (totals[k] = n); };
    var path = function (k) { var p = [], seen = {}; while (k != null && byKey[k] && !seen[k]) { seen[k] = 1; p.unshift(k); k = byKey[k].p; } return p; };
    var depth = function (k) { var m = 0; (kids[k] || []).forEach(function (c) { m = Math.max(m, depth(c)); }); return 1 + m; };
    var plural = function (n, one, many) { return (n > 1 ? many : one).replace('{n}', n); };
    var dot = function (d) { return '<span class="ldf-oc-dot" style="background:' + esc(d.col) + '"></span>'; };
    var link = function (d, cls, inner) { return '<a class="' + cls + '" data-k="' + esc(d.k) + '" href="' + esc(d.u || '#') + '"' + (D.newTab ? ' target="_blank" rel="noopener"' : '') + ' title="' + esc(T.open) + '">' + inner + '</a>'; };
    path(focus).forEach(function (k) { treeOpen[k] = 1; });

    function org() {
      var p = path(focus), me = byKey[focus], ch = kids[focus] || [], h = '<div class="ldf-oc-org"><div class="ldf-oc-anc">';
      p.slice(0, -1).forEach(function (k) {
        var d = byKey[k], others = (kids[k] || []).length - 1;
        h += '<div class="ldf-oc-ancrow">' + link(d, 'ldf-oc-node', dot(d) + '<span class="ldf-oc-nm">' + esc(d.n) + '</span>') + (others > 0 ? '<span class="ldf-oc-sib">' + esc(plural(others, T.oneOther, T.others)) + '</span>' : '') + '</div><div class="ldf-oc-v"></div>';
      });
      var t = total(focus);
      h += '</div><div class="ldf-oc-focus"><span class="ldf-oc-nm">' + esc(me.n) + '</span><span class="ldf-oc-facts">' + (me.cv ? '<span>' + dot(me) + ' ' + esc(me.cv) + '</span>' : '') + (me.s ? '<span>' + esc(me.s) + '</span>' : '')
        + '<span>' + esc(plural(ch.length, T.oneChild, T.children)) + '</span>' + (t > ch.length ? '<span>' + esc(T.below.replace('{n}', t)) + '</span>' : '') + '<span>' + esc(T.level.replace('{n}', p.length).replace('{m}', depth(root))) + '</span></span></div>';
      if (ch.length) {
        h += '<div class="ldf-oc-bus"><i></i></div><div class="ldf-oc-kids">';
        ch.forEach(function (k) {
          var d = byKey[k], g = kids[k] || [], open = !!openKids[k];
          h += '<div class="ldf-oc-kid">' + link(d, 'ldf-oc-node ldf-oc-card', '<span class="ldf-oc-top">' + dot(d) + '<span class="ldf-oc-nm">' + esc(d.n) + '</span></span>' + (d.s ? '<span class="ldf-oc-sub">' + esc(d.s) + '</span>' : ''));
          if (g.length) {
            h += '<button type="button" class="ldf-oc-more" data-x="' + esc(k) + '" aria-expanded="' + open + '">' + (open ? '▾ ' : '▸ ') + esc(plural(g.length, T.oneChild, T.children)) + '</button>';
            if (open) h += '<div class="ldf-oc-grand">' + g.map(function (c) { var x = byKey[c], n = (kids[c] || []).length; return link(x, 'ldf-oc-node', dot(x) + '<span class="ldf-oc-nm">' + esc(x.n) + '</span>' + (n ? '<span class="ldf-oc-sub">· ' + n + '</span>' : '')); }).join('') + '</div>';
          }
          h += '</div>';
        });
        h += '</div>';
      }
      return h + '</div>';
    }

    function tree() {
      var ql = q.trim().toLowerCase(), keep = {}, rows = '';
      if (ql) D.nodes.forEach(function (d) { if (d.n.toLowerCase().indexOf(ql) >= 0) path(d.k).forEach(function (k) { keep[k] = 1; }); });
      var walk = function (k, lvl) {
        if (ql && !keep[k]) return;
        var d = byKey[k], ch = kids[k] || [], open = ql ? true : !!treeOpen[k];
        var name = esc(d.n);
        if (ql) { var i = d.n.toLowerCase().indexOf(ql); if (i >= 0) name = esc(d.n.slice(0, i)) + '<mark>' + esc(d.n.slice(i, i + ql.length)) + '</mark>' + esc(d.n.slice(i + ql.length)); }
        rows += '<div class="ldf-oc-trow' + (k === focus ? ' ldf-oc-me' : '') + '" style="padding-left:' + (8 + lvl * 18) + 'px">'
          + '<button type="button" class="ldf-oc-car" data-t="' + esc(k) + '" aria-label="' + esc(open ? T.collapse : T.expand) + '"' + (ch.length ? '' : ' disabled style="visibility:hidden"') + '>' + (open ? '▾' : '▸') + '</button>'
          + dot(d) + link(d, 'ldf-oc-tn', name) + '<span class="ldf-oc-r">' + esc(d.s || '') + (ch.length ? ' · ' + total(k) : '') + '</span></div>';
        if (open) ch.forEach(function (c) { walk(c, lvl + 1); });
      };
      walk(root, 0);
      return '<div class="ldf-oc-tools"><input type="search" class="ldf-oc-q" placeholder="' + esc(T.search) + '" aria-label="' + esc(T.search) + '" value="' + esc(q) + '">'
        + '<button type="button" class="ldf-oc-all">' + esc(T.expandAll) + '</button><button type="button" class="ldf-oc-none">' + esc(T.collapseAll) + '</button></div>'
        + '<div class="ldf-oc-tree">' + (rows || '<div class="ldf-oc-trow">' + esc(T.noMatch) + '</div>') + '</div>';
    }

    function rings() {
      var cx = 220, cy = 220, ring = Math.max(18, Math.min(34, 180 / Math.max(1, depth(root) - 1))), paths = '', mine = {};
      path(focus).forEach(function (k) { mine[k] = 1; });
      var leaves = {}; var lv = function (k) { if (leaves[k]) return leaves[k]; var c = kids[k] || [], n = 0; c.forEach(function (x) { n += lv(x); }); return (leaves[k] = c.length ? n : 1); };
      var pt = function (a, r) { return [cx + r * Math.sin(a), cy - r * Math.cos(a)]; };
      var arc = function (a0, a1, r0, r1) { var big = a1 - a0 > Math.PI ? 1 : 0, p0 = pt(a0, r1), p1 = pt(a1, r1), p2 = pt(a1, r0), p3 = pt(a0, r0);
        return 'M' + p0 + 'A' + r1 + ',' + r1 + ' 0 ' + big + ' 1 ' + p1 + 'L' + p2 + 'A' + r0 + ',' + r0 + ' 0 ' + big + ' 0 ' + p3 + 'Z'; };
      var walk = function (k, a0, a1, lvl) {
        var d = byKey[k], op = mine[k] ? 1 : 0.42;
        if (lvl === 0) paths += '<a data-k="' + esc(d.k) + '" href="' + esc(d.u || '#') + '"' + (D.newTab ? ' target="_blank"' : '') + '><circle cx="' + cx + '" cy="' + cy + '" r="38" fill="' + esc(d.col) + '" opacity="' + op + '"><title>' + esc(d.n) + '</title></circle></a>';
        else if (a1 - a0 > 0.002) { var r0 = 40 + (lvl - 1) * ring, r1 = r0 + ring - 2;
          paths += '<a data-k="' + esc(d.k) + '" href="' + esc(d.u || '#') + '"' + (D.newTab ? ' target="_blank"' : '') + '><path d="' + arc(a0, Math.min(a1, a0 + Math.PI * 2 - 0.0001), r0, r1) + '" fill="' + esc(d.col) + '" opacity="' + (k === focus ? 1 : op) + '"' + (k === focus ? ' class="ldf-oc-cur"' : '') + '><title>' + esc(d.n) + '</title></path></a>'; }
        var a = a0, tot = lv(k);
        (kids[k] || []).forEach(function (c) { var span = (a1 - a0) * lv(c) / tot; walk(c, a, a + span, lvl + 1); a += span; });
      };
      walk(root, 0, Math.PI * 2, 0);
      var me = byKey[focus], legend = {};
      D.nodes.forEach(function (d) { if (d.cv && !legend[d.cv]) legend[d.cv] = d.col; });
      var lg = Object.keys(legend).slice(0, 8).map(function (k) { return '<span><span class="ldf-oc-dot" style="background:' + esc(legend[k]) + '"></span>' + esc(k) + '</span>'; }).join('');
      return '<div class="ldf-oc-sun"><svg viewBox="0 0 440 440" role="img" aria-label="' + esc(T.ringsAria) + '">' + paths + '</svg><div class="ldf-oc-suninfo"><span class="ldf-more">' + esc(T.ringsNote) + '</span><b>' + esc(me.n) + '</b>'
        + '<span class="ldf-more">' + esc(T.below.replace('{n}', total(focus))) + '</span><div class="ldf-oc-legend">' + lg + '</div></div></div>';
    }

    function render() {
      var ch = (kids[focus] || []).length, v = view;
      if (D.present === 'auto' && v === 'org' && ch > D.thr) v = 'tree';
      [].forEach.call(box.querySelectorAll('.ldf-oc-seg button'), function (b) { b.classList.toggle('ldf-oc-on', b.getAttribute('data-v') === v); });
      var body = box.querySelector('.ldf-oc-body');
      body.innerHTML = v === 'org' ? org() : v === 'tree' ? tree() : rings();
      var note = box.querySelector('.ldf-oc-note'), msg = '';
      if (v === 'tree' && view === 'org' && D.present === 'auto') msg = T.switched.replace('{n}', ch).replace('{m}', D.thr);
      if (D.cut) msg = (msg ? msg + ' ' : '') + T.cut.replace('{n}', D.nodes.length);
      note.textContent = msg; note.hidden = !msg;
      var input = body.querySelector('.ldf-oc-q');
      if (input) input.addEventListener('input', function (e) { var pos = e.target.selectionStart; q = e.target.value; render(); var n = box.querySelector('.ldf-oc-q'); n.focus(); try { n.setSelectionRange(pos, pos); } catch (x) {} });
    }

    box.addEventListener('click', function (e) {
      var t = e.target, b;
      // an entity opens in EBX's pop-up over this record (its preview command), unless the user asks for a new tab
      var a = t.closest('a[data-k]');
      if (a && D.openIn === 'popup' && !e.ctrlKey && !e.metaKey && !e.shiftKey && e.button === 0) {
        var d0 = byKey[a.getAttribute('data-k')];
        if (d0 && d0.pv) { e.preventDefault(); try { (new Function(d0.pv))(); } catch (x) { window.location.href = d0.u; } return; }
      }
      if ((b = t.closest('.ldf-oc-seg button'))) { view = b.getAttribute('data-v'); return render(); }
      if ((b = t.closest('[data-x]'))) { var k = b.getAttribute('data-x'); openKids[k] = !openKids[k]; return render(); }
      if ((b = t.closest('[data-t]'))) { var k2 = b.getAttribute('data-t'); treeOpen[k2] = !treeOpen[k2]; return render(); }
      if (t.closest('.ldf-oc-all')) { D.nodes.forEach(function (d) { treeOpen[d.k] = 1; }); return render(); }
      if (t.closest('.ldf-oc-none')) { treeOpen = {}; path(focus).forEach(function (k) { treeOpen[k] = 1; }); return render(); }
    });
    render();
  }
  window.ldfOrgChart = { scan: function () {
    [].forEach.call(document.querySelectorAll('.ldf-oc[data-oc]:not([data-oc-done])'), function (box) {
      box.setAttribute('data-oc-done', '1');
      try { chart(box, JSON.parse(box.querySelector('script[type="application/json"]').textContent)); }
      catch (e) { var body = box.querySelector('.ldf-oc-body'); if (body) body.textContent = String(e); }
    });
  } };
  window.ldfOrgChart.scan();
})();
