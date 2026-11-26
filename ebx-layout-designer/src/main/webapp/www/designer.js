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
 useOff:['Non utilisée par la table','Not used by the table'], useUnknown:['Modèle DMA : déclaration à vérifier','DMA model: check the declaration'], useNofile:['Déclaration à faire à la main','Declare it by hand'],
 useOther:['La table utilise une autre fiche','The table uses another form'],
 activation:['Activation','Activation'], code:['</> Code','</> Code'], deployTitle:['Activation de la fiche','Activating the form'],
 deployActive:['Le modèle déclare cette fiche. Elle s’applique dès que le serveur a redémarré après la déclaration ; ensuite, chaque enregistrement s’affiche à la prochaine ouverture d’une fiche.','The model declares this form. It applies once the server has restarted after the declaration; from then on, every save shows at the next opening of a record.'],
 deployRestart:['La déclaration est écrite dans le modèle, mais EBX ne l’a pas encore relue : redémarrez le serveur une fois.','The declaration is written in the model, but EBX has not read it yet: restart the server once.'],
 deployOff:['La table n’utilise pas encore cette mise en page : les enregistrements ne changent rien à la fiche tant qu’elle n’est pas déclarée.','The table does not use this layout yet: saving changes nothing on the form until it is declared.'],
 deployNofile:['Le designer ne peut pas écrire la déclaration dans le fichier du modèle : faites-la à la main (voir ci-dessous), puis redémarrez.','The designer cannot write the declaration into the model file: make it by hand (see below), then restart.'], deployUnknown:['Pour un modèle DMA, la déclaration se fait et se vérifie dans l’écran du modèle (ou avec set_form_layout) : ouvrez ensuite un enregistrement pour voir la fiche.','For a DMA model, the declaration is made and checked in the model screen (or with set_form_layout): then open a record to see the form.'],
 closeDlg:['Fermer','Close'], closeDesigner:['Fermer','Close'], closeTip:['Revenir à la table','Back to the table'], closeDirty:['Des modifications ne sont pas enregistrées. Fermer quand même ?','Some changes are not saved. Close anyway?'], codeTitle:['Ce que le designer produit','What the designer produces'], closeInsp:['Fermer','Close'], preview:['Aperçu avec','Preview with'], language:['Langue','Language'], texts:['Textes','Texts'],
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
 noTable:['La table n’a pas été trouvée dans le fichier XSD du modèle (type nommé ou modèle inclus ?) : déclarez la classe {0} à la main dans son osd:table.','The table was not found in the model’s XSD file (named type or included model?): declare class {0} by hand in its osd:table.'], noFile:['Le fichier XSD de ce modèle n’a pas été trouvé sur ce serveur ({0}) : déclarez la classe {1} à la main dans l’osd:table.','The XSD file of this model was not found on this server ({0}): declare class {1} by hand in the osd:table.'],
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
 kMap:['Carte','Map'], kHours:['Horaires d’ouverture','Opening hours'], dHours:['Ouvert maintenant, la semaine, les prochains horaires exceptionnels.','Open now, the week, the next exceptional hours.'],
 ohWeek:['Horaires de la semaine','Regular hours'], ohAssoc:['Association','Association'], ohDay:['Champ jour','Day field'], ohOpen:['Champ ouverture','Opening time field'], ohClose:['Champ fermeture','Closing time field'],
 ohClosedF:['Champ « fermé » (facultatif)','“Closed” field (optional)'], ohDays:['Codes du champ jour (facultatif)','Codes of the day field (optional)'],
 ohDaysHint:['Vide : reconnus seuls (1 à 7 avec lundi = 1, MON/MONDAY, LUN/LUNDI, LUNEDÌ…). Sinon, le code de chaque jour.','Empty: recognised by themselves (1 to 7 with Monday = 1, MON/MONDAY, LUN/LUNDI, LUNEDÌ…). Otherwise, the code of each day.'],
 ohExc:['Horaires exceptionnels (facultatif)','Exceptional hours (optional)'], ohDate:['Champ date','Date field'], ohReason:['Champ motif','Reason field'],
 ohMax:['Exceptions affichées (0 = section masquée)','Exceptions shown (0 = section hidden)'], ohTz:['Champ fuseau horaire (facultatif)','Time-zone field (optional)'],
 ohTzHint:['Un identifiant comme Europe/Paris ; sans lui, le fuseau du serveur.','An identifier such as Europe/Paris; without it, the server’s time zone.'],
 ohExcTitle:['Titre des exceptions','Exceptions title'], ohSetup:['Choisissez l’association des horaires de la semaine et ses champs.','Choose the association of the regular hours and its fields.'],
 ohPreview:['Aperçu indicatif : la fiche affiche les vraies valeurs.','Indicative preview: the form shows the real values.'], ohTimes:['Heures en texte « HH:mm » ou en champ heure.','Times as text “HH:mm” or as a time field.'], kTree:['Structure du groupe','Group structure'], kDup:['Doublons','Duplicates'], kNote:['Texte d’aide','Help text'], kCustom:['Composant enregistré','Registered component'],
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
 creating:['Nouvelle fiche (création)','New record (creation)'],
 setupBlock:['Choisissez les réglages de ce bloc dans l’inspecteur.','Choose this block’s settings in the inspector.'], histChanged:['Modification','Changed'], histCreated:['Création','Created'],
 missingL:['Manquants','Missing'], openRecord:['Ouvrir la fiche','Open the record'], noDates:['Pas encore de dates.','No dates yet.'], noAddress:['Pas encore d’adresse.','No address yet.'],
 valUpcoming:['À venir','Upcoming'], valActive:['En vigueur','In force'], valEnding:['Bientôt échu','Ending soon'], valExpired:['Expiré','Expired'], copy:['Copier','Copy'],
 codesPh:['valeurs séparées par des virgules','values separated by commas'], weight:['Poids','Weight'], addValue:['+ une valeur (Entrée)','+ a value (Enter)'], checkedWhen:['Coché quand','Ticked when'],
 behaviours:['Comportements','Behaviours'], famTitle:['Champs pilotés par une table de configuration','Fields driven by a configuration table'],
 famHint:['Un champ que la table cite pour une famille ne s’affiche que pour cette famille (obligatoire si la colonne le dit) ; un champ qu’elle ne cite jamais reste toujours affiché. En direct, sans enregistrer.','A field the table lists for a family shows only for that family (mandatory when the column says so); a field it never lists always shows. Live, without saving.'],
 behHint:['Un comportement change l’affichage, pas la validation d’EBX : pour bloquer l’enregistrement, ajoutez une règle au modèle.','A behaviour changes the display, not EBX’s validation: to block saving, add a rule to the model.'],
 wInput:['Saisie','Input'], wDisplay:['Affichage (lecture seule)','Display (read only)'], noSetting:['Pas de réglage.','No setting.'],
 displayHint:['Widget d’affichage : utilisé quand le champ est en lecture seule ; sinon la fiche garde la saisie d’EBX.','Display widget: used when the field is read only; otherwise the form keeps EBX’s input.'],
 explDisplayRo:['Widget d’affichage : le champ est passé en lecture seule.','Display widget: the field is now read only.'], sectionTitle:['Section','Section'],
 liveIf:['si {0} (en direct)','if {0} (live)'], famTag:['piloté par une table','driven by a table'], condX:['Afficher seulement si (XPath, à l’ouverture)','Show only if (XPath, when opened)'],
 famTree:['Familles en arbre (facultatif)','Families in a tree (optional)'], famParent:['Champ « famille parente »','“Parent family” field'], famNoTree:['— pas d’héritage —','— no inheritance —'],
 famTreeHint:['Une sous-famille reçoit aussi les attributs de ses familles parentes ; si deux niveaux citent le même attribut, le plus proche décide s’il est obligatoire.','A sub-family also gets the attributes of its parent families; when two levels list the same attribute, the closest one decides whether it is mandatory.'],
 newTag:['nouveau','new'], noBlockMatch:['Aucun bloc ne correspond.','No block matches.'],
 wbHint:['Widgets d’affichage et comportements se règlent dans l’inspecteur d’un champ ou d’un panneau.','Display widgets and behaviours are set in the inspector of a field or a panel.']
};
const c = (k, ...a) => { let s = (C[k] || [k,k])[UI]; a.forEach((v,i) => { s = s.split('{'+i+'}').join(v); }); return s; };

/* the form's own words (as the runtime writes them), in the preview language */
const U = {
 afterCreate2:['Shown once the record is created.','Affiché une fois l’enregistrement créé.','Visibile dopo la creazione del record.'], chCount:['2 fields changed','2 champs modifiés','2 campi modificati'], chEmpty:['(empty)','(vide)','(vuoto)'],
 ubSample1:['Contacts, through Company','Contacts, par Société','Contatti, tramite Società'], ubSample2:['Addresses, through Company','Adresses, par Société','Indirizzi, tramite Società'],
 riCreatedBy:['Created by','Créé par','Creato da'], riCreatedOn:['Created on','Créé le','Creato il'], riModifiedBy:['Last modified by','Modifié par','Modificato da'], riModifiedOn:['Last modified on','Modifié le','Modificato il'], riNew:['Filled in when the record is saved.','Renseigné à l’enregistrement.','Compilato al salvataggio del record.'],
 completeness:['completeness','complétude','completezza'], noRow:['No rows.','Aucune ligne.','Nessuna riga.'], rows:['rows','lignes','righe'], actions:['Actions ▾','Actions ▾','Azioni ▾'], filter:['Filter','Filtrer','Filtra'],
 noParent:['No parent · head of group','Aucune maison mère · tête de groupe','Nessuna controllante · capogruppo'], mame:['Match and Merge state, read at display','État Match and Merge, lu à l’affichage','Stato Match and Merge, letto alla visualizzazione'],
 noCoords:['No coordinates yet','Pas encore de coordonnées','Nessuna coordinata'], closed:['Closed','Fermé','Chiuso'], newRecord:['New record','Nouvel enregistrement','Nuovo record'],
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
 grid:{l:'kGrid', d:''}, map:{l:'kMap', d:'dMap'}, hours:{l:'kHours', d:'dHours'}, tree:{l:'kTree', d:'dTree'}, duplicates:{l:'kDup', d:'dDup'}, note:{l:'kNote', d:'dNote'}, custom:{l:'kCustom', d:''}
};
const bl = t => BLOCKS[t].ll ? cl(BLOCKS[t].ll) : c(BLOCKS[t].l);
const bd = t => BLOCKS[t].dd ? cl(BLOCKS[t].dd) : BLOCKS[t].d ? c(BLOCKS[t].d) : '';

/* ======================= component library (1.4.0) =======================
   Blocks, display widgets and behaviours are described by a spec: the inspector is generated from it, the palette
   lists the blocks by category and the search reads labels, descriptions and keywords. */
const cl = x => typeof x === 'string' ? c(x) : (x[UI] || x[1] || x[0]);
const CATS = {structure:['Structure','Structure'], identity:['Identité','Identity'], kpi:['Indicateurs','Indicators'], quality:['Qualité','Quality'], relations:['Relations','Relations'],
  location:['Localisation','Location'], time:['Temps','Time'], media:['Médias','Media'], actions:['Actions','Actions'], ext:['Extension','Extension']};
const TONES = [['ok',['Vert','Green']],['warn',['Orange','Orange']],['bad',['Rouge','Red']],['accent',['Violet','Purple']],['info',['Bleu','Blue']],['muted',['Gris','Grey']]];
const NUMS = ['int','dec'];
const getP = (o, path) => path.split('.').reduce((x, k) => x == null ? undefined : x[k], o);
function setP(o, path, v){
  const ks = path.split('.'); let x = o;
  for (let i = 0; i < ks.length - 1; i++){ if (x[ks[i]] == null || typeof x[ks[i]] !== 'object') x[ks[i]] = /^\d+$/.test(ks[i + 1]) ? [] : {}; x = x[ks[i]]; }
  const last = ks[ks.length - 1];
  if (v === undefined || v === '' || (Array.isArray(v) && !v.length && !/^(steps|exits|items|fields|lines)$/.test(last))) { if (Array.isArray(x)) x.splice(+last, 1); else delete x[last]; }
  else x[last] = v;
}
const valsOf = p => p && F[p] ? (F[p].t === 'bool' ? [{v:'true', lab:L('Oui','Yes','Sì')}, {v:'false', lab:L('Non','No','No')}] : F[p].vals || []) : [];
const tFieldsOf = a => (A[a] || {}).fields || [];
const tableOf = p => (B.tables || []).find(t => t.p === p);

