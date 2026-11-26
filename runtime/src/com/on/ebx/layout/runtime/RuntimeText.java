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
