package com.on.ebx.layout.runtime;

/** The look of a designed form: the ON EBX palette, panels as cards, two columns that stack under 900 px. */
final class Css
{
	static final String STYLE = ".ldf{font-family:'Work Sans',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#2E2340;font-size:13px;display:flex;flex-direction:column;gap:14px;padding:4px 2px 18px}"
		+ ".ldf-row{display:grid;grid-template-columns:minmax(0,1fr);gap:14px}"
		+ ".ldf-row.ldf-c2{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.ldf-row.ldf-c2.ldf-r21{grid-template-columns:minmax(0,2fr) minmax(0,1fr)}.ldf-row.ldf-c2.ldf-r12{grid-template-columns:minmax(0,1fr) minmax(0,2fr)}"
		+ "@media (max-width:900px){.ldf-row.ldf-c2,.ldf-row.ldf-c2.ldf-r21,.ldf-row.ldf-c2.ldf-r12{grid-template-columns:minmax(0,1fr)}}"
		+ ".ldf-col{display:flex;flex-direction:column;gap:14px;min-width:0}"
		+ ".ldf-panel{background:#fff;border:1px solid #E2DEE6;border-radius:13px;padding:0 16px 14px;box-shadow:0 3px 12px rgba(46,35,64,.05);min-width:0;overflow-x:auto}"
		+ ".ldf-panel>header{padding:12px 0 9px;border-bottom:1px solid #EFECF3;margin-bottom:8px;display:flex;justify-content:space-between;gap:10px;align-items:center;flex-wrap:wrap}"
		+ ".ldf-panel h3{margin:0;font-size:14px;color:#07073C}.ldf-panel h3 .ldf-n{font-weight:500;color:#6B6575}"
		+ ".ldf-panel header p{margin:2px 0 0;font-size:11.5px;color:#6B6575}"
		+ ".ldf-ro{display:grid;grid-template-columns:minmax(120px,38%) minmax(0,1fr);gap:4px 12px;padding:5px 0;align-items:baseline}"
		+ ".ldf-l{font-size:12px;color:#6B6575;overflow-wrap:anywhere}.ldf-v{overflow-wrap:anywhere}.ldf-v.ldf-empty{color:#6B6575}"
		+ ".ldf-mono{font-family:'Fragment Mono',ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12.5px}"
		+ ".ldf-fig{font-size:16px;font-weight:700;color:#07073C}"
		+ ".ldf-wl{font-size:12px;color:#6B6575;margin:8px 0 4px}"
		+ ".ldf-badge{display:inline-flex;font-size:11px;font-weight:600;padding:2px 8px;border-radius:999px;background:#F1EEF4;color:#2E2340}"
		+ ".ldf-badge.ldf-ok{background:#E7F8EF;color:#0B7A4B}.ldf-badge.ldf-warn{background:#FFF7E6;color:#8A4B00}"
		+ ".ldf-hero{background:#fff;border:1px solid #E2DEE6;border-radius:13px;padding:16px;display:flex;flex-wrap:wrap;gap:14px 18px;align-items:center;justify-content:space-between;box-shadow:0 3px 12px rgba(46,35,64,.05)}"
		+ ".ldf-hero-l{display:flex;gap:14px;align-items:center;min-width:0;flex:1 1 420px}"
		+ ".ldf-logo{width:64px;height:64px;border-radius:14px;background:#07073C;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:20px;flex:none}"
		+ ".ldf-ey{font-size:10.5px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#6B6575}"
		+ ".ldf-hero h2{margin:2px 0;font-size:22px;color:#07073C;line-height:1.2;overflow-wrap:anywhere}.ldf-sub{color:#6B6575;font-size:12.5px}"
		+ ".ldf-chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.ldf-hero-r{display:flex;flex-direction:column;gap:8px;align-items:flex-end}"
		+ ".ldf-meter{display:inline-flex;align-items:center;gap:8px;font-weight:700;color:#07073C}.ldf-meter i{width:120px;height:6px;border-radius:3px;background:#E2DEE6;position:relative;overflow:hidden;display:inline-block}"
		+ ".ldf-meter i b{position:absolute;left:0;top:0;bottom:0;background:#7F17FF}.ldf-meter small{font-weight:500;color:#6B6575}"
		+ ".ldf-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px}"
		+ ".ldf-fact{background:#fff;border:1px solid #E2DEE6;border-radius:10px;padding:10px 12px;min-width:0}"
		+ ".ldf-fact b{display:block;font-size:15px;color:#07073C;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
		+ ".ldf-fact span{font-size:10px;font-weight:700;letter-spacing:.05em;text-transform:uppercase;color:#6B6575}"
		+ ".ldf-vrep{background:#fff;border:1px solid #E2DEE6;border-radius:13px;padding:12px 16px 14px;display:flex;flex-direction:column;gap:10px;box-shadow:0 3px 12px rgba(46,35,64,.05)}"
		+ ".ldf-vhd{display:flex;flex-wrap:wrap;gap:8px 14px;align-items:center;justify-content:space-between}.ldf-vhd h3{margin:0;font-size:14px;color:#07073C}"
		+ ".ldf-vcs{display:flex;flex-wrap:wrap;gap:6px}.ldf-vc{font-size:11px;font-weight:700;border-radius:999px;padding:2px 9px}"
		+ ".ldf-vc-error{background:#FDEDE7;color:#C73200}.ldf-vc-warning{background:#FFF7E6;color:#8A4B00}.ldf-vc-info{background:#ECEBFD;color:#3931EC}"
		+ ".ldf-vg{display:flex;flex-direction:column;gap:6px}.ldf-vgh{font-size:10.5px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#6B6575;padding-top:2px}"
		+ ".ldf-vi{display:grid;grid-template-columns:22px minmax(0,1fr) auto;gap:10px;align-items:center;border:1px solid #EFECF3;border-radius:10px;padding:8px 10px}"
		+ ".ldf-vi-error{background:#FFF8F5}"
		+ ".ldf-sv{width:20px;height:20px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;color:#fff}"
		+ ".ldf-sv-error{background:#C73200}.ldf-sv-warning{background:#B86E00}.ldf-sv-info{background:#3931EC;font-style:italic;font-family:Georgia,serif}"
		+ ".ldf-vt{display:flex;flex-direction:column;gap:2px;min-width:0}.ldf-vt b{font-weight:600;color:#07073C;overflow-wrap:anywhere}"
		+ ".ldf-where{font-size:11.5px;color:#6B6575}.ldf-absent{color:#8A4B00;font-weight:600}"
		+ ".ldf-va{display:flex;gap:8px;align-items:center;flex-wrap:wrap;justify-content:flex-end}"
		+ ".ldf-go{color:#5B12C7;font-weight:600;font-size:12px;text-decoration:underline;cursor:pointer}"
		+ ".ldf-btn{font-weight:700;color:#5B12C7;background:#fff;border:1px solid #CDB8F5;border-radius:8px;padding:5px 11px;font-size:12px;text-decoration:none;cursor:pointer;display:inline-block}"
		+ ".ldf-btn.ldf-solid{background:#7F17FF;color:#fff;border-color:#7F17FF}"
		+ ".ldf-vnone{font-size:12.5px;color:#0B7A4B;background:#E7F8EF;border-radius:8px;padding:8px 10px}"
		+ ".ldf-more{font-size:12px;color:#6B6575}"
		+ ".ldf-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:10px;margin-bottom:8px}"
		+ ".ldf-card{border:1px solid #E2DEE6;border-radius:10px;padding:10px 12px;min-width:0}.ldf-card.ldf-warn{border-color:#F5C26B;background:#FFF7E6}"
		+ ".ldf-card b{color:#07073C;overflow-wrap:anywhere}.ldf-card .ldf-s{color:#6B6575;margin-top:2px;font-size:12px;overflow-wrap:anywhere}"
		+ ".ldf-manage>summary{cursor:pointer;color:#5B12C7;font-weight:600;font-size:12px;margin:4px 0}"
		+ ".ldf-map{height:var(--ldf-map-h,360px);border-radius:10px;overflow:hidden;border:1px solid #E2DEE6;position:relative;background:#E3ECF1}"
		+ ".ldf-map iframe{border:0;width:100%;height:100%}"
		+ ".ldf-tree{display:flex;flex-direction:column;gap:6px;font-size:12.5px}.ldf-tree .ldf-top{color:#6B6575}"
		+ ".ldf-tree .ldf-me{font-weight:700;color:#07073C;border-left:3px solid #7F17FF;padding-left:8px}"
		+ ".ldf-tree .ldf-kid{padding-left:22px;color:#5B12C7}"
		+ ".ldf-note{font-size:12.5px;color:#6B6575;padding:2px}"
		+ ".ldf-dup{display:flex;gap:10px;align-items:center;flex-wrap:wrap}"
		+ "@keyframes ldfflash{0%,60%{box-shadow:0 0 0 4px rgba(127,23,255,.35);background:#F1E8FF}100%{box-shadow:0 0 0 4px transparent}}"
		+ ".ldf-flash,.ldf-flash td{animation:ldfflash 1.8s ease-out}"
		+ "@media (prefers-reduced-motion: reduce){.ldf-flash,.ldf-flash td{animation:none;background:#F1E8FF}}"
		+ "@media (max-width:700px){.ldf-vi{grid-template-columns:22px minmax(0,1fr)}.ldf-va{grid-column:2;justify-content:flex-start}.ldf-ro{grid-template-columns:minmax(0,1fr)}}"
		// opening hours block (look validated on the PointOfSale form, 2026-10-07)
		+ ".ldf-oh-now{display:flex;align-items:center;gap:8px;margin:2px 0 10px;font-size:12.5px;color:#6B6575;flex-wrap:wrap}"
		+ ".ldf-oh-dot{width:8px;height:8px;border-radius:50%;background:#B9B3C2;display:inline-block}"
		+ ".ldf-oh-now.ldf-oh-on .ldf-oh-dot{background:#0B7A4B}.ldf-oh-now b{color:#07073C}"
		+ ".ldf-oh-week{display:grid;grid-template-columns:repeat(auto-fit,minmax(96px,1fr));gap:8px}"
		+ ".ldf-oh-day{border:1px solid #E2DEE6;border-top:3px solid #7F17FF;border-radius:10px;padding:8px 10px;min-width:0;background:#fff}"
		+ ".ldf-oh-day.ldf-oh-off{border-top-color:#DDD8E3;background:#FAF9FB;color:#8E8898}"
		+ ".ldf-oh-day.ldf-oh-today{box-shadow:0 0 0 2px #CDB8F5;background:#F8F3FF}"
		+ ".ldf-oh-dl{font-size:10.5px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#6B6575;display:flex;justify-content:space-between;gap:4px}"
		+ ".ldf-oh-dl i{font-style:normal;color:#5B12C7}"
		+ ".ldf-oh-h{font-size:13px;font-weight:600;color:#07073C;margin:4px 0 7px;overflow-wrap:anywhere}.ldf-oh-off .ldf-oh-h{color:#8E8898;font-weight:500}"
		+ ".ldf-oh-exc .ldf-oh-h{color:#8A4B00}"
		+ ".ldf-oh-bar{position:relative;height:5px;border-radius:3px;background:#EFECF3;overflow:hidden}"
		+ ".ldf-oh-bar b{position:absolute;top:0;bottom:0;background:#7F17FF;border-radius:3px}"
		+ ".ldf-oh-xt{font-size:10.5px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#6B6575;margin:14px 0 6px}"
		+ ".ldf-oh-x{display:grid;grid-template-columns:minmax(110px,auto) minmax(0,1fr) auto;gap:10px;align-items:baseline;padding:6px 0;border-top:1px solid #EFECF3;font-size:12.5px}"
		+ ".ldf-oh-x span{color:#6B6575;overflow-wrap:anywhere}.ldf-oh-x em{font-style:normal;font-weight:600;color:#07073C;white-space:nowrap}"
		+ ".ldf-oh-x em.ldf-oh-c{color:#C73200}.ldf-oh-empty{font-size:12.5px;color:#6B6575}";