/* ---------- block specs ---------- */
const BL = {
 timeline:{cat:'time', l:['Frise chronologique','Timeline'], d:['Les lignes d’une association sur une frise, la plus récente en haut.','The rows of an association on a timeline, most recent first.'], tags:'événements interactions historique events dates',
   def:{a:'', date:'', titleField:'', detail:'', color:{field:'', map:{}}, max:10},
   spec:[{k:'assoc', key:'a'}, {k:'tfield', key:'date', of:'a', types:['date','dt'], l:['Champ date','Date field']}, {k:'tfield', key:'titleField', of:'a', l:['Champ titre (vide : libellé de la ligne)','Title field (empty: the row label)']},
     {k:'tfield', key:'detail', of:'a', l:['Champ détail','Detail field']}, {k:'tfield', key:'color.field', of:'a', l:['Couleur selon','Colour by']}, {k:'tones', key:'color.map', l:['Couleurs par valeur','Colours by value'], when:b => b.color && b.color.field},
     {k:'num', key:'max', def:10, l:['Nombre affiché','Rows shown']}]},
 history:{cat:'time', l:['Historique des modifications','Change history'], d:['Qui a changé quoi et quand (table historisée).','Who changed what and when (historized table).'], tags:'audit traçabilité versions trail',
   def:{fields:[], max:8, author:true},
   spec:[{k:'fields', key:'fields', l:['Champs suivis (vide : tous)','Fields followed (empty: all)']}, {k:'num', key:'max', def:8, l:['Nombre d’entrées','Entries']}, {k:'bool', key:'author', def:true, l:['Afficher l’auteur','Show the author']},
     {k:'hint', l:['Lit l’historique d’EBX (AdaptationTable.getHistory) : l’historisation doit être activée sur la table.','Reads EBX’s history (AdaptationTable.getHistory): history must be activated on the table.']}]},
 completeness:{cat:'quality', l:['Complétude','Completeness'], d:['Le pourcentage de champs renseignés et les manquants, avec « Aller au champ ».','The share of filled fields and the missing ones, with “Go to field”.'], tags:'qualité remplissage score quality',
   def:{fields:[], threshold:80, listMissing:true},
   spec:[{k:'wfields', key:'fields', l:['Champs comptés et leur poids (vide : les champs de la fiche)','Fields counted and their weight (empty: the fields of the form)']}, {k:'num', key:'threshold', def:80, l:['Seuil d’alerte (%)','Alert threshold (%)']},
     {k:'bool', key:'listMissing', def:true, l:['Lister les manquants','List the missing ones']}]},
 stepper:{cat:'kpi', l:['Cycle de vie (étapes)','Lifecycle (steps)'], d:['Les étapes d’un champ statut, l’étape courante mise en avant.','The steps of a status field, the current one highlighted.'], tags:'statut workflow avancement status',
   def:{field:'', steps:[], exits:[]},
   spec:[{k:'field', key:'field', l:['Champ statut','Status field'], types:['enum','str','fk','int']}, {k:'codes', key:'steps', of:'field', l:['Étapes, dans l’ordre (vide : les valeurs de la liste)','Steps, in order (empty: the list’s values)']},
     {k:'codes', key:'exits', of:'field', l:['Étapes de sortie (en rouge)','Exit steps (in red)']}]},
 checklist:{cat:'quality', l:['Liste de contrôle','Checklist'], d:['Des points à vérifier, cochés selon une condition sur la fiche.','Points to check, ticked by a condition on the record.'], tags:'contrôle conformité prêt readiness',
   def:{items:[]},
   spec:[{k:'items', key:'items', l:['Éléments','Items'], add:['+ Élément','+ Item'], tpl:{label:{}, when:{filled:''}}, item:[{k:'loc', key:'label', l:['Libellé','Label']}, {k:'when', key:'when'}]}]},
 chart:{cat:'kpi', l:['Répartition (graphique)','Breakdown (chart)'], d:['Les lignes d’une association regroupées par un champ, en barres ou en anneau.','The rows of an association grouped by a field, as bars or a ring.'], tags:'graphique ventes chart donut',
   def:{a:'', by:'', measure:{fn:'count', field:''}, shape:'bars', top:6},
   spec:[{k:'assoc', key:'a'}, {k:'tfield', key:'by', of:'a', l:['Regrouper par','Group by']}, {k:'choice', key:'measure.fn', def:'count', l:['Mesure','Measure'], opts:[['count',['Nombre de lignes','Row count']],['sum',['Somme','Sum']],['avg',['Moyenne','Average']]]},
     {k:'tfield', key:'measure.field', of:'a', types:NUMS, l:['Champ mesuré','Measured field'], when:b => b.measure && b.measure.fn !== 'count'}, {k:'choice', key:'shape', def:'bars', l:['Forme','Shape'], opts:[['bars',['Barres','Bars']],['donut',['Anneau','Ring']]]},
     {k:'num', key:'top', def:6, l:['Afficher les premiers','Show the first']}]},
 aggregates:{cat:'kpi', l:['Totaux d’une association','Association totals'], d:['Somme, moyenne, minimum ou maximum d’un champ sur les lignes d’une association.','Sum, average, minimum or maximum of a field over an association’s rows.'], tags:'somme moyenne total stock sum',
   def:{a:'', items:[]},
   spec:[{k:'assoc', key:'a'}, {k:'items', key:'items', l:['Valeurs','Values'], add:['+ Valeur','+ Value'], tpl:{label:{}, fn:'sum', field:'', format:'number'}, item:[{k:'loc', key:'label', l:['Libellé','Label']},
     {k:'choice', key:'fn', def:'sum', l:['Calcul','Function'], opts:[['sum',['Somme','Sum']],['avg',['Moyenne','Average']],['min',['Minimum','Minimum']],['max',['Maximum','Maximum']],['count',['Nombre de lignes','Row count']]]},
     {k:'tfield', key:'field', of:'@a', types:NUMS, l:['Champ','Field']}, {k:'choice', key:'format', def:'number', l:['Format','Format'], opts:[['number',['Nombre','Number']],['money',['Montant','Amount']],['percent',['Pourcentage','Percentage']]]},
     {k:'str', key:'currency', l:['Devise (EUR, USD…)','Currency (EUR, USD…)'], when:i => i.format === 'money'}]}]},
 pivot:{cat:'relations', l:['Matrice (tableau croisé)','Matrix (cross table)'], d:['Les lignes d’une association croisées sur deux champs.','An association’s rows crossed on two fields.'], tags:'disponibilité canaux pays croisement availability',
   def:{a:'', rows:'', cols:'', value:{}},
   spec:[{k:'assoc', key:'a'}, {k:'tfield', key:'rows', of:'a', l:['Lignes','Rows']}, {k:'tfield', key:'cols', of:'a', l:['Colonnes','Columns']}, {k:'tfield', key:'value.field', of:'a', l:['Valeur (vide : une coche si la ligne existe)','Value (empty: a tick when the row exists)']}]},
 linked:{cat:'relations', l:['Fiche liée (aperçu)','Linked record (preview)'], d:['Les champs choisis de l’enregistrement visé par une clé étrangère, avec un lien pour l’ouvrir.','Chosen fields of the record a foreign key points to, with a link to open it.'], tags:'fournisseur marque référence supplier',
   def:{fk:'', fields:[], open:true},
   spec:[{k:'field', key:'fk', types:['fk'], l:['Clé étrangère','Foreign key']}, {k:'fkfields', key:'fields', of:'fk', l:['Champs affichés','Fields shown']}, {k:'bool', key:'open', def:true, l:['Lien « Ouvrir la fiche »','“Open the record” link']}]},
 contacts:{cat:'relations', l:['Contacts','Contacts'], d:['Les personnes d’une association en cartes, avec e-mail et téléphone.','The people of an association as cards, with e-mail and phone.'], tags:'personnes interlocuteurs people',
   def:{a:'', name:'', role:'', email:'', phone:'', main:''},
   spec:[{k:'assoc', key:'a'}, {k:'tfield', key:'name', of:'a', l:['Nom (vide : libellé de la ligne)','Name (empty: the row label)']}, {k:'tfield', key:'role', of:'a', l:['Fonction','Role']},
     {k:'tfield', key:'email', of:'a', l:['E-mail','E-mail']}, {k:'tfield', key:'phone', of:'a', l:['Téléphone','Phone']}, {k:'tfield', key:'main', of:'a', types:['bool'], l:['Contact principal (oui/non)','Main contact (yes/no)']}]},
 gallery:{cat:'media', l:['Galerie d’images','Image gallery'], d:['Les images de champs média ou d’adresse, ou d’une association.','Images of media or URL fields, or of an association.'], tags:'photos visuels dama images',
   def:{a:'', media:'', caption:'', fields:[], perRow:4},
   spec:[{k:'assoc', key:'a', none:true, l:['Association des images (facultatif)','Association of the images (optional)']}, {k:'tfield', key:'media', of:'a', types:['media','str'], l:['Champ média ou URL','Media or URL field'], when:b => b.a},
     {k:'tfield', key:'caption', of:'a', l:['Légende','Caption'], when:b => b.a}, {k:'fields', key:'fields', types:['media','str','list'], l:['Ou champs de la fiche','Or fields of the record']}, {k:'num', key:'perRow', def:4, l:['Vignettes par ligne','Thumbnails per row']},
     {k:'hint', l:['Un champ média du Digital Asset Manager (API publique de l’add-on) ou un texte contenant une adresse d’image.','A Digital Asset Manager media field (the add-on’s public API) or a text holding an image address.']}]},
 documents:{cat:'media', l:['Documents','Documents'], d:['Fiches techniques, certificats : nom, type, date, lien.','Data sheets, certificates: name, type, date, link.'], tags:'pièces jointes pdf certificats files',
   def:{a:'', file:'', name:'', docType:'', date:'', expiry:false},
   spec:[{k:'assoc', key:'a'}, {k:'tfield', key:'file', of:'a', types:['media','str'], l:['Fichier (média ou URL)','File (media or URL)']}, {k:'tfield', key:'name', of:'a', l:['Nom (vide : celui du fichier)','Name (empty: the file’s)']},
     {k:'tfield', key:'docType', of:'a', l:['Type','Type']}, {k:'tfield', key:'date', of:'a', types:['date','dt'], l:['Date','Date']}, {k:'bool', key:'expiry', def:false, l:['La date est une échéance (rouge une fois passée)','The date is an expiry (red once past)']}]},
 links:{cat:'actions', l:['Liens et actions','Links and actions'], d:['Des boutons vers une adresse construite avec les valeurs de la fiche.','Buttons to an address built from the record’s values.'], tags:'boutons url lien web',
   def:{items:[]},
   spec:[{k:'items', key:'items', l:['Boutons','Buttons'], add:['+ Bouton','+ Button'], tpl:{label:{}, url:''}, item:[{k:'loc', key:'label', l:['Libellé','Label']}, {k:'str', key:'url', l:['Adresse : ${./chemin} insère une valeur','Address: ${./path} inserts a value'], ph:'https://shop.example.com/p/${./Sku}'},
     {k:'bool', key:'sameTab', def:false, l:['Dans le même onglet','In the same tab']}]},
     {k:'hint', l:['Une valeur vide masque le bouton. Une adresse /ebx/?… ouvre un composant web d’EBX (documenté).','An empty value hides the button. An /ebx/?… address opens an EBX web component (documented).']}]},
 changes:{cat:'quality', l:['Changements de la demande','Changes in this request'], d:['Dans l’espace de travail d’un workflow : ce que la fiche change par rapport à la référence.','In a workflow’s work dataspace: what the record changes compared with the reference.'], tags:'workflow validation approbation diff comparaison différences review approval compare',
   def:{against:'creation', max:20},
   spec:[{k:'choice', key:'against', def:'creation', l:['Comparer avec','Compare with'], opts:[['creation',['La référence à la création de la demande','The reference when the request was created']],['current',['La référence telle qu’elle est maintenant','The reference as it is now']]]},
     {k:'num', key:'max', def:20, l:['Lignes au plus','Rows at most']},
     {k:'hint', l:['Hors d’un espace de données enfant, le bloc dit qu’il n’y a rien à comparer. Comparaison par l’API publique d’EBX (DifferenceHelper).','Outside a child dataspace the block says there is nothing to compare. Compared through EBX’s public API (DifferenceHelper).']}]},
 usedBy:{cat:'relations', l:['Utilisé par','Used by'], d:['Les enregistrements d’autres tables qui pointent vers celui-ci, comptés par table.','The records of other tables that point to this one, counted per table.'], tags:'références inverses dépendances impact suppression reverse references dependencies where used',
   def:{samples:3, showZero:false, otherDatasets:true},
   spec:[{k:'num', key:'samples', def:3, l:['Exemples cités par table','Examples named per table']}, {k:'bool', key:'showZero', def:false, l:['Montrer aussi les relations sans enregistrement','Also show relations with no record']},
     {k:'bool', key:'otherDatasets', def:true, l:['Chercher aussi dans les autres jeux de données','Also look in other datasets']},
     {k:'hint', l:['Relations inverses déclarées dans les modèles (clés étrangères, liens), filtrées selon les droits de l’utilisateur.','Reverse relationships declared in the models (foreign keys, links), filtered by the user’s permissions.']}]},
 recordInfo:{cat:'time', l:['Informations de l’enregistrement','Record information'], d:['Qui a créé la fiche et quand, qui l’a modifiée en dernier et quand.','Who created the record and when, who changed it last and when.'], tags:'métadonnées créateur auteur date création modification audit metadata creator author',
   def:{createdBy:true, createdOn:true, modifiedBy:true, modifiedOn:true, style:'grid', relative:true},
   spec:[{k:'bool', key:'createdBy', def:true, l:['Créé par','Created by']}, {k:'bool', key:'createdOn', def:true, l:['Créé le','Created on']}, {k:'bool', key:'modifiedBy', def:true, l:['Modifié par','Last modified by']}, {k:'bool', key:'modifiedOn', def:true, l:['Modifié le','Last modified on']},
     {k:'choice', key:'style', def:'grid', l:['Présentation','Layout'], opts:[['grid',['En grille','As a grid']],['line',['Sur une ligne','On one line']]]}, {k:'bool', key:'relative', def:true, l:['Ajouter « il y a 3 jours »','Add “3 days ago”']},
     {k:'hint', l:['Valeurs tenues par EBX pour chaque enregistrement, rien à configurer dans le modèle.','Values EBX keeps for every record, nothing to set up in the model.']}]},
 validity:{cat:'time', l:['Période de validité','Validity period'], d:['Début et fin : à venir, en vigueur, bientôt échu ou expiré.','Start and end: upcoming, in force, ending soon or expired.'], tags:'contrat prix promotion dates expiry',
   def:{from:'', to:'', warnDays:30},
   spec:[{k:'field', key:'from', types:['date','dt'], l:['Début','Start']}, {k:'field', key:'to', types:['date','dt'], l:['Fin','End']}, {k:'num', key:'warnDays', def:30, l:['Alerte avant la fin (jours)','Alert before the end (days)']}]},
 address:{cat:'location', l:['Adresse formatée','Formatted address'], d:['Les champs d’adresse mis en forme selon le pays, avec un lien vers la carte.','Address fields laid out after the country, with a map link.'], tags:'adresse postale postal',
   def:{lines:[], number:'', zip:'', city:'', region:'', country:'', format:'auto', mapLink:true},
   spec:[{k:'fields', key:'lines', l:['Lignes de rue','Street lines']}, {k:'field', key:'number', l:['Numéro (sur la première ligne)','Number (on the first line)']}, {k:'choice', key:'numberPlace', def:'auto', l:['Place du numéro','Number position'], opts:[['auto',['Selon le pays','By country']],['numberBefore',['Avant la rue','Before the street']],['numberAfter',['Après la rue','After the street']]], when:b => b.number}, {k:'field', key:'zip', l:['Code postal','Postcode']}, {k:'field', key:'city', l:['Ville','City']}, {k:'field', key:'region', l:['Région, état','Region, state']},
     {k:'field', key:'country', l:['Pays','Country']}, {k:'choice', key:'format', def:'auto', l:['Mise en forme','Layout'], opts:[['auto',['Selon le pays','By country']],['zipFirst',['Code postal puis ville','Postcode then city']],['cityFirst',['Ville, région code','City, region code']],['zipAfter',['Ville puis code (UK)','City then code (UK)']]]},
     {k:'bool', key:'mapLink', def:true, l:['Lien vers la carte','Map link']}]},
 hierarchy:{cat:'relations', l:['Arborescence','Hierarchy'], d:['Le chemin dans une hiérarchie (fil d’Ariane) et les enfants.','The path in a hierarchy (breadcrumb) and the children.'], tags:'catégories classification arbre tree breadcrumb',
   def:{parent:'', up:'', label:'', depth:6, children:''},
   spec:[{k:'field', key:'parent', types:['fk'], l:['Clé vers le parent','Key to the parent']}, {k:'fkfield', key:'up', of:'parent', types:['fk'], l:['Parent du parent (si la cible est une autre table)','Parent of the parent (when the target is another table)']},
     {k:'fkfield', key:'label', of:'parent', l:['Libellé (vide : celui de la fiche)','Label (empty: the record’s)']}, {k:'num', key:'depth', def:6, l:['Profondeur','Depth']}, {k:'assoc', key:'children', none:true, l:['Enfants (association, facultatif)','Children (association, optional)']}]}
};
/* the blocks of 1.0–1.3 keep their own inspector; they get a category and keywords for the palette */
const OLD = {panel:['structure','champs formulaire groupe fields form'], hero:['identity','entête titre résumé header'], facts:['kpi','kpi valeurs values'], validation:['quality','erreurs avertissements contrôle errors'],
  map:['location','gps plan carte'], tree:['relations','parent filiales group'], hours:['time','magasin horaires semaine store'], duplicates:['quality','mame golden fusion'], note:['structure','aide consigne help']};
