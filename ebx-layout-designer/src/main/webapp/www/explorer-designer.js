/* Layout Designer · "Design the explorer". Fed by window.LDX_BOOT (built by the Java service); posts back through EBX's
   own form: hidden fields ldxAction / ldxId / ldxLabel / ldxLayout, then a click on the hidden submit button ldxSubmit.
   The object edited is the explorer JSON itself (docs/EXPLORER-FORMAT.md), so keys this page does not know are kept. */
(function(){
'use strict';
const B = window.LDX_BOOT;
const root = document.getElementById('ldx-app');
if (!B || !root) return;

/* ======================= locales ======================= */
const LOCALES = B.locales && B.locales.length ? B.locales.slice() : ['en-US'];
const DEF = LOCALES.indexOf(B.defaultLocale) >= 0 ? B.defaultLocale : LOCALES[0];
let lang = LOCALES.indexOf(B.userLocale) >= 0 ? B.userLocale : DEF;
const UI = (B.userLocale || 'en').slice(0,2) === 'fr' ? 0 : 1;          // the designer's own words: French or English
const hasText = o => !!o && typeof o === 'object' && Object.keys(o).some(k => o[k]);
function T(o){ if (o == null) return ''; if (typeof o !== 'object') return String(o); return o[lang] || o[DEF] || Object.keys(o).map(k => o[k]).find(Boolean) || ''; }
function L(fr, en, it){ const o = {}; LOCALES.forEach(l => { const k = l.slice(0,2); const v = k === 'fr' ? fr : k === 'it' ? (it || en) : en; if (v) o[l] = v; }); return o; }
/* a copy of a text restricted to the instance locales (a model label may carry more) */
function txt(o){ const r = {}; if (o && typeof o === 'object') LOCALES.forEach(l => { if (o[l]) r[l] = o[l]; }); if (!hasText(r) && o){ const v = T(o); if (v) r[DEF] = v; } return r; }

/* the designer's own words */
const C = {
 title:['Explorateur','Explorer'], explorer:['Explorateur','Explorer'], newExplorer:['+ Nouvel explorateur','+ New explorer'], noExplorer:['(pas encore enregistré)','(not saved yet)'],
 variant:['Variante','Variant'], set:['Régler','Settings'], language:['Langue','Language'], asLabel:['Aperçu en tant que','Preview as'], asMe:['Moi (administrateur : tout)','Me (administrator: everything)'], asRole:['Rôle : {0}','Role: {0}'],
 mode:['Mode','Mode'], design:['Concevoir','Design'], preview:['Aperçu','Preview'], undo:['Annuler','Undo'], code:['</> Code','</> Code'], save:['Enregistrer','Save'], help:['Aide','Help'],
 open:['Ouvrir l’explorateur','Open the explorer'], openTip:['Ouvre la page enregistrée dans un nouvel onglet','Opens the saved page in a new tab'],
 dirty:['Non enregistré','Not saved'], saved:['Enregistré','Saved'], neverSaved:['Pas encore enregistré','Not saved yet'], inactive:['désactivé','turned off'],
 start:['Pour bien démarrer','Getting started'], suggest:['Partir d’une suggestion','Start from a suggestion'], reset:['Repartir de zéro','Start from scratch'], resetSure:['Confirmer : tout effacer','Confirm: clear everything'],
 stFacet:['Une facette','A facet'], stFlags:['Des drapeaux','Flags'], stTile:['Une tuile','A tile'], stCard:['Une carte complète','A full card'], stWidget:['Un widget choisi','A widget chosen'], stSearch:['La recherche','Search'], stRoles:['Des droits par rôle','Per-role visibility'], stVariant:['Une variante','A variant'], stAs:['Vérifier en tant que…','Check as…'],
 palette:['Palette','Palette'], fields:['Champs','Fields'], derived:['Valeurs calculées','Computed values'], elements:['Éléments','Elements'], psearch:['Rechercher un champ, un chemin…','Search a field, a path…'], noMatch:['Aucun champ ne correspond.','No field matches.'],
 derivedHint:['Une valeur calculée se lit une fois par reconstruction de l’index, jamais par carte affichée. L’ajouter quelque part la recopie dans la définition de l’explorateur.','A computed value is read once per index rebuild, never per card shown. Adding it somewhere copies it into the explorer’s definition.'],
 mediaHint:['Un champ média (DAMA) sert d’image de carte.','A media field (DAMA) serves as the card image.'],
 aFacet:['Facette','Facet'], aRanges:['Facette par plages','Range facet'], aCard:['Carte','Card'], aSearch:['Recherche','Search'], aTile:['Tuile','Tile'], aSort:['Tri','Sort'], remove:['Retirer','Remove'], removeTip:['Cliquer pour retirer','Click to remove'], add:['Ajouter','Add'], addDots:['+ Ajouter…','+ Add…'],
 elFlags:['Facette « À surveiller »','“Needs attention” facet'], elFlagsD:['Des drapeaux, chacun une condition sur la fiche.','Flags, each a condition on the record.'],
 elFav:['Facette « Mes favoris »','“My favorites” facet'], elFavD:['Les fiches étoilées par l’utilisateur.','The records the user starred.'],
 elTile:['Tuile','Tile'], elTileD:['Un chiffre et un filtre en un clic.','A number and a one-click filter.'], elVariant:['Variante','Variant'], elVariantD:['Une autre version de l’explorateur pour certains rôles.','Another version of the explorer for some roles.'],
 elActFav:['Action « Favori »','“Favorite” action'], elActFavD:['L’étoile de l’utilisateur.','The user’s star.'], elActOpen:['Action « Ouvrir la fiche »','“Open the record” action'], elActOpenD:['Ouvre la fiche dans EBX.','Opens the record in EBX.'],
 elWf:['Action « {0} »','“{0}” action'], elWfD:['Workflow {0}','Workflow {0}'], elService:['Action « Service »','“Service” action'], elServiceD:['Un service EBX (module@Service).','An EBX service (module@Service).'],
 elFlagTags:['Drapeaux sur la carte','Flags on the card'], elFlagTagsD:['Les pastilles « À surveiller » de chaque fiche.','Each record’s “Needs attention” pills.'],
 tiles:['Tuiles','Tiles'], tile:['Tuile','Tile'], facets:['Facettes','Facets'], facet:['Facette','Facet'], card:['Carte (modèle de chaque résultat)','Card (template of each result)'], cardShort:['Carte','Card'], searchZ:['Recherche','Search'],
 part:['Attribut de la carte','Card attribute'], image:['Image de la carte','Card image'], pill:['Pastille (en haut à droite)','Pill (top right)'], gauge:['Jauge','Gauge'], action:['Action','Action'],
 selected:['Sélection : {0}','Selected: {0}'], hintDesign:['Cliquez une zone, ou directement un attribut sur une carte, pour le régler à droite ; glissez un champ de la palette sur une zone pour l’y ajouter.','Click an area, or an attribute right on a card, to set it on the right; drag a field from the palette onto an area to add it.'],
 hintPreview:['Aperçu : l’explorateur tel que le rôle choisi le verra. Filtrez, cherchez, cliquez une tuile.','Preview: the explorer as the chosen role will see it. Filter, search, click a tile.'],
 noTile:['Aucune tuile','No tile'], noTileD:['Une tuile compte les fiches d’une condition et filtre en un clic. Ajoutez-en une depuis Éléments, ou glissez un champ ici.','A tile counts the records matching a condition and filters in one click. Add one from Elements, or drop a field here.'],
 noFacet:['Aucune facette','No facet'], noFacetD:['Glissez ici un champ de la palette, ou cliquez « + Facette » sur un champ.','Drop a field from the palette here, or click “+ Facet” on a field.'],
 searchPh:['Rechercher','Search'], records:['fiches','records'], none:['Aucune','None'], noRecord:['Aucune fiche ne répond à ces filtres.','No record matches these filters.'], emptySample:['La table ne contient encore aucune fiche : l’aperçu est vide.','The table holds no record yet: the preview is empty.'],
 sampleNote:['Aperçu sur les {0} premières fiches de la table ({1} en tout) ; les listes de valeurs longues et leurs compteurs viennent du serveur, sur toute la table.','Preview over the first {0} records of the table ({1} in all); long value lists and their counts come from the server, over the whole table.'],
 moreCards:['… et {0} autre(s) fiche(s) sur les pages suivantes.','… and {0} more record(s) on the next pages.'],
 variantShown:['Variante affichée','Variant shown'], permNote:['Les droits EBX (règles d’accès aux fiches, champs masqués) sont appliqués par le serveur sur la vraie page, pas dans cet aperçu ; ici, seuls les rôles du designer s’appliquent.','EBX permissions (record access rules, hidden fields) are applied by the server on the real page, not in this preview; here, only the designer’s roles apply.'],
 pickAs:['Choisissez « Aperçu en tant que » pour voir la variante et les éléments d’un rôle.','Pick “Preview as” to see a role’s variant and elements.'],
 closedStart:['Fermée au départ : un clic sur le titre l’ouvre.','Closed at start: a click on the title opens it.'], loading:['Chargement…','Loading…'], loadFailed:['Valeurs indisponibles.','Values unavailable.'],
 searchIn:['Chercher parmi {0} valeurs','Search {0} values'], found:['{0} valeur(s) trouvée(s)','{0} value(s) found'], ofMost:['{0} sur {1}, les plus fréquentes','{0} of {1}, most frequent first'], more10:['Afficher 10 de plus','Show 10 more'], less:['Réduire','Show less'], otherSearch:['Pour les {0} autres, cherchez.','For the other {0}, search.'],
 serverCounts:['comptes sur toute la table','counts over the whole table'], all:['Toutes','All'], myFavs:['Mes favoris','My favorites'], favPreview:['Les favoris de chaque utilisateur s’affichent sur la vraie page.','Each user’s favorites show on the real page.'],
 yes:['Oui','Yes'], no:['Non','No'], copy:['Copier','Copy'], copied:['Copié','Copied'], copiedV:['Copié : {0}','Copied: {0}'], openRec:['Ouvrir la fiche','Open'], favAdd:['Ajouter aux favoris','Add to favorites'],
 wouldOpen:['Ouvrirait la fiche dans EBX.','Would open the record in EBX.'], wouldFav:['Ajouterait la fiche aux favoris de l’utilisateur.','Would add the record to the user’s favorites.'], wouldWf:['Demanderait confirmation, puis lancerait {0}.','Would ask for confirmation, then launch {0}.'], wouldSvc:['Lancerait le service {0}.','Would run service {0}.'],
 compliant:['Fiche conforme','Record compliant'], noImage:['Aucune image DAMA pour cette fiche','No DAMA image for this record'],
 /* inspector */
 insp:['Réglages de la sélection','Selection settings'], wholeCard:['← Toute la carte','← Whole card'], up:['Monter','Up'], down:['Descendre','Down'], before:['← Avant','← Before'], after:['Après →','After →'], del:['Supprimer','Delete'], edit:['Régler','Edit'],
 cardD:['Le modèle de chaque résultat. Pour modifier un attribut déjà placé : cliquez-le sur n’importe quelle carte, ou sur sa ligne ci-dessous.','The template of each result. To change an attribute already placed: click it on any card, or on its row below.'],
 layout:['Disposition','Layout'], lVertical:['Verticale','Vertical'], lHorizontal:['Image à gauche','Image left'], lCompact:['Liste','List'], minWidth:['Largeur minimale (px)','Minimum width (px)'], edge:['Liseré en haut, de la couleur de la pastille','Top edge in the pill colour'],
 imageS:['Image','Image'], noMedia:['La table n’a aucun champ média (DAMA) : pas d’image possible.','The table has no media field (DAMA): no image possible.'], addImage:['+ Image depuis un champ média','+ Image from a media field'],
 titleF:['Titre','Title'], titleRec:['Libellé de la fiche (défaut)','Record label (default)'],
 overline:['Surtitre','Overline'], subtitle:['Sous-titre','Subtitle'], lines:['Lignes « libellé : valeur »','“Label: value” lines'], tags:['Étiquettes','Tags'], kpis:['Chiffres clés','Key figures'],
 pillSel:['Pastille de couleur (en haut à droite)','Colour pill (top right)'], gaugeSel:['Jauge','Gauge'], actions:['Actions et qui les voit','Actions and who sees them'], addAction:['+ Ajouter une action…','+ Add an action…'],
 permCard:['Droits EBX, en plus : un attribut dont le champ est masqué pour l’utilisateur disparaît de sa carte ; un bouton de workflow ne s’affiche qu’à qui peut lancer la publication.','EBX permissions, on top: an attribute whose field is hidden for the user leaves their card; a workflow button shows only to who may launch the publication.'],
 flagsOf:['Drapeaux « {0} »','“{0}” flags'], valueShown:['Valeur affichée','Value shown'], widget:['Widget','Widget'], exampleFrom:['Exemple tiré de la fiche {0}.','Example taken from record {0}.'],
 partLabel:['Libellé (vide : celui du champ)','Label (empty: the field’s)'], noLabel:['Cacher le libellé','Hide the label'], zone:['Zone de la carte','Card area'],
 permPart:['Même coché pour un rôle, l’attribut reste caché à qui ne peut pas lire {0}.','Even when ticked for a role, the attribute stays hidden from who cannot read {0}.'],
 visibleTo:['Visible pour','Visible to'], rolesSome:['Seuls ces rôles le voient. L’administrateur voit tout.','Only these roles see it. The administrator sees everything.'], rolesNone:['Aucun rôle coché : tout le monde le voit.','No role ticked: everyone sees it.'], noRoles:['Aucun rôle dans l’annuaire.','No role in the directory.'],
 field:['Champ','Field'], op:['Opérateur','Operator'], value:['Valeur','Value'], typeVal:['Tapez : {0} valeurs','Type: {0} values'], addValue:['+ valeur…','+ value…'], always:['Toujours','Always'], addCond:['+ Condition','+ Condition'], noCond:['Aucune condition','No condition'], rmCond:['Retirer la condition','Remove the condition'],
 compound:['Condition composée (toutes / l’une / non) : lisible ici, modifiable dans le JSON. « Simplifier » la remplace par sa première condition simple.','Compound condition (all / any / not): readable here, editable in the JSON. “Simplify” replaces it with its first simple condition.'], simplify:['Simplifier','Simplify'],
 o_in:['est parmi','is one of'], o_notIn:['n’est pas parmi','is not one of'], o_filled:['renseigné','filled'], o_empty:['vide','empty'], o_gt:['>','>'], o_lt:['<','<'], o_ge:['≥','≥'], o_le:['≤','≤'], o_eq:['=','='], o_ne:['≠','≠'], o_days:['dans les jours','within days'], dFrom:['à partir de J+','from day'], dTo:['jusqu’à J+ (exclu)','to day (excluded)'], daysHint:['Jours comptés depuis aujourd’hui (négatif = passé). Laisser vide : sans limite.','Days counted from today (negative = past). Empty: no limit.'],
 userLays:['Affichages au choix de l’utilisateur','Views the user may switch to'], userLaysHint:['Deux ou plus : la page montre un bouton pour passer de l’un à l’autre ; le choix est retenu par utilisateur.','Two or more: the page shows a switch; the choice is remembered per user.'],
 aLinked:['Fiches liées','Linked records'], linkedD:['Ouvre la table liée d’EBX filtrée sur cette fiche. Le nombre s’affiche sur le bouton ; sans fiche liée, le bouton est masqué.','Opens EBX’s linked table filtered on this record. The count shows on the button; with no linked record, the button is hidden.'], hideNone:['Masquer quand il n’y en a aucune','Hide when there is none'],
 groupBy:['Grouper par','Group by'], groupEmpty:['Titre du groupe sans valeur','Heading of the group without a value'], noGroupVal:['(vide)','(empty)'], noGroupBy:['(pas de groupes)','(no groups)'], groupHint:['Chaque groupe a son titre et son nombre de fiches ; le tri choisi s’applique dans chaque groupe.','Each group has a heading and its record count; the chosen sort applies inside each group.'],
 header:['En-tête de la page','Page header'], hCreate:['Bouton de création (formulaire EBX, si l’utilisateur peut créer)','Create button (EBX form, when the user may create)'], hLinks:['Liens','Links'], hAddLink:['+ Lien','+ Link'],
 hlTable:['Table du dataset (vide : celle-ci)','Table of the dataset (empty: this one)'], hlPred:['Filtre XPath (facultatif)','XPath filter (optional)'], hlSvc:['Service (facultatif, module@Service)','Service (optional, module@Service)'], hlUrl:['ou adresse (/… ou https://…)','or address (/… or https://…)'],
 colours:['Couleur par valeur','Colour per value'], coloursLong:['{0} valeurs : seules celles que vous choisissez ont une couleur, toutes les autres prennent la couleur par défaut. La liste n’est jamais chargée en entier.','{0} values: only those you pick get a colour, every other one takes the default colour. The list is never loaded whole.'],
 otherVals:['Toutes les autres valeurs','Every other value'], giveColour:['Donner une couleur à une valeur','Give a value a colour'], colourBtn:['+ Couleur','+ Colour'], foundN:['{0} trouvée(s)','{0} found'], mostFirst:['Les plus fréquentes d’abord.','Most frequent first.'],
 tone_ok:['Vert','Green'], tone_warn:['Orange','Amber'], tone_bad:['Rouge','Red'], tone_info:['Bleu','Blue'], tone_accent:['Violet','Purple'], tone_muted:['Gris','Grey'], neutral:['Neutre','Neutral'],
 prefix:['Avant la valeur','Before the value'], suffix:['Après la valeur','After the value'], unit:['Unité','Unit'], format:['Format','Format'], full:['Complet','Full'], short:['Court (k, M)','Short (k, M)'],
 maxEmpty:['Maximum (vide = le plus grand)','Maximum (empty = the largest)'], greenFrom:['Vert dès','Green from'], amberFrom:['Orange dès','Amber from'], nStars:['Nombre d’étoiles','Number of stars'],
 warnDays:['Orange si dans les N jours','Amber if within N days'], pastDate:['Date passée','Past date'], inRed:['En rouge','In red'], show:['Afficher','Show'], flagName:['Drapeau + nom','Flag + name'], flagOnly:['Drapeau','Flag'], codeOnly:['Code','Code'],
 noneText:['Texte quand aucun drapeau','Text when no flag'], shape:['Forme','Shape'], bar:['Barre','Bar'], ring:['Anneau','Ring'],
 position:['Position','Position'], sCover:['Bandeau','Banner'], sSide:['Côté','Side'], sLogo:['Logo','Logo'], sAvatar:['Rond','Round'], fit:['Cadrage','Fit'], fCover:['Remplir','Fill'], fContain:['Image entière','Whole image'],
 missing:['Fiche sans image','Record without image'], mInitials:['Initiales','Initials'], mHide:['Rien','Nothing'],
 imageD:['L’image est la version courante de l’asset DAMA du champ, servie par DAMA : aucun fichier n’est copié. Elle suit les droits du champ et ceux de DAMA.','The image is the current version of the field’s DAMA asset, served by DAMA: no file is copied. It follows the field’s permissions and DAMA’s.'],
 pillD:['Le liseré de la carte, s’il est activé, prend la même couleur.','The card edge, when on, takes the same colour.'],
 facetTitle:['Titre','Title'], wChecks:['Cases à cocher','Checkboxes'], wChips:['Boutons','Buttons'], wSelect:['Liste déroulante','Drop-down'], tooMany:['Trop de valeurs pour une liste déroulante','Too many values for a drop-down'],
 noSelect:['Liste déroulante impossible au-delà de 50 valeurs : cases ou boutons, avec une recherche.','No drop-down beyond 50 values: checkboxes or buttons, with a search.'], oneAtATime:['Une seule valeur à la fois.','One value at a time.'], several:['Plusieurs valeurs à la fois, comptées.','Several values at a time, counted.'],
 distinct:['Valeurs distinctes : {0}','Distinct values: {0}'], distinctLong:['Les plus fréquentes d’abord, puis « Afficher 10 de plus » jusqu’à 50, puis une recherche. Le navigateur ne reçoit jamais toute la liste : le serveur compte et renvoie une page.','Most frequent first, then “Show 10 more” up to 50, then a search. The browser never receives the whole list: the server counts and returns one page.'],
 topFirst:['Valeurs montrées d’abord','Values shown first'], openStart:['Ouverte au départ','Open at start'],
 flagsD:['Chaque drapeau est une condition sur la fiche. Une fiche peut en lever plusieurs ; ils peuvent aussi s’afficher sur la carte.','Each flag is a condition on the record. A record can raise several; they can also show on the card.'],
 nRecords:['{0} fiche(s)','{0} record(s)'], addFlag:['+ Ajouter un drapeau','+ Add a flag'], newFlag:['Nouveau drapeau','New flag'], colour:['Couleur','Colour'],
 rangesD:['Une plage compte les fiches dont la valeur est ≥ min et < max ; min ou max vide = sans borne.','A range counts the records whose value is ≥ min and < max; an empty min or max has no bound.'], addRange:['+ Ajouter une plage','+ Add a range'], min:['min','min'], max:['max','max'],
 parentPath:['Chemin du parent dans la table cible','Path of the parent in the target table'], labelsModel:['Libellés : ceux du modèle (énumération ou libellé de la fiche liée), dans chaque langue. Rien à saisir.','Labels: the model’s (enumeration or linked record label), in each language. Nothing to type.'],
 permFacet:['Droits EBX, en plus : masquée pour un utilisateur qui ne peut pas lire le champ ; les compteurs ne portent que sur ses fiches visibles.','EBX permissions, on top: hidden for a user who cannot read the field; counts only cover the records they see.'],
 favD:['Les fiches que l’utilisateur a étoilées (action « Favori » sur la carte).','The records the user starred (“Favorite” action on the card).'],
 tileLabel:['Libellé','Label'], numColour:['Couleur du chiffre','Number colour'], counts:['Compte les fiches qui…','Counts the records that…'], tCond:['répondent à une condition','match a condition'], tFlag:['ont un drapeau','have a flag'], tSet:['ont des valeurs de facettes','have facet values'],
 needFlags:['Ajoutez une facette « À surveiller » pour compter des drapeaux.','Add a “Needs attention” facet to count flags.'], setD:['La tuile applique ces valeurs de facettes :','The tile applies these facet values:'],
 tilesD:['Les chiffres en tête de l’explorateur. Un clic sur une tuile filtre la liste ; cliquez une tuile pour la régler.','The numbers at the top of the explorer. Clicking a tile filters the list; click a tile to set it.'], addTile:['+ Ajouter une tuile','+ Add a tile'], newTile:['Nouvelle tuile','New tile'],
 facetsD:['Cliquez une facette pour la régler. Une facette se crée depuis un champ (liste ou nombre), ou depuis Éléments pour des drapeaux.','Click a facet to set it. A facet is made from a field (list or number), or from Elements for flags.'],
 searchD:['Sans accent ni casse, sur le texte de l’index : pas de requête par frappe.','Accent- and case-insensitive, on the index text: no query per keystroke.'], searched:['Champs cherchés','Searched fields'], placeholder:['Texte d’invite','Placeholder'],
 sorts:['Tris proposés (le premier est le défaut)','Sort options (the first is the default)'], asc:['Croissant','Ascending'], desc:['Décroissant','Descending'], sortLabel:['Libellé (vide : celui du champ)','Label (empty: the field’s)'],
 permSearch:['Droits EBX : un champ masqué pour l’utilisateur n’est ni cherché ni proposé au tri.','EBX permissions: a field hidden for the user is neither searched nor offered as a sort.'],
 variantD:['Une variante est une version complète de l’explorateur. Un utilisateur voit la première variante (après la première) dont il a un des rôles, sinon la première.','A variant is a complete version of the explorer. A user sees the first variant (after the first) one of whose roles they hold, else the first one.'],
 vName:['Nom','Name'], forRoles:['Pour les rôles','For roles'], firstVariant:['Première variante : pour tous les rôles sans variante à eux.','First variant: for every role without a variant of its own.'], pageSize:['Cartes par page','Cards per page'],
  asOfS:['Date « en vigueur au »','“In force on” date'], asOfFrom:['Champ de début (date d’effet)','Start field (effective date)'], asOfTo:['Champ de fin (vide = sans fin)','End field (empty = open-ended)'], asOfLabel:['Libellé du sélecteur','Label of the date input'], asOfNone:['(aucune : toutes les lignes)','(none: every row)'],
  asOfHint:['Une date choisie sur la page ; seules les lignes dont début ≤ date ≤ fin (ou fin vide) comptent, dans les facettes aussi. La date suit l’utilisateur d’un explorateur à l’autre et jusqu’au bloc « Hiérarchie à une date » des fiches.','A date chosen on the page; only the rows whose start ≤ date ≤ end (or empty end) count, in the facets too. The date follows the user from one explorer to the next and into the “Hierarchy at a date” block of the forms.'],
  freshness:['Fraîcheur des résultats','Freshness of the results'], frOnDemand:['À la demande (recommandé)','On demand (recommended)'], frAuto:['Automatique','Automatic'],
  frMaxAge:['Âge maximum (minutes)','Maximum age (minutes)'],
  frHint:['À la demande : les résultats calculés sont partagés et gardés jusqu’à « Actualiser » ou l’âge maximum ; la page dit quand ils ont été calculés et si des données ont changé depuis. Automatique : recalculés dès qu’une table lue change (au plus une minute de retard) ; à réserver aux petits volumes ou à peu d’utilisateurs.','On demand: computed results are shared and kept until "Refresh" or the maximum age; the page says when they were computed and whether data changed since. Automatic: computed again as soon as a table read changes (at most a minute late); keep it for small volumes or few users.'],
 dupVariant:['Dupliquer en nouvelle variante','Duplicate as a new variant'], delVariant:['Supprimer la variante','Delete the variant'], allVariants:['Toutes les variantes','All variants'], neverShown:['aucun rôle : jamais vue','no role: never shown'], newVariantOpt:['+ Nouvelle variante (copie)','+ New variant (copy)'],
 explorerS:['Explorateur','Explorer'], rowLabel:['Nom de l’explorateur','Explorer name'], pageTitle:['Titre de la page','Page title'], openTo:['Qui peut ouvrir l’explorateur','Who may open the explorer'], openToHint:['Aucun rôle coché : quiconque peut lire la table.','No role ticked: anyone who may read the table.'],
 active:['Actif','Active'], isOff:['Désactivé : la page répond « Cet explorateur est désactivé ».','Turned off: the page answers “This explorer is turned off”.'], activate:['Activer','Turn on'], deactivate:['Désactiver','Turn off'], delExplorer:['Supprimer l’explorateur','Delete the explorer'],
 permVariant:['Droits EBX, en plus : il faut aussi le droit de lire la table ; chaque utilisateur ne compte et ne voit que ses fiches visibles.','EBX permissions, on top: reading the table is required too; each user only counts and sees their visible records.'],
 aOpen:['Ouvrir la fiche','Open the record'], aFav:['Favori','Favorite'], aWf:['Workflow','Workflow'], aSvc:['Service','Service'], wfPub:['Publication du workflow','Workflow publication'], wfVar:['Variable qui reçoit le XPath de la fiche','Variable receiving the record’s XPath'],
 wfVarMissing:['Indiquez la variable du workflow qui reçoit la fiche.','Give the workflow variable that receives the record.'], wfSingle:['Une seule demande en cours par fiche','One running request per record'], btnLabel:['Libellé du bouton','Button label'], shownIf:['Affiché seulement si','Shown only if'],
 svcKey:['Service (module@Service)','Service (module@Service)'], actionD:['Un workflow demande confirmation, se lance comme l’utilisateur avec le XPath de la fiche, puis ouvre la première tâche.','A workflow asks for confirmation, launches as the user with the record’s XPath, then opens the first task.'],
 /* dialogs, toasts */
 codeTitle:['Ce qui sera enregistré','What will be saved'], codeHint:['Une ligne de la table ExplorerLayout, relue à chaque ouverture : pas de redémarrage.','One row of the ExplorerLayout table, read at each opening: no restart.'], close:['Fermer','Close'], cancel:['Annuler','Cancel'],
 newTitle:['Nouvel explorateur sur « {0} »','New explorer on “{0}”'], newName:['Nom','Name'], create:['Créer','Create'], lose:['Les modifications non enregistrées seront perdues.','Unsaved changes will be lost.'],
 switchTitle:['Ouvrir un autre explorateur','Open another explorer'], goOn:['Continuer','Continue'], delTitle:['Supprimer « {0} » ?','Delete “{0}”?'], delText:['L’explorateur disparaît pour tous ses utilisateurs. Les liens vers lui ouvriront le premier explorateur actif de la table.','The explorer goes for all its users. Links to it will open the table’s first active explorer.'],
 tAdded:['« {0} » ajouté. Réglez-le à droite.','“{0}” added. Set it on the right.'], tFacet:['Facette « {0} » ajoutée.','Facet “{0}” added.'], tSearch:['« {0} » est cherché.','“{0}” is searched.'], tTile:['Tuile ajoutée : réglez sa condition à droite.','Tile added: set its condition on the right.'], tSort:['Tri sur « {0} » ajouté.','Sort on “{0}” added.'],
 tRemoved:['« {0} » retiré. « Annuler » le remet.','“{0}” removed. “Undo” puts it back.'], tKeepSearch:['La recherche garde au moins un champ.','Search keeps at least one field.'], tUndo:['Dernière modification annulée.','Last change undone.'],
 tFacetKind:['Une facette se fait sur une liste, un texte court ou un nombre.','A facet needs a list, a short text or a number.'], tSearchKind:['La recherche porte sur du texte.','Search works on text.'],
 tFlagAdded:['Drapeau ajouté : écrivez sa condition à droite.','Flag added: write its condition on the right.'], tVariant:['Nouvelle variante, copie de la précédente : cochez ses rôles, puis modifiez-la.','New variant, a copy of the previous one: tick its roles, then change it.'],
 tSuggest:['Suggestion chargée. « Annuler » revient en arrière.','Suggestion loaded. “Undo” goes back.'], tEmpty:['Explorateur vide. « Annuler » revient en arrière.','Empty explorer. “Undo” goes back.'], tDeleted:['Supprimé. « Annuler » le remet.','Deleted. “Undo” puts it back.'],
 rangeLo:['Moins de {0}','Below {0}'], rangeMid:['De {0} à moins de {1}','{0} to under {1}'], rangeHi:['{0} et plus','{0} and above'], needs:['À surveiller','Needs attention'], toComplete:['À compléter','To complete'], possibleDup:['Doublon possible','Possible duplicate'],
 ty_str:['Texte','Text'], ty_text:['Texte long','Long text'], ty_int:['Entier','Integer'], ty_dec:['Nombre','Number'], ty_date:['Date','Date'], ty_dt:['Date-heure','Date-time'], ty_bool:['Oui/non','Yes/no'], ty_enum:['Liste','List'], ty_fk:['Clé étr.','Foreign key'], ty_list:['Multiple','Multiple'], ty_media:['Image','Image'], ty_flags:['Drapeaux','Flags'],
 dt_stateAt:['Valeur de l’état en vigueur à la date','Value of the state in force on the date'], dt_count:['Comptage','Count'], dt_lookup:['Valeur liée','Lookup'], dt_sameValue:['Doublons','Duplicates'], dt_firstOf:['Premier renseigné','First filled'], dt_case:['Selon conditions','By conditions'], dt_test:['Test','Test'], dt_suspects:['Suspects M&M','M&M suspects'], dt_provided:['Fourni par un module','Provided by a module'],
 w_text:['Texte','Text'], w_bold:['Gras','Bold'], w_mono:['Code','Code'], w_copy:['Copiable','Copyable'], w_tag:['Étiquette','Tag'], w_link:['Lien vers la fiche','Record link'], w_pill:['Pastille colorée','Colour pill'], w_dot:['Point coloré','Coloured dot'], w_country:['Pays avec drapeau','Country with flag'],
 w_num:['Nombre','Number'], w_money:['Montant','Amount'], w_bar:['Barre','Bar'], w_ring:['Anneau','Ring'], w_level:['Pastille de seuil','Threshold pill'], w_stars:['Étoiles','Stars'], w_alert:['Alerte si non nul','Alert when not zero'], w_date:['Date','Date'], w_relative:['Date relative','Relative date'], w_flags:['Drapeaux','Flags'],
 wd_text:['Tel quel','As is'], wd_bold:['Mis en avant','Emphasized'], wd_mono:['Police à chasse fixe','Monospace'], wd_copy:['Avec un bouton Copier','With a Copy button'], wd_tag:['Encadré gris','Grey tag'], wd_link:['Ouvre la fiche liée','Opens the linked record'], wd_pill:['Couleur par valeur','Colour per value'], wd_dot:['Point de couleur + libellé','Coloured dot + label'], wd_country:['Drapeau + nom','Flag + name'],
 wd_num:['Chiffre seul','Figure only'], wd_money:['Avec l’unité','With the unit'], wd_bar:['Proportion du maximum','Share of the maximum'], wd_ring:['Jauge circulaire','Circular gauge'], wd_level:['Vert, orange, rouge selon seuils','Green, amber, red by thresholds'], wd_stars:['Note en étoiles','Star rating'], wd_alert:['Orange quand > 0','Amber when > 0'], wd_date:['Date courte','Short date'], wd_relative:['« il y a 3 mois », « dans 10 j »','“3 mo ago”, “in 10 d”'], wd_flags:['Les drapeaux levés','The raised flags'],
 today:['aujourd’hui','today'],
 sw:['Concevoir','Design'], swForm:['La fiche','The form'], swExpl:['L’explorateur','The explorer'],
 outdated:['Le modèle du module doit être rechargé : redémarrez le serveur une fois. L’enregistrement est désactivé d’ici là.','The module’s data model must be reloaded: restart the server once. Saving is disabled until then.'],
 noneYet:['La table « {0} » n’a pas encore d’explorateur.','Table “{0}” has no explorer yet.'], noneYetD:['Un explorateur est une page de recherche sur la table : recherche, facettes, tuiles et une carte par fiche. Créez-le, puis composez-le ici.','An explorer is a search page over the table: search, facets, tiles and one card per record. Create it, then build it here.'], createExpl:['Créer un explorateur','Create an explorer'], ago:['il y a {0}','{0} ago'], inN:['dans {0}','in {0}'], uDay:['j','d'], uMonth:['mois','mo'], uYear:['an','y'], uYears:['ans','y']
};
const c = (k, ...a) => { let s = (C[k] || [k,k])[UI]; a.forEach((v,i) => { s = s.split('{'+i+'}').join(v); }); return s; };
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const $ = s => root.querySelector(s);
const clone = o => o == null ? o : JSON.parse(JSON.stringify(o));
const uid = p => p + Math.random().toString(36).slice(2, 7);
const fmtN = n => { try { return Number(n).toLocaleString(lang); } catch (x) { return String(n); } };
const norm = s => String(s == null ? '' : s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/* ======================= model ======================= */
const FIELDS = (B.fields || []).filter(f => f && f.p);
const FM = {}; FIELDS.forEach(f => { FM[f.p] = f; });
const GROUPS = {}; (B.groups || []).forEach(g => { GROUPS[g.p] = g.lab; });
const SUGG = {}; (B.derivedSuggestions || []).forEach(s => { if (s && s.name) SUGG[s.name] = s; });
const ROLES = B.roles || [];
const roleLab = k => { const r = ROLES.find(x => x.k === k); return r ? T(r.l) || k : k; };
const WFS = B.workflows || [];
const ROWS = (B.sample && B.sample.rows) || [];
const TOTAL = B.sample && B.sample.total != null ? B.sample.total : ROWS.length;
const M = B.model || {};
const TABLE = T(M.tableLabel) || M.table || '';
const LISTS = ['overline','subtitle','lines','tags','kpis'];
const TONES = ['ok','warn','bad','info','accent','muted'];
const OPS = ['in','notIn','filled','empty','gt','lt','ge','le','eq','ne','days'];
const TOP = 10, MAXLIST = 50;
const DT = {count:'int', sameValue:'int', test:'bool', suspects:'list', lookup:'str', firstOf:'str', case:'str', provided:'str', stateAt:'str'};

let D, vi = 0, X, label = '';
let MC = {}, SIDX = {};                    // per-render caches: field metadata, sample index
const flush = () => { MC = {}; SIDX = {}; };

const isDer = ref => typeof ref === 'string' && ref.indexOf('derived:') === 0;
const isFlags = ref => typeof ref === 'string' && ref.indexOf('flags:') === 0;
const defOf = name => (D && D.derived && D.derived[name]) || (SUGG[name] && SUGG[name].def) || null;
/* a reference: "/path", "derived:name" or (card parts only) "flags:<facet id>" */
function meta(ref){
  if (!ref) return null;
  if (MC[ref]) return MC[ref];
  let m;
  if (isDer(ref)){
    const name = ref.slice(8), d = defOf(name), s = SUGG[name];
    let t = d ? DT[d.type] || 'str' : 'str';
    if (t === 'str'){ const vs = ROWS.map(r => r.d ? r.d[name] : null).filter(v => v != null && v !== ''); if (vs.length && vs.every(v => typeof v === 'number')) t = 'dec'; }
    m = {ref, name, derived:true, def:d, t, lab:s ? s.lab : null};
  } else if (isFlags(ref)) m = {ref, flags:ref.slice(6), t:'flags'};
  else m = FM[ref] ? Object.assign({ref}, FM[ref]) : {ref, t:'str', missing:true};
  return MC[ref] = m;
}
function labelOf(ref){
  const m = meta(ref); if (!m) return '';
  if (m.flags){ const fc = X && X.facets.find(f => f.id === m.flags); return c('flagsOf', fc ? T(fc.title) : m.flags); }
  return T(m.lab) || (m.derived ? m.name : ref);
}
function kind(m){
  if (!m) return 'text';
  const t = m.t;
  if (t === 'flags') return 'flags';
  if (t === 'int' || t === 'dec') return 'num';
  if (t === 'date' || t === 'dt') return 'date';
  if (t === 'media') return 'media';
  if (t === 'bool' || t === 'enum' || t === 'fk' || t === 'list' || (m.derived && m.def && m.def.type === 'case')) return 'list';
  return 'text';
}
const kindOf = ref => kind(meta(ref));
const facetable = ref => { const m = meta(ref); return kind(m) === 'list' || m.t === 'str'; };
const searchable = ref => { const k = kindOf(ref); return k === 'text' || k === 'list'; };
function isCountry(m){
  if (!m || m.derived) return false;
  if (/pays|countr|land|nation/i.test(m.ref + ' ' + (m.target || ''))) return true;
  const ks = ROWS.map(r => rv(r, m.ref)).filter(Boolean).map(v => v.k);
  return ks.length > 0 && ks.every(k => /^[A-Z]{2}$/.test(k));
}
const refOf = o => !o ? '' : o.flags != null ? 'flags:' + o.flags : o.derived != null ? 'derived:' + o.derived : o.field || '';
function setRef(o, ref){
  delete o.field; delete o.derived; delete o.flags;
  if (isFlags(ref)) o.flags = ref.slice(6); else if (isDer(ref)) o.derived = ref.slice(8); else o.field = ref;
  useRef(ref);
}
/* a computed value taken from the palette is copied into the explorer's own definitions */
function useRef(ref){ if (!isDer(ref)) return; const n = ref.slice(8); if (!D.derived[n] && SUGG[n]){ D.derived[n] = clone(SUGG[n].def); flush(); } }
/* the references offered in pickers: fields (no media), then computed values (the palette's and the explorer's own) */
function allRefs(filter){
  const out = FIELDS.filter(f => f.t !== 'media').map(f => f.p);
  Object.keys(SUGG).concat(Object.keys(D.derived || {}).filter(n => !SUGG[n])).forEach(n => out.push('derived:' + n));
  return filter ? out.filter(filter) : out;
}
function refOptions(cur, filter, none){
  const refs = allRefs(filter); if (cur && refs.indexOf(cur) < 0 && !isFlags(cur)) refs.unshift(cur);
  const f = refs.filter(r => !isDer(r)), d = refs.filter(isDer);
  const opt = r => `<option value="${esc(r)}"${r === cur ? ' selected' : ''}>${esc(labelOf(r))}</option>`;
  return (none ? `<option value="">${esc(none)}</option>` : '') + (f.length ? `<optgroup label="${esc(c('fields'))}">${f.map(opt).join('')}</optgroup>` : '') + (d.length ? `<optgroup label="${esc(c('derived'))}">${d.map(opt).join('')}</optgroup>` : '');
}

/* ======================= values ======================= */
function normVal(x){
  if (x == null || x === '') return null;
  if (Array.isArray(x)){ if (!x.length) return null; const a = x.map(v => v && typeof v === 'object' ? String(v.l != null ? v.l : v.k) : String(v)); return {k:a.join(', '), arr:a, l:a.join(', '), n:a.length}; }
  if (typeof x === 'number') return {k:String(x), l:fmtN(x), n:x};
  if (typeof x === 'boolean') return {k:String(x), l:x ? c('yes') : c('no'), n:null};
  if (typeof x === 'object'){
    if (Array.isArray(x.k)){ if (!x.k.length) return null; const a = x.k.map(String); return {k:a.join(', '), arr:a, l:Array.isArray(x.l) ? x.l.join(', ') : String(x.l != null ? x.l : a.join(', ')), n:a.length}; }
    if (x.k == null || x.k === '') return null;
    return {k:String(x.k), l:String(x.l != null ? x.l : x.k), n:typeof x.n === 'number' ? x.n : null, d:x.d || null};
  }
  const s = String(x);
  return {k:s, l:s, n:/^-?\d+(\.\d+)?$/.test(s) ? parseFloat(s) : null};
}
function rv(row, ref){
  if (!row || !ref || isFlags(ref)) return null;
  return normVal(isDer(ref) ? (row.d ? row.d[ref.slice(8)] : null) : (row.v ? row.v[ref] : null));
}
const keysOf = v => v ? (v.arr || [v.k]) : [];
const numOf = v => v == null ? null : v.n != null ? v.n : (isFinite(parseFloat(v.k)) ? parseFloat(v.k) : null);
function sampleIdx(ref){
  if (SIDX[ref]) return SIDX[ref];
  const labs = {}, cnt = {}, order = [];
  ROWS.forEach(r => { const v = rv(r, ref); if (!v) return; keysOf(v).forEach(k => { if (!(k in cnt)){ cnt[k] = 0; order.push(k); labs[k] = v.arr ? k : v.l; } cnt[k]++; }); });
  return SIDX[ref] = {labs, cnt, order};
}
const LABS = {};                              // labels the server sent for long lists
function vlabel(ref, k){
  const m = meta(ref);
  if (m && m.vals){ const e = m.vals.find(x => String(x.v) === String(k)); if (e) return T(e.lab) || String(k); }
  if (LABS[ref] && LABS[ref][k] != null) return LABS[ref][k];
  const ix = sampleIdx(ref); if (ix.labs[k] != null) return ix.labs[k];
  if (m && m.t === 'bool') return k === 'true' ? c('yes') : k === 'false' ? c('no') : String(k);
  return String(k);
}
/* the values of a short list: the enumeration in the model's order, else the sample's, most frequent first */
function shortValues(ref){
  const m = meta(ref), ix = sampleIdx(ref), out = [], seen = {};
  if (m && m.vals) m.vals.forEach(e => { const k = String(e.v); seen[k] = 1; out.push({v:k, l:T(e.lab) || k, n:ix.cnt[k] || 0}); });
  if (m && m.t === 'bool') ['true','false'].forEach(k => { if (!seen[k]){ seen[k] = 1; out.push({v:k, l:vlabel(ref, k), n:ix.cnt[k] || 0}); } });
  const rest = ix.order.filter(k => !seen[k]).map(k => ({v:k, l:ix.labs[k], n:ix.cnt[k]}));
  rest.sort((a, b) => b.n - a.n || String(a.l).localeCompare(String(b.l)));
  return out.concat(rest);
}
function distinctTotal(ref){ const m = meta(ref); return Math.max(m && m.n || 0, shortValues(ref).length); }
const isLong = ref => distinctTotal(ref) > 12;

/* one page of values from the server (never the whole list); cached; null while it is on its way */
const AJ = {};
let soonT = 0;
function soon(){ clearTimeout(soonT); soonT = setTimeout(() => keepFocus(() => { renderCanvas(); renderInspector(); }), 0); }
function localValues(ref, q, off, n){
  const nq = norm(q), all = shortValues(ref).filter(x => !nq || norm(x.l).indexOf(nq) >= 0);
  return {total:all.length, items:all.slice(off, off + n)};
}
function values(ref, q, off, n, cb){
  q = q || '';
  const key = [ref, q, off, n].join('\u0001'), e = AJ[key];
  if (e){ if (e.data) return e.data; if (cb) e.cbs.push(cb); else e.render = true; return null; }
  const url0 = B.ajax && B.ajax.values, H = window.EBX_AJAXResponseHandler;
  if (!url0 || typeof H !== 'function'){ const data = localValues(ref, q, off, n); AJ[key] = {data}; return data; }
  const ent = AJ[key] = {data:null, cbs:cb ? [cb] : [], render:!cb};
  const done = d => { ent.data = d; const lc = LABS[ref] = LABS[ref] || {}; d.items.forEach(x => { lc[x.v] = x.l; }); const cbs = ent.cbs; ent.cbs = []; cbs.forEach(f => f(d)); if (ent.render) soon(); };
  const h = new H();
  h.handleAjaxResponseSuccess = text => {
    let d = null; try { d = JSON.parse(text); } catch (x) { d = null; }
    done(d && Array.isArray(d.items) ? {total:+d.total || d.items.length, items:d.items.map(x => ({v:String(x.v), l:x.l != null ? String(x.l) : String(x.v), n:+x.n || 0}))} : {total:0, items:[], error:true});
  };
  h.handleAjaxResponseFailed = () => done({total:0, items:[], error:true});
  const url = url0 + (url0.indexOf('?') >= 0 ? '&' : '?') + 'f=' + encodeURIComponent(ref) + '&q=' + encodeURIComponent(q) + '&off=' + off + '&n=' + n;
  try { h.sendRequest(url); } catch (x) { done({total:0, items:[], error:true}); }
  return ent.data;
}

/* ======================= the explorer being edited ======================= */
function emptyLayout(){
  const s = FIELDS.find(f => f.t === 'str');
  return {search:{fields:s ? [s.p] : []}, facets:[], tiles:[], sorts:[], pageSize:48,
    card:{layout:'vertical', minWidth:260, overline:[], subtitle:[], lines:[], tags:[], kpis:[], actions:[{action:'open'}]}};
}
const stdName = () => L('Standard', 'Standard', 'Standard');
function emptyExplorer(){ return {format:'ebx-layout-designer/explorer', version:1, title:txt(M.tableLabel), openTo:[], derived:{}, variants:[{id:'std', name:stdName(), roles:[], layout:emptyLayout()}]}; }
function normLayout(l){
  l = l && typeof l === 'object' ? l : emptyLayout();
  if (!l.search || typeof l.search !== 'object') l.search = {};
  if (!Array.isArray(l.search.fields)) l.search.fields = [];
  ['facets','tiles','sorts'].forEach(k => { if (!Array.isArray(l[k])) l[k] = []; });
  if (!l.card || typeof l.card !== 'object') l.card = {};
  const cd = l.card;
  if (!cd.layout) cd.layout = 'vertical';
  LISTS.forEach(k => { if (!Array.isArray(cd[k])) cd[k] = []; });
  if (!Array.isArray(cd.actions)) cd.actions = [];
  l.facets.forEach(f => {
    if (!f.id) f.id = uid('f'); if (!f.kind) f.kind = 'field'; if (f.open == null) f.open = true; if (!f.widget) f.widget = 'checks';
    if (f.kind === 'flags' && !Array.isArray(f.flags)) f.flags = []; if (f.kind === 'ranges' && !Array.isArray(f.ranges)) f.ranges = [];
    (f.flags || []).forEach(fl => { if (!fl.id) fl.id = uid('FL'); });
  });
  l.tiles.forEach(t => { if (!t.id) t.id = uid('t'); });
  return l;
}
function normalize(d){
  d = d && typeof d === 'object' && !Array.isArray(d) ? d : emptyExplorer();
  if (!d.format) d.format = 'ebx-layout-designer/explorer';
  if (!d.version) d.version = 1;
  if (!Array.isArray(d.openTo)) d.openTo = [];
  if (!d.derived || typeof d.derived !== 'object') d.derived = {};
  if (!Array.isArray(d.variants) || !d.variants.length){ d.variants = [{id:'std', name:stdName(), roles:[], layout:d.layout || emptyLayout()}]; delete d.layout; }
  d.variants.forEach((v, i) => { if (!v.id) v.id = i ? uid('v') : 'std'; if (!v.name) v.name = i ? L('Variante ' + i, 'Variant ' + i) : stdName(); if (!Array.isArray(v.roles)) v.roles = []; v.layout = normLayout(v.layout); });
  return d;
}
const CUR = B.current || null;
const NOEXPL = !CUR || CUR.id == null;          // the table has no explorer yet: an empty state asks to create one
const OUTDATED = !!B.modelOutdated;
const EXPL = B.explorers || [];
D = normalize(CUR && CUR.layout ? clone(CUR.layout) : null);
label = (CUR && CUR.label) || T(M.tableLabel) || c('title');
X = D.variants[0].layout;

const S = {pick:{k:'card'}, pt:'fields', as:'', mode:'design', q:'', sel:{}, tile:null, sort:0, more:{}, fq:{}, tq:'', tmore:10, triedAs:false};
let HIST = [], dirty = false;
function snap(){ HIST.push(JSON.stringify({D, vi, label})); if (HIST.length > 100) HIST.shift(); dirty = true; }
function useVariant(i){ vi = Math.max(0, Math.min(i, D.variants.length - 1)); X = D.variants[vi].layout; S.sel = {}; S.tile = null; S.sort = 0; S.more = {}; S.fq = {}; flush(); }
const findFacet = id => X.facets.find(f => f.id === id);
const findTile = id => X.tiles.find(t => t.id === id);

/* ======================= evaluation over the sample ======================= */
const rolesOf = el => el && Array.isArray(el.roles) ? el.roles : [];
/* the designer's own audience: empty roles = everyone; previewing as the administrator shows everything */
const forMe = el => !S.as || !rolesOf(el).length || rolesOf(el).indexOf(S.as) >= 0;
function variantFor(role){ if (!role) return vi; for (let i = 1; i < D.variants.length; i++) if (rolesOf(D.variants[i]).indexOf(role) >= 0) return i; return 0; }
const opsOf = cd => cd && typeof cd === 'object' ? OPS.filter(o => o in cd) : [];
const isCompound = cd => !!cd && (Array.isArray(cd.all) || Array.isArray(cd.any) || cd.not != null || opsOf(cd).length > 1);
function test(row, cd){
  if (!cd || typeof cd !== 'object') return true;
  if (Array.isArray(cd.all)) return cd.all.every(x => test(row, x));
  if (Array.isArray(cd.any)) return cd.any.some(x => test(row, x));
  if (cd.not != null) return !test(row, cd.not);
  const ref = refOf(cd); if (!ref) return true;
  const v = rv(row, ref), ks = keysOf(v), x = numOf(v);
  let ok = true;
  const list = a => (Array.isArray(a) ? a : [a]).map(String);
  if ('in' in cd) ok = ok && ks.some(k => list(cd.in).indexOf(k) >= 0);
  if ('notIn' in cd) ok = ok && !ks.some(k => list(cd.notIn).indexOf(k) >= 0);
  if (cd.filled) ok = ok && !!v;
  if (cd.empty) ok = ok && !v;
  [['gt', (a, b) => a > b], ['lt', (a, b) => a < b], ['ge', (a, b) => a >= b], ['le', (a, b) => a <= b]].forEach(([op, f]) => { if (op in cd){ const y = parseFloat(cd[op]); ok = ok && x != null && isFinite(y) && f(x, y); } });
  const same = y => { const ny = parseFloat(y); return x != null && /^-?\d+(\.\d+)?$/.test(String(y).trim()) ? x === ny : ks.indexOf(String(y)) >= 0; };
  if ('days' in cd){
    const k = ks[0] ? String(ks[0]).slice(0, 10) : null, d = cd.days || {}, day = n => { const t = new Date(); t.setHours(12, 0, 0, 0); t.setDate(t.getDate() + n); return t.toISOString().slice(0, 10); };
    ok = ok && !!k && (d.from == null || d.from === '' || k >= day(+d.from)) && (d.to == null || d.to === '' || k < day(+d.to));
  }
  if ('eq' in cd) ok = ok && same(cd.eq);
  if ('ne' in cd) ok = ok && !same(cd.ne);
  return ok;
}
const flagFacets = () => X.facets.filter(f => f.kind === 'flags');
const allFlags = () => [].concat(...flagFacets().map(f => f.flags || []));
const flagsOf = row => allFlags().filter(fl => test(row, fl.when));
function facetVals(fc, row){
  if (fc.kind === 'flags') return (fc.flags || []).filter(fl => test(row, fl.when)).map(fl => fl.id);
  if (fc.kind === 'ranges'){ const x = numOf(rv(row, fc.field)); if (x == null) return []; return (fc.ranges || []).filter(r => (r.min == null || r.min === '' || x >= +r.min) && (r.max == null || r.max === '' || x < +r.max)).map(r => String(r.code)); }
  if (fc.kind === 'favorites') return [];
  return keysOf(rv(row, fc.field));
}
function facetLab(fc, v){
  if (fc.kind === 'flags'){ const fl = (fc.flags || []).find(x => x.id === v); return fl ? T(fl.label) || fl.id : v; }
  if (fc.kind === 'ranges'){ const r = (fc.ranges || []).find(x => String(x.code) === v); return r ? T(r.label) || r.code : v; }
  return vlabel(fc.field, v);
}
const visibleFacets = () => X.facets.filter(forMe);
const visibleTiles = () => X.tiles.filter(forMe);
function tileTest(row, t){
  if (t.flag) return flagsOf(row).some(fl => fl.id === t.flag);
  if (t.set && typeof t.set === 'object') return Object.keys(t.set).every(fid => { const fc = findFacet(fid); const want = (t.set[fid] || []).map(String); return !fc || !want.length || facetVals(fc, row).some(v => want.indexOf(v) >= 0); });
  return test(row, t.when);
}
function fails(row, skip){
  const fs = visibleFacets();
  for (const fc of fs){
    if (fc.id === skip) continue;
    const sel = S.sel[fc.id] || {}, on = Object.keys(sel).filter(k => sel[k]);
    if (on.length && !facetVals(fc, row).some(v => on.indexOf(v) >= 0)) return true;
  }
  if (S.tile){ const t = findTile(S.tile); if (t && !tileTest(row, t)) return true; }
  if (S.q){
    const q = norm(S.q), hay = norm(X.search.fields.map(ref => { const v = rv(row, ref); return v ? v.l + ' ' + v.k : ''; }).join(' ') + ' ' + (row.label || ''));
    if (hay.indexOf(q) < 0) return true;
  }
  return false;
}

/* ======================= drawing a value with its widget ======================= */
const maxOf = ref => Math.max.apply(null, ROWS.map(r => numOf(rv(r, ref)) || 0).concat([1]));
const flagEmoji = cc => /^[A-Z]{2}$/.test(cc) ? String.fromCodePoint.apply(null, cc.split('').map(ch => 0x1F1E6 + ch.charCodeAt(0) - 65)) : '';
const toneOf = (x, o) => x >= (o.green != null && o.green !== '' ? +o.green : 80) ? 'ok' : x >= (o.amber != null && o.amber !== '' ? +o.amber : 50) ? 'warn' : 'bad';
const toneFor = (o, k) => (o && o.colors && o.colors[k]) || (o && o.default) || 'muted';
function fmtDate(s){ const d = new Date(String(s).slice(0, 10) + 'T12:00:00'); return isNaN(d) ? String(s) : d.toLocaleDateString(lang, {day:'numeric', month:'short', year:'numeric'}); }
function relDate(s){
  const d = new Date(String(s).slice(0, 10) + 'T12:00:00'); if (isNaN(d)) return {txt:String(s), days:0};
  const now = new Date(); now.setHours(12, 0, 0, 0);
  const days = Math.round((d - now) / 86400000), a = Math.abs(days);
  const u = a < 31 ? [a, c('uDay')] : a < 365 ? [Math.round(a / 30), c('uMonth')] : [Math.round(a / 36.5) / 10, c(a >= 730 ? 'uYears' : 'uYear')];
  const n = fmtN(u[0]) + ' ' + u[1];
  return {txt:days < 0 ? c('ago', n) : days === 0 ? c('today') : c('inN', n), days};
}
function money(v, o){
  const unit = o.unit != null ? o.unit : '€';
  if (o.compact){ const a = Math.abs(v); return (a >= 1e6 ? (v / 1e6).toLocaleString(lang, {maximumFractionDigits:1}) + ' M' : a >= 1e3 ? Math.round(v / 1e3).toLocaleString(lang) + ' k' : fmtN(v) + ' ') + unit; }
  return fmtN(v) + (unit ? ' ' + unit : '');
}
function flagsHtml(row, fid, o){
  const fc = findFacet(fid); if (!fc) return '';
  const up = (fc.flags || []).filter(fl => test(row, fl.when));
  if (up.length) return up.map(fl => `<span class="pill t-${esc(fl.tone || 'warn')}">${esc(T(fl.label) || fl.id)}</span>`).join(' ');
  return o && hasText(o.none) ? `<span class="pill t-ok">${esc(T(o.none))}</span>` : '';
}
function wv(ref, w, row, o){
  o = o || {};
  if (isFlags(ref)) return flagsHtml(row, ref.slice(6), o);
  const v = rv(row, ref); if (!v) return '';
  const lab = v.arr ? v.arr.map(k => vlabel(ref, k)).join(', ') : v.l;
  const x = numOf(v);
  const pre = hasText(o.prefix) ? esc(T(o.prefix)) : '', suf = hasText(o.suffix) ? esc(T(o.suffix)) : '';
  const wrap = h => pre + h + suf;
  switch (w){
    case 'bold': return wrap('<b>' + esc(lab) + '</b>');
    case 'copy': return wrap(esc(lab)) + `<button type="button" class="cp" data-copyv="${esc(lab)}" aria-label="${esc(c('copy'))}">⧉</button>`;
    case 'mono': return wrap('<code class="v">' + esc(lab) + '</code>');
    case 'tag': return keysOf(v).map(k => `<span class="tag">${pre}${esc(v.arr ? vlabel(ref, k) : lab)}${suf}</span>`).join(' ');
    case 'link': return wrap('<span class="rlink">' + esc(lab) + '</span>');
    case 'pill': return keysOf(v).map(k => `<span class="pill t-${esc(toneFor(o, k))}">${esc(v.arr ? vlabel(ref, k) : lab)}</span>`).join(' ');
    case 'dot': return keysOf(v).map(k => `<span class="cdot" style="--tc:var(--${esc(toneFor(o, k))})">${esc(v.arr ? vlabel(ref, k) : lab)}</span>`).join(' ');
    case 'country': { const sh = o.show || 'both'; return (sh !== 'code' ? flagEmoji(v.k) + ' ' : '') + (sh === 'flag' ? '' : esc(sh === 'code' ? v.k : lab)); }
    case 'num': return wrap(esc(x != null ? fmtN(x) : lab));
    case 'money': return x == null ? esc(lab) : `<span class="money">${esc(money(x, o))}</span>`;
    case 'bar': { const m = +o.max || maxOf(ref); return `<span class="mini"><i><b style="width:${Math.max(0, Math.min(100, Math.round(100 * (x || 0) / m)))}%"></b></i>${esc(x != null ? fmtN(x) : lab)}</span>`; }
    case 'ring': { const m = +o.max || maxOf(ref), pc = Math.max(0, Math.min(1, (x || 0) / m)), Cc = 2 * Math.PI * 13, rt = toneOf(pc * 100, o);
      return `<span class="ring"><svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true"><circle cx="17" cy="17" r="13" fill="none" stroke="var(--line-2)" stroke-width="4.5"/><circle cx="17" cy="17" r="13" fill="none" stroke="var(--${rt})" stroke-width="4.5" stroke-linecap="round" stroke-dasharray="${(Cc * pc).toFixed(1)} ${Cc.toFixed(1)}" transform="rotate(-90 17 17)"/><text x="17" y="21" text-anchor="middle" font-size="9.5" font-weight="700" fill="var(--ink)">${esc(x != null ? fmtN(x) : lab)}</text></svg></span>`; }
    case 'stars': { const sm = +o.max || 5, top = maxOf(ref), sn = Math.max(0, Math.min(sm, Math.round(top > sm ? (x || 0) * sm / top : (x || 0)))); return `<span class="stars" aria-label="${sn}/${sm}">${'★'.repeat(sn)}<span>${'★'.repeat(sm - sn)}</span></span>`; }
    case 'level': return `<span class="pill t-${toneOf(x || 0, o)}">${esc(x != null ? fmtN(x) : lab)}</span>`;
    case 'alert': return (x || 0) > 0 ? `<span class="pill t-warn">${esc(fmtN(x))}</span>` : '<span class="hint">0</span>';
    case 'date': return esc(v.d || /^\d{4}-\d{2}-\d{2}/.test(v.k) ? fmtDate(v.d || v.k) : lab);
    case 'relative': { const iso = v.d || (/^\d{4}-\d{2}-\d{2}/.test(v.k) ? v.k : null); if (!iso) return esc(lab);
      const rd = relDate(iso), wd = +o.warnDays || 0, rt = rd.days < 0 && o.pastBad ? 'bad' : wd && rd.days >= 0 && rd.days <= wd ? 'warn' : '';
      return rt ? `<span class="pill t-${rt}" title="${esc(fmtDate(iso))}">${esc(rd.txt)}</span>` : `<span title="${esc(fmtDate(iso))}">${esc(rd.txt)}</span>`; }
    default: return wrap(esc(lab));
  }
}

/* ======================= canvas ======================= */
const isPick = (k, id) => S.pick.k === k && (id == null || S.pick.id === id);
const audTag = el => S.mode === 'design' && rolesOf(el).length ? `<span class="aud">${esc(rolesOf(el).map(roleLab).join(', '))}</span>` : '';
const partsOf = list => (X.card[list] || []).map((p, i) => ({p, i})).filter(x => forMe(x.p));
const itw = (list, i, html) => !html ? '' : S.mode === 'design' ? `<span class="it${S.pick.k === 'part' && S.pick.list === list && S.pick.i === i ? ' on' : ''}" data-part="${list}|${i}">${html}</span>` : html;
const cpick = (which, html, block) => !html || S.mode !== 'design' ? html : `<${block ? 'div' : 'span'} class="it${S.pick.k === which ? ' on' : ''}" data-cpick="${which}">${html}</${block ? 'div' : 'span'}>`;
const titleOf = row => { const t = X.card.title; if (t){ const v = rv(row, typeof t === 'string' ? t : refOf(t)); if (v) return v.l; } return row.label || row.key || ''; };
function mediaHtml(row, where){
  const m = X.card.image; if (!m || !m.field) return '';
  const shape = m.shape || 'logo', at = shape === 'cover' ? 'top' : shape === 'side' ? 'side' : 'inline'; if (at !== where) return '';
  const cls = shape === 'cover' ? 'img-cover' : shape === 'side' ? 'img-side' : shape === 'avatar' ? 'img-avatar' : 'img-logo', fit = m.fit === 'contain' ? ' contain' : '';
  const on = S.mode === 'design' ? ' it' + (S.pick.k === 'image' ? ' on' : '') : '', dp = S.mode === 'design' ? ' data-cpick="image"' : '';
  const img = row.img && row.img.url ? row.img : null;
  if (!img){
    if (m.whenMissing === 'hide') return '';
    const ini = String(titleOf(row)).split(/\s+/).filter(Boolean).map(w => w[0]).join('').slice(0, 2).toUpperCase();
    return `<span class="${cls} img-ph${on}"${dp} title="${esc(c('noImage'))}">${esc(ini)}</span>`;
  }
  return `<img class="${cls}${fit}${on}"${dp} src="${esc(img.url)}" alt="${esc(titleOf(row))}" title="${esc('DAMA · ' + (img.name || ''))}" loading="lazy">`;
}
/* the computed counts reading a linked table: each can back a "linked records" button */
function linkedDefs(){
  const names = Object.keys(SUGG).concat(Object.keys(D.derived || {}).filter(n => !SUGG[n]));
  return names.filter(n => { const d = defOf(n); return d && d.type === 'count' && (d.association || d.table && d.fk) && !d.where; });
}
function actionLabel(a){
  if (a.action === 'linked') return T(a.label) || (a.count ? labelOf('derived:' + a.count) : c('aLinked'));
  if (a.action === 'open') return c('openRec');
  if (a.action === 'favorite') return c('aFav');
  if (a.workflow){ const w = WFS.find(x => x.pub === a.workflow); return T(a.label) || (w ? T(w.lab) : a.workflow); }
  if (a.service) return T(a.label) || a.service;
  return T(a.label) || '?';
}
function card(row){
  const cd = X.card, dm = S.mode === 'design';
  let h = '';
  const pr = refOf(cd.pill), edgeTone = cd.edge === 'pill' && pr && forMe(cd.pill) && rv(row, pr) ? toneFor(cd.pill, keysOf(rv(row, pr))[0]) : '';
  const over = partsOf('overline').map(x => itw('overline', x.i, wv(refOf(x.p), x.p.widget, row, x.p.options))).filter(Boolean).join(' · ');
  const pill = pr && forMe(cd.pill) ? cpick('pill', wv(pr, 'pill', row, cd.pill)) : '';
  if (over || pill) h += `<div class="ctop"><span>${over}</span>${pill}</div>`;
  const sub = partsOf('subtitle').map(x => itw('subtitle', x.i, wv(refOf(x.p), x.p.widget, row, x.p.options))).filter(Boolean).join(' · ');
  const title = `<h3>${dm ? `<span class="it${S.pick.k === 'card' ? '' : ''}" data-cpick="title">${esc(titleOf(row))}</span>` : esc(titleOf(row))}</h3>` + (sub ? `<div class="sub">${sub}</div>` : '');
  const inl = mediaHtml(row, 'inline');
  h += inl ? `<div class="titlerow">${inl}<div>${title}</div></div>` : title;
  partsOf('lines').forEach(x => { const r = refOf(x.p), v = wv(r, x.p.widget, row, x.p.options); if (v) h += `<div class="sub">${itw('lines', x.i, ((x.p.options || {}).nolabel ? '' : esc(T(x.p.label) || labelOf(r)) + ' : ') + v)}</div>`; });
  let tags = '';
  partsOf('tags').forEach(x => {
    const r = refOf(x.p);
    if (isFlags(r)){ const fh = flagsHtml(row, r.slice(6), x.p.options); tags += itw('tags', x.i, fh || (dm ? `<span class="pill t-ok">${esc(c('compliant'))}</span>` : '')); }
    else tags += itw('tags', x.i, wv(r, x.p.widget, row, x.p.options));
  });
  if (tags) h += `<div class="trow">${tags}</div>`;
  const k = partsOf('kpis');
  if (k.length) h += '<div class="kpis">' + k.map(x => { const r = refOf(x.p); return itw('kpis', x.i, `<span><b>${wv(r, x.p.widget, row, x.p.options) || '0'}</b>${esc((T(x.p.label) || labelOf(r)).toLowerCase())}</span>`); }).join('') + '</div>';
  const g = cd.gauge, gr = refOf(g);
  if (gr && forMe(g)){
    const gx = numOf(rv(row, gr)) || 0, gm = +g.max || 100, gt = toneOf(gx * 100 / gm, g);
    const gh = g.widget === 'ring' ? `<span class="ring">${wv(gr, 'ring', row, {max:gm, green:g.green, amber:g.amber})}${esc(labelOf(gr))}</span>`
      : `<div class="gauge"><i><b style="width:${Math.max(0, Math.min(100, gx * 100 / gm))}%;background:var(--${gt})"></b></i>${esc(labelOf(gr))} ${esc(fmtN(gx))}/${esc(fmtN(gm))}</div>`;
    h += cpick('gauge', gh, true);
  }
  const acts = (cd.actions || []).filter(a => forMe(a) && (!a.cond || test(row, a.cond)));
  if (acts.length) h += '<div class="cact">' + acts.map(a => {
    const i = cd.actions.indexOf(a);
    if (a.action === 'favorite') return `<button type="button" data-act="${i}" aria-label="${esc(c('favAdd'))}">☆</button>`;
    return `<button type="button"${a.action === 'open' ? ' class="p"' : ''} data-act="${i}">${esc(actionLabel(a))}</button>`;
  }).join('') + '</div>';
  const lay = cd.layout || 'vertical';
  const body = lay === 'horizontal' ? `<div class="cbody">${h}</div>` : h;
  return `<article class="card ${esc(lay)}${edgeTone ? ' edge' : ''}"${edgeTone ? ` style="--edge:var(--${esc(edgeTone)})"` : ''}>${mediaHtml(row, 'top')}${mediaHtml(row, 'side')}${body}</article>`;
}
function zone(kind, id, lab, inner){
  return `<div class="zone${isPick(kind, id) ? ' on' : ''}" data-zone="${kind}"${id ? ` data-id="${esc(id)}"` : ''}><div class="zlabel"><span>${esc(lab)}</span></div>${inner}</div>`;
}
const checked = fc => { const sel = S.sel[fc.id] || {}; return Object.keys(sel).filter(k => sel[k]); };
function optRow(fc, x, pinned){
  return `<label class="opt${pinned ? ' pinned' : ''}${x.n === 0 ? ' zero' : ''}"><input type="checkbox" data-f="${esc(fc.id)}" data-v="${esc(x.v)}"${(S.sel[fc.id] || {})[x.v] ? ' checked' : ''}><span class="l">${esc(x.l)}</span><span class="n">${x.n === '' || x.n == null ? '' : esc(fmtN(x.n))}</span></label>`;
}
const chipRow = (fc, x) => `<button type="button" class="fchip" data-chipf="${esc(fc.id)}" data-v="${esc(x.v)}" aria-pressed="${!!(S.sel[fc.id] || {})[x.v]}">${esc(x.l)}${x.n === '' || x.n == null ? '' : `<span class="n">${esc(fmtN(x.n))}</span>`}</button>`;
function facetBody(fc, counts){
  if (!fc.open) return `<span class="hint">${esc(c('closedStart'))}</span>`;
  if (fc.kind === 'favorites') return `<label class="opt"><input type="checkbox" disabled><span class="l">${esc(c('myFavs'))}</span><span class="n"></span></label><span class="fnote">${esc(c('favPreview'))}</span>`;
  if ((fc.kind === 'field' || fc.kind === 'tree') && fc.field && isLong(fc.field)){
    const q = S.fq[fc.id] || '', top = +fc.top || TOP, shown = S.more[fc.id] || top;
    const pg = values(fc.field, q, 0, Math.min(shown, MAXLIST));
    const tot = distinctTotal(fc.field);
    let h = `<input class="fsearch" type="search" id="ldx-fq-${esc(fc.id)}" data-fq="${esc(fc.id)}" value="${esc(q)}" placeholder="${esc(c('searchIn', fmtN(tot)))}" aria-label="${esc(c('searchIn', fmtN(tot)))}">`;
    if (!pg) return h + `<span class="fnote">${esc(c('loading'))}</span>`;
    if (pg.error) return h + `<span class="fnote">${esc(c('loadFailed'))}</span>`;
    const picked = checked(fc), pinned = picked.map(v => ({v, l:vlabel(fc.field, v), n:counts[v] || 0}));
    const items = pg.items.filter(x => picked.indexOf(x.v) < 0);
    const w = fc.widget === 'chips' ? 'chips' : 'checks';
    h += w === 'chips' ? '<div class="fchips">' + pinned.concat(items).map(x => chipRow(fc, x)).join('') + '</div>' : pinned.map(x => optRow(fc, x, true)).join('') + items.map(x => optRow(fc, x)).join('');
    h += `<div class="fnote">${esc(q ? c('found', fmtN(pg.total)) : c('ofMost', fmtN(Math.min(shown, pg.total)), fmtN(pg.total)))} · ${esc(c('serverCounts'))}</div>`;
    if (pg.total > shown && shown < MAXLIST) h += `<button type="button" class="fmore" data-fmore="${esc(fc.id)}">${esc(c('more10'))}</button>`;
    else if (pg.total > shown) h += `<div class="fnote">${esc(c('otherSearch', fmtN(pg.total - shown)))}</div>`;
    if (S.more[fc.id] && S.more[fc.id] > top) h += ` <button type="button" class="fmore" data-fless="${esc(fc.id)}">${esc(c('less'))}</button>`;
    return h;
  }
  let order;
  if (fc.kind === 'flags') order = (fc.flags || []).map(fl => ({v:fl.id, l:T(fl.label) || fl.id}));
  else if (fc.kind === 'ranges') order = (fc.ranges || []).map(r => ({v:String(r.code), l:T(r.label) || r.code}));
  else order = fc.field ? shortValues(fc.field).map(x => ({v:x.v, l:x.l})) : [];
  order.forEach(x => { x.n = counts[x.v] || 0; });
  if (fc.widget === 'chips') return '<div class="fchips">' + order.map(x => chipRow(fc, x)).join('') + '</div>';
  if (fc.widget === 'select'){ const cur = checked(fc)[0] || ''; return `<select class="fselect" data-selectf="${esc(fc.id)}" aria-label="${esc(T(fc.title))}"><option value="">${esc(c('all'))}</option>${order.map(x => `<option value="${esc(x.v)}"${x.v === cur ? ' selected' : ''}>${esc(x.l)} (${esc(fmtN(x.n))})</option>`).join('')}</select>`; }
  return order.map(x => optRow(fc, x)).join('');
}
function sortsOf(){ return X.sorts.filter(forMe); }
function renderCanvas(){
  flush();
  const rows0 = ROWS, dm = S.mode === 'design';
  let rows = rows0.filter(r => !fails(r, null));
  let h = '';
  if (S.as || D.variants.length > 1) h += `<div class="banner"><span>${esc(c('variantShown'))} : <b>${esc(T(D.variants[vi].name))}</b></span><span>${esc(S.as ? c('permNote') : c('pickAs'))}</span></div>`;
  if (!ROWS.length) h += `<div class="banner"><span>${esc(c('emptySample'))}</span></div>`;
  else if (TOTAL > ROWS.length) h += `<div class="banner"><span>${esc(c('sampleNote', fmtN(ROWS.length), fmtN(TOTAL)))}</span></div>`;
  const tl = visibleTiles();
  h += zone('tiles', null, c('tiles'), tl.length ? '<div class="tiles">' + tl.map(t => {
    const n = rows0.filter(r => tileTest(r, t)).length;
    return `<button type="button" class="tile${t.tone ? ' ' + esc(t.tone) : ''}${S.tile === t.id ? ' active' : ''}${isPick('tile', t.id) ? ' selx' : ''}" data-tile="${esc(t.id)}"><b>${esc(fmtN(n))}</b><span>${esc(T(t.label))} ${audTag(t)}</span></button>`;
  }).join('') + '</div>' : `<div class="empty"><b>${esc(c('noTile'))}</b><br>${esc(c('noTileD'))}</div>`);
  h += '<div class="xbody"><div class="xside">';
  const ph = hasText(X.search.placeholder) ? T(X.search.placeholder) : X.search.fields.map(labelOf).join(', ') + '…';
  h += zone('search', null, c('searchZ'), `<label class="xsearch">&#128269;<input id="ldx-q" type="search" value="${esc(S.q)}" placeholder="${esc(ph)}" aria-label="${esc(c('searchPh'))}"></label>`);
  const fs = visibleFacets();
  const fh = fs.map(fc => {
    const counts = {};
    rows0.forEach(r => { if (!fails(r, fc.id)) facetVals(fc, r).forEach(v => { counts[v] = (counts[v] || 0) + 1; }); });
    return `<div class="facet${isPick('facet', fc.id) ? ' selx' : ''}" data-facet="${esc(fc.id)}"><h4><span>${esc(T(fc.title))}</span>${audTag(fc)}</h4>${facetBody(fc, counts)}</div>`;
  }).join('');
  h += zone('facets', null, c('facets'), fh || `<div class="empty"><b>${esc(c('noFacet'))}</b><br>${esc(c('noFacetD'))}</div>`);
  h += '</div><div class="xmain">';
  const chips = [];
  fs.forEach(fc => checked(fc).forEach(v => chips.push(`<button type="button" class="chip" data-unsel="${esc(fc.id)}" data-v="${esc(v)}">${esc(facetLab(fc, v))} ✕</button>`)));
  if (S.tile){ const tt = findTile(S.tile); if (tt) chips.push(`<button type="button" class="chip" data-untile="1">${esc(T(tt.label))} ✕</button>`); }
  const sorts = sortsOf(), si = Math.min(S.sort, Math.max(0, sorts.length - 1)), so = sorts[si];
  h += `<div class="xbar"><span class="xcount">${esc(fmtN(rows.length))} ${esc(c('records'))} <span class="hint">/ ${esc(fmtN(rows0.length))}</span></span>`
    + (sorts.length > 1 ? `<select aria-label="${esc(c('aSort'))}" id="ldx-sortsel">${sorts.map((s, i) => `<option value="${i}"${i === si ? ' selected' : ''}>${esc(T(s.label) || labelOf(refOf(s)))} ${s.dir === 'desc' ? '↓' : '↑'}</option>`).join('')}</select>` : '') + '</div>';
  if (chips.length) h += '<div class="chips">' + chips.join('') + '</div>';
  if (so){ const r = refOf(so), dir = so.dir === 'desc' ? -1 : 1;
    rows = rows.map(row => ({row, v:rv(row, r)})).sort((a, b) => { if (!a.v && !b.v) return 0; if (!a.v) return 1; if (!b.v) return -1; const x = numOf(a.v), y = numOf(b.v); return dir * (x != null && y != null && kindOf(r) !== 'text' ? x - y : String(a.v.l).localeCompare(String(b.v.l), lang)); }).map(x => x.row); }
  const ps = +X.pageSize > 0 ? +X.pageSize : 48, page = rows.slice(0, ps), cd = X.card;
  const grid = `<div class="grid${cd.layout === 'compact' ? ' compact' : ''}" style="grid-template-columns:${cd.layout === 'compact' ? '1fr' : `repeat(auto-fill,minmax(min(100%,${+cd.minWidth || 250}px),1fr))`}">${page.map(card).join('')}</div>`;
  h += zone('card', null, c('card'), rows.length ? grid + (rows.length > ps ? `<p class="hint">${esc(c('moreCards', fmtN(rows.length - ps)))}</p>` : '') : `<p class="hint">${esc(ROWS.length ? c('noRecord') : c('emptySample'))}</p>`);
  h += '</div></div>';
  const cv = $('#ldx-canvas'); cv.className = 'canvas ' + (dm ? 'design' : 'preview'); cv.innerHTML = h;
  const names = {tiles:c('tiles'), tile:c('tile'), facets:c('facets'), facet:c('facet'), card:c('cardShort'), part:c('part'), pill:c('pill'), gauge:c('gauge'), image:c('image'), action:c('action'), search:c('searchZ'), variant:c('variant')};
  $('#ldx-selname').textContent = dm ? c('selected', names[S.pick.k] || '') : '';
  $('#ldx-cbar-hint').textContent = dm ? c('hintDesign') : c('hintPreview');
}

/* ======================= palette ======================= */
function used(ref){
  const cd = X.card, parts = [].concat(...LISTS.map(l => cd[l] || [])).map(refOf);
  const inCard = parts.indexOf(ref) >= 0 || refOf(cd.pill) === ref || refOf(cd.gauge) === ref || (cd.image && cd.image.field === ref) || (typeof cd.title === 'string' ? cd.title : refOf(cd.title)) === ref;
  return {facet:X.facets.some(f => f.kind !== 'flags' && f.field === ref), card:!!inCard, search:X.search.fields.indexOf(ref) >= 0, sort:X.sorts.some(s => refOf(s) === ref)};
}
function fieldActions(ref){
  const k = kindOf(ref), u = used(ref), b = [];
  const btn = (act, lab, on) => on ? `<button type="button" class="in" data-rem="${act}" data-ref="${esc(ref)}" title="${esc(c('removeTip'))}"><span class="on">✓ ${esc(lab)}</span><span class="off">✕ ${esc(c('remove'))}</span></button>` : `<button type="button" data-add="${act}" data-ref="${esc(ref)}">+ ${esc(lab)}</button>`;
  if (k === 'num') b.push(btn('ranges', c('aRanges'), u.facet));
  else if (k !== 'media' && k !== 'date' && facetable(ref)) b.push(btn('facet', c('aFacet'), u.facet));
  b.push(btn('card', c('aCard'), u.card));
  if (k === 'media') return b.join('');
  if (searchable(ref)) b.push(btn('search', c('aSearch'), u.search));
  if (k !== 'date') b.push(`<button type="button" data-add="tile" data-ref="${esc(ref)}">+ ${esc(c('aTile'))}</button>`);
  if (k !== 'list' || meta(ref).t !== 'list') b.push(btn('sort', c('aSort'), u.sort));
  return b.join('');
}
const tyLab = m => m.derived ? (m.def ? c('dt_' + m.def.type) : c('ty_str')) : c('ty_' + m.t);
function pitem(ref, sub){
  const m = meta(ref);
  return `<div class="pitem" draggable="true" data-drag="${esc(ref)}"><div class="ph"><b>${esc(labelOf(ref))}</b><span class="ty">${esc(tyLab(m))}</span></div><code>${esc(sub)}</code><div class="pa">${fieldActions(ref)}</div></div>`;
}
function derivedSummary(d){
  if (!d) return '';
  const bits = [d.type]; ['association','table','fk','link','states','field'].forEach(k => { if (d[k]) bits.push(d[k]); });
  return bits.join(' · ');
}
function renderPalette(){
  const q = norm($('#ldx-psearch').value), h = [];
  root.querySelectorAll('[data-pt]').forEach(b => b.setAttribute('aria-selected', b.dataset.pt === S.pt));
  if (S.pt === 'parts'){
    const els = [['flags', c('elFlags'), c('elFlagsD')], ['favorites', c('elFav'), c('elFavD')], ['tile', c('elTile'), c('elTileD')], ['variant', c('elVariant'), c('elVariantD')],
      ['open', c('elActOpen'), c('elActOpenD')], ['favorite', c('elActFav'), c('elActFavD')]]
      .concat(WFS.map(w => ['wf:' + w.pub, c('elWf', T(w.lab) || w.pub), c('elWfD', w.pub)]))
      .concat([['service', c('elService'), c('elServiceD')], ['flagtags', c('elFlagTags'), c('elFlagTagsD')]]);
    h.push(`<div class="pgroup"><h3>${esc(c('elements'))}</h3>` + els.filter(p => !q || norm(p[1] + ' ' + p[2]).indexOf(q) >= 0).map(p =>
      `<div class="pitem"><div class="ph"><b>${esc(p[1])}</b></div><code>${esc(p[2])}</code><div class="pa"><button type="button" data-addpart="${esc(p[0])}">+ ${esc(c('add'))}</button></div></div>`).join('') + '</div>');
  } else if (S.pt === 'derived'){
    const names = Object.keys(SUGG).concat(Object.keys(D.derived).filter(n => !SUGG[n]));
    const items = names.filter(n => !q || norm(labelOf('derived:' + n) + ' ' + n).indexOf(q) >= 0).map(n => pitem('derived:' + n, derivedSummary(defOf(n))));
    h.push(items.length ? `<div class="pgroup">${items.join('')}</div>` : `<p class="hint">${esc(c('noMatch'))}</p>`);
    h.push(`<p class="hint">${esc(c('derivedHint'))}</p>`);
  } else {
    const byG = {}, order = [];
    FIELDS.forEach(f => { if (q && norm(labelOf(f.p) + ' ' + f.p).indexOf(q) < 0) return; const g = f.g || ''; if (!byG[g]){ byG[g] = []; order.push(g); } byG[g].push(f.p); });
    order.forEach(g => h.push(`<div class="pgroup"><h3>${esc(g ? T(GROUPS[g]) || g : TABLE)}</h3>${byG[g].map(p => pitem(p, p + (FM[p].target ? ' → ' + FM[p].target : '') + (FM[p].n ? ' · ' + fmtN(FM[p].n) : ''))).join('')}</div>`));
    if (!order.length) h.push(`<p class="hint">${esc(c('noMatch'))}</p>`);
    if (FIELDS.some(f => f.t === 'media')) h.push(`<p class="hint">${esc(c('mediaHint'))}</p>`);
  }
  $('#ldx-palette').innerHTML = h.join('');
}

/* ======================= inspector ======================= */
let REG = [];                                   // what each inspector control edits, rebuilt at each rendering
const reg = o => { REG.push(o); return REG.length - 1; };
/* a text: one input per locale */
function locIn(owner, key, ph){
  const i = reg({owner, key}), o = owner[key] && typeof owner[key] === 'object' ? owner[key] : {};
  return '<div class="locs">' + LOCALES.map(l => `<label><span>${esc(l)}</span><input type="text" id="ldx-t${i}-${esc(l)}" data-tx="${i}" data-loc="${esc(l)}" value="${esc(o[l] || '')}" placeholder="${esc(ph || '')}"${!o[l] && hasText(o) ? ' class="miss"' : ''}></label>`).join('') + '</div>';
}
function rolesEd(owner, title, hintSome, hintNone){
  const i = reg({owner}), cur = rolesOf(owner);
  if (!ROLES.length) return `<div class="fld"><span class="l">${esc(title || c('visibleTo'))}</span><span class="hint">${esc(c('noRoles'))}</span></div>`;
  return `<div class="fld"><span class="l">${esc(title || c('visibleTo'))}</span><div class="rolechips">${ROLES.map(r => `<label><input type="checkbox" data-role="${i}" data-k="${esc(r.k)}"${cur.indexOf(r.k) >= 0 ? ' checked' : ''}>${esc(T(r.l) || r.k)}</label>`).join('')}</div><span class="hint">${esc(cur.length ? (hintSome || c('rolesSome')) : (hintNone || c('rolesNone')))}</span></div>`;
}
const toneOpts = (cur, none) => (none ? `<option value="">${esc(none)}</option>` : '') + TONES.map(t => `<option value="${t}"${t === cur ? ' selected' : ''}>${esc(c('tone_' + t))}</option>`).join('');
/* the operators that make sense for a value of this kind */
function opsFor(ref){
  const k = kindOf(ref);
  if (k === 'num') return ['gt','ge','lt','le','eq','ne','filled','empty'];
  if (k === 'date') return ['days','filled','empty','eq','ne'];
  if (k === 'list') return ['in','notIn','filled','empty','eq','ne'];
  return ['filled','empty','eq','ne','in','notIn'];
}
function defaultCond(ref){
  const k = kindOf(ref), cd = {}; setRef(cd, ref);
  if (k === 'num') cd.gt = 0;
  else if (k === 'list'){ const v = isLong(ref) ? null : shortValues(ref)[0]; cd.in = v ? [v.v] : []; }
  else cd.filled = true;
  return cd;
}
/* the first simple condition inside a compound one */
function firstLeaf(cd){
  if (!cd || typeof cd !== 'object') return null;
  if (Array.isArray(cd.all) || Array.isArray(cd.any)){ for (const x of (cd.all || cd.any)){ const f = firstLeaf(x); if (f) return f; } return null; }
  if (cd.not != null) return firstLeaf(cd.not);
  const ops = opsOf(cd); if (!refOf(cd)) return null;
  const leaf = {}; setRef(leaf, refOf(cd)); if (ops[0]) leaf[ops[0]] = clone(cd[ops[0]]); else leaf.filled = true;
  return leaf;
}
function condEd(owner, key, optional){
  const cd = owner[key], i = reg({owner, key});
  if (!cd || typeof cd !== 'object' || (!refOf(cd) && !isCompound(cd))){
    return `<div class="fld"><span class="hint">${esc(optional ? c('always') : c('noCond'))}</span><button type="button" class="btn tiny ghost" data-cadd="${i}">${esc(c('addCond'))}</button></div>`;
  }
  if (isCompound(cd)) return `<div class="where">${esc(c('compound'))}</div><pre class="cjson">${esc(JSON.stringify(cd, null, 1))}</pre><div class="acts"><button type="button" class="btn tiny ghost" data-csimp="${i}">${esc(c('simplify'))}</button>${optional ? `<button type="button" class="btn tiny ghost danger" data-crm="${i}">${esc(c('rmCond'))}</button>` : ''}</div>`;
  const ref = refOf(cd), op = opsOf(cd)[0] || 'filled', ops = opsFor(ref);
  if (ops.indexOf(op) < 0) ops.push(op);
  let v = '';
  const long = isLong(ref) && kindOf(ref) !== 'num';
  if (op === 'in' || op === 'notIn'){
    const arr = Array.isArray(cd[op]) ? cd[op].map(String) : [String(cd[op])];
    v = `<div class="cvals">${arr.map((x, j) => `<button type="button" class="chip" data-cvrm="${i}" data-j="${j}" title="${esc(c('remove'))}">${esc(vlabel(ref, x))} ✕</button>`).join('')}`;
    v += long ? `<input type="text" id="ldx-cq-${i}" data-cvq="${i}" list="ldx-dl-${i}" placeholder="${esc(c('typeVal', fmtN(distinctTotal(ref))))}" aria-label="${esc(c('value'))}"><datalist id="ldx-dl-${i}"></datalist>`
      : `<select data-cvadd="${i}" aria-label="${esc(c('addValue'))}"><option value="">${esc(c('addValue'))}</option>${shortValues(ref).filter(x => arr.indexOf(x.v) < 0).map(x => `<option value="${esc(x.v)}">${esc(x.l)}</option>`).join('')}</select>`;
    v += '</div>';
  } else if (op === 'eq' || op === 'ne'){
    const cur = cd[op] == null ? '' : String(cd[op]);
    if (kindOf(ref) === 'list' && !long) v = `<select data-cvset="${i}" aria-label="${esc(c('value'))}">${shortValues(ref).map(x => `<option value="${esc(x.v)}"${x.v === cur ? ' selected' : ''}>${esc(x.l)}</option>`).join('')}${cur && !shortValues(ref).some(x => x.v === cur) ? `<option value="${esc(cur)}" selected>${esc(cur)}</option>` : ''}</select>`;
    else if (long) v = `<input type="text" id="ldx-cq-${i}" data-cvq="${i}" data-one="1" list="ldx-dl-${i}" value="${esc(cur ? vlabel(ref, cur) : '')}" placeholder="${esc(c('typeVal', fmtN(distinctTotal(ref))))}" aria-label="${esc(c('value'))}"><datalist id="ldx-dl-${i}"></datalist>`;
    else v = `<input type="${kindOf(ref) === 'num' ? 'number' : kindOf(ref) === 'date' ? 'date' : 'text'}" data-cvset="${i}" value="${esc(cur)}" aria-label="${esc(c('value'))}">`;
  } else if (op === 'days'){
    const d = cd.days || {};
    v = `<span class="cdays"><input type="number" data-cdays="${i}" data-b="from" value="${esc(d.from == null ? '' : d.from)}" title="${esc(c('dFrom'))}" aria-label="${esc(c('dFrom'))}"> → <input type="number" data-cdays="${i}" data-b="to" value="${esc(d.to == null ? '' : d.to)}" title="${esc(c('dTo'))}" aria-label="${esc(c('dTo'))}"></span>`;
  } else if (op !== 'filled' && op !== 'empty') v = `<input type="number" data-cvset="${i}" value="${esc(cd[op] == null ? '' : cd[op])}" aria-label="${esc(c('value'))}">`;
  const opsel = `<select data-cop="${i}" aria-label="${esc(c('op'))}">${ops.map(o => `<option value="${o}"${o === op ? ' selected' : ''}>${esc(c('o_' + o))}</option>`).join('')}</select>`;
  const inl = op === 'in' || op === 'notIn';
  return `<div class="cond3"><select data-cref="${i}" aria-label="${esc(c('field'))}">${refOptions(ref)}</select>${opsel}${inl ? '<span></span>' + v : v || '<span></span>'}</div>${op === 'days' ? `<span class="hint">${esc(c('daysHint'))}</span>` : ''}${optional ? `<button type="button" class="btn tiny ghost" data-crm="${i}">${esc(c('rmCond'))}</button>` : ''}`;
}
const segO = (key, cur, opts) => '<div class="seg">' + opts.map(x => `<button type="button" data-oset="${esc(key)}" data-val="${esc(x[0])}" aria-pressed="${String(cur) === String(x[0])}">${esc(x[1])}</button>`).join('') + '</div>';
const numO = (key, lab, val, id) => `<div class="fld"><label for="ldx-on-${id || key}">${esc(lab)}</label><input type="number" id="ldx-on-${id || key}" data-onum="${esc(key)}" value="${esc(val == null ? '' : val)}"></div>`;
/* which options object the inspector edits: an attribute's, the pill's, the gauge's or the image's */
function optTarget(){
  const p = S.pick;
  if (p.k === 'part'){ const pt = X.card[p.list][p.i]; if (!pt.options) pt.options = {}; return pt.options; }
  if (p.k === 'pill') return X.card.pill;
  if (p.k === 'gauge') return X.card.gauge;
  if (p.k === 'image') return X.card.image;
  if (p.k === 'action') return X.card.actions[p.i];
  return {};
}
function toneGrid(ref, o){
  if (!ref) return '';
  const long = isLong(ref);
  if (!long){
    const all = shortValues(ref); if (!all.length) return '';
    return `<div class="fld"><span class="l">${esc(c('colours'))}</span><div class="tonegrid">${all.map(x => `<div><span title="${esc(x.l)}">${esc(x.l)}</span><select data-otone="${esc(x.v)}" aria-label="${esc(c('colour') + ' · ' + x.l)}">${toneOpts(toneFor(o, x.v))}</select></div>`).join('')}<div><span>${esc(c('otherVals'))}</span><select data-odef="1" aria-label="${esc(c('otherVals'))}">${toneOpts(o.default || 'muted')}</select></div></div></div>`;
  }
  const tot = distinctTotal(ref), chosen = Object.keys(o.colors || {});
  let h = `<div class="fld"><span class="l">${esc(c('colours'))}</span><div class="where">${esc(c('coloursLong', fmtN(tot)))}</div>`;
  h += '<div class="vlist">' + chosen.map(v => `<div class="vrow"><span class="l">${esc(vlabel(ref, v))}</span><select data-otone="${esc(v)}" aria-label="${esc(c('colour') + ' · ' + vlabel(ref, v))}">${toneOpts(o.colors[v])}</select><button type="button" data-otonerm="${esc(v)}" aria-label="${esc(c('remove'))}">✕</button></div>`).join('') + '</div>';
  h += `<div class="tonegrid"><div><span>${esc(c('otherVals'))}</span><select data-odef="1" aria-label="${esc(c('otherVals'))}">${toneOpts(o.default || 'muted')}</select></div></div></div>`;
  const pg = values(ref, S.tq, 0, S.tmore);
  h += `<div class="fld"><label for="ldx-tq">${esc(c('giveColour'))}</label><input class="fsearch" type="search" id="ldx-tq" data-tq="1" value="${esc(S.tq)}" placeholder="${esc(c('searchIn', fmtN(tot)))}">`;
  if (!pg) h += `<span class="fnote">${esc(c('loading'))}</span>`;
  else {
    h += '<div class="vlist">' + pg.items.filter(x => chosen.indexOf(x.v) < 0).map(x => `<div class="vrow"><span class="l">${esc(x.l)}</span><span class="n">${esc(fmtN(x.n))}</span><button type="button" data-taddv="${esc(x.v)}">${esc(c('colourBtn'))}</button></div>`).join('') + '</div>';
    h += `<span class="fnote">${esc(S.tq ? c('foundN', fmtN(pg.total)) : c('mostFirst'))}</span>` + (pg.total > S.tmore && S.tmore < MAXLIST ? ` <button type="button" class="fmore" data-tmore="1">${esc(c('more10'))}</button>` : '');
  }
  return h + '</div>';
}
function wFor(ref){
  if (isFlags(ref)) return ['flags'];
  const m = meta(ref), k = kind(m);
  if (k === 'media') return [];
  if (k === 'num') return ['num','money','bar','ring','level','stars','alert','text','bold'];
  if (k === 'date') return ['date','relative','text'];
  const a = ['text','bold','mono','copy','tag'];
  if (k === 'list' || m.t === 'str') a.push('pill','dot');
  if (m.t === 'fk') a.push('link');
  if (isCountry(m)) a.push('country');
  return a;
}
function defW(list, ref){
  if (isFlags(ref)) return 'flags';
  const k = kindOf(ref);
  if (k === 'num') return 'num';
  if (k === 'date') return 'relative';
  if (list === 'tags') return 'tag';
  return 'text';
}
const demoRow = ref => ROWS.find(r => isFlags(ref) ? flagsOf(r).length : rv(r, ref)) || ROWS[0];
function widgetPicker(ref, cur, o){
  const row = demoRow(ref);
  return '<div class="wgrid">' + wFor(ref).map(w => `<button type="button" class="wopt" data-setw="${w}" aria-pressed="${w === cur}"><b>${esc(c('w_' + w))}</b><span class="demo">${(row && wv(ref, w, row, o)) || '—'}</span><small>${esc(c('wd_' + w))}</small></button>`).join('') + '</div>';
}
function widgetOpts(ref, w, o){
  let h = '';
  if (['text','bold','mono','copy','tag','num','link'].indexOf(w) >= 0) h += `<div class="fld"><span class="l">${esc(c('prefix'))}</span>${locIn(o, 'prefix')}</div><div class="fld"><span class="l">${esc(c('suffix'))}</span>${locIn(o, 'suffix')}</div>`;
  if (w === 'pill' || w === 'dot') h += toneGrid(ref, o);
  if (w === 'money') h += `<div class="fld"><label for="ldx-on-unit">${esc(c('unit'))}</label><input type="text" id="ldx-on-unit" data-oval="unit" list="ldx-units" value="${esc(o.unit != null ? o.unit : '')}" placeholder="€"><datalist id="ldx-units"><option value="€"><option value="k€"><option value="$"><option value="£"><option value="CHF"></datalist></div><div class="fld"><span class="l">${esc(c('format'))}</span>${segO('compact', !!o.compact, [['false', c('full')], ['true', c('short')]])}</div>`;
  if (w === 'bar' || w === 'ring') h += numO('max', c('maxEmpty'), o.max);
  if (w === 'ring' || w === 'level') h += '<div class="row2">' + numO('green', c('greenFrom'), o.green != null ? o.green : 80) + numO('amber', c('amberFrom'), o.amber != null ? o.amber : 50) + '</div>';
  if (w === 'stars') h += numO('max', c('nStars'), o.max || 5);
  if (w === 'relative') h += numO('warnDays', c('warnDays'), o.warnDays || 0) + `<div class="fld"><span class="l">${esc(c('pastDate'))}</span>${segO('pastBad', !!o.pastBad, [['false', c('neutral')], ['true', c('inRed')]])}</div>`;
  if (w === 'country') h += `<div class="fld"><span class="l">${esc(c('show'))}</span>${segO('show', o.show || 'both', [['both', c('flagName')], ['flag', c('flagOnly')], ['code', c('codeOnly')]])}</div>`;
  if (w === 'flags') h += `<div class="fld"><span class="l">${esc(c('noneText'))}</span>${locIn(o, 'none', c('compliant'))}</div>`;
  return h;
}
const LISTLAB = {overline:'overline', subtitle:'subtitle', lines:'lines', tags:'tags', kpis:'kpis'};
function partRow(list, p, i, n){
  const r = refOf(p);
  return `<div class="part partrow${S.pick.k === 'part' && S.pick.list === list && S.pick.i === i ? ' on' : ''}" data-editpart="${list}|${i}"><span>${esc(labelOf(r))}${p.widget ? ` <span class="wl">· ${esc(c('w_' + p.widget))}</span>` : ''}${rolesOf(p).length ? ` <span class="aud">${esc(rolesOf(p).map(roleLab).join(', '))}</span>` : ''}</span>`
    + `<button type="button" data-editpart="${list}|${i}" aria-label="${esc(c('edit'))}">✎</button><button type="button" data-mv="${list}|${i}|-1"${i ? '' : ' disabled'} aria-label="${esc(c('up'))}">↑</button><button type="button" data-mv="${list}|${i}|1"${i < n - 1 ? '' : ' disabled'} aria-label="${esc(c('down'))}">↓</button><button type="button" data-rm="${list}|${i}" aria-label="${esc(c('remove'))}">✕</button></div>`;
}
function listEd(list, filter){
  const arr = X.card[list], usedR = arr.map(refOf);
  const flagOpts = flagFacets().filter(f => usedR.indexOf('flags:' + f.id) < 0).map(f => `<option value="flags:${esc(f.id)}">${esc(c('flagsOf', T(f.title)))}</option>`).join('');
  return `<div class="sechd">${esc(c(LISTLAB[list]))}</div>` + arr.map((p, i) => partRow(list, p, i, arr.length)).join('')
    + `<select data-addto="${list}" aria-label="${esc(c('add') + ' · ' + c(LISTLAB[list]))}"><option value="">${esc(c('addDots'))}</option>${refOptions('', r => usedR.indexOf(r) < 0 && (!filter || filter(r)))}${flagOpts ? `<optgroup label="${esc(c('ty_flags'))}">${flagOpts}</optgroup>` : ''}</select>`;
}
function renderInspector(){
  flush(); REG = [];
  const p = S.pick, cd = X.card;
  let h = '';
  const back = `<button type="button" class="btn tiny ghost" data-back="card">${esc(c('wholeCard'))}</button>`;
  if (p.k === 'card'){
    h += `<h3>${esc(c('cardShort'))}</h3><div class="where">${esc(c('cardD'))}</div>`;
    h += `<div class="sechd">${esc(c('layout'))}</div><div class="seg">${[['vertical', c('lVertical')], ['horizontal', c('lHorizontal')], ['compact', c('lCompact')]].map(x => `<button type="button" data-cset="layout" data-val="${x[0]}" aria-pressed="${(cd.layout || 'vertical') === x[0]}">${esc(x[1])}</button>`).join('')}</div>`;
    const uls = Array.isArray(cd.layouts) ? cd.layouts : [];
    h += `<div class="fld"><span class="l">${esc(c('userLays'))}</span><div class="rolechips">${[['vertical', c('lVertical')], ['horizontal', c('lHorizontal')], ['compact', c('lCompact')]].map(x => `<label><input type="checkbox" data-clays="${x[0]}"${uls.indexOf(x[0]) >= 0 ? ' checked' : ''}>${esc(x[1])}</label>`).join('')}</div><span class="hint">${esc(c('userLaysHint'))}</span></div>`;
    h += `<div class="fld"><span class="l">${esc(c('minWidth'))}</span><div class="seg">${[220, 260, 300, 360].map(x => `<button type="button" data-cset="minWidth" data-val="${x}" aria-pressed="${(+cd.minWidth || 250) === x}">${x}</button>`).join('')}</div></div>`;
    h += `<label class="chk"><input type="checkbox" data-cedge="1"${cd.edge === 'pill' ? ' checked' : ''}${refOf(cd.pill) ? '' : ' disabled'}>${esc(c('edge'))}</label>`;
    const media = FIELDS.filter(f => f.t === 'media');
    h += `<div class="sechd">${esc(c('imageS'))} <span class="new">DAMA</span></div>`;
    if (cd.image && cd.image.field) h += `<div class="part partrow${p.k === 'image' ? ' on' : ''}" data-cpick="image"><span>${esc(labelOf(cd.image.field))} <span class="wl">· ${esc(c('s' + (cd.image.shape || 'logo').replace(/^./, s => s.toUpperCase())))}</span></span><button type="button" data-cpick="image" aria-label="${esc(c('edit'))}">✎</button></div>`;
    else h += media.length ? `<select data-addimage="1" aria-label="${esc(c('addImage'))}"><option value="">${esc(c('addImage'))}</option>${media.map(f => `<option value="${esc(f.p)}">${esc(labelOf(f.p))}</option>`).join('')}</select>` : `<p class="hint">${esc(c('noMedia'))}</p>`;
    const tr = typeof cd.title === 'string' ? cd.title : refOf(cd.title);
    h += `<div class="fld"><label for="ldx-title">${esc(c('titleF'))}</label><select id="ldx-title" data-ctitle="1">${refOptions(tr || '', r => kindOf(r) === 'text' || kindOf(r) === 'list', c('titleRec'))}</select></div>`;
    h += listEd('overline');
    h += `<div class="fld"><label for="ldx-pillsel">${esc(c('pillSel'))}</label><select id="ldx-pillsel" data-pill="1">${refOptions(refOf(cd.pill), r => kindOf(r) === 'list' || meta(r).t === 'str', c('none'))}</select></div>`;
    h += listEd('subtitle');
    h += listEd('lines');
    h += listEd('tags');
    h += listEd('kpis', r => kindOf(r) === 'num' || kindOf(r) === 'list');
    h += `<div class="fld"><label for="ldx-gaugesel">${esc(c('gaugeSel'))}</label><select id="ldx-gaugesel" data-gauge="1">${refOptions(refOf(cd.gauge), r => kindOf(r) === 'num', c('none'))}</select></div>`;
    h += `<div class="sechd">${esc(c('actions'))}</div>` + cd.actions.map((a, i) => `<div class="part partrow${p.k === 'action' && p.i === i ? ' on' : ''}" data-pickact="${i}"><span>${esc(actionLabel(a))}${a.workflow ? ` <span class="wl">· ${esc(a.workflow)}</span>` : ''}${rolesOf(a).length ? ` <span class="aud">${esc(rolesOf(a).map(roleLab).join(', '))}</span>` : ''}</span><button type="button" data-pickact="${i}" aria-label="${esc(c('edit'))}">✎</button><button type="button" data-amv="${i}" data-d="-1"${i ? '' : ' disabled'} aria-label="${esc(c('up'))}">↑</button><button type="button" data-amv="${i}" data-d="1"${i < cd.actions.length - 1 ? '' : ' disabled'} aria-label="${esc(c('down'))}">↓</button><button type="button" data-arm="${i}" aria-label="${esc(c('remove'))}">✕</button></div>`).join('');
    const hasA = k => cd.actions.some(a => a.action === k);
    h += `<select data-addact="1" aria-label="${esc(c('addAction'))}"><option value="">${esc(c('addAction'))}</option>${hasA('open') ? '' : `<option value="open">${esc(c('aOpen'))}</option>`}${hasA('favorite') ? '' : `<option value="favorite">${esc(c('aFav'))}</option>`}${WFS.map(w => `<option value="wf:${esc(w.pub)}">${esc(c('aWf') + ' · ' + (T(w.lab) || w.pub))}</option>`).join('')}<option value="service">${esc(c('aSvc'))}</option>${linkedDefs().map(n => `<option value="link:${esc(n)}">${esc(c('aLinked') + ' · ' + labelOf('derived:' + n))}</option>`).join('')}</select>`;
    h += `<div class="where">${esc(c('permCard'))}</div>`;
  } else if (p.k === 'part'){
    const part = X.card[p.list] && X.card[p.list][p.i];
    if (!part){ S.pick = {k:'card'}; return renderInspector(); }
    const r = refOf(part), m = meta(r), arr = X.card[p.list];
    if (!part.options) part.options = {};
    h += `<h3>${esc(labelOf(r))}</h3><div class="acts">${back}<button type="button" class="btn tiny ghost" data-mv="${p.list}|${p.i}|-1"${p.i ? '' : ' disabled'}>↑</button><button type="button" class="btn tiny ghost" data-mv="${p.list}|${p.i}|1"${p.i < arr.length - 1 ? '' : ' disabled'}>↓</button><button type="button" class="btn tiny ghost danger" data-rm="${p.list}|${p.i}">${esc(c('remove'))}</button></div>`;
    h += `<div class="where"><code class="v">${esc(m.flags ? 'flags · ' + m.flags : m.derived ? 'derived:' + m.name + ' · ' + derivedSummary(m.def) : r)}</code> · ${esc(tyLab(m))}</div>`;
    if (!m.flags) h += `<div class="fld"><label for="ldx-partref">${esc(c('valueShown'))}</label><select id="ldx-partref" data-partref="1">${refOptions(r)}</select></div>`;
    const row = demoRow(r);
    h += `<div class="fld"><span class="l">${esc(c('widget'))}</span>${widgetPicker(r, part.widget, part.options)}${row ? `<span class="hint">${esc(c('exampleFrom', titleOf(row)))}</span>` : ''}</div>`;
    h += widgetOpts(r, part.widget, part.options);
    if (p.list === 'lines' || p.list === 'kpis') h += `<div class="fld"><span class="l">${esc(c('partLabel'))}</span>${locIn(part, 'label', labelOf(r))}</div>`;
    if (p.list === 'lines') h += `<label class="chk"><input type="checkbox" data-onolabel="1"${part.options.nolabel ? ' checked' : ''}>${esc(c('noLabel'))}</label>`;
    h += `<div class="fld"><label for="ldx-partzone">${esc(c('zone'))}</label><select id="ldx-partzone" data-partzone="1">${LISTS.map(l => `<option value="${l}"${l === p.list ? ' selected' : ''}>${esc(c(LISTLAB[l]))}</option>`).join('')}</select></div>`;
    h += rolesEd(part);
    if (!m.flags) h += `<div class="where">${esc(c('permPart', r))}</div>`;
  } else if (p.k === 'pill'){
    const pl = cd.pill;
    h += `<h3>${esc(c('pill'))}</h3><div class="acts">${back}</div>`;
    h += `<div class="fld"><label for="ldx-pillsel2">${esc(c('field'))}</label><select id="ldx-pillsel2" data-pill="1">${refOptions(refOf(pl), r => kindOf(r) === 'list' || meta(r).t === 'str', c('none'))}</select></div>`;
    if (pl && refOf(pl)) h += toneGrid(refOf(pl), pl) + rolesEd(pl) + `<div class="where">${esc(c('pillD'))}</div>`;
  } else if (p.k === 'gauge'){
    const g = cd.gauge;
    h += `<h3>${esc(c('gauge'))}</h3><div class="acts">${back}</div>`;
    h += `<div class="fld"><label for="ldx-gaugesel2">${esc(c('field'))}</label><select id="ldx-gaugesel2" data-gauge="1">${refOptions(refOf(g), r => kindOf(r) === 'num', c('none'))}</select></div>`;
    if (g && refOf(g)) h += `<div class="fld"><span class="l">${esc(c('shape'))}</span>${segO('widget', g.widget || 'bar', [['bar', c('bar')], ['ring', c('ring')]])}</div><div class="row3">${numO('max', 'Max', g.max != null ? g.max : 100, 'gmax')}${numO('green', c('greenFrom'), g.green != null ? g.green : 80, 'ggreen')}${numO('amber', c('amberFrom'), g.amber != null ? g.amber : 50, 'gamber')}</div>` + rolesEd(g);
  } else if (p.k === 'image'){
    const m = cd.image;
    if (!m){ S.pick = {k:'card'}; return renderInspector(); }
    h += `<h3>${esc(c('image'))} <span class="new">DAMA</span></h3><div class="acts">${back}<button type="button" class="btn tiny ghost danger" data-rmimage="1">${esc(c('remove'))}</button></div>`;
    h += `<div class="fld"><label for="ldx-imgf">${esc(c('field'))}</label><select id="ldx-imgf" data-imgf="1">${FIELDS.filter(f => f.t === 'media').map(f => `<option value="${esc(f.p)}"${f.p === m.field ? ' selected' : ''}>${esc(labelOf(f.p))}</option>`).join('')}</select></div>`;
    h += `<div class="fld"><span class="l">${esc(c('position'))}</span>${segO('shape', m.shape || 'logo', [['cover', c('sCover')], ['side', c('sSide')], ['logo', c('sLogo')], ['avatar', c('sAvatar')]])}</div>`;
    h += `<div class="fld"><span class="l">${esc(c('fit'))}</span>${segO('fit', m.fit || 'cover', [['cover', c('fCover')], ['contain', c('fContain')]])}</div>`;
    h += `<div class="fld"><span class="l">${esc(c('missing'))}</span>${segO('whenMissing', m.whenMissing || 'initials', [['initials', c('mInitials')], ['hide', c('mHide')]])}</div>`;
    h += `<div class="where">${esc(c('imageD'))}</div>`;
  } else if (p.k === 'action'){
    const a = cd.actions[p.i];
    if (!a){ S.pick = {k:'card'}; return renderInspector(); }
    h += `<h3>${esc(actionLabel(a))}</h3><div class="acts">${back}<button type="button" class="btn tiny ghost danger" data-arm="${p.i}">${esc(c('remove'))}</button></div>`;
    if (a.workflow != null){
      const known = WFS.some(w => w.pub === a.workflow);
      h += `<div class="fld"><label for="ldx-wfpub">${esc(c('wfPub'))}</label><select id="ldx-wfpub" data-aval="workflow">${known ? '' : `<option value="${esc(a.workflow)}" selected>${esc(a.workflow)}</option>`}${WFS.map(w => `<option value="${esc(w.pub)}"${w.pub === a.workflow ? ' selected' : ''}>${esc((T(w.lab) || w.pub) + ' · ' + w.pub)}</option>`).join('')}</select></div>`;
      h += `<div class="fld"><label for="ldx-wfvar">${esc(c('wfVar'))}</label><input type="text" id="ldx-wfvar" data-aval="var" value="${esc(a.var || '')}"></div>` + (a.var ? '' : `<div class="where warn">${esc(c('wfVarMissing'))}</div>`);
      h += `<label class="chk"><input type="checkbox" data-asingle="1"${a.single ? ' checked' : ''}>${esc(c('wfSingle'))}</label>`;
      h += `<div class="fld"><span class="l">${esc(c('btnLabel'))}</span>${locIn(a, 'label', actionLabel(Object.assign({}, a, {label:null})))}</div>`;
      h += `<div class="fld"><span class="l">${esc(c('shownIf'))}</span>${condEd(a, 'cond', true)}</div>`;
      h += `<div class="where">${esc(c('actionD'))}</div>`;
    } else if (a.action === 'linked'){
      h += `<div class="fld"><span class="l">${esc(c('btnLabel'))}</span>${locIn(a, 'label', actionLabel(Object.assign({}, a, {label:null})))}</div>`;
      h += `<label class="chk"><input type="checkbox" data-ahide="1"${a.hideWhenNone !== false ? ' checked' : ''}>${esc(c('hideNone'))}</label>`;
      h += `<div class="fld"><span class="l">${esc(c('shownIf'))}</span>${condEd(a, 'cond', true)}</div>`;
      h += `<div class="where">${esc(c('linkedD'))}</div>`;
    } else if (a.service != null){
      h += `<div class="fld"><label for="ldx-svc">${esc(c('svcKey'))}</label><input type="text" id="ldx-svc" data-aval="service" value="${esc(a.service)}" placeholder="module@Service"></div>`;
      h += `<div class="fld"><span class="l">${esc(c('btnLabel'))}</span>${locIn(a, 'label', a.service)}</div>`;
    }
    h += rolesEd(a);
  } else if (p.k === 'facet'){
    const fc = findFacet(p.id);
    if (!fc){ S.pick = {k:'facets'}; return renderInspector(); }
    const i = X.facets.indexOf(fc);
    h += `<h3>${esc(c('facet'))} · ${esc(T(fc.title))}</h3><div class="acts"><button type="button" class="btn tiny ghost" data-fmv="-1"${i ? '' : ' disabled'}>↑ ${esc(c('up'))}</button><button type="button" class="btn tiny ghost" data-fmv="1"${i < X.facets.length - 1 ? '' : ' disabled'}>↓ ${esc(c('down'))}</button><button type="button" class="btn tiny ghost danger" data-del="facet">${esc(c('del'))}</button></div>`;
    h += `<div class="fld"><span class="l">${esc(c('facetTitle'))}</span>${locIn(fc, 'title')}</div>`;
    const tot = (fc.kind === 'field' || fc.kind === 'tree') && fc.field ? distinctTotal(fc.field) : 0, tooMany = tot > MAXLIST;
    if (fc.kind !== 'favorites') h += `<div class="fld"><span class="l">${esc(c('widget'))}</span><div class="seg">${[['checks', c('wChecks')], ['chips', c('wChips')], ['select', c('wSelect')]].map(x => { const off = x[0] === 'select' && tooMany; return `<button type="button" data-fw="${x[0]}" aria-pressed="${fc.widget === x[0] && !off}"${off ? ` disabled title="${esc(c('tooMany'))}"` : ''}>${esc(x[1])}</button>`; }).join('')}</div><span class="hint">${esc(tooMany ? c('noSelect') : fc.widget === 'select' ? c('oneAtATime') : c('several'))}</span></div>`;
    if (fc.kind === 'field' || fc.kind === 'tree' || fc.kind === 'ranges') h += `<div class="fld"><label for="ldx-ffield">${esc(c('field'))}</label><select id="ldx-ffield" data-ffield="1">${refOptions(fc.field, r => fc.kind === 'ranges' ? kindOf(r) === 'num' : fc.kind === 'tree' ? meta(r).t === 'fk' : facetable(r))}</select></div>`;
    if (tot) h += `<div class="where"><b>${esc(c('distinct', fmtN(tot)))}</b>${tot > 12 ? '<br>' + esc(c('distinctLong')) : ''}</div>`;
    if (tot > 12) h += `<div class="fld"><label for="ldx-ftop">${esc(c('topFirst'))}</label><select id="ldx-ftop" data-ftop="1">${[5, 10, 15, 20].map(n => `<option${(+fc.top || TOP) === n ? ' selected' : ''}>${n}</option>`).join('')}</select></div>`;
    if (fc.kind === 'tree') h += `<div class="fld"><label for="ldx-fparent">${esc(c('parentPath'))}</label><input type="text" id="ldx-fparent" data-fparent="1" value="${esc(fc.parent || '')}" placeholder="/parent"></div>`;
    if (fc.kind === 'field' && fc.field) h += `<div class="where">${esc(c('labelsModel'))}</div>`;
    if (fc.kind === 'favorites') h += `<div class="where">${esc(c('favD'))}</div>`;
    if (fc.kind === 'flags'){
      h += `<div class="where">${esc(c('flagsD'))}</div>`;
      h += fc.flags.map((fl, k) => `<div class="rule"><div class="rh"><b>${esc(T(fl.label) || fl.id)}</b><span class="hint"><code class="v">${esc(fl.id)}</code> · ${esc(c('nRecords', fmtN(ROWS.filter(r => test(r, fl.when)).length)))}</span></div>${locIn(fl, 'label')}${condEd(fl, 'when', false)}<div class="row2"><select data-fltone="${k}" aria-label="${esc(c('colour'))}">${toneOpts(fl.tone || 'warn')}</select><button type="button" class="btn tiny ghost danger" data-rmflag="${k}">${esc(c('remove'))}</button></div></div>`).join('');
      h += `<button type="button" class="btn small ghost" data-addflag="1">${esc(c('addFlag'))}</button>`;
    }
    if (fc.kind === 'ranges'){
      h += `<div class="where">${esc(c('rangesD'))}</div>`;
      h += fc.ranges.map((r, k) => `<div class="rule"><div class="rh"><b>${esc(T(r.label) || r.code)}</b><span class="hint"><code class="v">${esc(r.code)}</code> · ${esc(c('nRecords', fmtN(ROWS.filter(x => facetVals(fc, x).indexOf(String(r.code)) >= 0).length)))}</span></div>${locIn(r, 'label')}<div class="row3"><input type="number" data-rmin="${k}" value="${esc(r.min == null ? '' : r.min)}" placeholder="${esc(c('min'))}" aria-label="${esc(c('min'))}"><input type="number" data-rmax="${k}" value="${esc(r.max == null ? '' : r.max)}" placeholder="${esc(c('max'))}" aria-label="${esc(c('max'))}"><button type="button" class="btn tiny ghost danger" data-rmrange="${k}">${esc(c('remove'))}</button></div></div>`).join('');
      h += `<button type="button" class="btn small ghost" data-addrange="1">${esc(c('addRange'))}</button>`;
    }
    h += `<label class="chk"><input type="checkbox" data-fopen="1"${fc.open ? ' checked' : ''}>${esc(c('openStart'))}</label>`;
    h += rolesEd(fc);
    h += `<div class="where">${esc(c('permFacet'))}</div>`;
  } else if (p.k === 'tile'){
    const t = findTile(p.id);
    if (!t){ S.pick = {k:'tiles'}; return renderInspector(); }
    const ti = X.tiles.indexOf(t), fl = allFlags(), mode = t.flag ? 'flag' : t.set ? 'set' : 'cond';
    h += `<h3>${esc(c('tile'))} · ${esc(T(t.label))}</h3><div class="acts"><button type="button" class="btn tiny ghost" data-tmv="-1"${ti ? '' : ' disabled'}>${esc(c('before'))}</button><button type="button" class="btn tiny ghost" data-tmv="1"${ti < X.tiles.length - 1 ? '' : ' disabled'}>${esc(c('after'))}</button><button type="button" class="btn tiny ghost danger" data-del="tile">${esc(c('del'))}</button></div>`;
    h += `<div class="fld"><span class="l">${esc(c('tileLabel'))}</span>${locIn(t, 'label')}</div>`;
    h += `<div class="fld"><label for="ldx-ttone">${esc(c('numColour'))}</label><select id="ldx-ttone" data-ttone="1">${toneOpts(t.tone || '', c('neutral'))}</select></div>`;
    h += `<div class="fld"><span class="l">${esc(c('counts'))}</span><div class="seg"><button type="button" data-tmode="cond" aria-pressed="${mode === 'cond'}">${esc(c('tCond'))}</button><button type="button" data-tmode="flag" aria-pressed="${mode === 'flag'}"${fl.length ? '' : ' disabled'}>${esc(c('tFlag'))}</button>${mode === 'set' ? `<button type="button" aria-pressed="true">${esc(c('tSet'))}</button>` : ''}</div></div>`;
    if (mode === 'flag') h += `<select data-tflag="1" aria-label="${esc(c('tFlag'))}">${fl.map(x => `<option value="${esc(x.id)}"${x.id === t.flag ? ' selected' : ''}>${esc(T(x.label) || x.id)}</option>`).join('')}${fl.some(x => x.id === t.flag) ? '' : `<option selected>${esc(t.flag)}</option>`}</select>`;
    else if (mode === 'set') h += `<div class="where">${esc(c('setD'))} ${esc(Object.keys(t.set).map(fid => { const fc = findFacet(fid); return (fc ? T(fc.title) : fid) + ' = ' + (t.set[fid] || []).map(v => fc ? facetLab(fc, String(v)) : v).join(', '); }).join(' ; '))}</div>`;
    else h += condEd(t, 'when', false);
    if (!fl.length) h += `<p class="hint">${esc(c('needFlags'))}</p>`;
    h += rolesEd(t);
  } else if (p.k === 'tiles'){
    h += `<h3>${esc(c('tiles'))}</h3><div class="where">${esc(c('tilesD'))}</div>`;
    h += X.tiles.map(t => `<div class="part partrow" data-picktile="${esc(t.id)}"><span>${esc(T(t.label))} ${rolesOf(t).length ? `<span class="aud">${esc(rolesOf(t).map(roleLab).join(', '))}</span>` : ''}</span><button type="button" data-picktile="${esc(t.id)}" aria-label="${esc(c('edit'))}">✎</button></div>`).join('');
    h += `<button type="button" class="btn small ghost" data-addpart="tile">${esc(c('addTile'))}</button>`;
  } else if (p.k === 'facets'){
    h += `<h3>${esc(c('facets'))}</h3><div class="where">${esc(c('facetsD'))}</div>`;
    h += X.facets.map(fc => `<div class="part partrow" data-pickfacet="${esc(fc.id)}"><span>${esc(T(fc.title))} ${rolesOf(fc).length ? `<span class="aud">${esc(rolesOf(fc).map(roleLab).join(', '))}</span>` : ''}</span><button type="button" data-pickfacet="${esc(fc.id)}" aria-label="${esc(c('edit'))}">✎</button></div>`).join('');
    h += `<button type="button" class="btn small ghost" data-addpart="flags">+ ${esc(c('elFlags'))}</button>`;
  } else if (p.k === 'search'){
    h += `<h3>${esc(c('searchZ'))}</h3><div class="where">${esc(c('searchD'))}</div>`;
    h += `<div class="sechd">${esc(c('searched'))}</div>` + X.search.fields.map((r, i) => `<div class="part"><span>${esc(labelOf(r))}</span><button type="button" data-srm="${i}" aria-label="${esc(c('remove'))}">✕</button></div>`).join('');
    h += `<select data-sadd="1" aria-label="${esc(c('searched'))}"><option value="">${esc(c('addDots'))}</option>${refOptions('', r => X.search.fields.indexOf(r) < 0 && searchable(r))}</select>`;
    h += `<div class="fld"><span class="l">${esc(c('placeholder'))}</span>${locIn(X.search, 'placeholder', X.search.fields.map(labelOf).join(', ') + '…')}</div>`;
    h += `<div class="sechd">${esc(c('sorts'))}</div>` + X.sorts.map((s, i) => `<div class="rule"><div class="part"><span>${esc(labelOf(refOf(s)))}</span><button type="button" data-sdir="${i}" title="${esc(s.dir === 'desc' ? c('desc') : c('asc'))}" aria-label="${esc(s.dir === 'desc' ? c('desc') : c('asc'))}">${s.dir === 'desc' ? '↓' : '↑'}</button><button type="button" data-smv="${i}" data-d="-1"${i ? '' : ' disabled'} aria-label="${esc(c('up'))}">⇡</button><button type="button" data-smv="${i}" data-d="1"${i < X.sorts.length - 1 ? '' : ' disabled'} aria-label="${esc(c('down'))}">⇣</button><button type="button" data-sortrm="${i}" aria-label="${esc(c('remove'))}">✕</button></div><span class="hint">${esc(c('sortLabel'))}</span>${locIn(s, 'label', labelOf(refOf(s)))}${rolesEd(s)}</div>`).join('');
    h += `<select data-sortadd="1" aria-label="${esc(c('sorts'))}"><option value="">${esc(c('addDots'))}</option>${refOptions('', r => kindOf(r) !== 'media' && meta(r).t !== 'list')}</select>`;
    const gref = X.group ? refOf(X.group) : '';
    h += `<div class="sechd">${esc(c('groupBy'))}</div><select data-groupby="1" aria-label="${esc(c('groupBy'))}"><option value="">${esc(c('noGroupBy'))}</option>${refOptions(gref, r => kindOf(r) === 'list' && meta(r).t !== 'list')}</select><div class="hint">${esc(c('groupHint'))}</div>`;
    if (X.group) h += `<div class="fld"><span class="l">${esc(c('groupEmpty'))}</span>${locIn(X.group, 'emptyLabel', c('noGroupVal'))}</div>`;
    const hd = X.header || {};
    h += `<div class="sechd">${esc(c('header'))}</div><label class="chk"><input type="checkbox" data-hcreate="1"${hd.create ? ' checked' : ''}>${esc(c('hCreate'))}</label>`;
    h += `<div class="sechd">${esc(c('hLinks'))}</div>` + (hd.links || []).map((l, i) => `<div class="rule">${locIn(l, 'label', l.table || l.url || '')}`
      + `<input type="text" data-hl="${i}|table" value="${esc(l.table || '')}" placeholder="/root/…" aria-label="${esc(c('hlTable'))}" title="${esc(c('hlTable'))}">`
      + `<input type="text" data-hl="${i}|predicate" value="${esc(l.predicate || '')}" placeholder="${esc(c('hlPred'))}" aria-label="${esc(c('hlPred'))}">`
      + `<input type="text" data-hl="${i}|service" value="${esc(l.service || '')}" placeholder="${esc(c('hlSvc'))}" aria-label="${esc(c('hlSvc'))}">`
      + `<input type="text" data-hl="${i}|url" value="${esc(l.url || '')}" placeholder="${esc(c('hlUrl'))}" aria-label="${esc(c('hlUrl'))}">`
      + `${rolesEd(l)}<button type="button" class="btn tiny ghost danger" data-hlrm="${i}">${esc(c('remove'))}</button></div>`).join('');
    h += `<button type="button" class="btn tiny ghost" data-hladd="1">${esc(c('hAddLink'))}</button>`;
    h += `<div class="where">${esc(c('permSearch'))}</div>`;
  } else if (p.k === 'variant'){
    const V = D.variants[vi];
    h += `<h3>${esc(c('variant'))} · ${esc(T(V.name))}</h3><div class="where">${esc(c('variantD'))}</div>`;
    h += `<div class="fld"><span class="l">${esc(c('vName'))}</span>${locIn(V, 'name')}</div>`;
    h += vi > 0 ? rolesEd(V, c('forRoles'), ' ', c('neverShown')) : `<div class="where">${esc(c('firstVariant'))}</div>`;
    h += numO('pageSize', c('pageSize'), X.pageSize || 48, 'psize').replace('data-onum', 'data-psize');
    h += `<div class="acts"><button type="button" class="btn small ghost" data-addpart="variant">${esc(c('dupVariant'))}</button>${vi > 0 ? `<button type="button" class="btn small ghost danger" data-delvar="1">${esc(c('delVariant'))}</button>` : ''}</div>`;
    h += `<div class="sechd">${esc(c('allVariants'))}</div>` + D.variants.map((v, k) => `<div class="part partrow${k === vi ? ' on' : ''}" data-usevar="${k}"><span>${esc(T(v.name))} ${k ? (rolesOf(v).length ? `<span class="aud">${esc(rolesOf(v).map(roleLab).join(', '))}</span>` : `<span class="hint">${esc(c('neverShown'))}</span>`) : ''}</span><button type="button" data-usevar="${k}" aria-label="${esc(c('edit'))}">✎</button></div>`).join('');
    h += `<div class="sechd">${esc(c('explorerS'))}</div>`;
    h += `<div class="fld"><label for="ldx-label">${esc(c('rowLabel'))}</label><input type="text" id="ldx-label" data-label="1" value="${esc(label)}"></div>`;
    h += `<div class="fld"><span class="l">${esc(c('pageTitle'))}</span>${locIn(D, 'title', label)}</div>`;
    h += rolesEd(D.openTo ? {roles:D.openTo, __open:true} : {roles:[]}, c('openTo'), ' ', c('openToHint'));
    const ao = X.asOf || {}, dateF = FIELDS.filter(f => f.t === 'date' || f.t === 'dt');
    const dsel = (key, cur, none) => `<select data-asof="${key}"><option value="">${esc(none)}</option>${dateF.map(f => `<option value="${esc(f.p)}"${f.p === cur ? ' selected' : ''}>${esc(labelOf(f.p))}  ${esc(f.p)}</option>`).join('')}</select>`;
    h += `<div class="sechd">${esc(c('asOfS'))}</div><div class="fld"><span class="l">${esc(c('asOfFrom'))}</span>${dsel('from', ao.from, c('asOfNone'))}</div>`;
    if (ao.from) h += `<div class="fld"><span class="l">${esc(c('asOfTo'))}</span>${dsel('to', ao.to, '—')}</div><div class="fld"><span class="l">${esc(c('asOfLabel'))}</span>${locIn(X.asOf, 'label')}</div>`;
    h += `<div class="where">${esc(c('asOfHint'))}</div>`;
    const fr = D.freshness || {}, frMode = fr.mode === 'auto' ? 'auto' : 'onDemand';
    h += `<div class="fld"><label for="ldx-fresh">${esc(c('freshness'))}</label><select id="ldx-fresh" data-frmode="1"><option value="onDemand"${frMode === 'onDemand' ? ' selected' : ''}>${esc(c('frOnDemand'))}</option><option value="auto"${frMode === 'auto' ? ' selected' : ''}>${esc(c('frAuto'))}</option></select></div>`;
    if (frMode === 'onDemand') h += `<div class="fld"><label for="ldx-frage">${esc(c('frMaxAge'))}</label><input type="number" min="1" max="1440" id="ldx-frage" data-frage="1" value="${esc(fr.maxAgeMinutes || 60)}"></div>`;
    h += `<div class="where">${esc(c('frHint'))}</div>`;
    if (CUR && CUR.id != null){
      h += `<div class="acts"><span class="status${CUR.active === false ? ' off' : ' ok'}">${esc(CUR.active === false ? c('inactive') : c('active'))}</span><button type="button" class="btn small ghost" data-activate="${CUR.active === false ? 'activate' : 'deactivate'}">${esc(CUR.active === false ? c('activate') : c('deactivate'))}</button><button type="button" class="btn small ghost danger" data-delexpl="1">${esc(c('delExplorer'))}</button></div>`;
      if (CUR.active === false) h += `<div class="where warn">${esc(c('isOff'))}</div>`;
    }
    h += `<div class="where">${esc(c('permVariant'))}</div>`;
  }
  $('#ldx-insp').innerHTML = '<div class="insp">' + h + '</div>';
}

/* ======================= steps, top bar ======================= */
function renderSteps(){
  const parts = [].concat(...LISTS.map(l => X.card[l]));
  const anyRoles = D.variants.some(v => rolesOf(v).length) || X.facets.concat(X.tiles, parts, X.card.actions).some(e => rolesOf(e).length);
  const anyW = parts.some(p => p.widget && ['text','tag','num','flags'].indexOf(p.widget) < 0) || X.facets.some(f => f.widget && f.widget !== 'checks');
  const st = [
    [X.facets.some(f => f.kind !== 'flags'), c('stFacet'), 'facets'],
    [flagFacets().some(f => f.flags.length), c('stFlags'), 'facets'],
    [X.tiles.length > 0, c('stTile'), 'tiles'],
    [X.card.subtitle.length + X.card.tags.length + X.card.kpis.length > 1, c('stCard'), 'card'],
    [anyW, c('stWidget'), 'card'],
    [X.search.fields.length > 1, c('stSearch'), 'search'],
    [anyRoles, c('stRoles'), 'card'],
    [D.variants.length > 1, c('stVariant'), 'variant'],
    [S.triedAs, c('stAs'), 'as']
  ];
  $('#ldx-steplist').innerHTML = st.map((s, i) => `<button type="button" class="step${s[0] ? ' done' : ''}" data-step="${s[2]}"><i>${s[0] ? '✓' : i + 1}</i>${esc(s[1])}</button>`).join('');
  $('#ldx-undo').disabled = !HIST.length;
  const sv = $('#ldx-status');
  sv.textContent = dirty ? c('dirty') : CUR && CUR.layout ? c('saved') : c('neverSaved');
  sv.className = 'status' + (dirty || !(CUR && CUR.layout) ? '' : ' ok');
  $('#ldx-var').innerHTML = D.variants.map((v, k) => `<option value="${k}"${k === vi ? ' selected' : ''}>${esc(T(v.name))}${k && rolesOf(v).length ? ' · ' + esc(rolesOf(v).map(roleLab).join(', ')) : ''}</option>`).join('') + `<option value="new">${esc(c('newVariantOpt'))}</option>`;
}
function renderAll(){ keepFocus(() => { renderCanvas(); renderPalette(); renderInspector(); renderSteps(); }); }
/* a re-rendering keeps the focused input (same id) and its caret */
function keepFocus(fn){
  const a = document.activeElement, id = a && root.contains(a) ? a.id : '';
  let s = null, e = null; try { s = a.selectionStart; e = a.selectionEnd; } catch (x) { /* not a text input */ }
  fn();
  if (id){ const n = document.getElementById(id); if (n && n !== a){ n.focus(); try { if (s != null) n.setSelectionRange(s, e); } catch (x) { /* not a text input */ } } }
}
let toastT = 0;
function toast(m){ let t = $('.ldx-toast'); if (!t){ t = document.createElement('div'); t.className = 'ldx-toast'; t.setAttribute('role', 'status'); root.appendChild(t); } t.textContent = m; t.hidden = false; clearTimeout(toastT); toastT = setTimeout(() => { t.hidden = true; }, 2800); }
function commit(msg){ flush(); renderAll(); if (msg) toast(msg); }

/* ======================= edits ======================= */
const P = (ref, w) => { const o = {}; setRef(o, ref); o.widget = w; return o; };
function autoTones(ref){
  if (isLong(ref)) return {};
  const out = {};
  shortValues(ref).forEach(x => { const k = norm(x.v + ' ' + x.l);
    if (/bloqu|block|rejet|reject|inacti|ferm|clos|ko\b|erreur|error|suppr|delet/.test(k)) out[x.v] = 'bad';
    else if (/attente|pending|cours|progress|validation|review|brouillon|draft/.test(k)) out[x.v] = 'info';
    else if (/ecart|warn|alert|risque|risk/.test(k)) out[x.v] = 'warn';
    else if (/actif|active|valid|ok\b|conform|verifi|checked|true/.test(k)) out[x.v] = 'ok'; });
  return out;
}
function nice(x){ if (!x) return 0; const p = Math.pow(10, Math.floor(Math.log10(Math.abs(x)))); return Math.round(x / p) * p; }
function autoRanges(ref){
  const xs = ROWS.map(r => numOf(rv(r, ref))).filter(x => x != null).sort((a, b) => a - b);
  const mx = xs.length ? xs[xs.length - 1] : 100;
  let a, b;
  if (mx <= 100 && (xs[0] || 0) >= 0){ a = 50; b = 80; }
  else { a = nice(xs[Math.floor(xs.length / 3)] || mx / 3); b = nice(xs[Math.floor(2 * xs.length / 3)] || 2 * mx / 3); if (b <= a) b = a * 2 || 1; }
  const f = n => String(n);
  return [{code:'lo', label:lc('rangeLo', f(a)), max:a}, {code:'mid', label:lc('rangeMid', f(a), f(b)), min:a, max:b}, {code:'hi', label:lc('rangeHi', f(b)), min:b}];
}
/* a product text built from a chrome string, in French where the instance has French, English elsewhere */
function lc(k, ...a){ const fr = C[k][0], en = C[k][1], sub = s => { a.forEach((v, i) => { s = s.split('{' + i + '}').join(v); }); return s; }; return L(sub(fr), sub(en)); }
function defaultTile(ref){ return {id:uid('t'), label:txt(meta(ref).lab) || L(labelOf(ref), labelOf(ref)), when:defaultCond(ref)}; }
function addField(act, ref){
  const m = meta(ref), lab = labelOf(ref), cd = X.card;
  snap(); useRef(ref);
  let msg = c('tAdded', lab);
  if (act === 'facet' || act === 'ranges'){
    const id = uid('f'), title = txt(m.lab) || L(lab, lab);
    X.facets.push(act === 'ranges' ? {id, kind:'ranges', field:ref, title, widget:'checks', open:true, ranges:autoRanges(ref)} : {id, kind:'field', field:ref, title, widget:!isLong(ref) && shortValues(ref).length <= 4 ? 'chips' : 'checks', open:true});
    S.pick = {k:'facet', id}; msg = c('tFacet', lab);
  } else if (act === 'card' && kind(m) === 'media'){
    cd.image = cd.image || {field:ref, shape:'logo', fit:'contain', whenMissing:'initials'}; S.pick = {k:'image'};
  } else if (act === 'card'){
    const k = kind(m);
    let list = null;
    if (k === 'num'){ const mx = maxOf(ref); if (!refOf(cd.gauge) && mx <= 100 && /score|qual|taux|rate|pct|percent|complet/i.test(ref + ' ' + lab)){ cd.gauge = {field:ref, widget:'bar', max:100, green:80, amber:50}; if (isDer(ref)){ delete cd.gauge.field; cd.gauge.derived = ref.slice(8); } S.pick = {k:'gauge'}; } else list = 'kpis'; }
    else if (k === 'list' && !isLong(ref) && m.vals && !refOf(cd.pill)){ cd.pill = {colors:autoTones(ref), default:'muted'}; setRef(cd.pill, ref); S.pick = {k:'pill'}; }
    else list = k === 'list' ? 'tags' : k === 'date' ? 'lines' : 'subtitle';
    if (list){ cd[list].push(P(ref, defW(list, ref))); S.pick = {k:'part', list, i:cd[list].length - 1}; }
  } else if (act === 'search'){
    X.search.fields.push(ref); S.pick = {k:'search'}; msg = c('tSearch', lab);
  } else if (act === 'tile'){
    const t = defaultTile(ref); X.tiles.push(t); S.pick = {k:'tile', id:t.id}; msg = c('tTile');
  } else if (act === 'sort'){
    const s = {}; setRef(s, ref); s.dir = kindOf(ref) === 'num' || kindOf(ref) === 'date' ? 'desc' : 'asc'; X.sorts.push(s); S.pick = {k:'search'}; msg = c('tSort', lab);
  }
  commit(msg);
}
function removeField(act, ref){
  const lab = labelOf(ref), cd = X.card;
  snap();
  if (act === 'facet' || act === 'ranges'){
    X.facets.filter(f => f.kind !== 'flags' && f.field === ref).forEach(f => { delete S.sel[f.id]; if (S.pick.k === 'facet' && S.pick.id === f.id) S.pick = {k:'facets'}; });
    X.facets = X.facets.filter(f => !(f.kind !== 'flags' && f.field === ref));
  } else if (act === 'card'){
    LISTS.forEach(l => { cd[l] = cd[l].filter(p => refOf(p) !== ref); });
    if (refOf(cd.pill) === ref) delete cd.pill;
    if (refOf(cd.gauge) === ref) delete cd.gauge;
    if (cd.image && cd.image.field === ref) delete cd.image;
    if ((typeof cd.title === 'string' ? cd.title : refOf(cd.title)) === ref) delete cd.title;
    if (['part','pill','gauge','image'].indexOf(S.pick.k) >= 0) S.pick = {k:'card'};
  } else if (act === 'search'){
    X.search.fields = X.search.fields.filter(x => x !== ref);
  } else if (act === 'sort'){
    X.sorts = X.sorts.filter(s => refOf(s) !== ref); S.sort = 0;
  }
  commit(c('tRemoved', lab));
}
function addPart(what){
  snap();
  let msg = '';
  if (what === 'flags'){
    const ex = flagFacets()[0], fl = {id:uid('FL'), label:lc('newFlag'), tone:'warn', when:null};
    const num = allRefs(r => kindOf(r) === 'num')[0];
    fl.when = num ? {field:num, lt:70} : null; if (num && isDer(num)){ delete fl.when.field; fl.when.derived = num.slice(8); useRef(num); }
    if (ex){ ex.flags.push(fl); S.pick = {k:'facet', id:ex.id}; }
    else { const id = uid('f'); X.facets.unshift({id, kind:'flags', title:lc('needs'), widget:'checks', open:true, flags:[fl]}); S.pick = {k:'facet', id}; }
    msg = c('tFlagAdded');
  } else if (what === 'favorites'){
    const id = uid('f'); X.facets.push({id, kind:'favorites', title:lc('myFavs'), widget:'checks', open:true}); S.pick = {k:'facet', id};
    if (!X.card.actions.some(a => a.action === 'favorite')) X.card.actions.push({action:'favorite'});
  } else if (what === 'tile'){
    const r = allRefs(x => kindOf(x) === 'list' && !isLong(x))[0] || allRefs()[0];
    const t = r ? defaultTile(r) : {id:uid('t'), when:null}; t.label = lc('newTile'); X.tiles.push(t); S.pick = {k:'tile', id:t.id}; msg = c('tTile');
  } else if (what === 'variant'){
    const n = D.variants.length, nv = {id:uid('v'), name:L('Variante ' + n, 'Variant ' + n, 'Variante ' + n), roles:[], layout:clone(X)};
    D.variants.push(nv); useVariant(D.variants.length - 1); S.pick = {k:'variant'}; msg = c('tVariant');
  } else if (what === 'open' || what === 'favorite'){
    if (!X.card.actions.some(a => a.action === what)) X.card.actions.push({action:what}); S.pick = {k:'card'};
  } else if (what.indexOf('wf:') === 0){
    const w = WFS.find(x => x.pub === what.slice(3));
    X.card.actions.push({workflow:what.slice(3), var:'', single:true, label:w ? txt(w.lab) : {}}); S.pick = {k:'action', i:X.card.actions.length - 1};
  } else if (what.indexOf('link:') === 0){
    const n = what.slice(5), d = defOf(n) || {}; useRef('derived:' + n);
    const a = {action:'linked', count:n, label:{}};
    if (d.association) a.association = d.association; else { a.table = d.table; a.fk = d.fk; }
    X.card.actions.push(a); S.pick = {k:'action', i:X.card.actions.length - 1};
  } else if (what === 'service'){
    X.card.actions.push({service:'', label:{}}); S.pick = {k:'action', i:X.card.actions.length - 1};
  } else if (what === 'flagtags'){
    let fc = flagFacets()[0];
    if (!fc){ HIST.pop(); addPart('flags'); snap(); fc = flagFacets()[0]; }
    if (!X.card.tags.some(p => p.flags === fc.id)) X.card.tags.push({flags:fc.id, widget:'flags'});
    S.pick = {k:'card'};
  }
  commit(msg);
}
/* a starting explorer built from the model alone: list fields as facets, the record's own label as title, linked counts as key figures */
function suggestion(){
  const l = emptyLayout(), cd = l.card, fl = FIELDS.filter(f => f.t !== 'media');
  const str = fl.filter(f => f.t === 'str'), lists = fl.filter(f => (f.t === 'enum' || f.t === 'bool' || f.t === 'fk') && !f.fn);
  const nums = fl.filter(f => f.t === 'int' || f.t === 'dec'), score = nums.find(f => /score|qual|complet/i.test(f.p)) || null;
  const der = Object.keys(SUGG);
  const flags = [];
  if (SUGG.suspects) flags.push({id:'SUSP', label:lc('possibleDup'), tone:'warn', when:{derived:'suspects', filled:true}});
  if (score) flags.push({id:'QUAL', label:lc('toComplete'), tone:'bad', when:{field:score.p, lt:70}});
  if (flags.length) l.facets.push({id:'f_flags', kind:'flags', title:lc('needs'), widget:'checks', open:true, flags});
  lists.slice(0, 3).forEach((f, i) => l.facets.push({id:'f' + (i + 1), kind:'field', field:f.p, title:txt(f.lab), widget:(f.vals || []).length && f.vals.length <= 4 ? 'chips' : 'checks', open:true}));
  if (score) l.facets.push({id:'f_score', kind:'ranges', field:score.p, title:txt(score.lab), widget:'checks', open:true, ranges:autoRanges(score.p)});
  const en = lists.find(f => f.t === 'enum' && f.vals && f.vals.length);
  if (en) l.tiles.push({id:'t1', label:txt(en.vals[0].lab), when:{field:en.p, in:[String(en.vals[0].v)]}});
  flags.forEach((x, i) => l.tiles.push({id:'t' + (i + 2), label:clone(x.label), tone:x.tone, flag:x.id}));
  l.search.fields = str.slice(0, 4).map(f => f.p);
  const media = FIELDS.find(f => f.t === 'media');
  if (media) cd.image = {field:media.p, shape:'logo', fit:'contain', whenMissing:'initials'};
  const pillF = lists.find(f => f.t === 'enum' && /statu|state|etat/i.test(f.p)) || en;
  if (pillF){ cd.pill = {field:pillF.p, colors:{}, default:'muted'}; flush(); D.derived = D.derived || {}; cd.pill.colors = autoTones(pillF.p); }
  const ov = lists.find(f => f.t === 'enum' && f !== pillF); if (ov) cd.overline.push({field:ov.p, widget:'text'});
  str.slice(1, 3).forEach(f => cd.subtitle.push({field:f.p, widget:'text'}));
  const ctry = fl.find(f => isCountry(Object.assign({ref:f.p}, f))); if (ctry) cd.subtitle.push({field:ctry.p, widget:'country'});
  lists.filter(f => f !== pillF && f !== ov && f !== ctry).slice(0, 2).forEach(f => cd.tags.push({field:f.p, widget:'tag'}));
  if (flags.length) cd.tags.push({flags:'f_flags', widget:'flags'});
  der.filter(n => SUGG[n].def && (SUGG[n].def.type === 'count')).slice(0, 3).forEach(n => cd.kpis.push({derived:n, widget:'num'}));
  const money = nums.find(f => f !== score && f.t === 'dec'); if (money) cd.kpis.push({field:money.p, widget:'money', options:{compact:true}});
  if (score) cd.gauge = {field:score.p, widget:'bar', max:100, green:80, amber:50};
  cd.actions = [{action:'open'}, {action:'favorite'}];
  if (str[0]) l.sorts.push({field:str[0].p, dir:'asc'});
  if (score) l.sorts.push({field:score.p, dir:'desc'});
  return l;
}
function useDerivedOf(l){ const s = JSON.stringify(l); Object.keys(SUGG).forEach(n => { if (s.indexOf('"derived":"' + n + '"') >= 0 || s.indexOf('derived:' + n + '"') >= 0) useRef('derived:' + n); }); }

/* ======================= clicks and inputs ======================= */
const getReg = i => REG[+i] || {};
function onCondChanged(){ flush(); renderCanvas(); renderInspector(); renderSteps(); }
const ACTMSG = a => a.action === 'linked' ? c('aLinked') + ' · ' + actionLabel(a) : a.action === 'open' ? c('wouldOpen') : a.action === 'favorite' ? c('wouldFav') : a.workflow ? c('wouldWf', a.workflow) : c('wouldSvc', a.service || '?');
root.addEventListener('click', e => {
  const t = e.target;
  let el;
  if ((el = t.closest('[data-pt]'))){ S.pt = el.dataset.pt; return renderPalette(); }
  if ((el = t.closest('[data-rem]'))) return removeField(el.dataset.rem, el.dataset.ref);
  if ((el = t.closest('[data-add]'))) return addField(el.dataset.add, el.dataset.ref);
  if ((el = t.closest('[data-addpart]'))) return addPart(el.dataset.addpart);
  if ((el = t.closest('[data-fmore]'))){ const fc = findFacet(el.dataset.fmore); S.more[fc.id] = Math.min(MAXLIST, (S.more[fc.id] || +fc.top || TOP) + 10); return keepFocus(renderCanvas); }
  if ((el = t.closest('[data-fless]'))){ delete S.more[el.dataset.fless]; return renderCanvas(); }
  if (t.closest('[data-tmore]')){ S.tmore = Math.min(MAXLIST, S.tmore + 10); return renderInspector(); }
  if ((el = t.closest('[data-taddv]'))){ snap(); const o = optTarget(); o.colors = o.colors || {}; o.colors[el.dataset.taddv] = 'warn'; return commit(''); }
  if ((el = t.closest('[data-otonerm]'))){ snap(); const o = optTarget(); delete o.colors[el.dataset.otonerm]; return commit(''); }
  if ((el = t.closest('[data-step]'))){ const s = el.dataset.step; if (s === 'as'){ $('#ldx-as').focus(); return; } S.pick = {k:s}; setMode('design'); return; }
  if (t.closest('#ldx-varset')){ S.pick = {k:'variant'}; setMode('design'); return; }
  if ((el = t.closest('[data-copyv]'))){ const v = el.dataset.copyv; try { navigator.clipboard.writeText(v).then(() => toast(c('copiedV', v)), () => toast(v)); } catch (x) { toast(v); } return; }
  if ((el = t.closest('[data-tile]'))){ const id = el.dataset.tile; if (S.mode === 'design') S.pick = {k:'tile', id}; S.tile = S.tile === id ? null : id; renderCanvas(); return renderInspector(); }
  if (t.matches('input[type=checkbox][data-f]')){ const fid = t.dataset.f; (S.sel[fid] = S.sel[fid] || {})[t.dataset.v] = t.checked; if (S.mode === 'design') S.pick = {k:'facet', id:fid}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-chipf]'))){ const fid = el.dataset.chipf, sm = (S.sel[fid] = S.sel[fid] || {}); sm[el.dataset.v] = !sm[el.dataset.v]; if (S.mode === 'design') S.pick = {k:'facet', id:fid}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-unsel]'))){ (S.sel[el.dataset.unsel] || {})[el.dataset.v] = false; return renderCanvas(); }
  if (t.closest('[data-untile]')){ S.tile = null; return renderCanvas(); }
  if (S.mode === 'design' && (el = t.closest('#ldx-canvas [data-part]'))){ e.preventDefault(); const pp = el.dataset.part.split('|'); S.pick = {k:'part', list:pp[0], i:+pp[1]}; renderCanvas(); return renderInspector(); }
  if (S.mode === 'design' && (el = t.closest('[data-cpick]')) && !t.closest('button:not([data-cpick])')){ const w = el.dataset.cpick; S.pick = w === 'title' ? {k:'card'} : {k:w}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-act]'))) return toast(ACTMSG(X.card.actions[+el.dataset.act] || {}));
  if (S.mode === 'design' && !t.closest('input,select,button,label,a')){
    if ((el = t.closest('[data-facet]'))){ S.pick = {k:'facet', id:el.dataset.facet}; renderCanvas(); return renderInspector(); }
    if ((el = t.closest('#ldx-canvas [data-zone]'))){ S.pick = {k:el.dataset.zone}; renderCanvas(); return renderInspector(); }
  }
  if ((el = t.closest('[data-pickfacet]'))){ S.pick = {k:'facet', id:el.dataset.pickfacet}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-picktile]'))){ S.pick = {k:'tile', id:el.dataset.picktile}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-pickact]')) && !t.closest('[data-amv],[data-arm]')){ S.pick = {k:'action', i:+el.dataset.pickact}; return renderInspector(); }
  if ((el = t.closest('[data-editpart]')) && !t.closest('[data-mv],[data-rm]')){ const ep = el.dataset.editpart.split('|'); S.pick = {k:'part', list:ep[0], i:+ep[1]}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-back]'))){ S.pick = {k:el.dataset.back}; renderCanvas(); return renderInspector(); }
  if ((el = t.closest('[data-setw]'))){ snap(); const pt = X.card[S.pick.list][S.pick.i]; pt.widget = el.dataset.setw; if ((pt.widget === 'pill' || pt.widget === 'dot') && !(pt.options && pt.options.colors)){ pt.options = pt.options || {}; pt.options.colors = autoTones(refOf(pt)); } return commit(''); }
  if ((el = t.closest('[data-oset]'))){ snap(); const v = el.dataset.val, o = optTarget(); o[el.dataset.oset] = v === 'true' ? true : v === 'false' ? false : v; return commit(''); }
  if ((el = t.closest('[data-cset]'))){ snap(); const cd = X.card, k = el.dataset.cset, v = el.dataset.val;
    if (k === 'minWidth') cd.minWidth = +v;
    else { cd.layout = v; const m = cd.image; if (m){ if (v === 'horizontal') m.shape = 'side'; else if (v === 'compact') m.shape = 'avatar'; else if (m.shape === 'side' || m.shape === 'avatar') m.shape = 'logo'; } }
    return commit(''); }
  if (t.closest('[data-hladd]')){ snap(); X.header = Object.assign({}, X.header || {}); X.header.links = (X.header.links || []).concat([{label:{}}]); return commit(''); }
  if ((el = t.closest('[data-hlrm]'))){ snap(); X.header.links.splice(+el.dataset.hlrm, 1); if (!X.header.links.length) delete X.header.links; return commit(''); }
  if (t.closest('[data-rmimage]')){ snap(); delete X.card.image; S.pick = {k:'card'}; return commit(''); }
  if ((el = t.closest('[data-amv]'))){ const a = X.card.actions, i = +el.dataset.amv, j = i + +el.dataset.d; snap(); a.splice(j, 0, a.splice(i, 1)[0]); return commit(''); }
  if ((el = t.closest('[data-arm]'))){ snap(); X.card.actions.splice(+el.dataset.arm, 1); if (S.pick.k === 'action') S.pick = {k:'card'}; return commit(''); }
  if ((el = t.closest('[data-fw]'))){ const fc = findFacet(S.pick.id); snap(); fc.widget = el.dataset.fw; if (fc.widget === 'select' && S.sel[fc.id]){ const keep = checked(fc)[0]; S.sel[fc.id] = {}; if (keep) S.sel[fc.id][keep] = true; } return commit(''); }
  if ((el = t.closest('[data-usevar]'))){ useVariant(+el.dataset.usevar); S.pick = {k:'variant'}; return renderAll(); }
  if (t.closest('[data-delvar]')){ snap(); const gone = D.variants.splice(vi, 1)[0]; useVariant(0); S.pick = {k:'variant'}; return commit(c('tRemoved', T(gone.name))); }
  if ((el = t.closest('[data-mv]'))){ const m = el.dataset.mv.split('|'), arr = X.card[m[0]], i = +m[1], j = i + +m[2]; snap(); arr.splice(j, 0, arr.splice(i, 1)[0]); if (S.pick.k === 'part' && S.pick.list === m[0] && S.pick.i === i) S.pick.i = j; return commit(''); }
  if ((el = t.closest('[data-rm]'))){ const r = el.dataset.rm.split('|'); snap(); X.card[r[0]].splice(+r[1], 1); if (S.pick.k === 'part') S.pick = {k:'card'}; return commit(''); }
  if ((el = t.closest('[data-fmv]'))){ const i = X.facets.findIndex(x => x.id === S.pick.id), j = i + +el.dataset.fmv; snap(); X.facets.splice(j, 0, X.facets.splice(i, 1)[0]); return commit(''); }
  if ((el = t.closest('[data-tmv]'))){ const i = X.tiles.findIndex(x => x.id === S.pick.id), j = i + +el.dataset.tmv; snap(); X.tiles.splice(j, 0, X.tiles.splice(i, 1)[0]); return commit(''); }
  if ((el = t.closest('[data-del]'))){
    snap();
    if (el.dataset.del === 'facet'){ const gf = findFacet(S.pick.id); X.facets = X.facets.filter(x => x.id !== S.pick.id);
      if (gf && gf.kind === 'flags'){ const ids = gf.flags.map(f => f.id); X.tiles = X.tiles.filter(x => !x.flag || ids.indexOf(x.flag) < 0); LISTS.forEach(l => { X.card[l] = X.card[l].filter(p => p.flags !== gf.id); }); }
      X.tiles.forEach(x => { if (x.set) delete x.set[S.pick.id]; });
      delete S.sel[S.pick.id]; S.pick = {k:'facets'}; }
    else { X.tiles = X.tiles.filter(x => x.id !== S.pick.id); if (S.tile === S.pick.id) S.tile = null; S.pick = {k:'tiles'}; }
    return commit(c('tDeleted'));
  }
  if (t.closest('[data-addflag]')) return addPart('flags');
  if ((el = t.closest('[data-rmflag]'))){ const fc = findFacet(S.pick.id), k = +el.dataset.rmflag; snap(); const fid = fc.flags[k].id; fc.flags.splice(k, 1); X.tiles = X.tiles.filter(x => x.flag !== fid); return commit(''); }
  if (t.closest('[data-addrange]')){ const fc = findFacet(S.pick.id); snap(); const last = fc.ranges[fc.ranges.length - 1]; const min = last && last.max != null ? last.max : null; fc.ranges.push({code:'r' + (fc.ranges.length + 1), label:L('Plage ' + (fc.ranges.length + 1), 'Range ' + (fc.ranges.length + 1)), min}); return commit(''); }
  if ((el = t.closest('[data-rmrange]'))){ const fc = findFacet(S.pick.id); snap(); fc.ranges.splice(+el.dataset.rmrange, 1); return commit(''); }
  if ((el = t.closest('[data-tmode]'))){ const tt = findTile(S.pick.id); snap(); delete tt.set;
    if (el.dataset.tmode === 'flag'){ delete tt.when; tt.flag = allFlags()[0].id; }
    else { delete tt.flag; if (!tt.when){ const r = allRefs(x => kindOf(x) === 'list' && !isLong(x))[0] || allRefs()[0]; tt.when = r ? defaultCond(r) : null; } }
    return commit(''); }
  if ((el = t.closest('[data-srm]'))){ snap(); X.search.fields.splice(+el.dataset.srm, 1); return commit(''); }
  if ((el = t.closest('[data-sortrm]'))){ snap(); X.sorts.splice(+el.dataset.sortrm, 1); S.sort = 0; return commit(''); }
  if ((el = t.closest('[data-sdir]'))){ snap(); const s = X.sorts[+el.dataset.sdir]; s.dir = s.dir === 'desc' ? 'asc' : 'desc'; return commit(''); }
  if ((el = t.closest('[data-smv]'))){ const i = +el.dataset.smv, j = i + +el.dataset.d; snap(); X.sorts.splice(j, 0, X.sorts.splice(i, 1)[0]); S.sort = 0; return commit(''); }
  /* conditions */
  if ((el = t.closest('[data-cadd]'))){ const g = getReg(el.dataset.cadd); const r = allRefs(x => kindOf(x) === 'list' && !isLong(x))[0] || allRefs()[0]; snap(); g.owner[g.key] = r ? defaultCond(r) : null; return commit(''); }
  if ((el = t.closest('[data-crm]'))){ const g = getReg(el.dataset.crm); snap(); delete g.owner[g.key]; return commit(''); }
  if ((el = t.closest('[data-csimp]'))){ const g = getReg(el.dataset.csimp); snap(); const leaf = firstLeaf(g.owner[g.key]); const r = allRefs()[0]; g.owner[g.key] = leaf || (r ? defaultCond(r) : null); return commit(''); }
  if ((el = t.closest('[data-cvrm]'))){ const g = getReg(el.dataset.cvrm), cd = g.owner[g.key], op = opsOf(cd)[0]; snap(); if (!Array.isArray(cd[op])) cd[op] = [cd[op]]; cd[op].splice(+el.dataset.j, 1); return onCondChanged(); }
  /* explorer actions */
  if (t.closest('#ldx-save')){ if (OUTDATED || NOEXPL) return; return post('save'); }
  if (t.closest('#ldx-create')) return post('new', '', TABLE);
  if (t.closest('#ldx-to-form') && B.formDesignerUrl) return guarded(() => { dirty = false; window.top.location.href = B.formDesignerUrl; });
  if (t.closest('#ldx-undo')) return undo();
  if (t.closest('#ldx-codebtn')){ $('#ldx-code').textContent = JSON.stringify(output(), null, 2); $('#ldx-copy').textContent = c('copy'); $('#ldx-dlg').showModal(); return; }
  if (t.closest('#ldx-dlg-close')) return $('#ldx-dlg').close();
  if (t.closest('#ldx-copy')){ const b = $('#ldx-copy'), v = $('#ldx-code').textContent; try { navigator.clipboard.writeText(v).then(() => { b.textContent = c('copied'); }, () => selectCode()); } catch (x) { selectCode(); } return; }
  if (t.closest('#ldx-m-design')) return setMode('design');
  if (t.closest('#ldx-m-preview')) return setMode('preview');
  if (t.closest('#ldx-suggest')){ snap(); D.variants[vi].layout = suggestion(); useVariant(vi); useDerivedOf(X); S.pick = {k:'card'}; return commit(c('tSuggest')); }
  if (t.closest('#ldx-reset')) return armReset(t.closest('#ldx-reset'));
  if ((el = t.closest('[data-activate]'))) return guarded(() => post(el.dataset.activate));
  if (t.closest('[data-delexpl]')) return ask(c('delTitle', label), c('delText'), c('del'), () => post('delete'), true);
  if (t.closest('#ldx-ask-cancel')) return $('#ldx-ask').close();
  if (t.closest('#ldx-ask-ok')){ const f = askFn; $('#ldx-ask').close(); if (f) f(); return; }
});
root.addEventListener('change', e => {
  const t = e.target;
  let g;
  if (t.id === 'ldx-lang'){ lang = t.value; return renderAll(); }
  if (t.id === 'ldx-as'){ S.as = t.value; S.tile = null; if (t.value) S.triedAs = true; useVariant(variantFor(S.as)); if (['part','facet','tile','action'].indexOf(S.pick.k) >= 0) S.pick = {k:'card'}; return renderAll(); }
  if (t.id === 'ldx-var'){ if (t.value === 'new') return addPart('variant'); useVariant(+t.value); S.pick = {k:'variant'}; return renderAll(); }
  if (t.id === 'ldx-expl'){ const v = t.value; t.value = CUR && CUR.id != null ? String(CUR.id) : ''; if (v === '__new') return askNew(); if (v && (!CUR || String(CUR.id) !== v)) return guarded(() => post('switch', v)); return; }
  if (t.id === 'ldx-sortsel'){ S.sort = +t.value; return renderCanvas(); }
  if (t.matches('[data-selectf]')){ const id = t.dataset.selectf; S.sel[id] = {}; if (t.value) S.sel[id][t.value] = true; return renderCanvas(); }
  if (t.matches('[data-role]')){ g = getReg(t.dataset.role); snap(); if (!Array.isArray(g.owner.roles)) g.owner.roles = []; const list = g.owner.__open ? D.openTo : g.owner.roles, k = t.dataset.k, ix = list.indexOf(k); if (t.checked && ix < 0) list.push(k); if (!t.checked && ix >= 0) list.splice(ix, 1); return commit(''); }
  if (t.matches('[data-addto]') && t.value){ snap(); const l = t.dataset.addto; X.card[l].push(P(t.value, defW(l, t.value))); if (!isFlags(t.value)) S.pick = {k:'part', list:l, i:X.card[l].length - 1}; return commit(''); }
  if (t.matches('[data-addimage]') && t.value){ snap(); X.card.image = {field:t.value, shape:'logo', fit:'contain', whenMissing:'initials'}; S.pick = {k:'image'}; return commit(''); }
  if (t.matches('[data-imgf]')){ snap(); X.card.image.field = t.value; return commit(''); }
  if (t.matches('[data-ctitle]')){ snap(); if (t.value) { X.card.title = isDer(t.value) ? {derived:t.value.slice(8)} : t.value; useRef(t.value); } else delete X.card.title; return commit(''); }
  if (t.matches('[data-pill]')){ snap(); if (!t.value) delete X.card.pill; else { const old = X.card.pill; X.card.pill = old && refOf(old) === t.value ? old : {colors:autoTones(t.value), default:'muted'}; setRef(X.card.pill, t.value); } if (!X.card.pill && X.card.edge === 'pill') delete X.card.edge; return commit(''); }
  if (t.matches('[data-gauge]')){ snap(); if (!t.value) delete X.card.gauge; else { const old = X.card.gauge || {widget:'bar', max:100, green:80, amber:50}; X.card.gauge = old; setRef(old, t.value); } return commit(''); }
  if (t.matches('[data-cedge]')){ snap(); if (t.checked) X.card.edge = 'pill'; else delete X.card.edge; return commit(''); }
  if (t.matches('[data-addact]') && t.value){ const v = t.value; if (v === 'open' || v === 'favorite' || v === 'service' || v.indexOf('wf:') === 0 || v.indexOf('link:') === 0) return addPart(v); return; }
  if (t.matches('[data-clays]')){ snap(); const cd = X.card, set = new Set(Array.isArray(cd.layouts) ? cd.layouts : []); if (t.checked) set.add(t.dataset.clays); else set.delete(t.dataset.clays); cd.layouts = ['vertical','horizontal','compact'].filter(x => set.has(x)); if (cd.layouts.length && cd.layouts.indexOf(cd.layout || 'vertical') < 0) cd.layout = cd.layouts[0]; if (!cd.layouts.length) delete cd.layouts; return commit(''); }
  if (t.matches('[data-ahide]')){ snap(); X.card.actions[S.pick.i].hideWhenNone = t.checked; return commit(''); }
  if (t.matches('[data-groupby]')){ snap(); if (t.value){ useRef(t.value); X.group = Object.assign({}, X.group || {}); setRef(X.group, t.value); } else delete X.group; return commit(''); }
  if (t.matches('[data-hcreate]')){ snap(); X.header = Object.assign({}, X.header || {}); if (t.checked) X.header.create = true; else delete X.header.create; return commit(''); }
  if (t.matches('[data-hl]')){ snap(); const [i, k] = t.dataset.hl.split('|'), l = X.header.links[+i], v = t.value.trim(); if (v) l[k] = v; else delete l[k]; return commit(''); }
  if (t.matches('[data-aval]')){ snap(); X.card.actions[S.pick.i][t.dataset.aval] = t.value; return commit(''); }
  if (t.matches('[data-asingle]')){ snap(); X.card.actions[S.pick.i].single = t.checked; return commit(''); }
  if (t.matches('[data-odef]')){ snap(); optTarget().default = t.value; return commit(''); }
  if (t.matches('[data-otone]')){ snap(); const o = optTarget(); o.colors = o.colors || {}; o.colors[t.dataset.otone] = t.value; return commit(''); }
  if (t.matches('[data-oval]')){ snap(); const o = optTarget(); if (t.value === '') delete o[t.dataset.oval]; else o[t.dataset.oval] = t.value; return commit(''); }
  if (t.matches('[data-onum]')){ snap(); const o = optTarget(), k = t.dataset.onum; if (t.value === '') delete o[k]; else o[k] = +t.value; return commit(''); }
  if (t.matches('[data-asof]')){ snap(); const k = t.getAttribute('data-asof'); if (k === 'from' && !t.value) delete X.asOf; else { X.asOf = Object.assign({}, X.asOf || {}); X.asOf[k] = t.value; if (!X.asOf.to) delete X.asOf.to; } return commit(''); }
  if (t.matches('[data-frmode]')){ snap(); D.freshness = Object.assign({}, D.freshness || {}, {mode: t.value}); if (t.value !== 'auto') D.freshness.maxAgeMinutes = D.freshness.maxAgeMinutes || 60; return commit(''); }
  if (t.matches('[data-frage]')){ snap(); D.freshness = Object.assign({mode: 'onDemand'}, D.freshness || {}, {maxAgeMinutes: Math.max(1, Math.min(1440, +t.value || 60))}); return commit(''); }
  if (t.matches('[data-psize]')){ snap(); X.pageSize = Math.max(1, +t.value || 48); return commit(''); }
  if (t.matches('[data-onolabel]')){ snap(); const o = optTarget(); if (t.checked) o.nolabel = true; else delete o.nolabel; return commit(''); }
  if (t.matches('[data-partref]')){ snap(); const pt = X.card[S.pick.list][S.pick.i]; setRef(pt, t.value); if (wFor(t.value).indexOf(pt.widget) < 0) pt.widget = defW(S.pick.list, t.value); pt.options = {}; return commit(''); }
  if (t.matches('[data-partzone]')){ snap(); const src = X.card[S.pick.list], mv = src.splice(S.pick.i, 1)[0], dst = X.card[t.value]; if (t.value === 'kpis' && wFor(refOf(mv)).indexOf('num') >= 0) mv.widget = 'num'; dst.push(mv); S.pick = {k:'part', list:t.value, i:dst.length - 1}; return commit(''); }
  if (t.matches('[data-ffield]')){ const fc = findFacet(S.pick.id); snap(); fc.field = t.value; useRef(t.value); fc.title = txt(meta(t.value).lab) || L(labelOf(t.value), labelOf(t.value)); if (fc.kind === 'ranges') fc.ranges = autoRanges(t.value); delete S.sel[fc.id]; return commit(''); }
  if (t.matches('[data-ftop]')){ const fc = findFacet(S.pick.id); snap(); fc.top = +t.value; delete S.more[fc.id]; return commit(''); }
  if (t.matches('[data-fparent]')){ const fc = findFacet(S.pick.id); snap(); if (t.value) fc.parent = t.value; else delete fc.parent; return commit(''); }
  if (t.matches('[data-fopen]')){ const fc = findFacet(S.pick.id); snap(); fc.open = t.checked; return commit(''); }
  if (t.matches('[data-fltone]')){ const fc = findFacet(S.pick.id); snap(); fc.flags[+t.dataset.fltone].tone = t.value; return commit(''); }
  if (t.matches('[data-rmin],[data-rmax]')){ const fc = findFacet(S.pick.id), k = t.matches('[data-rmin]') ? 'min' : 'max', r = fc.ranges[+(t.dataset.rmin || t.dataset.rmax)]; snap(); if (t.value === '') delete r[k]; else r[k] = +t.value; return commit(''); }
  if (t.matches('[data-ttone]')){ const tt = findTile(S.pick.id); snap(); if (t.value) tt.tone = t.value; else delete tt.tone; return commit(''); }
  if (t.matches('[data-tflag]')){ const tt = findTile(S.pick.id); snap(); tt.flag = t.value; return commit(''); }
  if (t.matches('[data-sadd]') && t.value){ snap(); X.search.fields.push(t.value); useRef(t.value); return commit(''); }
  if (t.matches('[data-sortadd]') && t.value){ snap(); const s = {}; setRef(s, t.value); s.dir = kindOf(t.value) === 'num' || kindOf(t.value) === 'date' ? 'desc' : 'asc'; X.sorts.push(s); return commit(''); }
  if (t.matches('[data-label]')){ snap(); label = t.value.trim() || label; return commit(''); }
  /* conditions */
  if (t.matches('[data-cref]')){ g = getReg(t.dataset.cref); snap(); const old = g.owner[g.key], cd = defaultCond(t.value); if (old && old.roles) cd.roles = old.roles; g.owner[g.key] = cd; return onCondChanged(); }
  if (t.matches('[data-cop]')){ g = getReg(t.dataset.cop); const cd = g.owner[g.key], old = opsOf(cd)[0], prev = old != null ? cd[old] : null; snap(); OPS.forEach(o => { delete cd[o]; });
    const op = t.value, first = Array.isArray(prev) ? prev[0] : prev;
    if (op === 'filled' || op === 'empty') cd[op] = true;
    else if (op === 'days') cd.days = {from: 0, to: 30};
    else if (op === 'in' || op === 'notIn') cd[op] = Array.isArray(prev) ? prev : prev != null && prev !== true ? [String(prev)] : [];
    else if (['gt','lt','ge','le'].indexOf(op) >= 0) cd[op] = first != null && first !== true && isFinite(parseFloat(first)) ? parseFloat(first) : 0;
    else cd[op] = first != null && first !== true ? first : '';
    return onCondChanged(); }
  if (t.matches('[data-cvadd]') && t.value){ g = getReg(t.dataset.cvadd); const cd = g.owner[g.key], op = opsOf(cd)[0]; snap(); if (!Array.isArray(cd[op])) cd[op] = cd[op] != null ? [String(cd[op])] : []; cd[op].push(t.value); return onCondChanged(); }
  if (t.matches('[data-cdays]')){ g = getReg(t.dataset.cdays); const cd = g.owner[g.key]; snap(); cd.days = Object.assign({}, cd.days || {}); if (t.value === '') delete cd.days[t.dataset.b]; else cd.days[t.dataset.b] = Math.round(+t.value); return onCondChanged(); }
  if (t.matches('[data-cvset]')){ g = getReg(t.dataset.cvset); const cd = g.owner[g.key], op = opsOf(cd)[0]; snap(); cd[op] = t.type === 'number' ? (t.value === '' ? 0 : +t.value) : t.value; return onCondChanged(); }
  if (t.matches('[data-cvq]')) return takeTypeahead(t);
});
/* a value typed or picked in a long-list condition: the label the server sent gives the stored code */
const TA = {};
function takeTypeahead(t){
  const v = t.value.trim(); if (!v) return;
  const g = getReg(t.dataset.cvq), cd = g.owner[g.key], op = opsOf(cd)[0], ref = refOf(cd);
  const map = TA[t.dataset.cvq] || {}, code = map[v] != null ? map[v] : (Object.keys(LABS[ref] || {}).find(k => LABS[ref][k] === v) || v);
  snap();
  if (t.dataset.one){ cd[op] = code; }
  else { if (!Array.isArray(cd[op])) cd[op] = cd[op] != null ? [String(cd[op])] : []; if (cd[op].indexOf(code) < 0) cd[op].push(code); t.value = ''; }
  onCondChanged();
}
const typeT = {};
root.addEventListener('input', e => {
  const t = e.target;
  if (t.id === 'ldx-q'){ S.q = t.value; return keepFocus(renderCanvas); }
  if (t.id === 'ldx-psearch') return renderPalette();
  if (t.matches('[data-fq]')){ const fid = t.dataset.fq; S.fq[fid] = t.value; delete S.more[fid]; clearTimeout(typeT.fq); typeT.fq = setTimeout(() => keepFocus(renderCanvas), 250); return; }
  if (t.matches('[data-tq]')){ S.tq = t.value; S.tmore = 10; clearTimeout(typeT.tq); typeT.tq = setTimeout(() => keepFocus(renderInspector), 250); return; }
  if (t.matches('[data-tx]')){
    const g = getReg(t.dataset.tx); if (!t.dataset.snap){ snap(); t.dataset.snap = '1'; }
    const o = g.owner[g.key] && typeof g.owner[g.key] === 'object' ? g.owner[g.key] : (g.owner[g.key] = {});
    if (t.value) o[t.dataset.loc] = t.value; else delete o[t.dataset.loc];
    if (!hasText(o)) delete g.owner[g.key];
    renderCanvas(); renderSteps(); return;
  }
  if (t.matches('[data-cvq]')){
    const i = t.dataset.cvq, g = getReg(i), ref = refOf(g.owner[g.key]), q = t.value;
    clearTimeout(typeT['c' + i]);
    typeT['c' + i] = setTimeout(() => {
      const fill = d => { const dl = document.getElementById('ldx-dl-' + i); if (!dl || !d) return; const map = TA[i] = {}; d.items.forEach(x => { map[x.l] = x.v; }); dl.innerHTML = d.items.map(x => `<option value="${esc(x.l)}">${esc(fmtN(x.n))}</option>`).join(''); };
      const d = values(ref, q, 0, 10, fill); if (d) fill(d);
    }, 200);
  }
});
root.addEventListener('focusin', e => {
  const t = e.target;
  if (t.matches && t.matches('[data-cvq]') && !TA[t.dataset.cvq]){ const g = getReg(t.dataset.cvq), ref = refOf(g.owner[g.key]), i = t.dataset.cvq; const fill = d => { const dl = document.getElementById('ldx-dl-' + i); if (!dl || !d) return; const map = TA[i] = {}; d.items.forEach(x => { map[x.l] = x.v; }); dl.innerHTML = d.items.map(x => `<option value="${esc(x.l)}">${esc(fmtN(x.n))}</option>`).join(''); }; const d = values(ref, '', 0, 10, fill); if (d) fill(d); }
});
root.addEventListener('focusout', e => { if (e.target.dataset && e.target.dataset.snap) delete e.target.dataset.snap; });
root.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.matches && e.target.matches('[data-cvq]')){ e.preventDefault(); takeTypeahead(e.target); } });
document.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && !e.shiftKey && (e.key === 'z' || e.key === 'Z') && !(e.target.closest && e.target.closest('input,select,textarea'))){ e.preventDefault(); undo(); }
});

