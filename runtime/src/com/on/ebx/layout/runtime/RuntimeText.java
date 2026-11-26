package com.on.ebx.layout.runtime;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The few words the rendered form writes itself (not configured in a layout): English, French and Italian, English
 * for any other language. Configured texts never come from here.
 */
public final class RuntimeText
{
	private static final Map<String, String[]> TEXTS = new HashMap<String, String[]>();

	static
	{
		RuntimeText.put("hoursClosed", "Closed", "Ferm\u00e9", "Chiuso");
		RuntimeText.put("hoursOpenNow", "Open now", "Ouvert actuellement", "Aperto ora");
		RuntimeText.put("hoursClosedNow", "Closed now", "Ferm\u00e9 actuellement", "Chiuso ora");
		RuntimeText.put("hoursUntil", "until {n}", "jusqu'\u00e0 {n}", "fino alle {n}");
		RuntimeText.put("hoursExceptionToday", "exceptional hours today", "horaires exceptionnels aujourd'hui", "orario eccezionale oggi");
		RuntimeText.put("hoursLocalTime", "local time {n}", "heure locale {n}", "ora locale {n}");
		RuntimeText.put("hoursToday", "today", "auj.", "oggi");
		RuntimeText.put("hoursUpcoming", "Upcoming exceptional hours", "Prochains horaires exceptionnels", "Prossimi orari eccezionali");
		RuntimeText.put("hoursNone", "None planned.", "Aucun de pr\u00e9vu.", "Nessuno previsto.");
		RuntimeText.put("hoursMore", "+ {n} more", "+ {n} autres", "+ altri {n}");
		RuntimeText.put("hoursCreate", "The hours are entered once the record is created.", "Les horaires se saisissent une fois l'enregistrement cr\u00e9\u00e9.", "Gli orari si inseriscono dopo la creazione del record.");
		RuntimeText.put("hoursSetup", "Choose the association of the regular hours and its fields in the designer.", "Choisissez dans le designer l'association des horaires de la semaine et ses champs.", "Scegli nel designer l'associazione degli orari settimanali e i suoi campi.");
		// roles and personal arrangement (1.10.0)
		RuntimeText.put("myEssentials", "My essentials", "Mes essentiels", "I miei essenziali");
		RuntimeText.put("arrangeMyForm", "Arrange my form", "Personnaliser ma fiche", "Personalizza la mia scheda");
		RuntimeText.put("pinnedNotHere", "Your pinned blocks do not apply to this record (they show only under a condition it does not meet).",
			"Vos blocs épinglés ne concernent pas cette fiche (ils ne s'affichent que sous une condition qu'elle ne remplit pas).",
			"I tuoi blocchi fissati non riguardano questo record (compaiono solo a una condizione che non soddisfa).");
		RuntimeText.put("lazyLoading", "Loaded when this tab is opened.", "Chargé à l'ouverture de l'onglet.", "Caricato all'apertura della scheda.");
		RuntimeText.put("foldedBlock", "Folded block", "Bloc replié", "Blocco chiuso");
		RuntimeText.put("nothingForRole", "Nothing on this form is shown for your role.", "Rien sur cette fiche n'est affiché pour votre rôle.", "Niente di questa scheda è visibile per il tuo ruolo.");
		// blocks and widgets of 1.4.0
		RuntimeText.put("afterCreate", "Shown once the record is created.", "Affiché une fois l'enregistrement créé.", "Visibile dopo la creazione del record.");
		RuntimeText.put("setupBlock", "Choose this block's settings in the designer.", "Choisissez les réglages de ce bloc dans le designer.", "Scegli le impostazioni di questo blocco nel designer.");
		RuntimeText.put("historyOff", "History is not activated on this table.", "L'historique n'est pas activé sur cette table.", "La cronologia non è attivata su questa tabella.");
		RuntimeText.put("recordInfoTitle", "Record information", "Informations de l'enregistrement", "Informazioni sul record");
		RuntimeText.put("riCreatedBy", "Created by", "Cr\u00e9\u00e9 par", "Creato da");
		RuntimeText.put("riCreatedOn", "Created on", "Cr\u00e9\u00e9 le", "Creato il");
		RuntimeText.put("riModifiedBy", "Last modified by", "Modifi\u00e9 par", "Modificato da");
		RuntimeText.put("riModifiedOn", "Last modified on", "Modifi\u00e9 le", "Modificato il");
		RuntimeText.put("riAgo", "{n} days ago", "il y a {n} jours", "{n} giorni fa");
		RuntimeText.put("riYesterday", "yesterday", "hier", "ieri");
		RuntimeText.put("riNew", "Filled in when the record is saved.", "Renseign\u00e9 \u00e0 l'enregistrement.", "Compilato al salvataggio del record.");
		RuntimeText.put("changesTitle", "Changes in this request", "Changements de la demande", "Modifiche della richiesta");
		RuntimeText.put("chOutside", "Nothing to compare: this dataset has no copy in a parent dataspace.", "Rien \u00e0 comparer : ce jeu de donn\u00e9es n'a pas de copie dans un espace de donn\u00e9es parent.", "Niente da confrontare: questo dataset non ha copia in uno spazio dati padre.");
		RuntimeText.put("chCreated", "Created in this request", "Cr\u00e9\u00e9 dans cette demande", "Creato in questa richiesta");
		RuntimeText.put("chNone", "No change compared with {n}.", "Aucun changement par rapport \u00e0 {n}.", "Nessuna modifica rispetto a {n}.");
		RuntimeText.put("chCount", "{n} field(s) changed", "{n} champ(s) modifi\u00e9(s)", "{n} campo/i modificato/i");
		RuntimeText.put("chMore", "+ {n} more", "+ {n} autres", "+ altri {n}");
		RuntimeText.put("chEmpty", "(empty)", "(vide)", "(vuoto)");
		RuntimeText.put("usedByTitle", "Used by", "Utilis\u00e9 par", "Usato da");
		RuntimeText.put("ubNone", "No other record refers to this one.", "Aucun autre enregistrement ne fait r\u00e9f\u00e9rence \u00e0 celui-ci.", "Nessun altro record fa riferimento a questo.");
		RuntimeText.put("ubVia", "through {n}", "par {n}", "tramite {n}");
		RuntimeText.put("ubOpen", "Show", "Voir", "Mostra");
		RuntimeText.put("addressNotFound", "Address not found on the map.", "Adresse introuvable sur la carte.", "Indirizzo non trovato sulla mappa.");
		RuntimeText.put("addressPending", "Position being looked up: the map shows at the next opening.", "Position en cours de recherche : la carte s\u2019affiche \u00e0 la prochaine ouverture.", "Posizione in corso di ricerca: la mappa appare alla prossima apertura.");
		RuntimeText.put("fromAddress", "position from the address {n}", "position d'apr\u00e8s l'adresse {n}", "posizione dall'indirizzo {n}");
		RuntimeText.put("histFields", "{n} fields", "{n} champs", "{n} campi");
		RuntimeText.put("orgTitle", "Group structure", "Structure du groupe", "Struttura del gruppo");
		RuntimeText.put("ocSameTable", "The parent key must point to this same table.", "La cl\u00e9 vers le parent doit pointer vers cette m\u00eame table.", "La chiave verso il padre deve puntare a questa stessa tabella.");
		RuntimeText.put("oc.org", "Org chart", "Organigramme", "Organigramma");
		RuntimeText.put("oc.tree", "Tree", "Arbre", "Albero");
		RuntimeText.put("oc.rings", "Rings", "Anneaux", "Anelli");
		RuntimeText.put("oc.open", "Open the record", "Ouvrir la fiche", "Apri la scheda");
		RuntimeText.put("oc.oneOther", "+ 1 other entity", "+ 1 autre entit\u00e9", "+ 1 altra entit\u00e0");
		RuntimeText.put("oc.others", "+ {n} other entities", "+ {n} autres entit\u00e9s", "+ altre {n} entit\u00e0");
		RuntimeText.put("oc.oneChild", "1 direct subsidiary", "1 filiale directe", "1 controllata diretta");
		RuntimeText.put("oc.children", "{n} direct subsidiaries", "{n} filiales directes", "{n} controllate dirette");
		RuntimeText.put("oc.below", "{n} entities below", "{n} entit\u00e9s dessous", "{n} entit\u00e0 sotto");
		RuntimeText.put("oc.level", "level {n} of {m}", "niveau {n} sur {m}", "livello {n} di {m}");
		RuntimeText.put("oc.search", "Search an entity of the group", "Chercher une entit\u00e9 du groupe", "Cerca un'entit\u00e0 del gruppo");
		RuntimeText.put("oc.expandAll", "Expand all", "Tout d\u00e9plier", "Espandi tutto");
		RuntimeText.put("oc.collapseAll", "Collapse", "Replier", "Comprimi");
		RuntimeText.put("oc.expand", "Expand", "D\u00e9plier", "Espandi");
		RuntimeText.put("oc.collapse", "Collapse", "Replier", "Comprimi");
		RuntimeText.put("oc.noMatch", "No entity matches.", "Aucune entit\u00e9 ne correspond.", "Nessuna entit\u00e0 corrisponde.");
		RuntimeText.put("oc.ringsAria", "The group as rings, one ring per level", "Le groupe en anneaux, un anneau par niveau", "Il gruppo ad anelli, un anello per livello");
		RuntimeText.put("oc.ringsNote", "One ring per level; a slice's size follows the number of entities below it. The record's path is solid.", "Un anneau par niveau ; la taille d'une part suit le nombre d'entit\u00e9s dessous. Le chemin de la fiche est en plein.", "Un anello per livello; la dimensione di una fetta segue il numero di entit\u00e0 sotto. Il percorso della scheda \u00e8 pieno.");
		RuntimeText.put("oc.switched", "{n} direct subsidiaries: beyond {m}, the block shows the tree.", "{n} filiales directes : au-del\u00e0 de {m}, le bloc passe en arbre.", "{n} controllate dirette: oltre {m}, il blocco mostra l'albero.");
		RuntimeText.put("oc.cut", "Large group: only the first {n} entities are shown.", "Groupe important : seules les {n} premi\u00e8res entit\u00e9s sont montr\u00e9es.", "Gruppo grande: sono mostrate solo le prime {n} entit\u00e0.");
		RuntimeText.put("vhTitle", "Over time", "\u00c9volution", "Andamento");
		RuntimeText.put("vh.since", "since {n}", "depuis le {n}", "dal {n}");
		RuntimeText.put("vh.changeSince", "since {n}", "depuis le {n}", "dal {n}");
		RuntimeText.put("vh.unchanged", "Unchanged", "Inchang\u00e9", "Invariato");
		RuntimeText.put("vh.ofVersions", "{n} values over {m} versions of the record", "{n} valeurs sur {m} versions de la fiche", "{n} valori su {m} versioni della scheda");
		RuntimeText.put("vh.first", "First value", "Premi\u00e8re valeur", "Primo valore");
		RuntimeText.put("vh.noValue", "No value in the history.", "Aucune valeur dans l'historique.", "Nessun valore nella cronologia.");
		RuntimeText.put("vh.noChange", "The value has not changed since it was first entered, on {n}. The curve appears with the first change.", "La valeur n'a pas chang\u00e9 depuis sa premi\u00e8re saisie, le {n}. La courbe appara\u00eet au premier changement.", "Il valore non \u00e8 cambiato dalla prima immissione, il {n}. La curva appare al primo cambiamento.");
		RuntimeText.put("vh.system", "automatic process", "traitement automatique", "processo automatico");
		RuntimeText.put("vh.values", "values", "valeurs", "valori");
		RuntimeText.put("smTitle", "Sites", "Sites", "Sedi");
		RuntimeText.put("sm.hq", "Head office", "Si\u00e8ge", "Sede");
		RuntimeText.put("sm.approx", "Town position", "Position de la ville", "Posizione della citt\u00e0");
		RuntimeText.put("sm.pending", "Position at the next display", "Position \u00e0 l'affichage suivant", "Posizione alla prossima apertura");
		RuntimeText.put("sm.notFound", "Address not found", "Adresse introuvable", "Indirizzo non trovato");
		RuntimeText.put("sm.sites", "{n} sites, {m} on the map", "{n} sites, {m} sur la carte", "{n} sedi, {m} sulla mappa");
		RuntimeText.put("sm.openMap", "Open the map", "Ouvrir la carte", "Apri la mappa");
		RuntimeText.put("sm.zoomIn", "Zoom in", "Zoomer", "Ingrandisci");
		RuntimeText.put("sm.zoomOut", "Zoom out", "D\u00e9zoomer", "Riduci");
		RuntimeText.put("sm.open", "Open", "Ouvrir", "Apri");
		RuntimeText.put("sm.inactive", "Inactive", "Inactif", "Inattivo");
		RuntimeText.put("workflowTasksTitle", "Workflows in progress", "Workflows en cours", "Workflow in corso");
		RuntimeText.put("sourceValuesTitle", "Values by source", "Valeurs par source", "Valori per fonte");
		RuntimeText.put("translationsTitle", "Translations", "Traductions", "Traduzioni");
		RuntimeText.put("inheritanceTitle", "Inheritance", "H\u00e9ritage", "Ereditariet\u00e0");
		RuntimeText.put("sv.merged", "This record was merged into a golden record:", "Cette fiche a \u00e9t\u00e9 fusionn\u00e9e dans une fiche de r\u00e9f\u00e9rence :", "Questa scheda \u00e8 stata fusa in un record di riferimento:");
		RuntimeText.put("sv.none", "No source merged into this record.", "Aucune source fusionn\u00e9e dans cette fiche.", "Nessuna fonte fusa in questa scheda.");
		RuntimeText.put("sv.noAddon", "The Match and Merge add-on is not installed.", "L'add-on Match and Merge n'est pas install\u00e9.", "L'add-on Match and Merge non \u00e8 installato.");
		RuntimeText.put("sv.count", "{n} merged source(s); the value kept for the golden record is highlighted.", "{n} source(s) fusionn\u00e9e(s) ; la valeur retenue par la fiche de r\u00e9f\u00e9rence est mise en \u00e9vidence.", "{n} fonte/i fusa/e; il valore mantenuto \u00e8 evidenziato.");
		RuntimeText.put("sv.golden", "Golden record", "Fiche de r\u00e9f\u00e9rence", "Record di riferimento");
		RuntimeText.put("sv.kept", "Value kept", "Valeur retenue", "Valore mantenuto");
		RuntimeText.put("sv.allAgree", "Every source gives the same values.", "Toutes les sources donnent les m\u00eames valeurs.", "Tutte le fonti danno gli stessi valori.");
		RuntimeText.put("wf.none", "No workflow in progress on this record.", "Aucun workflow en cours sur cette fiche.", "Nessun workflow in corso su questa scheda.");
		RuntimeText.put("wf.started", "started on {n}", "lanc\u00e9 le {n}", "avviato il {n}");
		RuntimeText.put("wf.error", "In error", "En erreur", "In errore");
		RuntimeText.put("wf.noTask", "No task waiting (automatic step).", "Aucune t\u00e2che en attente (\u00e9tape automatique).", "Nessuna attivit\u00e0 in attesa (passo automatico).");
		RuntimeText.put("wf.allocated", "with {n}", "chez {n}", "presso {n}");
		RuntimeText.put("wf.offered", "offered to {n}", "propos\u00e9e \u00e0 {n}", "proposta a {n}");
		RuntimeText.put("wf.since", "since {n}", "depuis le {n}", "dal {n}");
		RuntimeText.put("wf.late", "deadline passed", "\u00e9ch\u00e9ance d\u00e9pass\u00e9e", "scadenza superata");
		RuntimeText.put("wf.monitor", "See in monitoring", "Voir dans le suivi", "Vedi nel monitoraggio");
		RuntimeText.put("wf.open", "Open the task", "Ouvrir la t\u00e2che", "Apri l'attivit\u00e0");
		RuntimeText.put("tr.complete", "Complete", "Complet", "Completo");
		RuntimeText.put("tr.missing", "Missing:", "Manque :", "Mancante:");
		RuntimeText.put("in.root", "This dataset does not inherit from another one.", "Ce jeu de donn\u00e9es n'h\u00e9rite d'aucun autre.", "Questo dataset non eredita da un altro.");
		RuntimeText.put("in.local", "Created here", "Cr\u00e9\u00e9e ici", "Creato qui");
		RuntimeText.put("in.notInParent", "{n} does not have this record.", "{n} n'a pas cette fiche.", "{n} non ha questa scheda.");
		RuntimeText.put("in.allInherited", "Everything inherited", "Tout est h\u00e9rit\u00e9", "Tutto ereditato");
		RuntimeText.put("in.changed", "{n} field(s) changed here", "{n} champ(s) modifi\u00e9(s) ici", "{n} campo/i modificato/i qui");
		RuntimeText.put("in.from", "from {n}", "de {n}", "da {n}");
		RuntimeText.put("in.inheritedList", "{n} field(s) follow the parent:", "{n} champ(s) suivent le parent :", "{n} campo/i seguono il padre:");
		RuntimeText.put("historyTitle", "Change history", "Historique des modifications", "Cronologia delle modifiche");
		RuntimeText.put("historyNone", "No change recorded yet.", "Aucune modification enregistrée pour l'instant.", "Nessuna modifica registrata.");
		RuntimeText.put("histCreated", "Created", "Création", "Creazione");
		RuntimeText.put("histChanged", "Changed", "Modification", "Modifica");
		RuntimeText.put("histDeleted", "Deleted", "Suppression", "Eliminazione");
		RuntimeText.put("filledOf", "{n} filled", "{n} renseignés", "{n} compilati");
		RuntimeText.put("missing", "Missing", "Manquants", "Mancanti");
		RuntimeText.put("other", "Other", "Autres", "Altri");
		RuntimeText.put("fn_count", "Rows", "Lignes", "Righe");
		RuntimeText.put("fn_sum", "Total", "Total", "Totale");
		RuntimeText.put("fn_avg", "Average", "Moyenne", "Media");
		RuntimeText.put("fn_min", "Minimum", "Minimum", "Minimo");
		RuntimeText.put("fn_max", "Maximum", "Maximum", "Massimo");
		RuntimeText.put("noLinked", "No linked record.", "Aucun enregistrement lié.", "Nessun record collegato.");
		RuntimeText.put("openRecord", "Open the record", "Ouvrir la fiche", "Apri la scheda");
		RuntimeText.put("mainContact", "Main", "Principal", "Principale");
		RuntimeText.put("noImage", "No image.", "Aucune image.", "Nessuna immagine.");
		RuntimeText.put("openFile", "Open", "Ouvrir", "Apri");
		RuntimeText.put("expiredOn", "expired {n}", "expiré le {n}", "scaduto il {n}");
		RuntimeText.put("noLink", "No link: a value it needs is empty.", "Aucun lien : une valeur nécessaire est vide.", "Nessun link: manca un valore necessario.");
		RuntimeText.put("noDates", "No dates yet.", "Pas encore de dates.", "Nessuna data.");
		RuntimeText.put("valUpcoming", "Upcoming", "À venir", "In arrivo");
		RuntimeText.put("valActive", "In force", "En vigueur", "In vigore");
		RuntimeText.put("valEnding", "Ending soon", "Bientôt échu", "In scadenza");
		RuntimeText.put("valExpired", "Expired", "Expiré", "Scaduto");
		RuntimeText.put("noEnd", "no end date", "sans date de fin", "senza data di fine");
		RuntimeText.put("inDays", "starts in {n} days", "commence dans {n} jours", "inizia tra {n} giorni");
		RuntimeText.put("daysAgo", "ended {n} days ago", "terminé depuis {n} jours", "terminato da {n} giorni");
		RuntimeText.put("daysLeft", "{n} days left", "{n} jours restants", "{n} giorni rimanenti");
		RuntimeText.put("noAddress", "No address yet.", "Pas encore d'adresse.", "Nessun indirizzo.");
		RuntimeText.put("topLevel", "Top level", "Premier niveau", "Primo livello");
		RuntimeText.put("copy", "Copy", "Copier", "Copia");
		RuntimeText.put("show", "Show", "Afficher", "Mostra");
		RuntimeText.put("today", "today", "aujourd'hui", "oggi");
		RuntimeText.put("day1", "1 day", "1 jour", "1 giorno");
		RuntimeText.put("dayN", "{n} days", "{n} jours", "{n} giorni");
		RuntimeText.put("monthN", "{n} months", "{n} mois", "{n} mesi");
		RuntimeText.put("yearN", "{n} years", "{n} ans", "{n} anni");
		RuntimeText.put("inX", "in {n}", "dans {n}", "tra {n}");
		RuntimeText.put("xAgo", "{n} ago", "il y a {n}", "{n} fa");
		RuntimeText.put("durY", "{n} y", "{n} an(s)", "{n} a");
		RuntimeText.put("durMo", "{n} mo", "{n} mois", "{n} m");
		RuntimeText.put("durD", "{n} d", "{n} j", "{n} g");
		RuntimeText.put("durH", "{n} h", "{n} h", "{n} h");
		RuntimeText.put("durMin", "{n} min", "{n} min", "{n} min");
		RuntimeText.put("checkOk", "check digit OK", "clé de contrôle OK", "cifra di controllo OK");
		RuntimeText.put("checkBad", "wrong check digit", "clé de contrôle fausse", "cifra di controllo errata");
		RuntimeText.put("noIssue", "The validation report shows nothing.", "Le rapport de validation ne signale rien.", "Il rapporto di convalida non segnala nulla.");
		RuntimeText.put("error", "Error", "Erreur", "Errore");
		RuntimeText.put("warning", "Warning", "Avertissement", "Avviso");
		RuntimeText.put("info", "Information", "Information", "Informazione");
		RuntimeText.put("error1", "error", "erreur", "errore");
		RuntimeText.put("warning1", "warning", "avertissement", "avviso");
		RuntimeText.put("info1", "information", "information", "informazione");
		RuntimeText.put("errorN", "errors", "erreurs", "errori");
		RuntimeText.put("warningN", "warnings", "avertissements", "avvisi");
		RuntimeText.put("infoN", "information", "informations", "informazioni");
		RuntimeText.put("goto", "Go to field", "Aller au champ", "Vai al campo");
		RuntimeText.put("gotoRow", "Open the rows", "Voir les lignes", "Apri le righe");
		RuntimeText.put("absent", "Field not on this form", "Champ absent de cette fiche", "Campo assente da questa scheda");
		RuntimeText.put("offForm", "Not on this form", "Hors de la fiche", "Fuori dalla scheda");
		RuntimeText.put("more", "{n} more", "{n} autres", "altri {n}");
		RuntimeText.put("noRow", "No rows.", "Aucune ligne.", "Nessuna riga.");
		RuntimeText.put("moreAtLeast", "Only the first {n} are shown: open the related table to see them all.",
			"Seuls les {n} premiers sont affichés : ouvrez la table liée pour les voir tous.",
			"Sono mostrati solo i primi {n}: aprire la tabella collegata per vederli tutti.");
		RuntimeText.put("manageRows", "Manage the rows", "Gérer les lignes", "Gestisci le righe");
		RuntimeText.put("noParent", "No parent · head of group", "Aucune maison mère · tête de groupe", "Nessuna controllante · capogruppo");
		RuntimeText.put("noCoords", "No coordinates yet", "Pas encore de coordonnées", "Nessuna coordinata");
		RuntimeText.put("openMap", "Open the map", "Ouvrir la carte", "Apri la mappa");
		RuntimeText.put("mameAbsent", "Match and Merge is not deployed on this instance.", "Match and Merge n'est pas déployé sur cette instance.", "Match and Merge non è installato su questa istanza.");
		RuntimeText.put("mameNone", "No Match and Merge state for this record.", "Pas d'état Match and Merge pour cet enregistrement.", "Nessuno stato Match and Merge per questo record.");
		RuntimeText.put("group", "Group", "Groupe", "Gruppo");
		RuntimeText.put("componentMissing", "Component \"{n}\" is not registered on this instance.", "Le composant « {n} » n'est pas enregistré sur cette instance.", "Il componente \"{n}\" non è registrato su questa istanza.");
		RuntimeText.put("completeness", "completeness", "complétude", "completezza");
		RuntimeText.put("newRecord", "New record", "Nouvel enregistrement", "Nuovo record");
		RuntimeText.put("layoutError", "The layout of this form could not be read; the standard form is shown.", "La mise en page de cette fiche n'a pas pu être lue ; la fiche standard s'affiche.", "Non è stato possibile leggere il layout di questa scheda; viene mostrata la scheda standard.");
	}

	private RuntimeText()
	{
	}

	private static void put(final String pKey, final String pEn, final String pFr, final String pIt)
	{
		RuntimeText.TEXTS.put(pKey, new String[] { pEn, pFr, pIt });
	}

	public static String get(final String pKey, final Locale pLocale)
	{
		final String[] t = RuntimeText.TEXTS.get(pKey);
		if (t == null)
		{
			return pKey;
		}
		final String lang = pLocale == null ? "en" : pLocale.getLanguage();
		return "fr".equals(lang) ? t[1] : "it".equals(lang) ? t[2] : t[0];
	}

	public static String get(final String pKey, final Locale pLocale, final Object pN)
	{
		return RuntimeText.get(pKey, pLocale).replace("{n}", String.valueOf(pN));
	}
}