	/**
	 * "Go to field": opens the tab holding the anchor (the n-th tab of the form) when the anchor is hidden, then
	 * scrolls to it and highlights it.
	 */
	/**
	 * "Go to field": EBX keeps every tab of a form in the page and hides the inactive ones
	 * ({@code div.ebx_FormPaneWithTabs_Tab}, visibility hidden); the tab's title is {@code li#ebx_TabTitle_<pane id>}.
	 * The script opens that tab when the anchor sits in a hidden one, then scrolls to the anchor and highlights it.
	 */
	static final String GO_JS = "window.ldfGo=function(id){try{var hidden=function(e){var p=e&&e.closest?e.closest('.ebx_FormPaneWithTabs_Tab'):null;return p&&getComputedStyle(p).visibility==='hidden'?p:null;};"
		+ "var el=document.getElementById(id);if(!el)return false;var pane=hidden(el);"
		+ "if(pane){var t=document.getElementById('ebx_TabTitle_'+pane.id);var a=t&&(t.querySelector('a')||t);if(a)a.click();}"
		+ "var tries=0;(function show(){var e=document.getElementById(id);if(e&&hidden(e)&&tries++<30){setTimeout(show,100);return;}if(!e)return;"
		+ "e.scrollIntoView({block:'center'});e.classList.add('ldf-flash');setTimeout(function(){e.classList.remove('ldf-flash');},1900);})();}catch(x){}return false;};";

	private Css()
	{
	}
}