/* drag a field from the palette onto an area */
let dragF = null;
root.addEventListener('dragstart', e => { const el = e.target.closest && e.target.closest('[data-drag]'); if (!el) return; dragF = el.dataset.drag; try { e.dataTransfer.setData('text/plain', dragF); } catch (x) { /* some browsers refuse */ } });
root.addEventListener('dragover', e => { if (!dragF) return; const z = e.target.closest && e.target.closest('#ldx-canvas [data-zone]'); root.querySelectorAll('.zone.drag').forEach(x => { if (x !== z) x.classList.remove('drag'); }); if (z){ e.preventDefault(); z.classList.add('drag'); } });
root.addEventListener('drop', e => {
  const z = e.target.closest && e.target.closest('#ldx-canvas [data-zone]'); if (!dragF || !z) return; e.preventDefault();
  const ref = dragF, k = z.dataset.zone, kd = kindOf(ref); dragF = null; z.classList.remove('drag');
  if (k === 'facets'){ if (kd === 'num') return addField('ranges', ref); if (kd !== 'media' && kd !== 'date' && facetable(ref)) return addField('facet', ref); return toast(c('tFacetKind')); }
  if (k === 'card') return addField('card', ref);
  if (k === 'search'){ if (searchable(ref)) return addField('search', ref); return toast(c('tSearchKind')); }
  if (k === 'tiles' && kd !== 'media') return addField('tile', ref);
});
root.addEventListener('dragend', () => { dragF = null; root.querySelectorAll('.zone.drag').forEach(x => x.classList.remove('drag')); });

