/* Layout Designer page. Fed by window.LD_BOOT (built by DesignerUserService/ModelExport); posts the layout back
   through EBX's own form: hidden fields ldAction / ldLayout / ldRecord, then a click on the hidden submit button. */
(function(){
'use strict';
const B = window.LD_BOOT;
const root = document.getElementById('ld-app');
if (!B || !root) return;

/* ======================= locales ======================= */
const LOCALES = B.locales, DEF = B.defaultLocale;
let lang = B.userLocale;
const UI = (B.userLocale || 'en').slice(0,2) === 'fr' ? 0 : 1;          // the designer's own words: French or English
const li = () => { const l = lang.slice(0,2); return l === 'fr' ? 1 : l === 'it' ? 2 : 0; };   // product words: en, fr, it
const hasText = o => !!o && typeof o === 'object' && Object.values(o).some(Boolean);
const isMissing = (o, lc) => hasText(o) && !o[lc];
function T(o){ if (o == null) return ''; if (typeof o === 'string') return o; return o[lang] || o[DEF] || Object.values(o).find(Boolean) || ''; }
function L(fr, en, it){ const o = {}; LOCALES.forEach(l => { const k = l.slice(0,2); const v = k==='fr'?fr:k==='it'?it:en; if (v) o[l] = v; }); return o; }

/* the designer's own words */
const C = {
 title:['Designer de fiche','Form designer'], help:['Aide','Help'],
 useActive:['Déclarée dans le modèle','Declared in the model'], useRestart:['Déclarée · redémarrage requis','Declared · restart needed'],
 useOff:['Non utilisée par la table','Not used by the table'], useUnknown:['Modèle DMA : déclaration à vérifier','DMA model: check the declaration'],
 useOther:['La table utilise une autre fiche','The table uses another form'],
 activation:['Activation','Activation'], code:['</> Code','</> Code'], deployTitle:['Activation de la fiche','Activating the form'],
 deployActive:['Le modèle déclare cette fiche. Elle s’applique dès que le serveur a redémarré après la déclaration ; ensuite, chaque enregistrement s’affiche à la prochaine ouverture d’une fiche.','The model declares this form. It applies once the server has restarted after the declaration; from then on, every save shows at the next opening of a record.'],
 deployRestart:['La déclaration est écrite dans le modèle, mais EBX ne l’a pas encore relue : redémarrez le serveur une fois.','The declaration is written in the model, but EBX has not read it yet: restart the server once.'],
 deployOff:['La table n’utilise pas encore cette mise en page : les enregistrements ne changent rien à la fiche tant qu’elle n’est pas déclarée.','The table does not use this layout yet: saving changes nothing on the form until it is declared.'],
 deployUnknown:['Pour un modèle DMA, la déclaration se fait et se vérifie dans l’écran du modèle (ou avec set_form_layout) : ouvrez ensuite un enregistrement pour voir la fiche.','For a DMA model, the declaration is made and checked in the model screen (or with set_form_layout): then open a record to see the form.'],
 closeDlg:['Fermer','Close'], codeTitle:['Ce que le designer produit','What the designer produces'], closeInsp:['Fermer','Close'], preview:['Aperçu avec','Preview with'], language:['Langue','Language'], texts:['Textes','Texts'],
 translations:['Traductions','Translations'], todo:['{0} à faire','{0} to do'], complete:['complètes','complete'], mode:['Mode','Mode'], design:['Concevoir','Design'],
 previewMode:['Aperçu','Preview'], width:['Largeur','Width'], undo:['Annuler','Undo'], save:['Enregistrer','Save'], saved:['Enregistré','Saved'],
 dirty:['Modifié, non enregistré','Changed, not saved'], draft:['Brouillon (non enregistré)','Draft (not saved)'], generated:['Mise en page de départ, générée depuis le modèle','Starting layout, built from the model'],
 palette:['Palette','Palette'], fields:['Champs','Fields'], assocs:['Associations','Associations'], blocks:['Blocs','Blocks'], search:['Rechercher un champ, un chemin…','Search a field, a path…'],
 placed:['placé','placed'], noMatch:['Aucun champ ne correspond.','No field matches.'], dropRemove:['Déposer ici pour retirer de la fiche','Drop here to remove from the form'],
 assocHint:['Une association devient un bloc : grille native d’EBX ou cartes.','An association becomes a block: EBX’s native grid, or cards.'],
 components:['Composants enregistrés','Registered components'], noComponent:['Aucun composant enregistré sur cette instance (LayoutComponents.register).','No component registered on this instance (LayoutComponents.register).'],
 selection:['Sélection','Selection'], tabs:['onglets','tabs'], nblocks:['blocs','blocks'], nfields:['champs sur {0} placés','fields of {0} placed'],
 shownIn:['Aperçu en {0}','Preview in {0}'], untrHint:['un texte souligné en pointillé n’a pas de traduction dans cette langue','a dotted underline marks a text with no translation in this language'],
 addTab:['+ Onglet','+ Tab'], addRow1:['+ Ligne à 1 colonne','+ Row with 1 column'], addRow2:['+ Ligne à 2 colonnes','+ Row with 2 columns'],
 dropHere:['Déposez un bloc ou un champ ici','Drop a block or a field here'], dropFields:['Déposez des champs ici','Drop fields here'], newPanel:['Nouveau panneau ici','New panel here'], here:['Ici','Here'], newRow:['Nouvelle ligne','New row'], inPanel:['Dans le panneau','In the panel'],
 row:['Ligne','Row'], col:['colonne','column'], cols:['colonnes','columns'],
 help1:['Cliquez un onglet, une ligne, un bloc ou un champ de la fiche pour le régler ici.','Click a tab, a row, a block or a field of the form to set it here.'],
 help2:['Glissez un champ dans un panneau, ou sur une colonne pour créer un panneau.','Drag a field into a panel, or onto a column to create a panel.'],
 help3:['Glissez une association pour poser sa grille.','Drag an association to place its grid.'],
 help4:['Glissez un bloc entre deux lignes pour ouvrir une nouvelle ligne.','Drag a block between two rows to open a new row.'],
 help5:['Glissez un onglet pour le déplacer ; un bloc posé sur un onglet part en fin de page.','Drag a tab to move it; a block dropped on a tab goes to its end.'],
 helpLoc:['Chaque texte saisi existe dans les langues de l’instance : {0}. Les libellés du modèle et les messages de validation viennent d’EBX, déjà traduits.','Every text typed here exists in the instance locales: {0}. Model labels and validation messages come from EBX, already translated.'],
 helpRules:['Un bandeau, des chiffres clés ou le rapport de validation prennent toute la largeur ; un champ vit dans un panneau ; sous 900 px, deux colonnes passent sur une.','A band, key figures or the validation report take the full width; a field lives in a panel; under 900 px, two columns stack.'],
 tab:['Onglet','Tab'], label:['Libellé','Label'], counter:['Compteur affiché','Counter shown'], counterHint:['Somme des lignes des associations cochées.','Sum of the rows of the ticked associations.'],
 home:['Premier onglet de la fiche.','First tab of the form.'], left:['Vers la gauche','Move left'], right:['Vers la droite','Move right'], delTab:['Supprimer l’onglet','Delete the tab'],
 columns:['Colonnes','Columns'], ratio:['Proportions','Proportions'], rowHint:['Sous 900 px, une ligne à deux colonnes s’affiche sur une seule, colonne de gauche d’abord.','Under 900 px, a two-column row stacks, left column first.'],
 up:['Monter','Up'], down:['Descendre','Down'], dup:['Dupliquer','Duplicate'], del:['Retirer','Remove'], delRow:['Retirer la ligne','Remove the row'],
 titleF:['Titre','Title'], note:['Note sous le titre','Note under the title'], allRo:['Tout le panneau en lecture seule','Whole panel read only'], none:['Aucun','None'],
 heroTitle:['Titre','Title'], logo:['Logo','Logo'], meter:['Jauge','Gauge'], statusPill:['Pastille d’état','Status pill'], sub:['Sous-titre','Subtitle'], chips:['Puces','Chips'], add:['+ Ajouter…','+ Add…'],
 values:['Valeurs','Values'], countRows:['Compter les lignes de','Count the rows of'],
 vInfo:['Source : le rapport de validation de la fiche, tel qu’EBX le calcule. Les messages arrivent dans la langue de l’utilisateur.','Source: the record’s validation report, as EBX computes it. Messages come in the user’s language.'],
 vPreview:['Aperçu : {0} message(s) du rapport de la fiche « {1} ».','Preview: {0} message(s) from the report of record “{1}”.'],
 sevShown:['Gravités affichées','Severities shown'], errors:['Erreurs','Errors'], warnings:['Avertissements','Warnings'], infos:['Informations','Information'],
 group:['Regrouper','Group'], bySev:['Par gravité','By severity'], byTab:['Par onglet','By tab'], noGroup:['Aucun','None'], maxMsg:['Messages avant « autres »','Messages before “more”'], all:['Tous','All'],
 links:['Lien « Aller au champ »','“Go to field” link'], hideEmpty:['Masquer le bloc quand le rapport est vide','Hide the block when the report is empty'],
 actions:['Boutons d’action','Action buttons'], actOn:['Message sur','Message about'], actGo:['Ouvre','Opens'], actBtn:['Libellé du bouton','Button label'], addAct:['+ Bouton d’action','+ Action button'],
 actHint:['Un bouton n’apparaît que si un message porte sur ce champ, ce groupe ou cette association.','A button shows only when a message is about this field, group or association.'],
 assoc:['Association','Association'], presentation:['Présentation','Presentation'], native:['Grille native','Native grid'], cards:['Cartes','Cards'], addBtn:['Bouton d’ajout','Add button'],
 cardFields:['Champs sur les cartes','Fields on the cards'], gridHint:['Sans titre saisi, le libellé de l’association dans le modèle s’affiche.','With no title typed, the association’s model label shows.'],
 lat:['Latitude','Latitude'], lng:['Longitude','Longitude'], tall:['Grande carte','Large map'], mapInfo:['Carte OpenStreetMap dans un cadre de hauteur fixe.','OpenStreetMap map in a fixed-height box.'],
 parent:['Maison mère','Parent'], children:['Filiales','Children'], dupInfo:['Lit l’état Match and Merge de la fiche ; sans l’add-on, le bloc le dit.','Reads the record’s Match and Merge state; without the add-on, the block says so.'],
 text:['Texte','Text'], component:['Composant','Component'], compInfo:['Composant Java enregistré par nom ; le designer lui passe ses paramètres et ses textes traduits.','Java component registered by name; the designer passes it its settings and translated texts.'],
 cond:['Afficher seulement si (XPath)','Show only if (XPath)'], always:['toujours','always'], hideCreate:['Masquer à la création d’une fiche','Hide when creating a record'],
 field:['Champ','Field'], path:['Chemin','Path'], fieldLabel:['Libellé sur cette fiche','Label on this form'], modelLabel:['(modèle)','(model)'], fieldLabelHint:['Vide : le libellé du modèle, dans la langue de l’utilisateur.','Empty: the model label, in the user’s language.'],
 widget:['Widget','Widget'], ro:['Lecture seule','Read only'], wide:['Libellé au-dessus, toute la largeur','Label above, full width'], required:['obligatoire','mandatory'], computed:['calculé','computed'],
 reqInfo:['Champ obligatoire : sans lui sur la fiche, la création d’un enregistrement échoue.','Mandatory field: without it on the form, creating a record fails.'],
 trTitle:['Textes de la fiche','Texts of the form'], trHint:['Langues de l’instance : {0}. Un texte absent dans une langue s’affiche dans la langue par défaut ({1}).','Instance locales: {0}. A text missing in a language shows in the default locale ({1}).'],
 trOnly:['Seulement les textes à traduire','Only texts to translate'], trDone:['Tout est traduit.','Everything is translated.'], trCount:['{0} à traduire','{0} to translate'],
 outTitle:['Ce que le designer produit','What the designer produces'], outJson:['Mise en page (JSON)','Layout (JSON)'], outXsd:['Déclaration dans le modèle','Declaration in the model'], outJava:['Classe Java figée','Frozen Java class'], copy:['Copier','Copy'], copied:['Copié','Copied'],
 jsonHow:['C’est tout ce que « Enregistrer » écrit, dans la table FormLayout du module. La classe ConfigurableForm le lit à chaque affichage d’une fiche : un changement enregistré s’affiche à la prochaine ouverture, sans redémarrage.','This is all “Save” writes, into the module’s FormLayout table. ConfigurableForm reads it at each display of a record: a saved change shows at the next opening, with no restart.'],
 javaHow:['Option : la même mise en page en classe Java ordinaire, pour qui ne veut pas dépendre du designer à l’exécution.','Option: the same layout as a plain Java class, for those who do not want the designer at run time.'],
 declared:['La table déclare la fiche conçue.','The table declares the designed form.'], notDeclared:['La table ne déclare pas encore la fiche conçue : la mise en page enregistrée n’est pas utilisée.','The table does not declare the designed form yet: the saved layout is not used.'],
 currentForm:['Fiche déclarée','Declared form'], file:['Fichier','File'], none2:['aucune (fiche standard)','none (standard form)'],
 activate:['Déclarer la fiche conçue sur cette table','Declare the designed form on this table'], deactivate:['Remettre la fiche précédente','Put the previous form back'],
 restart:['Un redémarrage du serveur est nécessaire une fois, après la déclaration.','A server restart is needed once, after the declaration.'],
 dma:['Modèle DMA : déclarez la classe {0} comme formulaire de la table dans l’écran du modèle, puis republiez.','DMA model: declare class {0} as the table’s form in the model screen, then republish.'],
 noFile:['Le fichier XSD de ce modèle n’a pas été trouvé sur ce serveur ({0}) : déclarez la classe {1} à la main dans l’osd:table.','The XSD file of this model was not found on this server ({0}): declare class {1} by hand in the osd:table.'],
 previousForm:['Fiche précédente','Previous form'],
 stored:['Version enregistrée','Stored version'], by:['par','by'], notStored:['Pas encore enregistrée','Not saved yet'],
 mustSave:['Enregistrez d’abord : la déclaration enregistre aussi la mise en page.','Save first: declaring also saves the layout.'],
 explFull:['« {0} » prend toute la largeur : il a été placé sur sa propre ligne.','“{0}” takes the full width: it was placed on its own row.'],
 explPanel:['Un champ vit dans un panneau : le panneau « {0} » a été créé autour de « {1} ».','A field lives in a panel: panel “{0}” was created around “{1}”.'],
 explTwice:['« {0} » était déjà sur la fiche : il y figure maintenant deux fois.','“{0}” was already on the form: it is now there twice.'],
 explRemoved:['« {0} » retiré de la fiche.','“{0}” removed from the form.'], explReq:['« {0} » est obligatoire : sans lui sur la fiche, la création échouera.','“{0}” is mandatory: without it on the form, creating a record fails.'],
 explMerged:['Les deux colonnes ont été fusionnées.','The two columns were merged.'], explSplit:['Les blocs ont été répartis entre les deux colonnes.','The blocks were split between the two columns.'],
 explFullRow:['Cette ligne contient un bloc pleine largeur : elle reste à une colonne.','This row holds a full-width block: it stays one column.'],
 explUndo:['Dernière modification annulée.','Last change undone.'], explTab:['Onglet ajouté. Saisissez son libellé dans chaque langue.','Tab added. Type its label in each language.'],
 explToTab:['Bloc déplacé à la fin de l’onglet « {0} ».','Block moved to the end of tab “{0}”.'], explHome:['« {0} » est maintenant le premier onglet.','“{0}” is now the first tab.'],
 explBlockDel:['Bloc retiré. « Annuler » le remet.','Block removed. “Undo” puts it back.'], explNoPanel:['Aucun panneau sélectionné : « {0} » est dans un nouveau panneau en bas de l’onglet.','No panel selected: “{0}” is in a new panel at the bottom of the tab.'],
 explGoto:['Message « {0} » : onglet « {1} ».','Message “{0}”: tab “{1}”.'], newTab:['Nouvel onglet','New tab'],
 kPanel:['Panneau','Panel'], kHero:['Bandeau d’identité','Identity band'], kFacts:['Chiffres clés','Key figures'], kValidation:['Rapport de validation','Validation report'], kGrid:['Association','Association'],
 kMap:['Carte','Map'], kTree:['Structure du groupe','Group structure'], kDup:['Doublons','Duplicates'], kNote:['Texte d’aide','Help text'], kCustom:['Composant enregistré','Registered component'],
 dPanel:['Un titre, une note et des lignes de champs natives.','A title, a note and native field rows.'], dHero:['Initiales, titre, puces, jauge.','Initials, title, chips, gauge.'], dFacts:['Une rangée de valeurs courtes.','A row of short values.'],
 dValidation:['Les messages de validation de la fiche, reliés au champ concerné.','The record’s validation messages, linked to their field.'], dMap:['Carte sur deux champs latitude et longitude.','Map on a latitude and a longitude field.'],
 dTree:['Maison mère, la fiche, ses filiales.','Parent, the record, its children.'], dDup:['État Match and Merge : golden record, groupe.','Match and Merge state: golden record, group.'], dNote:['Un paragraphe pour l’utilisateur.','A paragraph for the user.'],
 full:['pleine largeur','full width'], cond2:['si {0}','if {0}'], hiddenCreate:['masqué en création','hidden on creation'], condOn:['conditionnel','conditional'],
 wLine:['Texte','Text'], wMono:['Code (police fixe)','Code (fixed font)'], wLink:['Lien','Link'], wBadge:['Pastille','Pill'], wRo:['Lecture seule','Read only'], wArea:['Zone de texte','Text area'], wLine1:['Texte sur une ligne','Single-line text'],
 wNumber:['Nombre','Number'], wFig:['Chiffre mis en avant','Highlighted figure'], wDate:['Date','Date'], wCheck:['Case à cocher','Check box'], wYesNo:['Oui / Non (radio)','Yes / No (radio)'],
 wSelect:['Liste déroulante','Drop-down list'], wNoCreate:['Liste sans « Créer »','List without “Create”'], wRadio:['Boutons radio','Radio buttons'], wImage:['Image','Image'],
 tStr:['texte','text'], tText:['texte long','long text'], tInt:['entier','integer'], tDec:['décimal','decimal'], tDate:['date','date'], tDt:['date-heure','date-time'], tBool:['oui/non','yes/no'], tFk:['clé étr.','foreign key'], tEnum:['liste','list'], tMedia:['média','media'], tList:['multiple','multiple'],
 ctxTab:['Onglet {0} · libellé','Tab {0} · label'], ctxTitle:['titre','title'], ctxNote:['note','note'], ctxBtn:['bouton','button'], ctxText:['texte','text'], ctxAct:['bouton d’action {0}','action button {0}'], ctxField:['Champ « {0} » · libellé','Field “{0}” · label'],
 noRecord:['Aucun enregistrement dans cette table : l’aperçu est vide.','No record in this table: the preview is empty.'], recordOf:['{0} sur {1}','{0} of {1}'],
 creating:['Nouvelle fiche (création)','New record (creation)']
};
const c = (k, ...a) => { let s = (C[k] || [k,k])[UI]; a.forEach((v,i) => { s = s.split('{'+i+'}').join(v); }); return s; };

/* the form's own words (as the runtime writes them), in the preview language */
const U = {
 completeness:['completeness','complétude','completezza'], noRow:['No rows.','Aucune ligne.','Nessuna riga.'], rows:['rows','lignes','righe'], actions:['Actions ▾','Actions ▾','Azioni ▾'], filter:['Filter','Filtrer','Filtra'],
 noParent:['No parent · head of group','Aucune maison mère · tête de groupe','Nessuna controllante · capogruppo'], mame:['Match and Merge state, read at display','État Match and Merge, lu à l’affichage','Stato Match and Merge, letto alla visualizzazione'],
 noCoords:['No coordinates yet','Pas encore de coordonnées','Nessuna coordinata'], newRecord:['New record','Nouvel enregistrement','Nuovo record'],
 error:['Error','Erreur','Errore'], warning:['Warning','Avertissement','Avviso'], info:['Information','Information','Informazione'],
 error1:['error','erreur','errore'], warning1:['warning','avertissement','avviso'], info1:['information','information','informazione'],
 errorN:['errors','erreurs','errori'], warningN:['warnings','avertissements','avvisi'], infoN:['information','informations','informazioni'],
 goto:['Go to field','Aller au champ','Vai al campo'], gotoRow:['Open the rows','Voir les lignes','Apri le righe'], absent:['Field not on this form','Champ absent de cette fiche','Campo assente da questa scheda'],
 offForm:['Not on this form','Hors de la fiche','Fuori dalla scheda'], noIssue:['The validation report shows nothing.','Le rapport de validation ne signale rien.','Il rapporto di convalida non segnala nulla.'], more:['{n} more','{n} autres','altri {n}'],
 manageRows:['Manage the rows','Gérer les lignes','Gestisci le righe'], component:['Component {n}, drawn by its Java class','Composant {n}, dessiné par sa classe Java','Componente {n}, disegnato dalla sua classe Java']
};
const u = (k, n) => { const s = (U[k] || [k])[li()] || U[k][0]; return n != null ? s.replace('{n}', n) : s; };

/* ======================= model ======================= */
const F = {}; B.fields.forEach(f => F[f.p] = f);
const G = {}; B.groups.forEach(g => G[g.p] = g);
const A = {}; B.assocs.forEach(a => A[a.p] = a);
const V = B.values || {};
const REPORT = B.report || [];
const SEVS = ['error','warning','info'];
const lab = p => F[p] ? T(F[p].lab) : A[p] ? T(A[p].lab) : G[p] ? T(G[p].lab) : p;
const glab = g => g && G[g] ? T(G[g].lab) : T(B.model.tableLabel);
const COMPS = B.components || [];

const BLOCKS = {
 panel:{l:'kPanel', d:'dPanel'}, hero:{l:'kHero', d:'dHero', full:1}, facts:{l:'kFacts', d:'dFacts', full:1}, validation:{l:'kValidation', d:'dValidation', full:1},
 grid:{l:'kGrid', d:''}, map:{l:'kMap', d:'dMap'}, tree:{l:'kTree', d:'dTree'}, duplicates:{l:'kDup', d:'dDup'}, note:{l:'kNote', d:'dNote'}, custom:{l:'kCustom', d:''}
};
const bl = t => c(BLOCKS[t].l);

/* ======================= state ======================= */
let seq = 0; const nid = p => p + Date.now().toString(36) + (++seq);
function fromLayout(Lt){
  const S = {tabs:[], blocks:{}};
  ((Lt && Lt.tabs) || []).forEach(t => {
    const tab = {id:nid('t'), label:t.label || {}, count:(t.count || []).slice(), rows:[]};
    (t.rows || []).forEach(r => {
      const row = {id:nid('r'), cols:r.columns === 2 ? 2 : 1, ratio:r.ratio || '11', c:[]};
      (r.blocks || []).forEach(col => { const ids = []; (col || []).forEach(b => { const id = b.id && !S.blocks[b.id] ? b.id : nid('b'); S.blocks[id] = Object.assign({}, b, {id}); ids.push(id); }); row.c.push(ids); });
      while (row.c.length < row.cols) row.c.push([]);
      if (row.c.length > row.cols) row.c = [row.c.flat()];
      tab.rows.push(row);
    });
    S.tabs.push(tab);
  });
  if (!S.tabs.length) S.tabs.push({id:nid('t'), label:L('Général','General','Generale'), count:[], rows:[]});
  return S;
}
function toLayout(){
  return {format:'ebx-form-layout/1', model:B.model.location, table:B.model.table, locales:LOCALES,
    tabs:S.tabs.map(t => ({label:t.label, count:t.count, rows:t.rows.map(r => ({columns:r.cols, ratio:r.cols===2?(r.ratio||'11'):undefined, blocks:r.c.map(col => col.map(id => S.blocks[id]).filter(Boolean))}))}))};
}
let S = fromLayout(B.layout);
let cur = S.tabs[0].id, sel = null, mode = 'design', narrow = false, ptab = 'fields', otab = 'json', creating = false, onlyMissing = true;
let explain = '', flash = null, history = [], dirty = !!B.draft;

const $ = s => root.querySelector(s);
const esc = s => String(s==null?'':s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const tabOf = id => S.tabs.find(t => t.id === id);
const curTab = () => tabOf(cur) || S.tabs[0];
function tx(o){ const s = esc(T(o)); if (mode === 'design' && isMissing(o, lang)) { const from = o[DEF] ? DEF : Object.keys(o).find(k => o[k]); return `<span class="untr" title="${esc(lang)} → ${esc(from)}">${s}</span>`; } return s; }
function snapshot(){ history.push(JSON.stringify({S, cur})); if (history.length > 60) history.shift(); dirty = true; }
function commit(msg){ explain = msg || ''; renderAll(); }
function locate(bid){ for (const t of S.tabs) for (let ri = 0; ri < t.rows.length; ri++){ const r = t.rows[ri]; for (let ci = 0; ci < r.c.length; ci++){ const k = r.c[ci].indexOf(bid); if (k >= 0) return {t, r, ri, ci, k}; } } return null; }
function orderedBlocks(){ const out = []; S.tabs.forEach(t => t.rows.forEach(r => r.c.forEach(col => col.forEach(id => S.blocks[id] && out.push({t, b:S.blocks[id]}))))); return out; }
function placedFields(){ const s = new Set(); orderedBlocks().forEach(({b}) => { if (b.type === 'panel') (b.fields || []).forEach(f => s.add(f.p)); }); return s; }
function placedAssoc(){ const s = new Set(); orderedBlocks().forEach(({b}) => { if (b.type === 'grid') s.add(b.a); }); return s; }
function cleanup(){ S.tabs.forEach(t => { t.rows = t.rows.filter(r => r.c.some(col => col.length)); }); Object.keys(S.blocks).forEach(id => { if (!locate(id)) delete S.blocks[id]; }); }
function tabCount(t){ if (!t.count || !t.count.length) return null; let n = 0; for (const p of t.count){ if (!A[p]) return null; n += A[p].n || 0; } return n; }
function whereIs(node){
  if (!node) return null;
  const blocks = orderedBlocks();
  let p = node;
  while (p){
    for (const {t, b} of blocks){
      if (b.type === 'panel'){ const i = (b.fields || []).findIndex(f => f.p === p); if (i >= 0) return {t, b, i}; }
      if (b.type === 'grid' && b.a === p) return {t, b};
      if ((b.covers || []).includes(p)) return {t, b};
    }
    const k = p.lastIndexOf('/'); p = k > 0 ? p.slice(0, k) : null;
  }
  for (const {t, b} of blocks){ if (b.type === 'panel'){ const i = (b.fields || []).findIndex(f => f.p.startsWith(node + '/')); if (i >= 0) return {t, b, i}; } }
  return null;
}
const blockName = b => hasText(b.title) ? T(b.title) : b.type === 'grid' ? lab(b.a) : bl(b.type);

function allTexts(){
  const out = [];
  S.tabs.forEach((t, ti) => {
    out.push({ctx:c('ctxTab', ti+1), o:t, p:'label', go:{k:'tab', id:t.id}});
    t.rows.forEach(r => r.c.forEach(col => col.forEach(id => { const b = S.blocks[id]; if (!b) return; const nm = `${bl(b.type)} « ${blockName(b)} »`;
      ['title','note','addBtn','text'].forEach(p => { if (hasText(b[p])) out.push({ctx:`${nm} · ${c({title:'ctxTitle',note:'ctxNote',addBtn:'ctxBtn',text:'ctxText'}[p])}`, o:b, p, go:{k:'block', id}}); });
      (b.actions || []).forEach((a, i) => { if (hasText(a.btn)) out.push({ctx:`${nm} · ${c('ctxAct', i+1)}`, o:a, p:'btn', go:{k:'block', id}}); });
      (b.fields || []).forEach((f, i) => { if (hasText(f.label)) out.push({ctx:c('ctxField', lab(f.p)), o:f, p:'label', go:{k:'field', b:id, i}}); });
    })));
  });
  return out;
}
function missingByLocale(){ const m = {}; LOCALES.forEach(l => m[l] = 0); allTexts().forEach(x => LOCALES.forEach(l => { if (isMissing(x.o[x.p], l)) m[l]++; })); return m; }

/* ======================= the form preview ======================= */
const val = p => creating ? '' : (V[p] == null ? '' : V[p]);
function widgetsFor(t){
  return ({str:[['line','wLine'],['mono','wMono'],['link','wLink'],['badge','wBadge'],['ro','wRo']], text:[['area','wArea'],['line','wLine1'],['ro','wRo']],
    int:[['line','wNumber'],['fig','wFig'],['ro','wRo']], dec:[['line','wNumber'],['fig','wFig'],['ro','wRo']], date:[['line','wDate'],['ro','wRo']], dt:[['line','wDate'],['ro','wRo']],
    bool:[['check','wCheck'],['yesno','wYesNo'],['badge','wBadge'],['ro','wRo']], fk:[['select','wSelect'],['nocreate','wNoCreate'],['ro','wRo']],
    enum:[['select','wSelect'],['radio','wRadio'],['badge','wBadge'],['ro','wRo']], media:[['image','wImage'],['ro','wRo']], list:[['ro','wRo']]})[t] || [['line','wLine'],['ro','wRo']];
}
const defaultWidget = t => widgetsFor(t)[0][0];
function renderWidget(f, blk){
  const fd = F[f.p] || {t:'str'}; const w = (f.ro || blk.ro || fd.fn) && (f.w == null || ['line','select','nocreate','area'].includes(f.w)) ? 'ro' : (f.w || defaultWidget(fd.t));
  const v = val(f.p);
  if (w === 'ro') return v ? `<div class="w-ro">${esc(v)}</div>` : `<div class="w-ro empty">—</div>`;
  if (w === 'mono') return f.ro || blk.ro ? `<div class="w-ro mono">${esc(v) || '—'}</div>` : `<div class="w-in mono"><span>${esc(v)}</span></div>`;
  if (w === 'link') return v ? `<a class="w-link" href="#" data-noop="1">${esc(v)}</a>` : `<div class="w-ro empty">—</div>`;
  if (w === 'badge') return v ? `<span class="w-badge">${esc(v)}</span>` : `<div class="w-ro empty">—</div>`;
  if (w === 'fig') return `<div class="w-fig">${esc(v) || '—'}</div>`;
  if (w === 'area') return `<div class="w-in area"><span>${esc(v)}</span></div>`;
  if (w === 'check') return `<span class="w-check ${v && v !== 'false' && !/^(non|no|false)$/i.test(v) ? 'on' : ''}"></span>`;
  if (w === 'yesno' || w === 'radio') return `<div class="w-radio"><span class="on">${esc(v || '—')}</span><span>…</span></div>`;
  if (w === 'image') return `<div class="w-img">${esc((v || '+').slice(0,3))}</div>`;
  if (w === 'select') return `<div class="w-in sel ${v ? '' : 'ph'}"><span>${esc(v) || '—'}</span>${fd.t === 'fk' ? '<span class="new">+</span>' : ''}</div>`;
  if (w === 'nocreate') return `<div class="w-in sel ${v ? '' : 'ph'}"><span>${esc(v) || '—'}</span></div>`;
  return `<div class="w-in"><span>${esc(v)}</span></div>`;
}
function head(b, extra){
  const n = b.type === 'grid' && A[b.a] ? A[b.a].n : null;
  const title = hasText(b.title) ? tx(b.title) : (b.type === 'grid' ? esc(lab(b.a)) : '');
  const note = hasText(b.note) ? `<p>${tx(b.note)}</p>` : '';
  if (!title && !note && !extra) return '';
  return `<header><div><h4>${title}${n != null && title && !creating ? ` <span class="n">· ${n}</span>` : ''}</h4>${note}</div>${extra || ''}</header>`;
}
const sevIcon = s => `<span class="sv sv-${s}" role="img" aria-label="${esc(u(s))}">${s === 'info' ? 'i' : '!'}</span>`;
function renderValidation(b){
  const sev = b.sev || {error:true, warning:true, info:false};
  const all = (creating ? [] : REPORT).filter(m => sev[m.sev]).sort((x,y) => SEVS.indexOf(x.sev) - SEVS.indexOf(y.sev));
  if (!all.length && b.hideEmpty && mode === 'preview') return '';
  const counts = SEVS.filter(s => sev[s]).map(s => { const n = all.filter(m => m.sev === s).length; return n ? `<span class="vc vc-${s}">${n} ${esc(u(n === 1 ? s+'1' : s+'N'))}</span>` : ''; }).join('');
  const max = b.max == null ? 5 : b.max;
  const shown = max ? all.slice(0, max) : all;
  const item = m => {
    const at = whereIs(m.node);
    const acts = (b.actions || []).filter(a => a.on && m.node && (m.node === a.on || m.node.startsWith(a.on + '/')) && hasText(a.btn));
    const where = at ? `${tx(at.t.label)} › ${esc(blockName(at.b))}${at.i != null ? ' › ' + esc(hasText(at.b.fields[at.i].label) ? T(at.b.fields[at.i].label) : lab(at.b.fields[at.i].p)) : ''}`
      : m.node ? `<span class="absent">${esc(lab(m.node))} · ${esc(u('absent'))}</span>` : '';
    return `<div class="vi vi-${m.sev}">${sevIcon(m.sev)}<div class="vt"><b>${esc(T(m.t))}</b><span class="where">${where}</span></div><div class="va">${acts.map(a => `<span class="pbtn">${tx(a.btn)}</span>`).join('')}${b.links !== false && at ? `<button type="button" class="golink" data-goto="${esc(m.node)}">${esc(u(at.i != null ? 'goto' : 'gotoRow'))}</button>` : ''}</div></div>`;
  };
  let body;
  if (!all.length) body = `<div class="vnone">${esc(u('noIssue'))}</div>`;
  else if (b.group === 'tab'){
    const groups = new Map();
    shown.forEach(m => { const at = whereIs(m.node); const key = at ? at.t.id : '_'; if (!groups.has(key)) groups.set(key, {name: at ? tx(at.t.label) : esc(u('offForm')), list:[]}); groups.get(key).list.push(m); });
    body = [...groups.values()].map(g => `<div class="vg"><span class="vgh">${g.name} · ${g.list.length}</span>${g.list.map(item).join('')}</div>`).join('');
  } else if (b.group === 'none') body = shown.map(item).join('');
  else body = SEVS.map(s => { const l = shown.filter(m => m.sev === s); return l.length ? `<div class="vg"><span class="vgh">${esc(u(s))} · ${l.length}</span>${l.map(item).join('')}</div>` : ''; }).join('');
  if (all.length > shown.length) body += `<div class="more">${esc(u('more', all.length - shown.length))}</div>`;
  return `<div class="vrep"><div class="vhd"><h4>${tx(b.title)}${all.length ? ` · ${all.length}` : ''}</h4><div class="vcs">${counts}</div></div>${body}</div>`;
}
function renderBlock(b){
  const tag = `<div class="btag"><span draggable="true" data-drag="block" data-id="${b.id}">${esc(bl(b.type))}${b.type === 'custom' ? ' · ' + esc(b.cls) : ''}</span>${b.cond ? `<span class="cond" title="${esc(b.cond)}">${esc(c('cond2', b.cond))}</span>` : ''}${b.hideCreate ? `<span class="cond">${esc(c('hiddenCreate'))}</span>` : ''}</div>`;
  const selc = sel && sel.k === 'block' && sel.id === b.id ? ' sel' : '';
  if (creating && b.hideCreate && mode === 'preview') return '';
  const flashc = flash && flash.b === b.id && flash.i == null ? ' flash' : '';
  let inner = '';
  switch (b.type){
    case 'panel': {
      const rows = (b.fields || []).map((f, i) => {
        const fd = F[f.p]; if (!fd) return '';
        const s = (sel && sel.k === 'field' && sel.b === b.id && sel.i === i ? ' sel' : '') + (flash && flash.b === b.id && flash.i === i ? ' flash' : '');
        const label = hasText(f.label) ? tx(f.label) : esc(lab(f.p));
        return `<div class="fr${f.wide ? ' wide' : ''}${s}" data-item="field" data-b="${b.id}" data-i="${i}"><div class="fl" draggable="true" data-drag="field" data-b="${b.id}" data-i="${i}">${label}${fd.req ? ' <i>*</i>' : ''}</div><div class="fv" draggable="true" data-drag="field" data-b="${b.id}" data-i="${i}">${renderWidget(f, b)}</div></div>`;
      }).join('');
      inner = `<div class="panel">${head(b)}<div class="pf" data-drop="fields" data-b="${b.id}">${rows || (mode === 'design' ? `<div class="pf-empty">${esc(c('dropFields'))}</div>` : '')}</div></div>`; break; }
    case 'hero': {
      const t = creating ? u('newRecord') : val(b.title);
      const sub = creating ? '' : (b.sub || []).map(val).filter(Boolean).join(' · ');
      const chips = creating ? '' : (b.chips || []).map(val).filter(Boolean).map(v => `<span class="w-badge">${esc(v)}</span>`).join('');
      const m = creating ? 0 : Math.max(0, Math.min(100, Math.round(V[b.meter + '#n'] || +val(b.meter) || 0)));
      const st = !creating && b.status ? val(b.status) : '';
      inner = `<div class="hero"><div class="hero-l"><div class="logo">${esc((t || '?').slice(0,3))}</div><div style="min-width:0"><div class="ey">${esc(T(B.model.tableLabel))}${creating || !B.record.key ? '' : ' · #' + esc(B.record.key)}</div><h1>${esc(t)}</h1><div class="sub">${esc(sub)}</div><div class="chips">${chips}</div></div></div>
        <div class="hero-r">${b.meter ? `<span class="meter" style="--v:${m}%"><i></i>${m} % <small>${esc(u('completeness'))}</small></span>` : ''}${st ? `<span class="w-badge warn">${esc(lab(b.status))} : ${esc(st)}</span>` : ''}</div></div>`; break; }
    case 'facts': {
      const items = (b.items || []).map(p => `<div class="fact"><b>${esc(val(p) || '—')}</b><span>${esc(lab(p))}</span></div>`);
      if (b.count && A[b.count]) items.push(`<div class="fact"><b>${creating ? '—' : A[b.count].n}</b><span>${esc(lab(b.count))}</span></div>`);
      inner = `<div class="facts">${items.join('')}</div>`; break; }
    case 'validation': inner = renderValidation(b); if (!inner) return ''; break;
    case 'grid': {
      const a = A[b.a];
      if (!a){ inner = `<div class="panel">${head(b)}<div class="note-b">${esc(b.a)}</div></div>`; break; }
      const n = creating ? 0 : a.n || 0;
      let content;
      if (b.style === 'cards'){
        content = !n ? `<div class="note-b">${esc(u('noRow'))}</div>` : `<div class="cards">${(a.rows || []).map(r => `<div class="cardx"><div class="t"><b>${esc(r)}</b></div>${(b.cardFields || []).length ? `<div class="s">${esc((b.cardFields || []).map(p => { const tf = (a.fields || []).find(x => x.p === p); return tf ? T(tf.lab) : p; }).join(' · '))}</div>` : ''}</div>`).join('')}</div>${n > (a.rows || []).length ? `<div class="more">${esc(u('more', n - a.rows.length))}</div>` : ''}<div class="more">▸ ${hasText(b.addBtn) ? tx(b.addBtn) : esc(u('manageRows'))}</div>`;
      } else {
        content = `<div class="grid-tb"><span class="plus">+</span><span>${esc(u('actions'))}</span><span>${esc(u('filter'))}</span><span>${n} ${esc(u('rows'))}</span></div><div class="gtab"><table><tbody>${(creating ? [] : a.rows || []).map(r => `<tr><td>${esc(r)}</td></tr>`).join('')}</tbody></table></div>${!n ? `<div class="more">${esc(u('noRow'))}</div>` : n > (a.rows || []).length ? `<div class="more">${esc(u('more', n - a.rows.length))}</div>` : ''}`;
      }
      inner = `<div class="panel${flashc}">${head(b)}${content}</div>`; break; }
    case 'map': inner = `<div class="panel">${head(b)}<div class="map ${b.tall ? 'tall' : ''}"><span class="pin"></span><small>${creating || !val(b.lat) ? esc(u('noCoords')) : esc(val(b.lat) + ', ' + val(b.lng))}</small></div></div>`; break;
    case 'tree': { const kids = A[b.children];
      inner = `<div class="panel${flashc}">${head(b)}<div class="tree"><span class="top">${val(b.parent) ? esc(val(b.parent)) : esc(u('noParent'))}</span><span class="me">${esc(B.record.label || '')}</span>${(creating || !kids ? [] : kids.rows || []).map(r => `<a href="#" data-noop="1">${esc(r)}</a>`).join('')}</div></div>`; break; }
    case 'duplicates': inner = `<div class="panel">${head(b)}<div class="dup"><span class="w-badge ok">Golden</span><span class="w-badge">${esc(u('mame'))}</span></div></div>`; break;
    case 'note': inner = `<div class="note-b">${tx(b.text)}</div>`; break;
    case 'custom': inner = `<div class="panel${flashc}">${head(b)}<div class="note-b">${esc(u('component', b.cls))}</div></div>`; break;
  }
  return `<div class="blk${selc}" data-item="block" data-id="${b.id}" data-type="${b.type}">${tag}${inner}</div>`;
}
function renderCanvas(){
  const t = curTab();
  const tabs = S.tabs.map(x => { const n = creating ? null : tabCount(x); return `<button type="button" class="tab${sel && sel.k === 'tab' && sel.id === x.id ? ' sel' : ''}" role="tab" aria-selected="${x.id === cur}" data-tab="${x.id}" ${mode === 'design' ? 'draggable="true" data-drag="tab"' : ''}>${tx(x.label)}${n ? ` <span class="n">${n}</span>` : ''}</button>`; }).join('')
    + (mode === 'design' ? `<button type="button" class="tab add" id="ld-addtab">${esc(c('addTab'))}</button>` : '');
  let rows = '';
  t.rows.forEach((r, ri) => {
    rows += `<div class="rowgap" data-drop="rowgap" data-ri="${ri}"></div>`;
    const cls = r.cols === 2 ? `c2 ${r.ratio === '21' ? 'r21' : r.ratio === '12' ? 'r12' : ''}` : '';
    rows += `<div class="frow ${cls}${sel && sel.k === 'row' && sel.id === r.id ? ' sel' : ''}" data-row="${r.id}"><div class="rowtag"><button type="button" data-rowsel="${r.id}">${esc(c('row'))} · ${r.cols} ${esc(c(r.cols > 1 ? 'cols' : 'col'))}</button></div>`
      + r.c.map((col, ci) => `<div class="col${col.length ? '' : ' isempty'}" data-drop="col" data-ri="${ri}" data-ci="${ci}" data-empty="${esc(c('dropHere'))}">${col.map(id => S.blocks[id] ? renderBlock(S.blocks[id]) : '').join('')}</div>`).join('') + `</div>`;
  });
  rows += `<div class="rowgap" data-drop="rowgap" data-ri="${t.rows.length}"></div>`;
  if (!t.rows.length && mode === 'design') rows += `<div class="frow"><div class="col isempty" data-drop="col" data-ri="0" data-ci="0" data-new="1" data-empty="${esc(c('dropHere'))}"></div></div>`;
  rows += `<div class="addrow"><button type="button" data-addrow="1">${esc(c('addRow1'))}</button><button type="button" data-addrow="2">${esc(c('addRow2'))}</button></div>`;
  const cv = $('#ld-canvas');
  cv.className = `canvas ${mode === 'design' ? 'design' : ''} ${narrow ? 'narrow' : ''}`;
  cv.setAttribute('lang', lang);
  cv.innerHTML = `<div class="ws"><b>${esc(creating ? u('newRecord') : (B.record.label || ''))}</b><span>${esc(B.model.dataset)} › ${esc(T(B.model.tableLabel))}</span></div>
    <nav class="tabs" role="tablist">${tabs}</nav><div class="body">${rows}</div>`;
  $('#ld-cbar').innerHTML = `<span><b>${S.tabs.length}</b> ${esc(c('tabs'))} · <b>${Object.keys(S.blocks).length}</b> ${esc(c('nblocks'))} · <b>${placedFields().size}</b> ${esc(c('nfields', B.fields.length))}</span><span>${esc(c('shownIn', lang))}${mode === 'design' ? ' · ' + esc(c('untrHint')) : ''}</span>`;
}

/* ======================= palette ======================= */
const TY = {str:'tStr', text:'tText', int:'tInt', dec:'tDec', date:'tDate', dt:'tDt', bool:'tBool', fk:'tFk', enum:'tEnum', media:'tMedia', list:'tList'};
function renderPalette(){
  ['fields','assoc','blocks'].forEach(k => $('#ld-pt-' + k).setAttribute('aria-selected', ptab === k));
  const q = $('#ld-psearch').value.trim().toLowerCase();
  const m = s => !q || s.toLowerCase().includes(q);
  let h = '';
  if (ptab === 'fields'){
    const placed = placedFields(); let g = undefined, any = false;
    B.fields.forEach(f => { const l = lab(f.p); if (!m(l + ' ' + f.p + ' ' + glab(f.g))) return; any = true;
      if (f.g !== g){ if (g !== undefined) h += '</div>'; h += `<div class="pgroup"><span>${esc(glab(f.g))}</span>`; g = f.g; }
      const on = placed.has(f.p);
      h += `<button type="button" class="pitem${on ? ' placed' : ''}" draggable="true" data-drag="newfield" data-p="${esc(f.p)}"><span class="tx"><b>${esc(l)}</b><small>${esc(f.p)}</small></span><span class="ty">${esc(on ? c('placed') : c(TY[f.t] || 'tStr'))}</span></button>`; });
    if (g !== undefined) h += '</div>';
    if (!any) h += `<p class="hint">${esc(c('noMatch'))}</p>`;
  } else if (ptab === 'assoc'){
    const pa = placedAssoc();
    B.assocs.filter(a => m(lab(a.p) + ' ' + a.p)).forEach(a => {
      h += `<button type="button" class="pitem${pa.has(a.p) ? ' placed' : ''}" draggable="true" data-drag="newblock" data-type="grid" data-a="${esc(a.p)}"><span class="tx"><b>${esc(lab(a.p))}</b><small>${esc(a.p)}</small></span><span class="ty">${esc(pa.has(a.p) ? c('placed') : String(a.n))}</span></button>`; });
    h += `<p class="hint">${esc(c('assocHint'))}</p>`;
  } else {
    Object.keys(BLOCKS).filter(k => k !== 'grid' && k !== 'custom' && m(bl(k) + ' ' + c(BLOCKS[k].d))).forEach(k => {
      h += `<button type="button" class="pitem" draggable="true" data-drag="newblock" data-type="${k}"><span class="tx"><b>${esc(bl(k))}</b><small class="plain">${esc(c(BLOCKS[k].d))}</small></span>${BLOCKS[k].full ? `<span class="ty">${esc(c('full'))}</span>` : ''}</button>`; });
    h += `<div class="pgroup"><span>${esc(c('components'))}</span>`;
    COMPS.forEach(cp => { h += `<button type="button" class="pitem" draggable="true" data-drag="newblock" data-type="custom" data-cls="${esc(cp.name)}"><span class="tx"><b>${esc(T(cp.lab) || cp.name)}</b><small>${esc(cp.name)}</small></span></button>`; });
    if (!COMPS.length) h += `<p class="hint">${esc(c('noComponent'))}</p>`;
    h += '</div>';
  }
  h += `<div class="pdrop on" data-drop="palette">${esc(c('dropRemove'))}</div>`;
  $('#ld-palette').innerHTML = h;
}

/* ======================= inspector ======================= */
const opt = (v, cv, l) => `<option value="${esc(v)}"${v === cv ? ' selected' : ''}>${esc(l)}</option>`;
const fieldOpts = (cv, filter) => `<option value="">—</option>` + B.fields.filter(f => !filter || filter(f)).map(f => opt(f.p, cv, lab(f.p) + '  ' + f.p)).join('');
const assocOpts = cv => B.assocs.map(a => opt(a.p, cv, lab(a.p))).join('');
const moveBtns = () => `<div class="row-btns"><button type="button" class="btn small ghost" data-act="up">${esc(c('up'))}</button><button type="button" class="btn small ghost" data-act="down">${esc(c('down'))}</button><button type="button" class="btn small ghost" data-act="dup">${esc(c('dup'))}</button><button type="button" class="btn small ghost" data-act="del">${esc(c('del'))}</button></div>`;
function locField(key, label, obj, ph, multi){
  const any = hasText(obj);
  return `<div class="fld">${label ? `<span class="l">${esc(label)}</span>` : ''}<div class="loc">${LOCALES.map(lc => {
    const v = obj && obj[lc] || ''; const p = ph ? ph(lc) : '';
    const input = multi ? `<textarea data-lt="${key}" data-lc="${lc}" rows="2" aria-label="${esc(label)} (${lc})" placeholder="${esc(p)}">${esc(v)}</textarea>` : `<input type="text" data-lt="${key}" data-lc="${lc}" value="${esc(v)}" aria-label="${esc(label)} (${lc})" placeholder="${esc(p)}">`;
    return `<label class="lrow${lc === lang ? ' cur' : ''}${any && !v ? ' miss' : ''}"><span class="lc">${lc}</span>${input}</label>`; }).join('')}</div></div>`;
}
function locRef(key){
  const [kind, a] = key.split('.');
  if (kind === 'tab') return {o:tabOf(sel.id), p:'label'};
  if (kind === 'block') return {o:S.blocks[sel.id], p:a};
  if (kind === 'field') return {o:S.blocks[sel.b].fields[sel.i], p:'label'};
  if (kind === 'action') return {o:S.blocks[sel.id].actions[+a], p:'btn'};
  if (kind === 'tr'){ const x = allTexts()[+a]; return {o:x.o, p:x.p}; }
}
function pathList(key, arr){
  return `<div class="list-mini">${(arr || []).map((p, i) => `<label style="justify-content:space-between"><span>${esc(lab(p))}</span><button type="button" class="btn small ghost" data-pdel="${key}:${i}" style="height:22px">${esc(c('del'))}</button></label>`).join('')}<select data-padd="${key}" aria-label="${esc(c('add'))}">${fieldOpts('', null).replace('<option value="">—</option>', `<option value="">${esc(c('add'))}</option>`)}</select></div>`;
}
function locRow(rid){ for (const t of S.tabs){ const ri = t.rows.findIndex(r => r.id === rid); if (ri >= 0) return {t, r:t.rows[ri], ri}; } return null; }
function renderInspector(){
  let h = explain ? `<div class="explain" role="status">${explain}</div>` : '';
  if (!sel){
    h += `<div class="empty-insp"><p style="margin:0">${esc(c('help1'))}</p><ol><li>${esc(c('help2'))}</li><li>${esc(c('help3'))}</li><li>${esc(c('help4'))}</li><li>${esc(c('help5'))}</li></ol>
      <div class="info">${esc(c('helpLoc', LOCALES.join(', ')))}</div><div class="info">${esc(c('helpRules'))}</div></div>`;
  } else if (sel.k === 'deploy'){
    h += deployHtml();
  } else if (sel.k === 'i18n'){
    const texts = allTexts(); const miss = missingByLocale();
    h += `<div class="insp"><span class="kind">${esc(c('translations'))}</span><h3>${esc(c('trTitle'))}</h3><p class="hint">${esc(c('trHint', LOCALES.join(', '), DEF))}</p>
      <div class="lsum">${LOCALES.map(l => `<span class="${miss[l] ? 'todo' : 'done'}">${l} · ${esc(miss[l] ? c('trCount', miss[l]) : c('complete'))}</span>`).join('')}</div>
      <label class="chk"><input type="checkbox" id="ld-i-miss"${onlyMissing ? ' checked' : ''}> ${esc(c('trOnly'))}</label>
      <div class="trs">${texts.map((x, i) => (onlyMissing && !LOCALES.some(l => isMissing(x.o[x.p], l))) ? '' : `<div class="trx"><button type="button" class="trctx" data-trgo="${i}">${esc(x.ctx)}</button>${locField('tr.' + i, '', x.o[x.p])}</div>`).join('') || `<p class="hint">${esc(c('trDone'))}</p>`}</div></div>`;
  } else if (sel.k === 'tab'){
    const t = tabOf(sel.id);
    h += `<div class="insp"><span class="kind">${esc(c('tab'))}</span><h3>${esc(T(t.label))}</h3>${locField('tab.label', c('label'), t.label)}
      <div class="fld"><span class="l">${esc(c('counter'))}</span><div class="list-mini">${B.assocs.map(a => `<label><input type="checkbox" data-count="${esc(a.p)}"${t.count.includes(a.p) ? ' checked' : ''}> ${esc(lab(a.p))} <span style="color:var(--muted)">(${a.n})</span></label>`).join('')}</div><p class="hint">${esc(c('counterHint'))}</p></div>
      ${S.tabs[0] === t ? `<div class="info">${esc(c('home'))}</div>` : ''}
      <div class="row-btns"><button type="button" class="btn small ghost" data-act="tleft">${esc(c('left'))}</button><button type="button" class="btn small ghost" data-act="tright">${esc(c('right'))}</button><button type="button" class="btn small ghost" data-act="tdel"${S.tabs.length < 2 ? ' disabled' : ''}>${esc(c('delTab'))}</button></div></div>`;
  } else if (sel.k === 'row'){
    const Lr = locRow(sel.id); if (!Lr){ sel = null; return renderInspector(); }
    const r = Lr.r;
    h += `<div class="insp"><span class="kind">${esc(c('row'))}</span><h3>${esc(c('row'))} ${Lr.ri + 1} · ${esc(T(Lr.t.label))}</h3>
      <div class="fld"><span class="l">${esc(c('columns'))}</span><div class="seg"><button type="button" data-cols="1" aria-pressed="${r.cols === 1}">1</button><button type="button" data-cols="2" aria-pressed="${r.cols === 2}">2</button></div></div>
      ${r.cols === 2 ? `<div class="fld"><span class="l">${esc(c('ratio'))}</span><div class="seg"><button type="button" data-ratio="11" aria-pressed="${(r.ratio || '11') === '11'}">1 : 1</button><button type="button" data-ratio="21" aria-pressed="${r.ratio === '21'}">2 : 1</button><button type="button" data-ratio="12" aria-pressed="${r.ratio === '12'}">1 : 2</button></div></div>` : ''}
      <p class="hint">${esc(c('rowHint'))}</p><div class="row-btns"><button type="button" class="btn small ghost" data-act="rup">${esc(c('up'))}</button><button type="button" class="btn small ghost" data-act="rdown">${esc(c('down'))}</button><button type="button" class="btn small ghost" data-act="rdel">${esc(c('delRow'))}</button></div></div>`;
  } else if (sel.k === 'block'){
    const b = S.blocks[sel.id]; if (!b){ sel = null; return renderInspector(); }
    h += `<div class="insp"><span class="kind">${esc(bl(b.type))}${b.type === 'custom' ? ' · ' + esc(b.cls) : ''}</span>`;
    if (['panel','grid','map','tree','duplicates','custom','validation'].includes(b.type)) h += locField('block.title', c('titleF'), b.title, b.type === 'grid' && A[b.a] ? (lc => A[b.a].lab[lc] || '') : null);
    if (['panel','grid','map','tree','duplicates','custom'].includes(b.type)) h += locField('block.note', c('note'), b.note);
    if (b.type === 'panel') h += `<label class="chk"><input type="checkbox" id="ld-i-ro"${b.ro ? ' checked' : ''}> ${esc(c('allRo'))}</label><div class="fld"><span class="l">${esc(c('fields'))} (${(b.fields || []).length})</span><div class="list-mini">${(b.fields || []).map((f, i) => `<label><button type="button" class="btn small ghost" data-fsel="${i}" style="height:24px">${esc(hasText(f.label) ? T(f.label) : lab(f.p))}</button></label>`).join('')}</div></div>`;
    if (b.type === 'hero') h += `<div class="fld"><label for="ld-i-ht">${esc(c('heroTitle'))}</label><select id="ld-i-ht" data-bk="title">${fieldOpts(b.title, f => f.t === 'str' || f.t === 'fk')}</select></div>
      <div class="fld"><label for="ld-i-hm">${esc(c('meter'))}</label><select id="ld-i-hm" data-bk="meter">${fieldOpts(b.meter, f => f.t === 'int' || f.t === 'dec')}</select></div>
      <div class="fld"><label for="ld-i-hs">${esc(c('statusPill'))}</label><select id="ld-i-hs" data-bk="status">${fieldOpts(b.status, f => f.t === 'enum' || f.t === 'fk' || f.t === 'str')}</select></div>
      <div class="fld"><span class="l">${esc(c('sub'))}</span>${pathList('sub', b.sub)}</div><div class="fld"><span class="l">${esc(c('chips'))}</span>${pathList('chips', b.chips)}</div>`;
    if (b.type === 'facts') h += `<div class="fld"><span class="l">${esc(c('values'))}</span>${pathList('items', b.items)}</div><div class="fld"><label for="ld-i-fc">${esc(c('countRows'))}</label><select id="ld-i-fc" data-bk="count"><option value="">—</option>${assocOpts(b.count)}</select></div>`;
    if (b.type === 'validation'){
      const sev = b.sev || {};
      const onOpts = cv => `<option value="">—</option>` + B.groups.map(g => opt(g.p, cv, '▸ ' + lab(g.p))).join('') + B.fields.map(f => opt(f.p, cv, lab(f.p) + '  ' + f.p)).join('') + B.assocs.map(a => opt(a.p, cv, '⇄ ' + lab(a.p))).join('');
      h += `<div class="info">${esc(c('vInfo'))}<br>${esc(c('vPreview', REPORT.length, B.record.label || '—'))}</div>
        <div class="fld"><span class="l">${esc(c('sevShown'))}</span><div class="list-mini">${SEVS.map(s => `<label><input type="checkbox" data-sev="${s}"${sev[s] ? ' checked' : ''}> ${esc(c({error:'errors',warning:'warnings',info:'infos'}[s]))} <span style="color:var(--muted)">(${REPORT.filter(m => m.sev === s).length})</span></label>`).join('')}</div></div>
        <div class="fld"><span class="l">${esc(c('group'))}</span><div class="seg"><button type="button" data-vgroup="severity" aria-pressed="${(b.group || 'severity') === 'severity'}">${esc(c('bySev'))}</button><button type="button" data-vgroup="tab" aria-pressed="${b.group === 'tab'}">${esc(c('byTab'))}</button><button type="button" data-vgroup="none" aria-pressed="${b.group === 'none'}">${esc(c('noGroup'))}</button></div></div>
        <div class="fld"><label for="ld-i-vmax">${esc(c('maxMsg'))}</label><select id="ld-i-vmax">${[3,5,10,0].map(n => `<option value="${n}"${(b.max == null ? 5 : b.max) === n ? ' selected' : ''}>${n || esc(c('all'))}</option>`).join('')}</select></div>
        <label class="chk"><input type="checkbox" id="ld-i-vlinks"${b.links !== false ? ' checked' : ''}> ${esc(c('links'))}</label>
        <label class="chk"><input type="checkbox" id="ld-i-vempty"${b.hideEmpty ? ' checked' : ''}> ${esc(c('hideEmpty'))}</label>
        <div class="fld"><span class="l">${esc(c('actions'))} (${(b.actions || []).length})</span><div class="rules">${(b.actions || []).map((a, i) => `<div class="rule"><label class="l">${esc(c('actOn'))}</label><select data-ak="${i}" data-af="on">${onOpts(a.on)}</select>${locField('action.' + i, c('actBtn'), a.btn)}<div style="display:flex;gap:6px;align-items:center"><select data-ak="${i}" data-af="go" aria-label="${esc(c('actGo'))}" style="flex:1;min-width:0">${onOpts(a.go)}</select><button type="button" class="btn small ghost" data-adel="${i}">${esc(c('del'))}</button></div></div>`).join('')}</div><button type="button" class="btn small" data-act="aadd" style="align-self:flex-start">${esc(c('addAct'))}</button><p class="hint">${esc(c('actHint'))}</p></div>`;
    }
    if (b.type === 'grid'){
      const a = A[b.a];
      h += `<div class="fld"><label for="ld-i-ga">${esc(c('assoc'))}</label><select id="ld-i-ga" data-bk="a">${assocOpts(b.a)}</select></div>
        <div class="fld"><span class="l">${esc(c('presentation'))}</span><div class="seg"><button type="button" data-style="native" aria-pressed="${b.style !== 'cards'}">${esc(c('native'))}</button><button type="button" data-style="cards" aria-pressed="${b.style === 'cards'}">${esc(c('cards'))}</button></div></div>
        ${b.style === 'cards' && a ? `<div class="fld"><span class="l">${esc(c('cardFields'))}</span><div class="list-mini">${(a.fields || []).map(f => `<label><input type="checkbox" data-cardf="${esc(f.p)}"${(b.cardFields || []).includes(f.p) ? ' checked' : ''}> ${esc(T(f.lab))}</label>`).join('')}</div></div>${locField('block.addBtn', c('addBtn'), b.addBtn, () => c('none'))}` : ''}
        <p class="hint">${esc(c('gridHint'))}</p>`;
    }
    if (b.type === 'map') h += `<div class="fld"><label for="ld-i-ml">${esc(c('lat'))}</label><select id="ld-i-ml" data-bk="lat">${fieldOpts(b.lat)}</select></div><div class="fld"><label for="ld-i-mg">${esc(c('lng'))}</label><select id="ld-i-mg" data-bk="lng">${fieldOpts(b.lng)}</select></div><label class="chk"><input type="checkbox" id="ld-i-mt"${b.tall ? ' checked' : ''}> ${esc(c('tall'))}</label><div class="info">${esc(c('mapInfo'))}</div>`;
    if (b.type === 'tree') h += `<div class="fld"><label for="ld-i-tp">${esc(c('parent'))}</label><select id="ld-i-tp" data-bk="parent">${fieldOpts(b.parent, f => f.t === 'fk')}</select></div><div class="fld"><label for="ld-i-tc">${esc(c('children'))}</label><select id="ld-i-tc" data-bk="children"><option value="">—</option>${assocOpts(b.children)}</select></div>`;
    if (b.type === 'duplicates') h += `<div class="info">${esc(c('dupInfo'))}</div>`;
    if (b.type === 'note') h += locField('block.text', c('text'), b.text, null, true);
    if (b.type === 'custom') h += `<div class="fld"><label for="ld-i-cc">${esc(c('component'))}</label><input type="text" id="ld-i-cc" class="mono" value="${esc(b.cls)}"></div><div class="info">${esc(c('compInfo'))}</div>`;
    h += `<div class="fld"><label for="ld-i-cond">${esc(c('cond'))}</label><input type="text" id="ld-i-cond" class="mono" value="${esc(b.cond || '')}" placeholder="${esc(c('always'))}"></div>
      <label class="chk"><input type="checkbox" id="ld-i-hc"${b.hideCreate ? ' checked' : ''}> ${esc(c('hideCreate'))}</label>${moveBtns()}</div>`;
  } else if (sel.k === 'field'){
    const b = S.blocks[sel.b]; const f = b && b.fields[sel.i]; if (!f || !F[f.p]){ sel = null; return renderInspector(); }
    const fd = F[f.p];
    h += `<div class="insp"><span class="kind">${esc(c('field'))} · ${esc(blockName(b))}</span><h3>${esc(hasText(f.label) ? T(f.label) : lab(f.p))}</h3>
      <div class="fld"><span class="l">${esc(c('path'))}</span><code style="font-family:var(--mono);font-size:12px">.${esc(f.p)}</code><span class="hint">${esc(c(TY[fd.t] || 'tStr'))}${fd.req ? ' · ' + esc(c('required')) : ''}${fd.fn ? ' · ' + esc(c('computed')) : ''}</span></div>
      ${locField('field.label', c('fieldLabel'), f.label, lc => (fd.lab[lc] || '') + ' ' + c('modelLabel'))}<p class="hint" style="margin-top:-6px">${esc(c('fieldLabelHint'))}</p>
      <div class="fld"><label for="ld-i-fw">${esc(c('widget'))}</label><select id="ld-i-fw">${widgetsFor(fd.t).map(([k, l]) => opt(k, f.w || defaultWidget(fd.t), c(l))).join('')}</select></div>
      <label class="chk"><input type="checkbox" id="ld-i-fro"${f.ro || fd.fn ? ' checked' : ''}${fd.fn ? ' disabled' : ''}> ${esc(c('ro'))}</label>
      <label class="chk"><input type="checkbox" id="ld-i-fwide"${f.wide ? ' checked' : ''}> ${esc(c('wide'))}</label>
      ${fd.req ? `<div class="info">${esc(c('reqInfo'))}</div>` : ''}${moveBtns()}</div>`;
  }
  $('#ld-insp').innerHTML = h;
  /* on a narrow screen the inspector is a drawer: open while something is selected */
  const bench = root.querySelector('.bench'); if (bench) bench.classList.toggle('has-sel', !!sel);
}

/* ======================= output ======================= */
const J = s => '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/[^\x20-\x7e]/g, ch => '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0')) + '"';
const JT = o => !hasText(o) ? 'null' : 'text(c, ' + LOCALES.filter(l => o[l]).map(l => J(l) + ', ' + J(o[l])).join(', ') + ')';
function javaExport(){
  const Ls = ['/** Generated by the Layout Designer from ' + B.model.table + '. Regenerated on export: put your own code in a subclass. */',
    'public class GeneratedForm extends com.orchestranetworks.ui.form.UIForm {', '', '\t@Override', '\tpublic void defineBody(final UIFormBody pBody, final UIFormContext c) {', '\t\tfinal UIFormPaneWithTabs tabs = new UIFormPaneWithTabs();'];
  S.tabs.forEach((t, i) => Ls.push(`\t\ttabs.addTab(UserMessage.createInfo(${JT(t.label)}), new Tab${i + 1}());`));
  Ls.push('\t\tpBody.setContent(tabs);', '\t}', '');
  S.tabs.forEach((t, ti) => {
    Ls.push(`\t/** ${T(t.label)} */`, `\tstatic final class Tab${ti + 1} implements UIFormPane {`, '\t\tpublic void writePane(final UIFormPaneWriter w, final UIFormContext c) {');
    t.rows.forEach(r => { Ls.push(`\t\t\tw.add("<div class=\\"ldf-row${r.cols === 2 ? ' ldf-c2' : ''}\\">");`);
      r.c.forEach(col => { Ls.push('\t\t\tw.add("<div class=\\"ldf-col\\">");');
        col.forEach(id => { const b = S.blocks[id]; if (!b) return;
          if (b.type === 'panel'){ Ls.push(`\t\t\tpanel(w, c, ${JT(b.title)}, ${JT(b.note)});`, '\t\t\tw.startTableFormRow();');
            (b.fields || []).forEach(f => { const p = 'Path.parse(' + J('.' + f.p) + ')'; const w = f.w || defaultWidget((F[f.p] || {}).t);
              if (w === 'nocreate') Ls.push(`\t\t\t{ final UIComboBox x = w.newComboBox(${p}); x.setCreateButtonDisplayable(false);${f.ro ? ' x.setEditorDisabled(true);' : ''} w.addFormRow(w.newFormRow(${p}), x); }`);
              else if (f.ro || b.ro) Ls.push(`\t\t\t{ final UIWidget x = w.newBestMatching(${p}); x.setEditorDisabled(true); w.addFormRow(w.newFormRow(${p}), x); }`);
              else Ls.push(`\t\t\tw.addFormRow(${p});`); });
            Ls.push('\t\t\tw.endTableFormRow();', '\t\t\tw.add("</section>");');
          } else if (b.type === 'grid'){ Ls.push(`\t\t\tpanel(w, c, ${hasText(b.title) ? JT(b.title) : 'null'}, ${JT(b.note)});`, `\t\t\tw.addWidget(Path.parse(${J('.' + b.a)}));`, '\t\t\tw.add("</section>");');
          } else Ls.push(`\t\t\t// ${b.type}: ${JSON.stringify((({id, type, ...o}) => o)(b)).slice(0, 160)}`);
        });
        Ls.push('\t\t\tw.add("</div>");'); });
      Ls.push('\t\t\tw.add("</div>");'); });
    Ls.push('\t\t}', '\t}', '');
  });
  Ls.push('}');
  return Ls.join('\n');
}
/* where the form stands, as the model's XSD says (no public API tells which form the running schema uses):
   declared, another class declared, nothing declared; unknown for a DMA model */
function useState(){
  const ac = B.activation;
  if (!ac.file) return 'unknown';
  if (ac.declared) return 'active';
  return ac.currentForm ? 'other' : 'off';
}
function renderUse(){
  const u = useState(), el = $('#ld-use');
  el.className = 'status use-' + u;
  el.textContent = c({active:'useActive', restart:'useRestart', off:'useOff', other:'useOther', unknown:'useUnknown'}[u]);
  $('#ld-deploy').classList.toggle('primary', u === 'off' || u === 'other');
  $('#ld-deploy').classList.toggle('ghost', !(u === 'off' || u === 'other'));
  $('#ld-deploy').setAttribute('aria-pressed', !!(sel && sel.k === 'deploy'));
}
function deployHtml(){
  const ac = B.activation, st = B.stored, u = useState();
  const storedLine = st.exists ? `${esc(c('stored'))} ${st.version} · ${esc(st.updatedOn || '')} ${st.updatedBy ? esc(c('by')) + ' ' + esc(st.updatedBy) : ''}` : esc(c('notStored'));
  let h = `<div class="insp act"><span class="kind">${esc(c('activation'))}</span><h3>${esc(c('deployTitle'))}</h3>
    <div class="${u === 'active' ? 'notice' : 'explain'}">${esc(c(u === 'active' ? 'deployActive' : u === 'restart' ? 'deployRestart' : u === 'unknown' ? 'deployUnknown' : 'deployOff'))}</div>
    <div class="kv"><span>${esc(c('currentForm'))}</span><code>${esc(ac.currentForm || (ac.file ? c('none2') : '—'))}</code><span>${esc(c('file'))}</span><code>${esc(ac.file || '—')}</code>${st.previousForm ? `<span>${esc(c('previousForm'))}</span><code>${esc(st.previousForm)}</code>` : ''}<span>FormLayout</span><span>${storedLine}</span></div>`;
  if (ac.file){
    h += `<div class="row-btns">${!ac.declared ? `<button type="button" class="btn primary small" data-submit="activate">${esc(c('activate'))}</button>` : `<button type="button" class="btn small ghost" data-submit="deactivate">${esc(c('deactivate'))}</button>`}</div><p class="hint">${esc(c('restart'))}</p>`;
  } else if (ac.reason === 'dma'){ if (u !== 'active') h += `<p class="hint">${esc(c('dma', ac.formClass))}</p>`; }
  else h += `<p class="hint">${esc(c('noFile', ac.reason, ac.formClass))}</p>`;
  return h + `<p class="hint">${esc(c('jsonHow'))}</p></div>`;
}
function renderCode(){
  const d = $('#ld-dlg'); if (!d || !d.open) return;
  ['json','java'].forEach(k => $('#ld-o-' + k).setAttribute('aria-pressed', otab === k));
  $('#ld-code').textContent = otab === 'json' ? JSON.stringify(toLayout(), null, 2) : javaExport();
  $('#ld-how').innerHTML = `<p>${esc(otab === 'json' ? c('jsonHow') : c('javaHow'))}</p>`;
}
function renderOutput(){ renderUse(); renderCode(); }
function renderStatus(){
  const s = $('#ld-status'); s.className = 'status ' + (dirty ? 'dirty' : 'saved'); s.textContent = dirty ? c(B.draft && !history.length ? 'draft' : 'dirty') : c('saved');
  $('#ld-undo').disabled = !history.length;
  const miss = missingByLocale(); const n = LOCALES.reduce((a, l) => a + miss[l], 0);
  $('#ld-i18n').innerHTML = `${esc(c('translations'))} <span class="badge ${n ? 'todo' : 'done'}">${esc(n ? c('todo', n) : c('complete'))}</span>`;
  $('#ld-i18n').setAttribute('aria-pressed', !!(sel && sel.k === 'i18n'));
}
function renderAll(){ renderCanvas(); renderPalette(); renderInspector(); renderOutput(); renderStatus(); }

/* ======================= mutations ======================= */
function newBlock(type, extra){
  const id = nid('b');
  const base = {panel:{title:L('Nouveau panneau','New panel','Nuovo pannello'), fields:[]},
    hero:{title:(B.fields.find(f => f.t === 'str') || {}).p || '', sub:[], chips:[], meter:'', status:''},
    facts:{items:[]}, validation:{title:L('À traiter','To review','Da verificare'), sev:{error:true, warning:true, info:false}, group:'severity', max:5, links:true, hideEmpty:false, actions:[]},
    grid:{a:(B.assocs[0] || {}).p || '', style:'native'}, map:{title:L('Localisation','Location','Posizione'), lat:'', lng:''},
    tree:{title:L('Structure du groupe','Group structure','Struttura del gruppo'), parent:'', children:''}, duplicates:{title:L('Doublons','Duplicates','Duplicati')},
    note:{text:L('Texte d’aide pour l’utilisateur.','Help text for the user.','Testo di aiuto per l’utente.')}, custom:{cls:'', title:{}}}[type];
  S.blocks[id] = Object.assign({id, type}, JSON.parse(JSON.stringify(base)), extra || {});
  return id;
}
function removeBlockRef(id){ const Lc = locate(id); if (Lc) Lc.r.c[Lc.ci].splice(Lc.k, 1); }
function insertBlock(id, tab, ri, ci, k){
  const b = S.blocks[id]; const r = tab.rows[ri];
  if (BLOCKS[b.type].full && r && r.cols === 2){ tab.rows.splice(ri + 1, 0, {id:nid('r'), cols:1, ratio:'11', c:[[id]]}); return c('explFull', bl(b.type)); }
  if (!r){ tab.rows.splice(ri, 0, {id:nid('r'), cols:1, ratio:'11', c:[[id]]}); return ''; }
  r.c[ci].splice(k, 0, id); return '';
}
const groupTitle = g => g && G[g] ? JSON.parse(JSON.stringify(G[g].lab)) : JSON.parse(JSON.stringify(B.model.tableLabel));
function act(a){
  if (!sel) return;
  snapshot(); let msg = '';
  if (sel.k === 'block'){
    const Lc = locate(sel.id); if (!Lc) return; const col = Lc.r.c[Lc.ci];
    if (a === 'up'){ if (Lc.k > 0){ col.splice(Lc.k, 1); col.splice(Lc.k - 1, 0, sel.id); } else if (Lc.ri > 0){ col.splice(Lc.k, 1); const pr = Lc.t.rows[Lc.ri - 1]; pr.c[Math.min(Lc.ci, pr.c.length - 1)].push(sel.id); } }
    if (a === 'down'){ if (Lc.k < col.length - 1){ col.splice(Lc.k, 1); col.splice(Lc.k + 1, 0, sel.id); } else if (Lc.ri < Lc.t.rows.length - 1){ col.splice(Lc.k, 1); const nr = Lc.t.rows[Lc.ri + 1]; nr.c[Math.min(Lc.ci, nr.c.length - 1)].unshift(sel.id); } }
    if (a === 'dup'){ const cp = JSON.parse(JSON.stringify(S.blocks[sel.id])); const id = nid('b'); cp.id = id; S.blocks[id] = cp; col.splice(Lc.k + 1, 0, id); sel = {k:'block', id}; }
    if (a === 'del'){ col.splice(Lc.k, 1); sel = null; msg = c('explBlockDel'); }
    if (a === 'aadd'){ const b = S.blocks[sel.id]; b.actions = b.actions || []; const firstNode = (REPORT.find(m => m.node) || {}).node || ''; b.actions.push({on:firstNode, btn:{[lang]:''}, go:firstNode}); }
  } else if (sel.k === 'field'){
    const b = S.blocks[sel.b]; const i = sel.i;
    if (a === 'up' && i > 0){ const [f] = b.fields.splice(i, 1); b.fields.splice(i - 1, 0, f); sel.i--; }
    if (a === 'down' && i < b.fields.length - 1){ const [f] = b.fields.splice(i, 1); b.fields.splice(i + 1, 0, f); sel.i++; }
    if (a === 'dup'){ b.fields.splice(i + 1, 0, JSON.parse(JSON.stringify(b.fields[i]))); sel.i++; }
    if (a === 'del'){ const fd = F[b.fields[i].p]; b.fields.splice(i, 1); sel = {k:'block', id:b.id}; msg = fd && fd.req ? c('explReq', lab(fd.p)) : c('explRemoved', fd ? lab(fd.p) : ''); }
  } else if (sel.k === 'tab'){
    const i = S.tabs.findIndex(t => t.id === sel.id);
    if (a === 'tleft' && i > 0){ const [t] = S.tabs.splice(i, 1); S.tabs.splice(i - 1, 0, t); }
    if (a === 'tright' && i < S.tabs.length - 1){ const [t] = S.tabs.splice(i, 1); S.tabs.splice(i + 1, 0, t); }
    if (a === 'tdel' && S.tabs.length > 1){ S.tabs.splice(i, 1); cur = S.tabs[Math.max(0, i - 1)].id; sel = null; }
  } else if (sel.k === 'row'){
    const Lr = locRow(sel.id); if (!Lr) return;
    if (a === 'rup' && Lr.ri > 0){ Lr.t.rows.splice(Lr.ri, 1); Lr.t.rows.splice(Lr.ri - 1, 0, Lr.r); }
    if (a === 'rdown' && Lr.ri < Lr.t.rows.length - 1){ Lr.t.rows.splice(Lr.ri, 1); Lr.t.rows.splice(Lr.ri + 1, 0, Lr.r); }
    if (a === 'rdel'){ Lr.t.rows.splice(Lr.ri, 1); sel = null; }
  }
  cleanup(); commit(msg);
}
function addFromPalette(ds){
  snapshot(); const tab = curTab(); let msg = '';
  if (ds.drag === 'newfield'){
    let b = sel && sel.k === 'block' && S.blocks[sel.id] && S.blocks[sel.id].type === 'panel' ? S.blocks[sel.id] : sel && sel.k === 'field' ? S.blocks[sel.b] : null;
    if (!b){ const id = newBlock('panel', {title:groupTitle(F[ds.p].g)}); tab.rows.push({id:nid('r'), cols:1, ratio:'11', c:[[id]]}); b = S.blocks[id]; msg = c('explNoPanel', lab(ds.p)); }
    const at = sel && sel.k === 'field' ? sel.i + 1 : b.fields.length;
    b.fields.splice(at, 0, {p:ds.p}); sel = {k:'field', b:b.id, i:at};
  } else {
    const id = newBlock(ds.type, ds.type === 'grid' ? {a:ds.a} : ds.type === 'custom' ? {cls:ds.cls} : null);
    const Lc = sel && sel.k === 'block' ? locate(sel.id) : null;
    if (Lc && Lc.t === tab && !BLOCKS[ds.type].full) Lc.r.c[Lc.ci].splice(Lc.k + 1, 0, id);
    else tab.rows.push({id:nid('r'), cols:1, ratio:'11', c:[[id]]});
    sel = {k:'block', id};
  }
  cleanup(); commit(msg);
}

/* ======================= drag and drop ======================= */
let drag = null, target = null;
const line = document.createElement('div'); line.id = 'dropline'; document.body.appendChild(line);
const tip = document.createElement('div'); tip.id = 'droptip'; document.body.appendChild(tip);
function hideLine(){ line.style.display = 'none'; tip.style.display = 'none'; root.querySelectorAll('.droptab').forEach(e => e.classList.remove('droptab')); target = null; }
function indexIn(container, selector, y){
  const kids = [...container.querySelectorAll(':scope > ' + selector)];
  const box = k => k.getBoundingClientRect().height ? k : (k.firstElementChild || k);
  for (let i = 0; i < kids.length; i++){ const r = box(kids[i]).getBoundingClientRect(); if (y < r.top + r.height / 2) return {i, ref:box(kids[i]), before:true}; }
  return {i:kids.length, ref:kids.length ? box(kids[kids.length - 1]) : null, before:false};
}
function showLineAt(el, before, label, container){
  const cr = container.getBoundingClientRect(); let y;
  if (el){ const r = el.getBoundingClientRect(); y = before ? r.top - 7 : r.bottom + 5; } else y = cr.top + 8;
  Object.assign(line.style, {display:'block', left:cr.left + 'px', width:cr.width + 'px', top:y + 'px'});
  Object.assign(tip.style, {display:'block', left:cr.left + 'px', top:(y - 20) + 'px'}); tip.textContent = label;
}
root.addEventListener('dragstart', e => {
  const d = e.target.closest && e.target.closest('[data-drag]'); if (!d) return;
  const k = d.dataset.drag;
  drag = {k, id:d.dataset.id, b:d.dataset.b, i:+d.dataset.i, p:d.dataset.p, type:d.dataset.type, a:d.dataset.a, cls:d.dataset.cls, tab:d.dataset.tab, isField:k === 'field' || k === 'newfield'};
  e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', k); } catch (_){ /* some browsers */ }
  root.classList.add('dragging');
});
root.addEventListener('dragend', () => { drag = null; hideLine(); root.classList.remove('dragging'); });
root.addEventListener('dragover', e => {
  if (!drag) return; hideLine();
  const t = e.target.closest ? e.target : e.target.parentElement;
  if (drag.k === 'tab'){ const tb = t.closest('[data-tab]'); if (!tb || tb.dataset.tab === drag.tab) return; e.preventDefault(); tb.classList.add('droptab'); target = {kind:'tabmove', to:tb.dataset.tab}; return; }
  if (t.closest('[data-drop="palette"], #ld-palette') && (drag.k === 'block' || drag.k === 'field')){ e.preventDefault(); target = {kind:'remove'}; return; }
  const tb = t.closest('[data-tab]');
  if (tb && !drag.isField){ e.preventDefault(); tb.classList.add('droptab'); target = {kind:'totab', tab:tb.dataset.tab}; return; }
  if (drag.isField){ const pf = t.closest('[data-drop="fields"]'); if (pf){ e.preventDefault(); const ix = indexIn(pf, '.fr', e.clientY); showLineAt(ix.ref, ix.before, c('inPanel'), pf); target = {kind:'field', b:pf.dataset.b, i:ix.i}; return; } }
  const gap = t.closest('[data-drop="rowgap"]');
  if (gap){ e.preventDefault(); const r = gap.getBoundingClientRect(); Object.assign(line.style, {display:'block', left:r.left + 'px', width:r.width + 'px', top:(r.top + r.height / 2 - 1) + 'px'}); Object.assign(tip.style, {display:'block', left:r.left + 'px', top:(r.top - 14) + 'px'}); tip.textContent = c('newRow'); target = {kind:'gap', ri:+gap.dataset.ri}; return; }
  const col = t.closest('[data-drop="col"]');
  if (col){ e.preventDefault(); const ix = indexIn(col, '.blk', e.clientY); showLineAt(ix.ref, ix.before, drag.isField ? c('newPanel') : c('here'), col); target = {kind:'col', ri:+col.dataset.ri, ci:+col.dataset.ci, k:ix.i, isNew:col.dataset.new}; }
});
root.addEventListener('drop', e => {
  if (!drag || !target) return; e.preventDefault();
  const d = drag, tg = target; hideLine(); root.classList.remove('dragging'); drag = null;
  snapshot(); let msg = ''; const tab = curTab(); let field = null, bid = null;
  if (d.k === 'field') field = S.blocks[d.b].fields[d.i];
  if (d.k === 'newfield'){ field = {p:d.p}; if (placedFields().has(d.p)) msg = c('explTwice', lab(d.p)); }
  if (d.k === 'block') bid = d.id;
  if (d.k === 'newblock') bid = newBlock(d.type, d.type === 'grid' ? {a:d.a} : d.type === 'custom' ? {cls:d.cls} : null);
  if (tg.kind === 'remove'){
    if (d.k === 'field'){ const fd = F[field.p]; S.blocks[d.b].fields.splice(d.i, 1); msg = fd && fd.req ? c('explReq', lab(fd.p)) : c('explRemoved', lab(field.p)); }
    if (d.k === 'block'){ removeBlockRef(bid); msg = c('explBlockDel'); }
    sel = null; cleanup(); return commit(msg);
  }
  if (tg.kind === 'tabmove'){ const i = S.tabs.findIndex(t => t.id === d.tab), j = S.tabs.findIndex(t => t.id === tg.to); const [t] = S.tabs.splice(i, 1); S.tabs.splice(j, 0, t); return commit(j === 0 ? c('explHome', T(t.label)) : ''); }
  if (tg.kind === 'totab'){
    if (d.k === 'block') removeBlockRef(bid);
    const t2 = tabOf(tg.tab); const last = t2.rows[t2.rows.length - 1];
    if (last && last.cols === 1 && !BLOCKS[S.blocks[bid].type].full) last.c[0].push(bid); else t2.rows.push({id:nid('r'), cols:1, ratio:'11', c:[[bid]]});
    cur = t2.id; sel = {k:'block', id:bid}; cleanup(); return commit(c('explToTab', T(t2.label)));
  }
  if (tg.kind === 'field'){
    const b = S.blocks[tg.b]; let i = tg.i;
    if (d.k === 'field'){ S.blocks[d.b].fields.splice(d.i, 1); if (d.b === tg.b && d.i < i) i--; }
    b.fields.splice(i, 0, field); sel = {k:'field', b:b.id, i}; cleanup(); return commit(msg);
  }
  if (field){
    if (d.k === 'field') S.blocks[d.b].fields.splice(d.i, 1);
    bid = newBlock('panel', {title:groupTitle(F[field.p] && F[field.p].g), fields:[field]});
    msg = (msg ? msg + ' ' : '') + c('explPanel', glab(F[field.p] && F[field.p].g), lab(field.p));
  } else if (d.k === 'block'){
    const Lc = locate(bid);
    if (tg.kind === 'col' && Lc && Lc.t === tab && Lc.ri === tg.ri && Lc.ci === tg.ci && Lc.k < tg.k) tg.k--;
    removeBlockRef(bid);
  }
  if (tg.kind === 'gap') tab.rows.splice(tg.ri, 0, {id:nid('r'), cols:1, ratio:'11', c:[[bid]]});
  if (tg.kind === 'col'){
    if (tg.isNew || !tab.rows[tg.ri]) tab.rows.splice(tg.ri, 0, {id:nid('r'), cols:1, ratio:'11', c:[[bid]]});
    else { const m2 = insertBlock(bid, tab, tg.ri, tg.ci, tg.k); if (m2) msg = (msg ? msg + ' ' : '') + m2; }
  }
  sel = field ? {k:'field', b:bid, i:0} : {k:'block', id:bid};
  cleanup(); commit(msg);
});

/* ======================= posting through EBX's form ======================= */
function submit(action, record){
  document.getElementById('ldAction').value = action;
  document.getElementById('ldLayout').value = JSON.stringify(toLayout());
  document.getElementById('ldRecord').value = record || '';
  const holder = document.getElementById('ldSubmit');
  const btn = holder && (/^(BUTTON|INPUT|A)$/.test(holder.tagName) ? holder : holder.querySelector('button,input[type=submit],a'));
  if (btn){ dirty = false; btn.click(); } else toast('ldSubmit?');
}

/* ======================= clicks and inputs ======================= */
function goTo(node){
  const at = whereIs(node); if (!at) return;
  cur = at.t.id; flash = {b:at.b.id, i:at.i};
  if (mode === 'design') sel = at.i != null ? {k:'field', b:at.b.id, i:at.i} : {k:'block', id:at.b.id};
  const m = REPORT.find(x => x.node === node);
  explain = mode === 'design' && m ? esc(c('explGoto', T(m.t), T(at.t.label))) : '';
  renderAll();
  const el = root.querySelector(at.i != null ? `#ld-canvas .fr[data-b="${at.b.id}"][data-i="${at.i}"] .fl` : `#ld-canvas .blk[data-id="${at.b.id}"]`);
  if (el) el.scrollIntoView({block:'center'});
  setTimeout(() => { flash = null; root.querySelectorAll('#ld-canvas .flash').forEach(e => e.classList.remove('flash')); }, 1800);
}
document.addEventListener('keydown', e => { if (e.key === 'Escape' && sel && window.matchMedia('(max-width:1280px)').matches && !e.target.closest('input,textarea,select')){ sel = null; explain = ''; renderAll(); } });
root.addEventListener('click', e => {
  if (e.target.closest('#ld-insp-close')){ sel = null; explain = ''; return renderAll(); }
  const t = e.target;
  if (t.closest('[data-noop]')){ e.preventDefault(); return; }
  if (t.closest('#ld-m-design')){ mode = 'design'; return renderAll(); }
  if (t.closest('#ld-m-preview')){ mode = 'preview'; sel = null; explain = ''; return renderAll(); }
  if (t.closest('#ld-w-wide')){ narrow = false; return renderAll(); }
  if (t.closest('#ld-w-narrow')){ narrow = true; return renderAll(); }
  if (t.closest('#ld-i18n')){ sel = sel && sel.k === 'i18n' ? null : {k:'i18n'}; explain = ''; return renderAll(); }
  for (const k of ['fields','assoc','blocks']) if (t.closest('#ld-pt-' + k)){ ptab = k; return renderPalette(); }
  for (const k of ['json','java']) if (t.closest('#ld-o-' + k)){ otab = k; return renderCode(); }
  if (t.closest('#ld-deploy')){ sel = sel && sel.k === 'deploy' ? null : {k:'deploy'}; explain = ''; return renderAll(); }
  if (t.closest('#ld-codebtn')){ const d = $('#ld-dlg'); if (d.showModal) d.showModal(); else d.setAttribute('open', ''); return renderCode(); }
  if (t.closest('#ld-dlg-close')){ $('#ld-dlg').close ? $('#ld-dlg').close() : $('#ld-dlg').removeAttribute('open'); return; }
  if (t.closest('#ld-undo')){ const s = history.pop(); if (s){ const o = JSON.parse(s); S = o.S; cur = o.cur; sel = null; dirty = history.length > 0; commit(c('explUndo')); } return; }
  if (t.closest('#ld-save')) return submit('save');
  const sb = t.closest('[data-submit]'); if (sb) return submit(sb.dataset.submit);
  if (t.closest('#ld-copy')){ const txt = $('#ld-code').textContent; try { navigator.clipboard.writeText(txt).then(() => toast(c('copied'))); } catch (_){ /* no clipboard */ } return; }
  const gl = t.closest('[data-goto]'); if (gl){ e.preventDefault(); return goTo(gl.dataset.goto); }
  if (t.closest('#ld-addtab')){ snapshot(); const nt = {id:nid('t'), label:{[lang]:c('newTab')}, count:[], rows:[]}; S.tabs.push(nt); cur = nt.id; sel = {k:'tab', id:nt.id}; return commit(c('explTab')); }
  const ar = t.closest('[data-addrow]'); if (ar){ snapshot(); const n = +ar.dataset.addrow; const r = {id:nid('r'), cols:n, ratio:'11', c:n === 2 ? [[], []] : [[]]}; r.c[0].push(newBlock('panel')); if (n === 2) r.c[1].push(newBlock('panel')); curTab().rows.push(r); sel = {k:'row', id:r.id}; return commit(''); }
  const tb = t.closest('[data-tab]'); if (tb){ cur = tb.dataset.tab; if (mode === 'design') sel = {k:'tab', id:cur}; explain = ''; return renderAll(); }
  const rs = t.closest('[data-rowsel]'); if (rs){ sel = {k:'row', id:rs.dataset.rowsel}; explain = ''; return renderAll(); }
  if (mode === 'design'){
    const fr = t.closest('#ld-canvas [data-item="field"]'); if (fr){ sel = {k:'field', b:fr.dataset.b, i:+fr.dataset.i}; explain = ''; return renderAll(); }
    const bk = t.closest('#ld-canvas [data-item="block"]'); if (bk){ sel = {k:'block', id:bk.dataset.id}; explain = ''; return renderAll(); }
  }
  const pi = t.closest('#ld-palette .pitem'); if (pi) return addFromPalette(pi.dataset);
  const ab = t.closest('[data-act]'); if (ab) return act(ab.dataset.act);
  const trg = t.closest('[data-trgo]'); if (trg){ const x = allTexts()[+trg.dataset.trgo]; sel = x.go; if (x.go.k === 'tab') cur = x.go.id; else { const Lc = locate(x.go.k === 'field' ? x.go.b : x.go.id); if (Lc) cur = Lc.t.id; } explain = ''; return renderAll(); }
  const fs = t.closest('[data-fsel]'); if (fs){ sel = {k:'field', b:sel.id, i:+fs.dataset.fsel}; explain = ''; return renderAll(); }
  const cb = t.closest('[data-cols]'); if (cb){ snapshot(); const Lr = locRow(sel.id); const n = +cb.dataset.cols; if (n !== Lr.r.cols){ if (n === 1){ Lr.r.c = [Lr.r.c.flat()]; explain = c('explMerged'); } else { const all = Lr.r.c[0]; if (all.some(id => BLOCKS[S.blocks[id].type].full)){ explain = c('explFullRow'); history.pop(); return renderInspector(); } const h = Math.ceil(all.length / 2); Lr.r.c = [all.slice(0, h), all.slice(h)]; Lr.r.ratio = '11'; explain = all.length > 1 ? c('explSplit') : ''; } Lr.r.cols = n; } return renderAll(); }
  const rb = t.closest('[data-ratio]'); if (rb){ snapshot(); locRow(sel.id).r.ratio = rb.dataset.ratio; return commit(''); }
  const stb = t.closest('[data-style]'); if (stb){ snapshot(); S.blocks[sel.id].style = stb.dataset.style; return commit(''); }
  const vg = t.closest('[data-vgroup]'); if (vg){ snapshot(); S.blocks[sel.id].group = vg.dataset.vgroup; return commit(''); }
  const ad = t.closest('[data-adel]'); if (ad){ snapshot(); S.blocks[sel.id].actions.splice(+ad.dataset.adel, 1); return commit(''); }
  const pd = t.closest('[data-pdel]'); if (pd){ snapshot(); const [k, i] = pd.dataset.pdel.split(':'); S.blocks[sel.id][k].splice(+i, 1); return commit(''); }
});
/* Enter in a text field would submit EBX's form */
root.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.tagName === 'INPUT') e.preventDefault(); });
root.addEventListener('input', e => {
  const t = e.target;
  if (t.id === 'ld-psearch') return renderPalette();
  if (!sel) return;
  const live = fn => { if (!t.dataset.snap){ snapshot(); t.dataset.snap = 1; } fn(); renderCanvas(); renderOutput(); renderStatus(); };
  if (t.dataset.lt) return live(() => {
    const r = locRef(t.dataset.lt); if (!r || !r.o) return;
    const o = (r.o[r.p] && typeof r.o[r.p] === 'object') ? r.o[r.p] : (r.o[r.p] = {});
    if (t.value) o[t.dataset.lc] = t.value; else delete o[t.dataset.lc];
    if (!hasText(o) && t.dataset.lt === 'field.label') delete r.o[r.p];
    const any = hasText(o);
    t.closest('.loc').querySelectorAll('.lrow').forEach(row => { const inp = row.querySelector('input,textarea'); row.classList.toggle('miss', any && !inp.value); });
  });
  if (sel.k === 'block'){ const b = S.blocks[sel.id]; const map = {'ld-i-cond':'cond', 'ld-i-cc':'cls'}; if (map[t.id]) return live(() => { b[map[t.id]] = t.value; }); }
});
root.addEventListener('change', e => {
  const t = e.target;
  if (t.id === 'ld-lang'){ lang = t.value; return renderAll(); }
  if (t.id === 'ld-rec'){ if (t.value === '__new'){ creating = true; return renderAll(); } creating = false; return submit('record', t.value); }
  if (!sel) return;
  if (t.id === 'ld-i-miss'){ onlyMissing = t.checked; return renderInspector(); }
  const Bk = sel.k === 'block' ? S.blocks[sel.id] : null;
  if (sel.k === 'tab' && t.dataset.count){ snapshot(); const tb = tabOf(sel.id); if (t.checked) tb.count.push(t.dataset.count); else tb.count = tb.count.filter(p => p !== t.dataset.count); return commit(''); }
  if (Bk && t.dataset.bk){ snapshot(); Bk[t.dataset.bk] = t.value; return commit(''); }
  if (Bk && t.dataset.padd && t.value){ snapshot(); (Bk[t.dataset.padd] = Bk[t.dataset.padd] || []).push(t.value); return commit(''); }
  if (Bk && t.dataset.sev){ snapshot(); Bk.sev = Bk.sev || {}; Bk.sev[t.dataset.sev] = t.checked; return commit(''); }
  if (Bk && t.dataset.ak){ snapshot(); Bk.actions[+t.dataset.ak][t.dataset.af] = t.value; return commit(''); }
  if (Bk && t.dataset.cardf){ snapshot(); Bk.cardFields = (Bk.cardFields || []).filter(p => p !== t.dataset.cardf); if (t.checked) Bk.cardFields.push(t.dataset.cardf); return commit(''); }
  const flags = {'ld-i-vlinks':'links', 'ld-i-vempty':'hideEmpty', 'ld-i-ro':'ro', 'ld-i-hc':'hideCreate', 'ld-i-mt':'tall'};
  if (Bk && flags[t.id]){ snapshot(); Bk[flags[t.id]] = t.checked; return commit(''); }
  if (Bk && t.id === 'ld-i-vmax'){ snapshot(); Bk.max = +t.value; return commit(''); }
  if (sel.k === 'field'){ const f = S.blocks[sel.b].fields[sel.i];
    if (t.id === 'ld-i-fw'){ snapshot(); f.w = t.value; return commit(''); }
    if (t.id === 'ld-i-fro'){ snapshot(); f.ro = t.checked; return commit(''); }
    if (t.id === 'ld-i-fwide'){ snapshot(); f.wide = t.checked; return commit(''); }
  }
  if (t.dataset.lt || t.id === 'ld-i-cond' || t.id === 'ld-i-cc'){ delete t.dataset.snap; if (sel.k !== 'i18n') renderInspector(); renderPalette(); }
});
function toast(m){ const d = document.createElement('div'); d.className = 'toast'; d.textContent = m; document.body.appendChild(d); setTimeout(() => d.remove(), 2200); }