Object.keys(OLD).forEach(k => { BLOCKS[k].cat = OLD[k][0]; BLOCKS[k].tags = OLD[k][1]; });
Object.keys(BL).forEach(k => { BLOCKS[k] = {l:'', d:'', cat:BL[k].cat, tags:BL[k].tags, spec:BL[k].spec, def:BL[k].def, ll:BL[k].l, dd:BL[k].d}; });

/* ---------- display widgets of a field ---------- */
const WG = {
 badgeMap:{t:['enum','str','bool','fk'], l:['Pastille colorée par valeur','Pill coloured by value'], spec:[{k:'tones', key:'colors', of:'@p', l:['Couleurs par valeur','Colours by value']}, {k:'choice', key:'defaultTone', def:'muted', l:['Couleur par défaut','Default colour'], opts:TONES}]},
 money:{t:NUMS, l:['Montant avec devise','Amount with currency'], spec:[{k:'str', key:'currency', l:['Devise (EUR, USD…)','Currency (EUR, USD…)']}, {k:'field', key:'currencyField', types:['str','enum','fk'], l:['Ou champ devise','Or currency field']}, {k:'num', key:'decimals', l:['Décimales','Decimals']}]},
 unit:{t:NUMS, l:['Valeur avec unité','Value with unit'], spec:[{k:'str', key:'unit', l:['Unité (cm, kg…)','Unit (cm, kg…)']}, {k:'field', key:'unitField', types:['str','enum','fk'], l:['Ou champ unité','Or unit field']}, {k:'num', key:'decimals', l:['Décimales','Decimals']}]},
 percent:{t:NUMS, l:['Pourcentage en barre','Percentage bar'], spec:[{k:'choice', key:'scale', def:100, num:true, l:['Échelle','Scale'], opts:[[100,['0 – 100','0 – 100']],[1,['0 – 1','0 – 1']]]}, {k:'num', key:'thresholds.0', def:50, l:['Rouge en dessous de','Red below']}, {k:'num', key:'thresholds.1', def:80, l:['Orange en dessous de','Orange below']}]},
 stars:{t:NUMS, l:['Note en étoiles','Star rating'], spec:[{k:'num', key:'max', def:5, l:['Maximum','Maximum']}]},
 relativeDate:{t:['date','dt'], l:['Date relative','Relative date'], spec:[{k:'num', key:'warnDays', def:0, l:['Alerte à moins de (jours, 0 = sans couleur)','Alert within (days, 0 = no colour)']}]},
 duration:{t:NUMS, l:['Durée','Duration'], spec:[{k:'choice', key:'unit', def:'day', l:['Unité du champ','Unit of the field'], opts:[['minute',['Minutes','Minutes']],['hour',['Heures','Hours']],['day',['Jours','Days']],['month',['Mois','Months']],['year',['Années','Years']]]}]},
 country:{t:['str','enum','fk'], l:['Pays','Country'], spec:[{k:'choice', key:'show', def:'both', l:['Afficher','Show'], opts:[['both',['Nom et code','Name and code']],['name',['Nom','Name']],['code',['Code','Code']]]}, {k:'bool', key:'flag', def:true, l:['Drapeau','Flag']}]},
 phone:{t:['str'], l:['Téléphone','Phone'], spec:[]},
 email:{t:['str'], l:['E-mail','E-mail'], spec:[]},
 url:{t:['str'], l:['Adresse web','Web address'], spec:[{k:'choice', key:'show', def:'domain', l:['Afficher','Show'], opts:[['domain',['Le domaine','The domain']],['full',['L’adresse complète','The full address']]]}]},
 color:{t:['str','enum'], l:['Couleur','Colour'], spec:[{k:'bool', key:'showCode', def:true, l:['Afficher le code','Show the code']}]},
 barcode:{t:['str','int','dec'], l:['Code-barres EAN / GTIN','EAN / GTIN barcode'], spec:[{k:'bool', key:'checkDigit', def:true, l:['Contrôler la clé','Verify the check digit']}]},
 copyId:{t:['str','int','dec','fk'], l:['Identifiant copiable','Copyable identifier'], spec:[]},
 tags:{t:['list','enum'], l:['Puces','Tags'], spec:[{k:'num', key:'max', def:6, l:['Au-delà, « + n »','Beyond, “+ n”']}, {k:'tones', key:'colors', of:'@p', l:['Couleurs par valeur','Colours by value']}]},
 masked:{t:['str'], l:['Valeur masquée','Masked value'], spec:[{k:'num', key:'visible', def:4, l:['Caractères visibles à la fin','Visible characters at the end']}, {k:'codes', key:'revealRoles', l:['Rôles autorisés à tout afficher','Roles allowed to show all']}]},
 recordLink:{t:['fk'], l:['Lien vers la fiche','Link to the record'], spec:[{k:'bool', key:'sameTab', def:false, l:['Dans le même onglet','In the same tab']}]},
 thumbnail:{t:['str','media'], l:['Vignette','Thumbnail'], spec:[{k:'choice', key:'size', def:'small', l:['Taille','Size'], opts:[['small',['Petite','Small']],['medium',['Moyenne','Medium']],['large',['Grande','Large']]]}]},
 geo:{t:['dec','str'], l:['Coordonnées GPS','GPS coordinates'], spec:[{k:'field', key:'lng', types:['dec','str'], l:['Champ longitude','Longitude field']}, {k:'choice', key:'format', def:'decimal', l:['Format','Format'], opts:[['decimal',['Décimal','Decimal']],['dms',['Degrés, minutes, secondes','Degrees, minutes, seconds']]]}]},
 boolIcon:{t:['bool'], l:['Oui / Non en icône','Yes / No as an icon'], spec:[{k:'loc', key:'labels.true', l:['Libellé si oui','Label when yes']}, {k:'loc', key:'labels.false', l:['Libellé si non','Label when no']}]}
};
const isDisplayW = w => !!WG[w] || ['fig','badge','link','mono'].includes(w);