function setMode(m){ S.mode = m; $('#ldx-m-design').setAttribute('aria-pressed', m === 'design'); $('#ldx-m-preview').setAttribute('aria-pressed', m === 'preview'); $('#ldx-main').classList.toggle('preview-mode', m === 'preview'); renderAll(); }
function undo(){
  if (!HIST.length) return;
  const h = JSON.parse(HIST.pop()); D = h.D; label = h.label; useVariant(h.vi); dirty = true;
  if (['part','facet','tile','image','pill','gauge','action'].indexOf(S.pick.k) >= 0) S.pick = {k:'card'};
  commit(c('tUndo'));
}
let resetArmed = false, resetT = 0;
function armReset(b){
  if (!resetArmed){ resetArmed = true; b.textContent = c('resetSure'); clearTimeout(resetT); resetT = setTimeout(() => { resetArmed = false; b.textContent = c('reset'); }, 3000); return; }
  resetArmed = false; b.textContent = c('reset'); snap(); D.variants[vi].layout = emptyLayout(); useVariant(vi); S.q = ''; S.pick = {k:'card'}; commit(c('tEmpty'));
}
function selectCode(){ const r = document.createRange(); r.selectNodeContents($('#ldx-code')); const s = window.getSelection(); s.removeAllRanges(); s.addRange(r); $('#ldx-copy').textContent = c('copied'); }