/* ======================= page frame ======================= */
const recOpts = B.records.map(r => `<option value="${esc(r.key)}"${r.key === B.record.key ? ' selected' : ''}>${esc(r.label)}</option>`).join('') + `<option value="__new">${esc(c('creating'))}</option>`;
root.innerHTML = `<div class="wrap">
  ${B.notice ? `<div class="notice${B.noticeError ? ' err' : ''}" role="status">${esc(B.notice)}</div>` : ''}
  ${B.generated ? `<div class="explain">${esc(c('generated'))}</div>` : ''}
  <header class="top">
    <div class="brand"><b>${esc(c('title'))} · ${esc(T(B.model.tableLabel))}</b><span>${esc(B.model.dataspace)} › ${esc(B.model.dataset)} › ${esc(B.model.table)}</span></div>
    <div class="pick"><label for="ld-rec">${esc(c('preview'))}</label><select id="ld-rec">${B.records.length ? recOpts : `<option>${esc(c('noRecord'))}</option>`}</select></div>
    <div class="pick"><label for="ld-lang">${esc(c('language'))}</label><select id="ld-lang">${LOCALES.map(l => `<option value="${l}"${l === lang ? ' selected' : ''}>${l}${l === DEF ? ' ★' : ''}</option>`).join('')}</select></div>
    <div class="pick"><label>${esc(c('texts'))}</label><button type="button" class="btn ghost" id="ld-i18n" aria-pressed="false">${esc(c('translations'))}</button></div>
    <div class="pick"><label>${esc(c('mode'))}</label><div class="seg"><button type="button" id="ld-m-design" aria-pressed="true">${esc(c('design'))}</button><button type="button" id="ld-m-preview" aria-pressed="false">${esc(c('previewMode'))}</button></div></div>
    <div class="pick"><label>${esc(c('width'))}</label><div class="seg"><button type="button" id="ld-w-wide" aria-pressed="true">1 440 px</button><button type="button" id="ld-w-narrow" aria-pressed="false">890 px</button></div></div>
    <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">${B.helpUrl ? `<a class="btn ghost" href="${esc(B.helpUrl)}" target="_blank" rel="noopener">${esc(c('help'))}</a>` : ''}<span class="status" id="ld-use" role="status"></span><button type="button" class="btn ghost" id="ld-deploy">${esc(c('activation'))}</button><button type="button" class="btn ghost" id="ld-codebtn">${esc(c('code'))}</button><span class="status saved" id="ld-status"></span><button type="button" class="btn ghost" id="ld-undo" disabled>${esc(c('undo'))}</button><button type="button" class="btn primary" id="ld-save">${esc(c('save'))}</button></div>
  </header>
  <div class="bench">
    <aside class="side palette"><div class="side-hd"><h2>${esc(c('palette'))}</h2><div class="ptabs" role="tablist"><button type="button" role="tab" id="ld-pt-fields">${esc(c('fields'))}</button><button type="button" role="tab" id="ld-pt-assoc">${esc(c('assocs'))}</button><button type="button" role="tab" id="ld-pt-blocks">${esc(c('blocks'))}</button></div><input type="search" class="search" id="ld-psearch" placeholder="${esc(c('search'))}"></div><div class="side-bd" id="ld-palette"></div></aside>
    <section class="canvas-wrap"><div class="canvas-bar" id="ld-cbar"></div><div class="canvas design" id="ld-canvas"></div></section>
    <aside class="side inspector" aria-label="${esc(c('selection'))}"><div class="side-hd"><div class="insp-hd"><h2>${esc(c('selection'))}</h2><button type="button" class="btn small ghost insp-close" id="ld-insp-close">${esc(c('closeInsp'))}</button></div></div><div class="side-bd" id="ld-insp"></div></aside>
  </div>
  <dialog class="ld-dlg" id="ld-dlg" aria-labelledby="ld-dlg-t"><div class="ld-dlg-hd"><h2 id="ld-dlg-t">${esc(c('codeTitle'))}</h2><div class="seg"><button type="button" id="ld-o-json" aria-pressed="true">${esc(c('outJson'))}</button><button type="button" id="ld-o-java" aria-pressed="false">${esc(c('outJava'))}</button></div><button type="button" class="btn small ghost" id="ld-dlg-close">${esc(c('closeDlg'))}</button></div>
    <div class="ld-dlg-bd"><pre class="code" id="ld-code"></pre><div class="how" id="ld-how"></div><div><button type="button" class="btn small ghost" id="ld-copy">${esc(c('copy'))}</button></div></div></dialog>
</div>`;
['ld-m-design','ld-m-preview','ld-w-wide','ld-w-narrow'].forEach(id => {
  const obs = () => { $('#ld-m-design').setAttribute('aria-pressed', mode === 'design'); $('#ld-m-preview').setAttribute('aria-pressed', mode === 'preview'); $('#ld-w-wide').setAttribute('aria-pressed', !narrow); $('#ld-w-narrow').setAttribute('aria-pressed', narrow); };
  $('#' + id).addEventListener('click', () => setTimeout(obs, 0));
});
renderAll();
})();