/* ---------- behaviours ---------- */
const CONDOPS = [['in',['est l’une des valeurs','is one of']],['notIn',['n’est aucune des valeurs','is none of']],['filled',['est renseigné','is filled']],['empty',['est vide','is empty']]];
const condOp = q => !q ? 'in' : q.filled ? 'filled' : q.empty ? 'empty' : q.notIn ? 'notIn' : 'in';
function condHtml(path, q, label, extra){
  const op = condOp(q), f = q && q.field || '';
  const vals = valsOf(f), cur = (q && (q[op] || [])) || [];
  return `<div class="fld"><span class="l">${esc(cl(label))}</span><div class="rule"><select data-sk="${path}.field" data-scond="1" aria-label="${esc(c('field'))}">${fieldOpts(f)}</select>
    ${f ? `<select data-sop="${path}" aria-label="op">${CONDOPS.map(([k, l]) => opt(k, op, cl(l))).join('')}</select>` : ''}
    ${f && (op === 'in' || op === 'notIn') ? (vals.length ? `<div class="chips">${vals.map(v => `<button type="button" class="chip${cur.some(x => String(x).toLowerCase() === v.v.toLowerCase()) ? ' on' : ''}" data-scode="${path}.${op}|${esc(v.v)}">${esc(T(v.lab))}</button>`).join('')}</div>` : '')
      + `<input type="text" class="mono" data-sk="${path}.${op}" data-sv="codes" value="${esc(cur.join(', '))}" placeholder="${esc(c('codesPh'))}" aria-label="${esc(c('values'))}">` : ''}${extra || ''}</div></div>`;
}