/* ======================= saving through EBX's form ======================= */
/* the JSON written: computed values of the palette that nothing uses any more are dropped, empty options too */
function output(){
  const d = clone(D);
  Object.keys(d.derived).forEach(n => {
    if (!SUGG[n]) return;
    const others = Object.keys(d.derived).filter(x => x !== n).map(x => JSON.stringify(d.derived[x])).join('');
    const s = JSON.stringify(d.variants) + others;
    if (s.indexOf('"derived":"' + n + '"') < 0 && s.indexOf('"derived:' + n + '"') < 0) delete d.derived[n];
  });
  const clean = o => { if (Array.isArray(o)) o.forEach(clean); else if (o && typeof o === 'object') Object.keys(o).forEach(k => { const v = o[k]; if ((k === 'options' || k === 'label' || k === 'prefix' || k === 'suffix') && v && typeof v === 'object' && !Array.isArray(v) && !Object.keys(v).length) delete o[k]; else clean(v); }); };
  clean(d.variants);
  return d;
}
let askFn = null;
function ask(title, text, okLabel, fn, danger, extra){
  askFn = fn;
  $('#ldx-ask-t').textContent = title;
  $('#ldx-ask-txt').innerHTML = esc(text) + (extra || '');
  const ok = $('#ldx-ask-ok'); ok.textContent = okLabel; ok.className = 'btn ' + (danger ? 'danger' : 'primary');
  $('#ldx-ask').showModal();
  const inp = $('#ldx-ask-in'); if (inp) inp.focus();
}
/* an action that reloads the page: unsaved changes are asked about first */
function guarded(fn){ if (!dirty) return fn(); ask(c('switchTitle'), c('lose'), c('goOn'), fn, true); }
function askNew(){
  const extra = `<div class="fld" style="margin-top:10px"><label for="ldx-ask-in">${esc(c('newName'))}</label><input type="text" id="ldx-ask-in" value=""></div>` + (dirty ? `<p class="hint" style="margin-top:8px">${esc(c('lose'))}</p>` : '');
  ask(c('newTitle', TABLE), '', c('create'), () => { const v = ($('#ldx-ask-in') || {}).value; post('new', '', (v || '').trim() || TABLE); }, false, extra);
}
function post(action, id, lab){
  const set = (k, v) => { const e = document.getElementById(k); if (e) e.value = v == null ? '' : v; };
  const cur = CUR && CUR.id != null ? String(CUR.id) : '';
  set('ldxAction', action);
  set('ldxId', id != null ? id : action === 'new' ? '' : cur);
  set('ldxLabel', lab != null ? lab : label);
  set('ldxLayout', action === 'save' ? JSON.stringify(output()) : '');
  const holder = document.getElementById('ldxSubmit');
  const btn = holder && (/^(BUTTON|INPUT|A)$/.test(holder.tagName) ? holder : holder.querySelector('button,input[type=submit],a'));
  if (btn){ dirty = false; btn.click(); } else toast('ldxSubmit?');
}

/* ======================= page frame ======================= */
const explOpts = EXPL.map(x => `<option value="${esc(x.id)}"${CUR && String(CUR.id) === String(x.id) ? ' selected' : ''}>${esc(x.label)}${x.active === false ? ' · ' + esc(c('inactive')) : ''}</option>`).join('');
root.innerHTML = `<div class="wrap" id="ldx-main">
  ${B.notice ? `<div class="notice${B.noticeError ? ' err' : ''}" role="status">${esc(B.notice)}</div>` : ''}
  ${OUTDATED ? `<div class="notice err" role="alert">${esc(c('outdated'))}</div>` : ''}
  <header class="top">
    <div class="brand"><b>Layout Designer · ${esc(c('title'))} · ${esc(TABLE)}</b><span>${esc(M.dataspace || '')} › ${esc(M.dataset || '')} › ${esc(M.table || '')}</span></div>
    <div class="pick"><span class="lb">${esc(c('sw'))}</span><div class="seg" role="group" aria-label="${esc(c('sw'))}"><button type="button" id="ldx-to-form" aria-pressed="false"${B.formDesignerUrl ? '' : ' disabled'}>${esc(c('swForm'))}</button><button type="button" aria-pressed="true">${esc(c('swExpl'))}</button></div></div>
    <div class="pick"><label for="ldx-expl">${esc(c('explorer'))}</label><select id="ldx-expl">${CUR && CUR.id != null ? '' : `<option value="" selected>${esc(label)} ${esc(c('noExplorer'))}</option>`}${explOpts}<option value="__new">${esc(c('newExplorer'))}</option></select></div>
    <div class="pick"${NOEXPL ? ' hidden' : ''}><label for="ldx-var">${esc(c('variant'))}</label><div class="acts"><select id="ldx-var"></select><button type="button" class="btn small ghost" id="ldx-varset">${esc(c('set'))}</button></div></div>
    <div class="pick"><label for="ldx-lang">${esc(c('language'))}</label><select id="ldx-lang">${LOCALES.map(l => `<option value="${esc(l)}"${l === lang ? ' selected' : ''}>${esc(l)}${l === DEF ? ' ★' : ''}</option>`).join('')}</select></div>
    <div class="pick"${NOEXPL ? ' hidden' : ''}><label for="ldx-as">${esc(c('asLabel'))}</label><select id="ldx-as"><option value="">${esc(c('asMe'))}</option>${ROLES.map(r => `<option value="${esc(r.k)}">${esc(c('asRole', T(r.l) || r.k))}</option>`).join('')}</select></div>
    <div class="pick"${NOEXPL ? ' hidden' : ''}><span class="lb">${esc(c('mode'))}</span><div class="seg" role="group" aria-label="${esc(c('mode'))}"><button type="button" id="ldx-m-design" aria-pressed="true">${esc(c('design'))}</button><button type="button" id="ldx-m-preview" aria-pressed="false">${esc(c('preview'))}</button></div></div>
    <div class="acts">${B.helpUrl ? `<a class="btn ghost" href="${esc(B.helpUrl)}" target="_blank" rel="noopener">${esc(c('help'))}</a>` : ''}${B.openUrl && CUR && CUR.id != null ? `<a class="btn ghost" href="${esc(B.openUrl)}" target="_blank" rel="noopener" title="${esc(c('openTip'))}">${esc(c('open'))} ↗</a>` : ''}<span class="acts"${NOEXPL ? ' hidden' : ''}><button type="button" class="btn ghost" id="ldx-undo" disabled>${esc(c('undo'))}</button><button type="button" class="btn ghost" id="ldx-codebtn">${esc(c('code'))}</button><span class="status" id="ldx-status"></span><button type="button" class="btn primary" id="ldx-save"${OUTDATED || NOEXPL ? ' disabled' : ''}>${esc(c('save'))}</button></span></div>
  </header>
  ${NOEXPL ? `<section class="noexpl"><h2>${esc(c('noneYet', TABLE))}</h2><p class="hint">${esc(c('noneYetD'))}</p><div><button type="button" class="btn primary" id="ldx-create"${OUTDATED ? ' disabled' : ''}>${esc(c('createExpl'))}</button></div></section>` : ''}
  <nav class="steps"${NOEXPL ? ' hidden' : ''} aria-label="${esc(c('start'))}"><span class="lb">${esc(c('start'))}</span><span id="ldx-steplist" style="display:contents"></span><span class="spacer"><button type="button" class="btn small ghost" id="ldx-suggest">${esc(c('suggest'))}</button><button type="button" class="btn small ghost danger" id="ldx-reset">${esc(c('reset'))}</button></span></nav>
  <div class="bench"${NOEXPL ? ' hidden' : ''}>
    <aside class="side palette" aria-label="${esc(c('palette'))}"><div class="side-hd"><h2>${esc(c('palette'))}</h2><div class="ptabs" role="tablist"><button type="button" role="tab" data-pt="fields">${esc(c('fields'))}</button><button type="button" role="tab" data-pt="derived">${esc(c('derived'))}</button><button type="button" role="tab" data-pt="parts">${esc(c('elements'))}</button></div><input type="search" class="search" id="ldx-psearch" placeholder="${esc(c('psearch'))}" aria-label="${esc(c('psearch'))}"></div><div class="side-bd" id="ldx-palette"></div></aside>
    <section class="canvas-wrap"><div class="cbar"><span id="ldx-cbar-hint"></span><span id="ldx-selname"></span></div><div class="canvas design" id="ldx-canvas"></div></section>
    <aside class="side inspector" aria-label="${esc(c('insp'))}"><div class="side-hd"><h2>${esc(c('insp'))}</h2></div><div class="side-bd" id="ldx-insp"></div></aside>
  </div>
  <dialog class="ldx-dlg" id="ldx-dlg" aria-labelledby="ldx-dlg-t"><div class="dhd"><h2 id="ldx-dlg-t">${esc(c('codeTitle'))}</h2><span class="hint">${esc(c('codeHint'))}</span><button type="button" class="btn small ghost" id="ldx-dlg-close">${esc(c('close'))}</button></div><div class="dbd"><pre class="code" id="ldx-code"></pre><div><button type="button" class="btn small ghost" id="ldx-copy">${esc(c('copy'))}</button></div></div></dialog>
  <dialog class="ldx-dlg small" id="ldx-ask" aria-labelledby="ldx-ask-t"><div class="dhd"><h2 id="ldx-ask-t"></h2></div><div class="dbd"><div id="ldx-ask-txt"></div><div class="acts" style="justify-content:flex-end"><button type="button" class="btn ghost" id="ldx-ask-cancel">${esc(c('cancel'))}</button><button type="button" class="btn primary" id="ldx-ask-ok"></button></div></div></dialog>
</div>`;
root.querySelector('#ldx-ask').addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.id === 'ldx-ask-in'){ e.preventDefault(); $('#ldx-ask-ok').click(); } });
useDerivedOf(D.variants);
renderAll();
})();