/* ---------- the generic inspector ---------- */
function ctlHtml(s, o, base){
  if (s.k === 'hint') return `<div class="info">${esc(cl(s.l))}</div>`;
  const key = base + s.key, v = getP(o, s.key), lbl = s.l ? cl(s.l) : '';
  const id = 'ld-s-' + key.replace(/[^A-Za-z0-9]/g, '_');
  const sel = (opts, extraFirst) => `<div class="fld"><label for="${id}">${esc(lbl)}</label><select id="${id}" data-sk="${key}"${s.num ? ' data-sv="num"' : ''}>${extraFirst === false ? '' : '<option value="">—</option>'}${opts}</select></div>`;
  const ofA = s.of && s.of.charAt(0) === '@' ? S.blocks[sel_.id] && S.blocks[sel_.id][s.of.slice(1)] : o[s.of];
  const typeOk = f => !s.types || s.types.includes(f.t);
  switch (s.k){
    case 'hint': return `<div class="info">${esc(lbl)}</div>`;
    case 'assoc': return `<div class="fld"><label for="${id}">${esc(lbl || c('assoc'))}</label><select id="${id}" data-sk="${key}"><option value="">—</option>${B.assocs.map(a => opt(a.p, v, lab(a.p))).join('')}</select></div>`;
    case 'field': return sel(B.fields.filter(typeOk).map(f => opt(f.p, v, lab(f.p) + '  ' + f.p)).join(''));
    case 'tfield': return ofA ? sel(tFieldsOf(ofA).filter(typeOk).map(f => opt(f.p, v, T(f.lab) + '  ' + f.p)).join('')) : '';
    case 'fkfield': { const fk = F[o[s.of]]; return fk && fk.tf ? sel(fk.tf.filter(typeOk).map(f => opt(f.p, v, T(f.lab) + '  ' + f.p)).join('')) : ''; }
    case 'fkfields': { const fk = F[o[s.of]]; if (!fk || !fk.tf) return ''; const cur = v || [];
      return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="list-mini">${fk.tf.map(f => `<label><input type="checkbox" data-sarr="${key}|${esc(f.p)}"${cur.includes(f.p) ? ' checked' : ''}> ${esc(T(f.lab))}</label>`).join('')}</div></div>`; }
    case 'tfields': { if (!ofA) return ''; const cur = v || [];
      return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="list-mini">${tFieldsOf(ofA).map(f => `<label><input type="checkbox" data-sarr="${key}|${esc(f.p)}"${cur.includes(f.p) ? ' checked' : ''}> ${esc(T(f.lab))}</label>`).join('')}</div></div>`; }
    case 'fields': { const cur = v || [];
      return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="list-mini">${cur.map((p, i) => `<label style="justify-content:space-between"><span>${esc(lab(p))}</span><button type="button" class="btn small ghost" data-sdel="${key}|${i}" style="height:22px">${esc(c('del'))}</button></label>`).join('')}<select data-spush="${key}" aria-label="${esc(c('add'))}"><option value="">${esc(c('add'))}</option>${B.fields.filter(typeOk).map(f => opt(f.p, '', lab(f.p) + '  ' + f.p)).join('')}</select></div></div>`; }
    case 'wfields': { const cur = v || [];
      return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="list-mini">${cur.map((f, i) => `<label style="justify-content:space-between;gap:6px"><span style="flex:1">${esc(lab(f.p))}</span><input type="number" min="0" max="10" style="width:52px" data-sk="${key}.${i}.weight" data-sv="num" value="${f.weight == null ? 1 : f.weight}" aria-label="${esc(c('weight'))}"><button type="button" class="btn small ghost" data-sdel="${key}|${i}" style="height:22px">${esc(c('del'))}</button></label>`).join('')}<select data-spushw="${key}" aria-label="${esc(c('add'))}"><option value="">${esc(c('add'))}</option>${B.fields.map(f => opt(f.p, '', lab(f.p) + '  ' + f.p)).join('')}</select></div></div>`; }
    case 'num': return `<div class="fld"><label for="${id}">${esc(lbl)}</label><input type="number" id="${id}" data-sk="${key}" data-sv="num" value="${v == null ? (s.def == null ? '' : s.def) : v}"></div>`;
    case 'bool': return `<label class="chk"><input type="checkbox" data-sk="${key}" data-sv="bool"${(v == null ? s.def : v) ? ' checked' : ''}> ${esc(lbl)}</label>`;
    case 'choice': { const cv = v == null ? s.def : v; return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="seg wrap">${s.opts.map(([k, l]) => `<button type="button" data-schoice="${key}|${k}"${s.num ? ' data-num="1"' : ''} aria-pressed="${String(cv) === String(k)}">${esc(cl(l))}</button>`).join('')}</div></div>`; }
    case 'str': return `<div class="fld"><label for="${id}">${esc(lbl)}</label><input type="text" class="mono" id="${id}" data-sk="${key}" value="${esc(v || '')}" placeholder="${esc(s.ph || '')}"></div>`;
    case 'codes': { const cur = v || []; const vals = valsOf(o[s.of]);
      return `<div class="fld"><span class="l">${esc(lbl)}</span>${vals.length ? `<div class="chips">${vals.map(x => `<button type="button" class="chip${cur.includes(x.v) ? ' on' : ''}" data-scode="${key}|${esc(x.v)}">${esc(T(x.lab))}</button>`).join('')}</div>` : ''}<input type="text" class="mono" data-sk="${key}" data-sv="codes" value="${esc(cur.join(', '))}" placeholder="${esc(c('codesPh'))}"></div>`; }
    case 'tones': { const cur = v || {}; const src = s.of === '@p' ? valsOf(o.p) : valsOf(o[s.of]);
      const keys = [...new Set([...src.map(x => x.v), ...Object.keys(cur)])];
      return `<div class="fld"><span class="l">${esc(lbl)}</span><div class="kv tones">${keys.map(k => { const x = src.find(y => y.v === k); return `<span>${esc(x ? T(x.lab) : k)}</span><select data-stone="${key}|${esc(k)}" aria-label="${esc(k)}"><option value="">—</option>${TONES.map(([t, l]) => opt(t, cur[k], cl(l))).join('')}</select>`; }).join('')}</div><input type="text" class="mono" data-stadd="${key}" placeholder="${esc(c('addValue'))}"></div>`; }
    case 'loc': return locField('spec.' + key, lbl, v);
    case 'when': { const w = v || {}; const kind = w.xpath != null ? 'xpath' : w.field != null ? 'value' : 'filled';
      return `<div class="fld"><span class="l">${esc(c('checkedWhen'))}</span><div class="seg wrap">${[['filled',['Champ renseigné','Field filled']],['value',['Valeur d’un champ','Value of a field']],['xpath',['XPath','XPath']]].map(([k, l]) => `<button type="button" data-swhen="${key}|${k}" aria-pressed="${kind === k}">${esc(cl(l))}</button>`).join('')}</div>
        ${kind === 'filled' ? `<select data-sk="${key}.filled" aria-label="${esc(c('field'))}">${fieldOpts(w.filled)}</select>` : kind === 'xpath' ? `<input type="text" class="mono" data-sk="${key}.xpath" value="${esc(w.xpath || '')}" placeholder="./Price > 0">` : condHtml(key, w, ['Condition','Condition'])}</div>`; }
    case 'items': { const arr = v || [];
      return `<div class="fld"><span class="l">${esc(lbl)} (${arr.length})</span><div class="rules">${arr.map((it, i) => `<div class="rule">${s.item.filter(x => !x.when || x.when(it)).map(x => ctlHtml(x, it, key + '.' + i + '.')).join('')}<button type="button" class="btn small ghost" data-sdel="${key}|${i}" style="align-self:flex-end">${esc(c('del'))}</button></div>`).join('')}</div><button type="button" class="btn small" data-sadd="${key}" data-tpl="${esc(JSON.stringify(s.tpl || {}))}" style="align-self:flex-start">${esc(cl(s.add || ['+ Ajouter','+ Add']))}</button></div>`; }
    case 'table': return sel((B.tables || []).map(t => opt(t.p, v, T(t.lab) + '  ' + t.p)).join(''));
    case 'col': { const t = tableOf(o[s.of]); return t ? sel(t.fields.filter(typeOk).map(f => opt(f.p, v, T(f.lab) + '  ' + f.p)).join('')) : ''; }
  }
  return '';
}
let sel_ = {};
function specHtml(spec, o, base){ sel_ = sel || {}; return spec.filter(s => !s.when || s.when(o)).map(s => ctlHtml(s, o, base || '')).join(''); }
/* the object the generic inspector edits: the selected block, field or row */
function specBase(){
  if (!sel) return null;
  if (sel.k === 'block') return S.blocks[sel.id];
  if (sel.k === 'field') return S.blocks[sel.b] && S.blocks[sel.b].fields[sel.i];
  if (sel.k === 'row'){ const Lr = locRow(sel.id); return Lr && Lr.r; }
  return null;
}
/* behaviours of a block (live "show if") and of a panel (fields driven by a configuration table) */
function behaviourBlockHtml(b){
  let h = `<h3 class="sub">${esc(c('behaviours'))}</h3>` + condHtml('showIf', b.showIf, ['Afficher seulement si (en direct)','Show only if (live)']);
  if (b.showIf && b.type === 'panel') h += ctlHtml({k:'choice', key:'onHide', def:'keep', l:['Valeurs des champs cachés','Values of hidden fields'], opts:[['keep',['Gardées','Kept']],['clear',['Vidées','Cleared']]]}, b, '');
  if (b.type === 'panel'){
    const by = b.attributesBy || {};
    h += `<div class="fld"><span class="l">${esc(c('famTitle'))}</span><div class="rule">` + specHtml([{k:'table', key:'attributesBy.table', l:['Table de configuration','Configuration table']}, {k:'field', key:'attributesBy.key', l:['Champ de la fiche qui pilote (la famille)','Field of the record that drives (the family)']}], b)
      + (by.table ? specHtml([{k:'col', key:'familyCol', of:'table', l:['Colonne famille','Family column']}, {k:'col', key:'attributeCol', of:'table', l:['Colonne attribut (chemin ou nom du champ)','Attribute column (path or field name)']},
        {k:'col', key:'mandatoryCol', of:'table', types:['bool'], l:['Colonne obligatoire (facultatif)','Mandatory column (optional)']}, {k:'bool', key:'hideEmpty', def:true, l:['Cacher le panneau sans champ','Hide the panel with no field']}], by, 'attributesBy.')
        + famTreeHtml(by) : '')
      + `</div><p class="hint">${esc(c('famHint'))}</p></div>`;
  }
  return h;
}
/* families in a tree: the table of the families (default: the one the driving key points to) and its parent field */
function famTreeHtml(by){
  const ft = by.familyTable || (F[by.key] || {}).target || '';
  const t = tableOf(ft);
  return `<div class="fld"><span class="l">${esc(c('famTree'))}</span>` + specHtml([{k:'table', key:'attributesBy.familyTable', l:['Table des familles (vide : celle de la clé étrangère)','Families table (empty: the foreign key’s)']}], S.blocks[sel.id])
    + (t ? `<div class="fld"><label for="ld-s-fampar">${esc(c('famParent'))}</label><select id="ld-s-fampar" data-sk="attributesBy.familyParent"><option value="">${esc(c('famNoTree'))}</option>${t.fields.map(f => opt(f.p, by.familyParent, T(f.lab) + '  ' + f.p)).join('')}</select></div>` : '')
    + `<p class="hint">${esc(c('famTreeHint'))}</p></div>`;
}
function behaviourFieldHtml(f){
  return `<h3 class="sub">${esc(c('behaviours'))}</h3>` + condHtml('showIf', f.showIf, ['Afficher seulement si (en direct)','Show only if (live)'],
      f.showIf ? ctlHtml({k:'choice', key:'onHide', def:'keep', l:['Valeur une fois caché','Value once hidden'], opts:[['keep',['Gardée','Kept']],['clear',['Vidée','Cleared']]]}, f, '') : '')
    + condHtml('mandatoryIf', f.mandatoryIf, ['Obligatoire si (astérisque en direct)','Mandatory if (live asterisk)'])
    + condHtml('readOnlyIf', f.readOnlyIf, ['Lecture seule si','Read only if'], ctlHtml({k:'codes', key:'readOnlyIf.roles', l:['… ou pour les rôles','… or for the roles']}, f, '') + ctlHtml({k:'codes', key:'readOnlyIf.exceptRoles', l:['Sauf pour les rôles','Except for the roles']}, f, ''))
    + `<p class="hint">${esc(c('behHint'))}</p>`;
}

/* ---------- previews of the 1.4.0 blocks: real values of the previewed record where the page has them, else indicative ---------- */
const vk = p => creating || !p ? '' : (V[p + '#k'] != null ? String(V[p + '#k']) : '');
const vd = p => creating || !p ? null : V[p + '#d'] ? new Date(V[p + '#d'] + 'T00:00:00') : null;
const tfl = (a, p) => { const f = tFieldsOf(a).find(x => x.p === p); return f ? T(f.lab) : p; };
const indicative = () => `<div class="more">${esc(c('ohPreview'))}</div>`;
const setupNote = () => `<div class="note-b">${esc(c('setupBlock'))}</div>`;
const dayDiff = d => Math.round((d - new Date(new Date().toDateString())) / 864e5);
function previewMore(b){
  const a = A[b.a], rows = creating || !a ? [] : (a.rows || []);
  switch (b.type){
    case 'timeline': if (!b.a || !b.date) return setupNote();
      return rows.length ? `<ol class="tl">${rows.slice(0, b.max || 10).map((r, i) => `<li${i === 0 ? ' class="t-accent"' : ''}><span class="d">${esc(tfl(b.a, b.date))}</span><b>${esc(r)}</b>${b.detail ? `<span class="s">${esc(tfl(b.a, b.detail))}</span>` : ''}</li>`).join('')}</ol>${indicative()}` : `<div class="note-b">${esc(u('noRow'))}</div>`;
    case 'history': return `<ol class="tl"><li><span class="d">2026-10-02 · admin</span><b>${esc(c('histChanged'))}</b><span class="s">${esc((b.fields && b.fields[0]) ? lab(b.fields[0]) : '…')} : 10 → 12</span></li><li><span class="d">2026-09-14 · admin</span><b>${esc(c('histCreated'))}</b></li></ol>${indicative()}`;
    case 'completeness': {
      const list = (b.fields || []).length ? b.fields.map(f => f.p || f) : [...placedFields()];
      if (!list.length) return setupNote();
      let tot = 0, ok = 0; const miss = [];
      list.forEach((p, i) => { const w = (b.fields || [])[i] && b.fields[i].weight != null ? b.fields[i].weight : 1; tot += w; if (val(p)) ok += w; else miss.push(p); });
      const pct = tot ? Math.round(100 * ok / tot) : 0, th = b.threshold == null ? 80 : b.threshold;
      return `<div class="cpx ${pct >= th ? 't-ok' : pct >= th / 2 ? 't-warn' : 't-bad'}"><b>${pct} %</b><span class="barx"><i style="width:${pct}%"></i></span></div>${b.listMissing !== false && miss.length ? `<div class="more">${esc(c('missingL'))} : ${miss.slice(0, 8).map(p => esc(lab(p))).join(', ')}${miss.length > 8 ? '…' : ''}</div>` : ''}`; }
    case 'stepper': { if (!b.field) return setupNote();
      const steps = (b.steps || []).length ? b.steps : valsOf(b.field).map(x => x.v).filter(x => !(b.exits || []).includes(x));
      const cur = vk(b.field), at = steps.findIndex(s => s.toLowerCase() === cur.toLowerCase());
      const lbl = s => { const x = valsOf(b.field).find(y => y.v === s); return x ? T(x.lab) : s; };
      return `<ol class="stepx">${steps.map((s, i) => `<li class="${i < at ? 'done' : i === at ? 'on' : ''}"><i>${i < at ? '✓' : i + 1}</i>${esc(lbl(s))}</li>`).join('')}</ol>${at < 0 && cur ? `<span class="w-badge ${(b.exits || []).includes(cur) ? 't-bad' : ''}">${esc(val(b.field))}</span>` : ''}`; }
    case 'checklist': { if (!(b.items || []).length) return setupNote();
      const done = it => { const w = it.when || {}; if (w.filled != null) return !!val(w.filled); if (!w.field || creating) return null; const v = vk(w.field).toLowerCase(), has = l => (l || []).some(x => String(x).toLowerCase() === v);
        return w.empty ? !v : w.notIn ? !has(w.notIn) : w.in ? has(w.in) : !!v; };
      const n = b.items.filter(it => done(it)).length;
      return `<div class="more"><b>${n} / ${b.items.length}</b></div><ul class="ckx">${b.items.map(it => { const d = done(it); return `<li class="${d ? 'done' : ''}"><i>${d ? '✓' : d === null ? '?' : ''}</i>${tx(it.label) || '…'}</li>`; }).join('')}</ul>`; }
    case 'chart': { if (!b.a || !b.by) return setupNote();
      const items = rows.length ? rows.slice(0, b.top || 6) : ['A', 'B', 'C'];
      return (b.shape === 'donut' ? `<div class="donutx"><span></span><ul>${items.map(r => `<li>${esc(r)}</li>`).join('')}</ul></div>` : `<div class="barsx">${items.map((r, i) => `<span>${esc(r)}</span><span class="barx"><i style="width:${100 - i * 14}%"></i></span>`).join('')}</div>`) + indicative(); }
    case 'aggregates': if (!b.a || !(b.items || []).length) return setupNote();
      return `<div class="facts">${b.items.map(it => `<div class="fact"><b>—</b><span>${hasText(it.label) ? tx(it.label) : esc(cl({sum:['Somme','Sum'],avg:['Moyenne','Average'],min:['Minimum','Minimum'],max:['Maximum','Maximum'],count:['Lignes','Rows']}[it.fn || 'sum']) + (it.field ? ' · ' + tfl(b.a, it.field) : ''))}</span></div>`).join('')}</div>${indicative()}`;
    case 'pivot': if (!b.a || !b.rows || !b.cols) return setupNote();
      return `<div class="gtab"><table><thead><tr><th></th><th>${esc(tfl(b.a, b.cols))} 1</th><th>${esc(tfl(b.a, b.cols))} 2</th></tr></thead><tbody>${(rows.length ? rows.slice(0, 4) : ['…']).map(r => `<tr><th>${esc(r)}</th><td>✓</td><td>·</td></tr>`).join('')}</tbody></table></div>${indicative()}`;
    case 'linked': { if (!b.fk) return setupNote(); const fk = F[b.fk] || {};
      return `<b>${esc(val(b.fk) || '—')}</b>${(b.fields || []).map(p => { const f = (fk.tf || []).find(x => x.p === p); return `<div class="fr"><div class="fl">${esc(f ? T(f.lab) : p)}</div><div class="fv"><div class="w-ro">…</div></div></div>`; }).join('')}${b.open !== false ? `<div style="margin-top:6px"><span class="pbtn">${esc(c('openRecord'))}</span></div>` : ''}`; }
    case 'contacts': if (!b.a) return setupNote();
      return rows.length ? `<div class="cards">${rows.slice(0, 6).map(r => `<div class="cardx"><div class="t"><b>${esc(r)}</b></div>${b.role ? `<div class="s">${esc(tfl(b.a, b.role))}</div>` : ''}${b.email ? `<div class="s">✉ ${esc(tfl(b.a, b.email))}</div>` : ''}</div>`).join('')}</div>` : `<div class="note-b">${esc(u('noRow'))}</div>`;
    case 'gallery': if (!b.a && !(b.fields || []).length) return setupNote();
      return `<div class="galx" style="grid-template-columns:repeat(${Math.max(1, Math.min(8, b.perRow || 4))},1fr)">${(rows.length ? rows : ['', '', '', '']).slice(0, (b.perRow || 4) * 2).map(r => `<span title="${esc(r)}"></span>`).join('')}</div>${indicative()}`;
    case 'documents': if (!b.a || !b.file) return setupNote();
      return rows.length ? rows.map(r => `<div class="docx"><span class="file">PDF</span><b>${esc(r)}</b></div>`).join('') : `<div class="note-b">${esc(u('noRow'))}</div>`;
    case 'links': if (!(b.items || []).length) return setupNote();
      return `<div style="display:flex;gap:8px;flex-wrap:wrap">${b.items.map((it, i) => `<span class="pbtn${i === 0 ? ' solid' : ''}">${tx(it.label) || '…'}</span>`).join('')}</div>`;
    case 'changes': if (creating) return `<div class="note-b">${esc(u('afterCreate2'))}</div>`;
      return `<span class="w-badge t-warn">${esc(u('chCount'))}</span><table class="chx"><tr><th>${esc(F[Object.keys(F)[0]] ? lab(Object.keys(F)[0]) : '…')}</th><td class="o">12,90 €</td><td>→</td><td class="n">13,50 €</td></tr><tr><th>${esc(F[Object.keys(F)[1]] ? lab(Object.keys(F)[1]) : '…')}</th><td class="o">${esc(u('chEmpty'))}</td><td>→</td><td class="n">…</td></tr></table>${indicative()}`;
    case 'usedBy': if (creating) return `<div class="note-b">${esc(u('afterCreate2'))}</div>`;
      return `<div class="ubx"><div><b>12</b><span>${esc(u('ubSample1'))}</span></div><div><b>3</b><span>${esc(u('ubSample2'))}</span></div></div>${indicative()}`;
    case 'recordInfo': { if (creating) return `<div class="note-b">${esc(u('riNew'))}</div>`;
      const m = B.record.meta || {}, its = [['createdBy','riCreatedBy'],['createdOn','riCreatedOn'],['modifiedBy','riModifiedBy'],['modifiedOn','riModifiedOn']].filter(([k]) => b[k] !== false);
      if (!its.length) return setupNote();
      return b.style === 'line' ? `<div class="rix line">${its.map(([k, l]) => `<span><span class="l">${esc(u(l))}</span> <b>${esc(m[k] || '—')}</b></span>`).join('')}</div>`
        : `<div class="rix">${its.map(([k, l]) => `<div><span class="l">${esc(u(l))}</span><b>${esc(m[k] || '—')}</b></div>`).join('')}</div>`; }
    case 'validity': { if (!b.from && !b.to) return setupNote();
      const f = vd(b.from), t = vd(b.to), warn = b.warnDays == null ? 30 : b.warnDays;
      if (!f && !t) return `<div class="note-b">${esc(c('noDates'))}</div>`;
      const n = new Date(new Date().toDateString());
      const st = f && n < f ? ['valUpcoming','t-info'] : t && n > t ? ['valExpired','t-bad'] : t && dayDiff(t) <= warn ? ['valEnding','t-warn'] : ['valActive','t-ok'];
      return `<span class="w-badge ${st[1]}">${esc(c(st[0]))}</span> <span class="more">${esc(val(b.from) || '…')} → ${esc(val(b.to) || '…')}</span>`; }
    case 'address': { const ls = (b.lines || []).map(val).filter(Boolean); if (val(b.number)) { if (ls.length) ls[0] = (b.numberPlace === 'numberAfter' ? ls[0] + ' ' + val(b.number) : val(b.number) + ' ' + ls[0]); else ls.push(val(b.number)); } const zc = [val(b.zip), val(b.city)].filter(Boolean).join(' ');
      if (zc) ls.push(zc); if (val(b.region)) ls.push(val(b.region)); if (val(b.country)) ls.push(val(b.country));
      return ls.length ? `<address class="addrx">${ls.map(esc).join('<br>')}</address>` : !b.city && !(b.lines || []).length ? setupNote() : `<div class="note-b">${esc(c('noAddress'))}</div>`; }
    case 'hierarchy': if (!b.parent) return setupNote();
      return `<div class="crumbx"><a href="#" data-noop="1">…</a> › <a href="#" data-noop="1">${esc(val(b.parent) || '—')}</a> › <b>${esc(creating ? u('newRecord') : B.record.label || '')}</b></div>`;
  }
  return '';
}
/* preview of a display widget of a field, from the record's value */
function previewWidget(w, f, v){
  const fd = F[f.p] || {};
  if (!v && w !== 'boolIcon') return `<div class="w-ro empty">—</div>`;
  switch (w){
    case 'badgeMap': { const t = (f.colors || {})[vk(f.p)] || f.defaultTone || 'muted'; return `<span class="w-badge t-${t}">${esc(v)}</span>`; }
    case 'money': return `<div class="w-fig">${esc(V[f.p + '#n'] != null ? new Intl.NumberFormat(lang, f.currency ? {style:'currency', currency:f.currency} : {}).format(V[f.p + '#n']) : v)}</div>`;
    case 'unit': return `<div class="w-ro">${esc(v)} <span class="more">${esc(f.unitField ? val(f.unitField) : f.unit || '')}</span></div>`;
    case 'percent': { const n = V[f.p + '#n'] || 0, p = Math.max(0, Math.min(100, f.scale === 1 ? n * 100 : n)), th = f.thresholds || [50, 80]; return `<span class="pctx ${p < th[0] ? 't-bad' : p < (th[1] || 80) ? 't-warn' : 't-ok'}"><span class="barx"><i style="width:${p}%"></i></span><b>${Math.round(p)} %</b></span>`; }
    case 'stars': { const m = f.max || 5, n = Math.max(0, Math.min(m, V[f.p + '#n'] || 0)); return `<span class="starsx" style="--v:${100 * n / m}%">${'★'.repeat(m)}</span> <span class="more">${n} / ${m}</span>`; }
    case 'relativeDate': { const d = vd(f.p); if (!d) return `<div class="w-ro">${esc(v)}</div>`; const n = dayDiff(d), wd = f.warnDays || 0;
      const t = !wd ? 'muted' : n < 0 ? 'bad' : n <= wd ? 'warn' : 'ok';
      return `<span class="w-badge t-${t}" title="${esc(v)}">${esc(new Intl.RelativeTimeFormat(lang, {numeric:'auto'}).format(Math.abs(n) < 60 ? n : Math.abs(n) < 730 ? Math.round(n / 30.44) : Math.round(n / 365.25), Math.abs(n) < 60 ? 'day' : Math.abs(n) < 730 ? 'month' : 'year'))}</span>`; }
    case 'country': { const k = vk(f.p).toUpperCase(); let nm = ''; try { nm = k.length === 2 ? new Intl.DisplayNames([lang], {type:'region'}).of(k) : ''; } catch (_){ nm = ''; }
      const fl = k.length === 2 && f.flag !== false ? String.fromCodePoint(...[...k].map(ch => 0x1F1E6 + ch.charCodeAt(0) - 65)) + ' ' : '';
      return `<div class="w-ro">${esc(fl + (nm ? (f.show === 'code' ? k : f.show === 'name' ? nm : nm + ' (' + k + ')') : v))}</div>`; }
    case 'phone': case 'email': return `<div class="w-ro"><a class="w-link" href="#" data-noop="1">${esc(v)}</a> <span class="cpb">${esc(c('copy'))}</span></div>`;
    case 'url': return `<div class="w-ro"><a class="w-link" href="#" data-noop="1">${esc(f.show === 'full' ? v : v.replace(/^https?:\/\//i, '').replace(/^www\./, '').replace(/[/?#].*$/, ''))} ↗</a></div>`;
    case 'color': return `<div class="w-ro"><span class="swx" style="background:${esc(/^#[0-9a-f]{3,6}$|^[a-z]{3,20}$/i.test(vk(f.p)) ? vk(f.p) : 'transparent')}"></span>${f.showCode !== false ? esc(v) : ''}</div>`;
    case 'barcode': return `<div class="w-ro"><span class="bcx"></span><span class="mono">${esc(v)}</span></div>`;
    case 'copyId': return `<div class="w-ro"><span class="mono">${esc(v)}</span> <span class="cpb">${esc(c('copy'))}</span></div>`;
    case 'tags': { const l = V[f.p + '#l'] || String(v).split(/,\s*/); const m = f.max || 6; return `<div class="w-ro">${l.slice(0, m).map(x => `<span class="w-badge">${esc(x)}</span>`).join(' ')}${l.length > m ? ` <span class="w-badge">+ ${l.length - m}</span>` : ''}</div>`; }
    case 'masked': { const s = String(vk(f.p) || v), n = f.visible == null ? 4 : f.visible; return `<div class="w-ro mono">${esc('•'.repeat(Math.max(0, s.length - n)) + s.slice(Math.max(0, s.length - n)))}</div>`; }
    case 'recordLink': return `<a class="w-link chipx" href="#" data-noop="1">${esc(v)} ↗</a>`;
    case 'thumbnail': return `<div class="w-img" style="width:${f.size === 'large' ? 90 : f.size === 'medium' ? 64 : 40}px;height:${f.size === 'large' ? 90 : f.size === 'medium' ? 64 : 40}px"></div>`;
    case 'geo': return `<div class="w-ro"><a class="w-link" href="#" data-noop="1">${esc(v)}${f.lng ? ', ' + esc(val(f.lng)) : ''} ↗</a></div>`;
    case 'boolIcon': { const on = vk(f.p) === 'true'; const lb = (f.labels || {})[on ? 'true' : 'false']; return `<span class="bix ${on ? 't-ok' : 't-muted'}"><i>${on ? '✓' : '✕'}</i>${hasText(lb) ? tx(lb) : esc(v || '—')}</span>`; }
    case 'duration': return `<div class="w-ro">${esc(v)} <span class="more">${esc(f.unit || 'day')}</span></div>`;
  }
  return `<div class="w-ro">${esc(v)}</div>`;
}


/* ======================= state ======================= */
let seq = 0; const nid = p => p + Date.now().toString(36) + (++seq);
function fromLayout(Lt){
  const S = {tabs:[], blocks:{}};
  ((Lt && Lt.tabs) || []).forEach(t => {
    const tab = {id:nid('t'), label:t.label || {}, count:(t.count || []).slice(), rows:[]};
    (t.rows || []).forEach(r => {
      const row = {id:nid('r'), cols:r.columns === 2 ? 2 : 1, ratio:r.ratio || '11', c:[], section:r.section};
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
    tabs:S.tabs.map(t => ({label:t.label, count:t.count, rows:t.rows.map(r => ({columns:r.cols, ratio:r.cols===2?(r.ratio||'11'):undefined, section:r.section && r.section.on ? r.section : undefined, blocks:r.c.map(col => col.map(id => S.blocks[id]).filter(Boolean))}))}))};
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
const defTitle = b => ['grid','timeline','contacts','documents','chart','pivot','aggregates'].includes(b.type) || (b.type === 'gallery' && b.a) ? (b.a ? lab(b.a) : '') : b.type === 'linked' && b.fk ? lab(b.fk) : b.type === 'stepper' && b.field ? lab(b.field) : '';
const blockName = b => hasText(b.title) ? T(b.title) : defTitle(b) || bl(b.type);

const isLoc = v => !!v && typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length > 0 && Object.keys(v).every(k => LOCALES.includes(k) && typeof v[k] === 'string');
const CTXK = {title:'ctxTitle', note:'ctxNote', addBtn:'ctxBtn', text:'ctxText', excTitle:'ohExcTitle'};
function allTexts(){
  const out = [];
  const walk = (o, name, go, depth) => { if (!o || typeof o !== 'object' || depth > 6) return;
    Object.keys(o).forEach(k => { const v = o[k];
      if (isLoc(v)){ if (hasText(v)) out.push({ctx:`${name} · ${CTXK[k] ? c(CTXK[k]) : k}`, o, p:k, go}); }
      else if (v && typeof v === 'object' && k !== 'fields') walk(v, name, go, depth + 1); }); };
  S.tabs.forEach((t, ti) => {
    out.push({ctx:c('ctxTab', ti+1), o:t, p:'label', go:{k:'tab', id:t.id}});
    t.rows.forEach((r, ri) => { if (r.section && hasText(r.section.title)) out.push({ctx:`${c('row')} ${ri + 1} · ${c('sectionTitle')}`, o:r.section, p:'title', go:{k:'row', id:r.id}});
      r.c.forEach(col => col.forEach(id => { const b = S.blocks[id]; if (!b) return; const nm = `${bl(b.type)} « ${blockName(b)} »`;
        walk(b, nm, {k:'block', id}, 0);
        (b.fields || []).forEach((f, i) => { if (f && typeof f === 'object') walk(f, c('ctxField', lab(f.p)).replace(/ · .*$/, ''), b.type === 'panel' ? {k:'field', b:id, i} : {k:'block', id}, 0); });
      })); });
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
const displayWidgetsFor = t => Object.keys(WG).filter(k => WG[k].t.includes(t)).map(k => [k, WG[k].l]);
function renderWidget(f, blk){
  const fd = F[f.p] || {t:'str'}; const roF = f.ro || blk.ro || fd.fn;
  const w = roF && (f.w == null || ['line','select','nocreate','area'].includes(f.w)) ? 'ro' : WG[f.w] && !roF ? defaultWidget(fd.t) : (f.w || defaultWidget(fd.t));
  const v = val(f.p);
  if (WG[w]) return previewWidget(w, f, v);
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
  const title = hasText(b.title) ? tx(b.title) : esc(defTitle(b));
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
  const tag = `<div class="btag"><span draggable="true" data-drag="block" data-id="${b.id}">${esc(bl(b.type))}${b.type === 'custom' ? ' · ' + esc(b.cls) : ''}</span>${b.showIf && b.showIf.field ? `<span class="cond">${esc(c('liveIf', lab(b.showIf.field)))}</span>` : ''}${b.attributesBy && b.attributesBy.table ? `<span class="cond">${esc(c('famTag'))}</span>` : ''}${b.cond ? `<span class="cond" title="${esc(b.cond)}">${esc(c('cond2', b.cond))}</span>` : ''}${b.hideCreate ? `<span class="cond">${esc(c('hiddenCreate'))}</span>` : ''}</div>`;
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
    case 'hours': { const wk = b.week || {}; const dn = i => new Date(2024, 0, 1 + i).toLocaleDateString(lang, {weekday:'short'});
      const cards = wk.a ? [0,1,2,3,4,5,6].map(i => `<div class="ohd${i === 6 ? ' off' : ''}${i === 2 ? ' today' : ''}"><b>${esc(dn(i))}</b><span>${i === 6 ? esc(u('closed')) : '10:00 – 19:00'}</span></div>`).join('') : '';
      inner = `<div class="panel${flashc}">${head(b)}${wk.a ? `<div class="ohw">${cards}</div><div class="more">${esc(c('ohPreview'))}</div>` : `<div class="note-b">${esc(c('ohSetup'))}</div>`}</div>`; break; }
    case 'duplicates': inner = `<div class="panel">${head(b)}<div class="dup"><span class="w-badge ok">Golden</span><span class="w-badge">${esc(u('mame'))}</span></div></div>`; break;
    case 'note': inner = `<div class="note-b">${tx(b.text)}</div>`; break;
    case 'custom': inner = `<div class="panel${flashc}">${head(b)}<div class="note-b">${esc(u('component', b.cls))}</div></div>`; break;
    default: if (BL[b.type]) inner = `<div class="panel${flashc}">${head(b)}${previewMore(b)}</div>`;
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
    if (r.section && r.section.on) rows += `<div class="secx${r.section.collapsed ? ' shut' : ''}" data-rowsel="${r.id}">${r.section.collapsed ? '▸' : '▾'} ${tx(r.section.title) || esc(c('sectionTitle'))}</div>`;
    rows += `<div class="frow ${cls}${r.section && r.section.on ? ' insec' : ''}${sel && sel.k === 'row' && sel.id === r.id ? ' sel' : ''}" data-row="${r.id}"><div class="rowtag"><button type="button" data-rowsel="${r.id}">${esc(c('row'))} · ${r.cols} ${esc(c(r.cols > 1 ? 'cols' : 'col'))}</button></div>`
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
    const nq = s => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const words = nq(q).split(/\s+/).filter(Boolean);
    const hit = k => !words.length || words.every(w => nq([bl(k), bd(k), BLOCKS[k].tags || '', cl(CATS[BLOCKS[k].cat] || ['','']), k].join(' ')).includes(w));
    let anyB = false;
    Object.keys(CATS).forEach(cat => {
      const ks = Object.keys(BLOCKS).filter(k => k !== 'grid' && k !== 'custom' && BLOCKS[k].cat === cat && hit(k));
      if (!ks.length) return; anyB = true;
      h += `<div class="pgroup"><span>${esc(cl(CATS[cat]))}</span>`;
      ks.forEach(k => { h += `<button type="button" class="pitem" draggable="true" data-drag="newblock" data-type="${k}"><span class="tx"><b>${esc(bl(k))}</b><small class="plain">${esc(bd(k))}</small></span>${BLOCKS[k].full ? `<span class="ty">${esc(c('full'))}</span>` : BL[k] ? `<span class="ty new">${esc(c('newTag'))}</span>` : ''}</button>`; });
      h += '</div>'; });
    if (!anyB) h += `<p class="hint">${esc(c('noBlockMatch'))}</p>`;
    h += `<p class="hint">${esc(c('wbHint'))}</p>`;
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
  if (kind === 'spec'){ const path = key.slice(5), base = specBase(); if (!base) return null; const ks = path.split('.'); const last = ks.pop();
    let o = base; ks.forEach((k2, i) => { if (o[k2] == null || typeof o[k2] !== 'object') o[k2] = /^\d+$/.test(ks[i + 1] || last) ? [] : {}; o = o[k2]; }); return {o, p:last}; }
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
      <p class="hint">${esc(c('rowHint'))}</p>${specHtml([{k:'bool', key:'section.on', def:false, l:['Section repliable','Collapsible section']}, {k:'loc', key:'section.title', l:['Titre de la section','Section title'], when:x => x.section && x.section.on}, {k:'bool', key:'section.collapsed', def:false, l:['Repliée à l’ouverture','Collapsed when opened'], when:x => x.section && x.section.on}], r)}<div class="row-btns"><button type="button" class="btn small ghost" data-act="rup">${esc(c('up'))}</button><button type="button" class="btn small ghost" data-act="rdown">${esc(c('down'))}</button><button type="button" class="btn small ghost" data-act="rdel">${esc(c('delRow'))}</button></div></div>`;
  } else if (sel.k === 'block'){
    const b = S.blocks[sel.id]; if (!b){ sel = null; return renderInspector(); }
    h += `<div class="insp"><span class="kind">${esc(bl(b.type))}${b.type === 'custom' ? ' · ' + esc(b.cls) : ''}</span>`;
    const titled = ['panel','grid','map','tree','duplicates','custom','validation','hours'].includes(b.type) || !!BL[b.type];
    if (titled) h += locField('block.title', c('titleF'), b.title, defTitle(b) ? (lc => { const s2 = b.a && A[b.a] ? A[b.a].lab[lc] : b.fk && F[b.fk] ? F[b.fk].lab[lc] : b.field && F[b.field] ? F[b.field].lab[lc] : ''; return s2 || ''; }) : null);
    if (titled && b.type !== 'validation') h += locField('block.note', c('note'), b.note);
    if (BL[b.type]) h += specHtml(BL[b.type].spec, b);
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
    if (b.type === 'hours'){ const wk = b.week || {}, ex = b.exceptions || {}, dy = b.days || {};
      const tOpts = (a, cv) => `<option value="">—</option>` + ((A[a] || {}).fields || []).map(f => opt(f.p, cv, T(f.lab) + '  ' + f.p)).join('');
      const sel2 = (id, nk, label, a, cv) => `<div class="fld"><label for="${id}">${esc(label)}</label><select id="${id}" data-nk="${nk}">${tOpts(a, cv)}</select></div>`;
      const dn = i => new Date(2024, 0, i).toLocaleDateString(UI === 0 ? 'fr' : 'en', {weekday:'long'});
      h += `<h3 style="margin-top:6px">${esc(c('ohWeek'))}</h3>
        <div class="fld"><label for="ld-i-ohwa">${esc(c('ohAssoc'))}</label><select id="ld-i-ohwa" data-nk="week.a"><option value="">—</option>${assocOpts(wk.a)}</select></div>`;
      if (wk.a) h += sel2('ld-i-ohwd', 'week.day', c('ohDay'), wk.a, wk.day) + sel2('ld-i-ohwo', 'week.open', c('ohOpen'), wk.a, wk.open) + sel2('ld-i-ohwc', 'week.close', c('ohClose'), wk.a, wk.close) + sel2('ld-i-ohwx', 'week.closed', c('ohClosedF'), wk.a, wk.closed)
        + `<p class="hint">${esc(c('ohTimes'))}</p><div class="fld"><span class="l">${esc(c('ohDays'))}</span><div class="kv">${[1,2,3,4,5,6,7].map(i => `<span>${esc(dn(i))}</span><input type="text" data-nk="days.${i}" value="${esc(dy[i] || '')}" aria-label="${esc(dn(i))}">`).join('')}</div><p class="hint">${esc(c('ohDaysHint'))}</p></div>`;
      h += `<h3 style="margin-top:10px">${esc(c('ohExc'))}</h3>
        <div class="fld"><label for="ld-i-ohea">${esc(c('ohAssoc'))}</label><select id="ld-i-ohea" data-nk="exceptions.a"><option value="">—</option>${assocOpts(ex.a)}</select></div>`;
      if (ex.a) h += sel2('ld-i-ohed', 'exceptions.date', c('ohDate'), ex.a, ex.date) + sel2('ld-i-oheo', 'exceptions.open', c('ohOpen'), ex.a, ex.open) + sel2('ld-i-ohec', 'exceptions.close', c('ohClose'), ex.a, ex.close)
        + sel2('ld-i-ohex', 'exceptions.closed', c('ohClosedF'), ex.a, ex.closed) + sel2('ld-i-oher', 'exceptions.reason', c('ohReason'), ex.a, ex.reason)
        + `<div class="fld"><label for="ld-i-ohmax">${esc(c('ohMax'))}</label><input type="number" min="0" max="50" id="ld-i-ohmax" value="${b.max == null ? 3 : b.max}"></div>` + locField('block.excTitle', c('ohExcTitle'), b.excTitle, lc => '');
      h += `<div class="fld"><label for="ld-i-ohtz">${esc(c('ohTz'))}</label><select id="ld-i-ohtz" data-bk="tz">${fieldOpts(b.tz, f => f.t === 'str' || f.t === 'enum' || f.t === 'fk')}</select><p class="hint">${esc(c('ohTzHint'))}</p></div>`;
    }
    if (b.type === 'tree') h += `<div class="fld"><label for="ld-i-tp">${esc(c('parent'))}</label><select id="ld-i-tp" data-bk="parent">${fieldOpts(b.parent, f => f.t === 'fk')}</select></div><div class="fld"><label for="ld-i-tc">${esc(c('children'))}</label><select id="ld-i-tc" data-bk="children"><option value="">—</option>${assocOpts(b.children)}</select></div>`;
    if (b.type === 'duplicates') h += `<div class="info">${esc(c('dupInfo'))}</div>`;
    if (b.type === 'note') h += locField('block.text', c('text'), b.text, null, true);
    if (b.type === 'custom') h += `<div class="fld"><label for="ld-i-cc">${esc(c('component'))}</label><input type="text" id="ld-i-cc" class="mono" value="${esc(b.cls)}"></div><div class="info">${esc(c('compInfo'))}</div>`;
    h += behaviourBlockHtml(b);
    h += `<div class="fld"><label for="ld-i-cond">${esc(c('condX'))}</label><input type="text" id="ld-i-cond" class="mono" value="${esc(b.cond || '')}" placeholder="${esc(c('always'))}"></div>
      <label class="chk"><input type="checkbox" id="ld-i-hc"${b.hideCreate ? ' checked' : ''}> ${esc(c('hideCreate'))}</label>${moveBtns()}</div>`;
  } else if (sel.k === 'field'){
    const b = S.blocks[sel.b]; const f = b && b.fields[sel.i]; if (!f || !F[f.p]){ sel = null; return renderInspector(); }
    const fd = F[f.p];
    h += `<div class="insp"><span class="kind">${esc(c('field'))} · ${esc(blockName(b))}</span><h3>${esc(hasText(f.label) ? T(f.label) : lab(f.p))}</h3>
      <div class="fld"><span class="l">${esc(c('path'))}</span><code style="font-family:var(--mono);font-size:12px">.${esc(f.p)}</code><span class="hint">${esc(c(TY[fd.t] || 'tStr'))}${fd.req ? ' · ' + esc(c('required')) : ''}${fd.fn ? ' · ' + esc(c('computed')) : ''}</span></div>
      ${locField('field.label', c('fieldLabel'), f.label, lc => (fd.lab[lc] || '') + ' ' + c('modelLabel'))}<p class="hint" style="margin-top:-6px">${esc(c('fieldLabelHint'))}</p>
      <div class="fld"><label for="ld-i-fw">${esc(c('widget'))}</label><select id="ld-i-fw"><optgroup label="${esc(c('wInput'))}">${widgetsFor(fd.t).filter(([k]) => !isDisplayW(k)).map(([k, l]) => opt(k, f.w || defaultWidget(fd.t), cl(l))).join('')}</optgroup><optgroup label="${esc(c('wDisplay'))}">${widgetsFor(fd.t).filter(([k]) => isDisplayW(k)).concat(displayWidgetsFor(fd.t)).map(([k, l]) => opt(k, f.w || defaultWidget(fd.t), cl(l))).join('')}</optgroup></select></div>
      ${WG[f.w] ? `<div class="rule">${specHtml(WG[f.w].spec, f) || `<p class="hint">${esc(c('noSetting'))}</p>`}</div>` : ''}${isDisplayW(f.w) ? `<p class="hint">${esc(c('displayHint'))}</p>` : ''}
      <label class="chk"><input type="checkbox" id="ld-i-fro"${f.ro || fd.fn ? ' checked' : ''}${fd.fn ? ' disabled' : ''}> ${esc(c('ro'))}</label>
      <label class="chk"><input type="checkbox" id="ld-i-fwide"${f.wide ? ' checked' : ''}> ${esc(c('wide'))}</label>
      ${fd.req ? `<div class="info">${esc(c('reqInfo'))}</div>` : ''}${behaviourFieldHtml(f)}${moveBtns()}</div>`;
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
  if (!ac.file) return ac.reason === 'dma' ? 'unknown' : 'nofile';
  if (ac.declared) return 'active';
  return ac.currentForm ? 'other' : 'off';
}
function renderUse(){
  const u = useState(), el = $('#ld-use');
  el.className = 'status use-' + u;
  el.textContent = c({active:'useActive', restart:'useRestart', off:'useOff', other:'useOther', unknown:'useUnknown', nofile:'useNofile'}[u]);
  $('#ld-deploy').classList.toggle('primary', u === 'off' || u === 'other');
  $('#ld-deploy').classList.toggle('ghost', !(u === 'off' || u === 'other'));
  $('#ld-deploy').setAttribute('aria-pressed', !!(sel && sel.k === 'deploy'));
}
function deployHtml(){
  const ac = B.activation, st = B.stored, u = useState();
  const storedLine = st.exists ? `${esc(c('stored'))} ${st.version} · ${esc(st.updatedOn || '')} ${st.updatedBy ? esc(c('by')) + ' ' + esc(st.updatedBy) : ''}` : esc(c('notStored'));
  let h = `<div class="insp act"><span class="kind">${esc(c('activation'))}</span><h3>${esc(c('deployTitle'))}</h3>
    <div class="${u === 'active' ? 'notice' : 'explain'}">${esc(c(u === 'active' ? 'deployActive' : u === 'restart' ? 'deployRestart' : u === 'unknown' ? 'deployUnknown' : u === 'nofile' ? 'deployNofile' : 'deployOff'))}</div>
    <div class="kv"><span>${esc(c('currentForm'))}</span><code>${esc(ac.currentForm || (ac.file ? c('none2') : '—'))}</code><span>${esc(c('file'))}</span><code>${esc(ac.file || '—')}</code>${st.previousForm ? `<span>${esc(c('previousForm'))}</span><code>${esc(st.previousForm)}</code>` : ''}<span>FormLayout</span><span>${storedLine}</span></div>`;
  if (ac.file){
    h += `<div class="row-btns">${!ac.declared ? `<button type="button" class="btn primary small" data-submit="activate">${esc(c('activate'))}</button>` : `<button type="button" class="btn small ghost" data-submit="deactivate">${esc(c('deactivate'))}</button>`}</div><p class="hint">${esc(c('restart'))}</p>`;
  } else if (ac.reason === 'dma'){ if (u !== 'active') h += `<p class="hint">${esc(c('dma', ac.formClass))}</p>`; }
  else if (ac.reason === 'table') h += `<p class="hint">${esc(c('noTable', ac.formClass))}</p>`;
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
    tree:{title:L('Structure du groupe','Group structure','Struttura del gruppo'), parent:'', children:''},
    hours:{title:L('Horaires d’ouverture','Opening hours','Orari di apertura'), week:{a:'', day:'', open:'', close:'', closed:''}, days:{}, exceptions:{a:'', date:'', open:'', close:'', closed:'', reason:''}, max:3, tz:''}, duplicates:{title:L('Doublons','Duplicates','Duplicati')},
    note:{text:L('Texte d’aide pour l’utilisateur.','Help text for the user.','Testo di aiuto per l’utente.')}, custom:{cls:'', title:{}}}[type] || Object.assign({title:BL[type] && !['timeline','contacts','documents','chart','pivot','aggregates','linked','stepper'].includes(type) ? L(BL[type].l[0], BL[type].l[1]) : {}}, BL[type] ? BL[type].def : {});
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
/* back to the table: EBX's own Close button of the service (kept in its button bar, given an id) */
function closeDesigner(){
  if (dirty && !confirm(c('closeDirty'))) return;
  const holder = document.getElementById('ldClose');
  const btn = holder && (/^(BUTTON|INPUT|A)$/.test(holder.tagName) ? holder : holder.querySelector('button,input,a'));
  if (btn){ dirty = false; btn.click(); } else window.history.back();
}
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
  if (t.closest('#ld-close')) return closeDesigner();
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
  const sbase = specBase();
  if (sbase){
    const sc = t.closest('[data-schoice]'); if (sc){ snapshot(); const [k, v] = sc.dataset.schoice.split('|'); setP(sbase, k, sc.dataset.num ? Number(v) : v); return commit(''); }
    const sd = t.closest('[data-scode]'); if (sd){ snapshot(); const i = sd.dataset.scode.indexOf('|'); const k = sd.dataset.scode.slice(0, i), v = sd.dataset.scode.slice(i + 1);
      const arr = (getP(sbase, k) || []).slice(); const j = arr.findIndex(x => String(x).toLowerCase() === v.toLowerCase()); if (j >= 0) arr.splice(j, 1); else arr.push(v); setP(sbase, k, arr); return commit(''); }
    const sdl = t.closest('[data-sdel]'); if (sdl){ snapshot(); const [k, i] = sdl.dataset.sdel.split('|'); const arr = getP(sbase, k); if (Array.isArray(arr)) arr.splice(+i, 1); return commit(''); }
    const sa = t.closest('[data-sadd]'); if (sa){ snapshot(); const k = sa.dataset.sadd; const arr = getP(sbase, k); const item = JSON.parse(sa.dataset.tpl || '{}'); if (Array.isArray(arr)) arr.push(item); else setP(sbase, k, [item]); return commit(''); }
    const sw = t.closest('[data-swhen]'); if (sw){ snapshot(); const [k, kind] = sw.dataset.swhen.split('|'); setP(sbase, k, kind === 'xpath' ? {xpath:''} : kind === 'value' ? {field:''} : {filled:''}); return commit(''); }
  }
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
  const sb = specBase();
  if (sb && t.dataset.sk != null){
    snapshot(); const k = t.dataset.sk, sv = t.dataset.sv;
    if (t.dataset.scond && !t.value){ const pk = k.replace(/\.field$/, ''); const q = getP(sb, pk); if (q){ ['field','in','notIn','filled','empty'].forEach(x => delete q[x]); if (!Object.keys(q).length) setP(sb, pk, undefined); } return commit(''); }
    const v = sv === 'num' ? (t.value === '' ? undefined : Number(t.value)) : sv === 'bool' ? t.checked : sv === 'codes' ? t.value.split(',').map(x => x.trim()).filter(Boolean) : t.value.trim();
    setP(sb, k, sv === 'bool' ? !!v : v); return commit('');
  }
  if (sb && t.dataset.sop){ snapshot(); const q = getP(sb, t.dataset.sop) || {}; const prev = q.in || q.notIn || []; ['in','notIn','filled','empty'].forEach(x => delete q[x]);
    if (t.value === 'in' || t.value === 'notIn') q[t.value] = prev; else q[t.value] = true; setP(sb, t.dataset.sop, q); return commit(''); }
  if (sb && t.dataset.sarr){ snapshot(); const i = t.dataset.sarr.indexOf('|'); const k = t.dataset.sarr.slice(0, i), v = t.dataset.sarr.slice(i + 1);
    const arr = (getP(sb, k) || []).filter(x => x !== v); if (t.checked) arr.push(v); setP(sb, k, arr); return commit(''); }
  if (sb && t.dataset.spush && t.value){ snapshot(); const arr = (getP(sb, t.dataset.spush) || []).slice(); arr.push(t.value); setP(sb, t.dataset.spush, arr); return commit(''); }
  if (sb && t.dataset.spushw && t.value){ snapshot(); const arr = (getP(sb, t.dataset.spushw) || []).slice(); arr.push({p:t.value, weight:1}); setP(sb, t.dataset.spushw, arr); return commit(''); }
  if (sb && t.dataset.stone){ snapshot(); const i = t.dataset.stone.indexOf('|'); const k = t.dataset.stone.slice(0, i), v = t.dataset.stone.slice(i + 1);
    const m = Object.assign({}, getP(sb, k) || {}); if (t.value) m[v] = t.value; else delete m[v]; setP(sb, k, Object.keys(m).length ? m : undefined); return commit(''); }
  if (sb && t.dataset.stadd && t.value.trim()){ snapshot(); const m = Object.assign({}, getP(sb, t.dataset.stadd) || {}); m[t.value.trim()] = 'accent'; setP(sb, t.dataset.stadd, m); return commit(''); }
  const Bk = sel.k === 'block' ? S.blocks[sel.id] : null;
  if (sel.k === 'tab' && t.dataset.count){ snapshot(); const tb = tabOf(sel.id); if (t.checked) tb.count.push(t.dataset.count); else tb.count = tb.count.filter(p => p !== t.dataset.count); return commit(''); }
  if (Bk && t.dataset.bk){ snapshot(); Bk[t.dataset.bk] = t.value; return commit(''); }
  if (Bk && t.dataset.nk){ snapshot(); const [o, k] = t.dataset.nk.split('.'); Bk[o] = Bk[o] || {}; Bk[o][k] = t.value.trim(); return commit(''); }
  if (Bk && t.id === 'ld-i-ohmax'){ snapshot(); Bk.max = Math.max(0, +t.value || 0); return commit(''); }
  if (Bk && t.dataset.padd && t.value){ snapshot(); (Bk[t.dataset.padd] = Bk[t.dataset.padd] || []).push(t.value); return commit(''); }
  if (Bk && t.dataset.sev){ snapshot(); Bk.sev = Bk.sev || {}; Bk.sev[t.dataset.sev] = t.checked; return commit(''); }
  if (Bk && t.dataset.ak){ snapshot(); Bk.actions[+t.dataset.ak][t.dataset.af] = t.value; return commit(''); }
  if (Bk && t.dataset.cardf){ snapshot(); Bk.cardFields = (Bk.cardFields || []).filter(p => p !== t.dataset.cardf); if (t.checked) Bk.cardFields.push(t.dataset.cardf); return commit(''); }
  const flags = {'ld-i-vlinks':'links', 'ld-i-vempty':'hideEmpty', 'ld-i-ro':'ro', 'ld-i-hc':'hideCreate', 'ld-i-mt':'tall'};
  if (Bk && flags[t.id]){ snapshot(); Bk[flags[t.id]] = t.checked; return commit(''); }
  if (Bk && t.id === 'ld-i-vmax'){ snapshot(); Bk.max = +t.value; return commit(''); }
  if (sel.k === 'field'){ const f = S.blocks[sel.b].fields[sel.i];
    if (t.id === 'ld-i-fw'){ snapshot(); if (WG[f.w]) WG[f.w].spec.forEach(x => { if (x.key) delete f[x.key.split('.')[0]]; }); f.w = t.value; if (isDisplayW(t.value) && !f.ro && !(F[f.p] || {}).fn){ f.ro = true; return commit(esc(c('explDisplayRo'))); } return commit(''); }
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
    <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">${B.helpUrl ? `<a class="btn ghost" href="${esc(B.helpUrl)}" target="_blank" rel="noopener">${esc(c('help'))}</a>` : ''}<span class="status" id="ld-use" role="status"></span><button type="button" class="btn ghost" id="ld-deploy">${esc(c('activation'))}</button><button type="button" class="btn ghost" id="ld-codebtn">${esc(c('code'))}</button><span class="status saved" id="ld-status"></span><button type="button" class="btn ghost" id="ld-undo" disabled>${esc(c('undo'))}</button><button type="button" class="btn primary" id="ld-save">${esc(c('save'))}</button><button type="button" class="btn ghost" id="ld-close" title="${esc(c('closeTip'))}">${esc(c('closeDesigner'))}</button></div>
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
