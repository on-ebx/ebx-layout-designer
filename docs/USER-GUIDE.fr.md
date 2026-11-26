# Layout Designer : guide de l'utilisateur

Le Layout Designer met en page la fiche d'une table EBX par glisser-déposer, sans code ni compilation. Il est réservé
aux administrateurs. Les utilisateurs métier n'en voient que le résultat : la fiche de la table.

## 1. Commencer une mise en page

**Depuis la liste des mises en page** : la page *Mises en page de fiches* (dataset `LayoutDesigner`, table *Mises en
page de fiches*, **Actions › Record form layouts**, ou son entrée dans une perspective) liste les mises en page
enregistrées : table, modèle, version, état, dernière modification. *Ouvrir le designer* en ouvre une sur un dataset de
son modèle.

Pour en commencer une nouvelle, choisissez l'**espace de données** et le **dataset** avec les sélecteurs d'EBX (arbre
et recherche), puis la **table** (la liste suit le dataset choisi), puis *Continuer*.

**Depuis la table elle-même** : ouvrez la table, **Actions › Design the record form**.

La première fois, le designer propose une mise en page de départ construite depuis le modèle : un onglet *Général*
avec un panneau par groupe du modèle (deux colonnes), les champs hors groupe dans un premier panneau, et un onglet par
association. C'est un brouillon tant que vous ne l'enregistrez pas.

## 2. L'écran

| Zone | Contenu |
|---|---|
| Barre du haut | *Aperçu avec* (la fiche montrée dans l'aperçu), *Langue* (celle de l'aperçu et des textes saisis), *Traductions*, mode *Concevoir* / *Aperçu*, *Largeur* (tester un écran étroit), *Aide*, *Fermer*, *</> Code*, *Annuler*, *Enregistrer* |
| Palette (gauche) | les **champs** de la table par groupe (recherche par libellé ou chemin ; un champ placé est marqué), ses **associations**, les **blocs**, les **composants enregistrés** |
| Fiche (centre) | la fiche telle qu'elle s'affichera, avec les vraies valeurs de l'enregistrement choisi |
| Inspecteur (droite) | les réglages de ce que vous avez cliqué : onglet, ligne, bloc ou champ, ou l'activation de la fiche. Sous 1 280 px de large, il s'ouvre en panneau latéral à droite tant qu'un élément est sélectionné |

## 3. Construire la fiche

- **Onglets** : *+ Onglet*, glissez un onglet pour le déplacer. L'inspecteur règle son libellé (un par langue) et un
  compteur : la somme des lignes des associations cochées, affichée dans le titre (« Commandes (5) »).
- **Lignes** : *+ Ligne à 1 colonne* ou *à 2 colonnes*, en proportions 1:1, 2:1 ou 1:2. Sous 900 px, une ligne à deux
  colonnes s'affiche sur une seule, colonne de gauche d'abord.
- **Champs** : glissez un champ dans un panneau ; posé sur une colonne vide, il crée un panneau autour de lui. Par
  champ : un libellé propre à cette fiche (vide = le libellé du modèle, traduit par EBX), un widget, la lecture seule,
  le libellé au-dessus sur toute la largeur. Un champ obligatoire absent de la fiche est signalé : la création d'un
  enregistrement échouerait sans lui.
- **Associations** : glissez-en une pour poser sa grille.
- **Blocs** : glissez depuis la palette ; posé entre deux lignes, un bloc ouvre une nouvelle ligne ; posé sur un
  onglet, il part à la fin de celui-ci.
- **Retirer** : sélectionnez un bloc ou un champ et appuyez sur Suppr (la touche « delete » d’un clavier Mac), ou déposez-le
  sur la corbeille en bas à droite de la fenêtre. « Annuler » le remet.
- Chaque modification dit ce qu'elle a fait (« le panneau X a été créé autour de Y ») et *Annuler* la défait.

### Widgets d'un champ

La liste ne propose que les widgets qui conviennent au type du champ, en deux groupes.

- **Saisie** : texte, texte sur une ligne, zone de texte, nombre, date, case à cocher, Oui / Non (radio), liste
  déroulante, liste sans « Créer » (clé étrangère), boutons radio. Le widget d'EBX s'applique si rien n'est choisi.
- **Affichage (lecture seule)** : chiffre mis en avant, pastille, lien, code, et depuis la 1.4.0 pastille colorée par
  valeur, montant avec devise, valeur avec unité, pourcentage en barre, note en étoiles, date relative (« dans 12
  jours »), durée, pays (nom et drapeau), téléphone et e-mail (avec Copier), adresse web, couleur, code-barres EAN /
  GTIN (barres et clé de contrôle), identifiant copiable, puces, valeur masquée (certains rôles peuvent tout afficher),
  lien vers la fiche d'une clé étrangère, vignette, coordonnées GPS, oui / non en icône. Leurs réglages (devise, unité,
  couleurs par valeur…) apparaissent sous la liste. En choisir un coche *Lecture seule* : un widget d'affichage sert
  toujours sur un enregistrement existant depuis la 1.4.4 (le champ passe en lecture seule, même dans une fiche
  enregistrée avant sans la case cochée) ; à la création d'un enregistrement, le champ garde la saisie d'EBX.

### Comportements d'un champ

Sous *Comportements*, dans l'inspecteur du champ :

- **Afficher seulement si (en direct)** : le champ apparaît ou disparaît pendant la saisie d'un autre champ, sans
  enregistrer (`Famille est l'une des valeurs Essuie-glaces`, `Consentement est renseigné`…) ; sa valeur peut être
  gardée ou vidée quand il se cache ;
- **Obligatoire si (astérisque en direct)** : l'astérisque suit une valeur ;
- **Lecture seule si** : pour certaines valeurs d'un champ, et/ou pour certains rôles, sauf pour d'autres (calculé à
  l'ouverture).

Un comportement change l'affichage, pas la validation d'EBX : pour bloquer l'enregistrement, ajoutez une règle au modèle.

- **Charger une grille à l'ouverture de son onglet** (inspecteur d'un bloc grille) : la fiche s'ouvre plus vite quand elle contient plusieurs grilles natives ; la grille est construite, dans un cadre, la première fois que son onglet s'affiche, avec sa barre d'outils et son « + ».

## 4. Les blocs

| Bloc | Réglages |
|---|---|
| **Panneau** | titre, note sous le titre, champs, tout le panneau en lecture seule |
| **Bandeau d'identité** | champ titre, champs de sous-titre, puces, jauge (un champ de 0 à 100), pastille d'état. Affiche « Nouvel enregistrement » en création |
| **Chiffres clés** | une rangée de valeurs courtes, plus le nombre de lignes d'une association |
| **Rapport de validation** | voir section 5 |
| **Association** | *Grille native* (la grille d'EBX : barre d'outils, filtre, pagination, création) ou *Cartes* (champs choisis sur chaque carte, la grille complète sous *Gérer les lignes*) ; titre (par défaut, le libellé de l'association), libellé du bouton d'ajout |
| **Carte** | un champ latitude et un champ longitude, ou (1.7.0) des champs d'adresse placés par la recherche d'OpenStreetMap, appelée par le serveur EBX ; OpenStreetMap dans un cadre de hauteur fixe, option *Grande carte*. Carte calculée depuis l'adresse : chaque adresse inconnue est cherchée dans un service de géocodage (une demande par seconde au plus sur le service public) et gardée dans un cache ; elle apparaît à l'ouverture suivante, ou tout de suite avec le bouton « Localiser maintenant » de la carte. Pour beaucoup d'enregistrements, ajoutez des champs latitude et longitude à la table et choisissez-les dans le bloc |
| **Horaires d'ouverture** | ouvert maintenant (dans le fuseau de la fiche), la semaine en cours une carte par jour, les prochains jours exceptionnels. Choisissez l'association des horaires de la semaine et ses champs jour, ouverture et fermeture (et un champ « fermé » facultatif), éventuellement les codes du champ jour, l'association des horaires exceptionnels et ses champs, le nombre d'exceptions listées et le champ fuseau horaire de la fiche. Détails dans la référence du format |
| **Structure du groupe** | la clé étrangère vers la maison mère et l'association des filiales : maison mère, la fiche, ses filiales |
| **Doublons** | l'état Match and Merge de la fiche (golden, fusionnée, groupe). Sans l'add-on, le bloc le dit |
| **Texte d'aide** | un paragraphe pour l'utilisateur |
| **Composant enregistré** | un bloc Java écrit par un développeur (section 9) |

Deux réglages sur chaque bloc : **Afficher seulement si (XPath)**, un prédicat sur la fiche
(`./Lifecycle/status = 'ACTIVE'`), et **Masquer à la création d'une fiche**.


Depuis la 1.4.0 :

| Bloc | Ce qu'il montre |
|---|---|
| **Frise chronologique** | les lignes d'une association sur une frise, la plus récente en haut, colorées selon un champ |
| **Historique des modifications** | qui a changé quoi et quand, pour les champs choisis (historisation activée sur la table) |
| **Complétude** | la part des champs renseignés (chacun avec un poids), les manquants avec *Aller au champ* |
| **Cycle de vie (étapes)** | les étapes d'un champ statut, l'étape courante mise en avant, les étapes de sortie en rouge |
| **Liste de contrôle** | des points cochés selon une condition : champ renseigné, valeur, ou prédicat XPath |
| **Répartition (graphique)** | les lignes d'une association regroupées par un champ, comptées, sommées ou moyennées, en barres ou en anneau |
| **Totaux d'une association** | somme, moyenne, minimum, maximum ou nombre de lignes, en nombre, montant ou pourcentage |
| **Matrice** | les lignes d'une association croisées sur deux champs (disponibilité par pays et par canal) |
| **Fiche liée** | les champs choisis de l'enregistrement visé par une clé étrangère, avec un lien pour l'ouvrir |
| **Contacts** | les personnes d'une association en cartes, e-mail et téléphone avec Copier, le contact principal en tête |
| **Galerie d'images** | les images de champs média ou d'adresse, de la fiche ou d'une association ; *Cadrage* : vignettes carrées (recadrées) ou images entières (bandeaux, logos) |
| **Documents** | les fichiers d'une association : nom, type, date (en rouge une fois une échéance passée), lien |
| **Liens et actions** | des boutons vers une adresse construite avec les valeurs de la fiche (`https://boutique.exemple.fr/p/${./Sku}`) |
| **Période de validité** | à venir, en vigueur, bientôt échu ou expiré, avec les jours restants |
| **Valeurs par source** (1.9.0) | sur une fiche de référence Match and Merge : la valeur donnée par chaque source fusionnée, la valeur retenue mise en évidence |
| **Workflows en cours** (1.9.0) | les workflows ouverts sur la fiche, qui tient chaque tâche et depuis quand, la tâche ouverte dans une fenêtre |
| **Traductions** (1.9.0) | pour chaque langue : la part traduite et les champs manquants |
| **Héritage** (1.9.0) | dans un jeu de données enfant : ce que la fiche hérite et ce qui est modifié ici |
| **Évolution d'une valeur** (1.9.0) | la courbe d'une valeur dans l'historique de la fiche (en escalier ou en ligne), la valeur actuelle, l'écart, la liste des changements avec leur auteur |
| **Sites sur la carte** (1.9.0) | les lignes d'une association (établissements, adresses) sur une carte OpenStreetMap avec leur liste ; placées par coordonnées ou par adresse. Carte calculée depuis l'adresse : chaque adresse inconnue est cherchée dans un service de géocodage (une demande par seconde au plus sur le service public) et gardée dans un cache ; elle apparaît à l'ouverture suivante, ou tout de suite avec le bouton « Localiser maintenant » de la carte. Pour beaucoup d'enregistrements, ajoutez des champs latitude et longitude à la table et choisissez-les dans le bloc |
| **Hiérarchie graphique** (1.8.0) | le groupe de la fiche en organigramme centré sur elle, en arbre repliable avec recherche, ou en anneaux ; un clic ouvre la fiche d'une entité |
| **Changements de la demande** (1.6.0) | dans l'espace de travail d'un workflow, ce que la fiche change par rapport à la référence, champ par champ (ancienne → nouvelle valeur) |
| **Utilisé par** (1.6.0) | les enregistrements d'autres tables qui pointent vers celui-ci, comptés par table, avec quelques exemples |
| **Informations de l'enregistrement** (1.5.0) | qui a créé la fiche et quand, qui l'a modifiée en dernier et quand, en grille ou sur une ligne ; rien à configurer |
| **Adresse formatée** | les champs d'adresse mis en forme selon le pays, avec un lien vers la carte |
| **Arborescence** | le fil d'Ariane d'une clé parent sur plusieurs niveaux, et les enfants |

Réglages communs à tous les blocs : **Afficher seulement si (en direct)**, une condition sur un autre champ évaluée
pendant la saisie (sur un panneau, les valeurs des champs cachés peuvent être vidées) ; **Afficher seulement si (XPath,
à l'ouverture)**, un prédicat sur la fiche ; **Masquer à la création d'une fiche**.

Un **panneau** peut aussi tirer ses champs d'une **table de configuration** du dataset : *Champs pilotés par une table
de configuration* indique la table, le champ de la fiche qui pilote (la famille) et les colonnes famille, attribut
(chemin ou nom du champ) et, en option, obligatoire. Un champ que la table cite pour une famille ne s'affiche que pour
cette famille, en direct ; un champ qu'elle ne cite jamais reste affiché.

Une **ligne** peut être une **section repliable** (inspecteur de la ligne) : un titre, ouverte ou repliée à l'ouverture.

## 5. Le bloc « Rapport de validation »

Il affiche le rapport de validation de la fiche tel qu'EBX le calcule à partir des contraintes et des règles du
modèle : rien à configurer côté données.

- choisissez les gravités affichées (erreurs, avertissements, informations), le regroupement (par gravité, par
  onglet, aucun) et le nombre de messages avant « autres » ;
- chaque message dit **où** se trouve le champ sur cette fiche (*Vue d'ensemble › Fidélité › Points*) et, avec
  *Aller au champ*, ouvre le bon onglet et met le champ en évidence ;
- **boutons d'action** : un bouton n'apparaît que si un message porte sur le champ, le groupe ou l'association choisis,
  et ouvre l'endroit où l'on corrige (par exemple *Ajouter une adresse* pour une adresse de facturation électronique
  manquante) ;
- *Masquer le bloc quand le rapport est vide*.

Les messages arrivent dans la langue de l'utilisateur. Un message rédigé dans une seule langue (une règle de
validation DMA saisie en anglais) s'affiche dans la langue par défaut de l'instance, puis en anglais.

## 6. Les langues

Chaque texte saisi (libellés d'onglets, titres, notes, libellés de boutons, textes d'aide, libellés de champs) a une
valeur par langue de l'instance. Choisissez la langue dans *Langue* et saisissez ; un soulignement en pointillé marque
un texte non traduit dans la langue affichée. *Traductions* liste tous les textes de la fiche avec ce qui manque par
langue, et *Seulement les textes à traduire* restreint la liste. Un texte absent dans une langue s'affiche dans la
langue par défaut. Les libellés du modèle et les messages de validation viennent d'EBX, déjà traduits.

Les écrans du designer lui-même sont en français ou en anglais, selon la langue de l'utilisateur.

## 7. Qui voit quoi : rôles, variantes, fiche personnelle

Par défaut **tout le monde voit toute la fiche**, comme avant la 1.10.0. Rien n'est à régler si ce n'est pas utile ;
ensuite, trois niveaux, du plus léger au plus complet :

1. **Restreindre une partie.** Sélectionnez un onglet, un bloc ou un champ : la partie *Qui voit* de l'inspecteur a
   deux listes de rôles, *Visible pour* (vide = tout le monde) et *Lecture seule pour*. Les rôles viennent de l'annuaire
   d'EBX ; un autre nom se tape dans le champ dessous (annuaire LDAP ou SSO). Un onglet masqué emporte ses blocs, un
   panneau dont tous les champs sont masqués disparaît. Un champ obligatoire masqué pour un rôle l'empêche de créer une
   fiche : le designer le signale.
2. **Une variante pour un rôle.** *Fiche › + Nouvelle variante* (ou *Profils et variantes › Dupliquer en variante*)
   copie la fiche en cours ; donnez-lui un nom et les rôles qui la reçoivent, puis modifiez-la comme une autre. À
   l'ouverture, chacun reçoit la première variante dont il a un rôle (l'ordre se règle dans *Profils et variantes*),
   sinon la fiche standard. Exemple : une fiche « Demande de création » d'un seul onglet pour le demandeur.
3. **La fiche personnelle.** Cochez *Les utilisateurs peuvent personnaliser leur fiche* dans *Profils et variantes* :
   un bouton *Personnaliser ma fiche* apparaît en haut de la fiche (et dans le menu Actions de l'enregistrement). Chacun
   y change l'ordre des onglets, l'onglet ouvert en premier, masque ou replie un bloc, ou l'épingle dans un premier
   onglet *Mes essentiels*. Ce n'est jamais une copie : une évolution faite ensuite dans le designer arrive chez tout le
   monde, un nouveau bloc apparaît à sa place. Cochez *Toujours affiché* sur un onglet ou un bloc qui doit rester (ex. :
   paiements bloqués). Pendant la création d'une fiche, rien n'est masqué ni replié. *Revenir à la fiche standard*
   efface l'organisation de l'utilisateur.

*Aperçu en tant que* (en haut) montre la fiche d'un rôle ou d'un utilisateur de l'annuaire : en mode *Concevoir*, ce
qu'il ne voit pas est barré et grisé ; en mode *Aperçu*, il disparaît, et la fiche passe sur la variante qu'il recevrait.
*Profils et variantes* liste les variantes, *qui reçoit quelle fiche* et le tableau *Qui voit quoi* (un clic sur une
ligne la sélectionne).

Les administrateurs voient tout (option décochable). **Ce n'est pas de la sécurité** : un champ absent de la fiche reste
lisible dans la vue tableau, un export ou les API ; ce sont les permissions d'EBX qui protègent la donnée.

### Conditions liées aux workflows (1.17.0)

Dans l'inspecteur de chaque bloc, et de chaque bouton d'un bloc *Actions*, *Selon les workflows* le masque :

- selon le **contexte de la fiche** : *Hors workflow* (par exemple, pas de bouton « Changer le RIB » dans une tâche) ou
  *Dans une tâche de workflow* (un bloc d'aide pour la personne qui fait la tâche) ;
- selon une **demande en cours sur la fiche** : *Masquer si en cours* ou *Seulement si en cours*, pour une publication de
  workflow précise ou toutes, avec les variables qui désignent la fiche (`tiersXPath`…).

## 8. Enregistrer, déclarer, utiliser

- **Fraîcheur** (réglages de l’explorateur) : *À la demande* (par défaut) partage les résultats calculés et les garde
  jusqu’à ce qu’un utilisateur clique **Actualiser** sur la page ou jusqu’à l’âge maximum (60 minutes par défaut) ; la page
  indique quand ils ont été calculés et signale si des données ont changé depuis. *Automatique* recalcule dès que les
  données changent : à réserver aux petites tables ou à peu d’utilisateurs.
- **Enregistrer** écrit la mise en page (JSON) comme nouvelle version dans `LayoutDesigner/FormLayout`. Elle s'affiche à
  la prochaine ouverture d'une fiche, **sans redémarrage**, dès que la table déclare la fiche conçue.
- **Activation** (le bandeau sous l'en-tête, depuis la 1.9.0 : orange = pas encore utilisée, bleu = en attente du
  redémarrage, violet = modèle DMA, vert = active et vue pour la dernière fois sur telle fiche ; *Voir les étapes* ouvre
  le guide) :
  - modèle packagé dans un module : *Activer* écrit la ligne `recordForm` dans le XSD, avec une copie de sauvegarde ;
    **redémarrez le serveur une fois**, puis ouvrez une fiche pour vérifier. *Remettre la fiche précédente* l'annule
    (nouveau redémarrage) ;
  - modèle DMA, quatre étapes dans l'écran du modèle : déclarer `com.on.ebx.layout.runtime.ConfigurableForm` dans la
    **Librairie de composants** du modèle (*Nom du composant*, *Configuration › Règle* ; EBX en déduit le type UIForm),
    le choisir pour la table dans **Mise en page du formulaire › Composant**, **publier** le modèle, ouvrir une fiche
    pour vérifier. Pas de redémarrage. L'opération MCP `set_form_layout` du serveur `data-model` fait les deux
    premières étapes ;
  - quand le designer ne peut pas écrire le XSD du module (dossier introuvable), le guide donne la ligne à coller.
- **</> Code** : le JSON enregistré, et la même mise en page en classe `UIForm` figée pour un projet qui ne veut pas
  du designer à l'exécution (facultatif).

### Références disparues (1.17.1)

Une mise en page nomme des champs, des tables, des publications de workflow et des composants enregistrés. Quand l'un
d'eux disparaît (champ supprimé du modèle puis modèle republié, publication retirée, module désinstallé), rien ne
casse : la fiche l'omet. Mais la mise en page le signale :

- l'enregistrement `FormLayout` porte un message de validation par référence (erreur pour un champ ou une table,
  avertissement pour une publication, un composant, ou un modèle sans dataset sur cette instance) ; l'enregistrement
  n'est jamais bloqué ;
- le designer affiche *N référence(s) à vérifier* dans la barre du haut ; un clic les liste avec *Voir le bloc* et
  *Retirer*. Un champ disparu d'un panneau s'affiche en ligne rouge avec son propre bouton de retrait ; chaque bloc
  concerné porte une étiquette rouge.

## 9. Pour les développeurs : composants enregistrés

Un module peut ajouter son propre bloc. Implémentez `com.on.ebx.layout.runtime.LayoutComponent` (`getLabel(Locale)`,
`write(UIFormPaneWriter, UIFormContext, JsonNode block, Locale)`) et enregistrez-le une fois au démarrage :

```java
LayoutComponents.register("acme.creditScore", new CreditScoreComponent());
```

Il apparaît alors dans la palette sous *Composants enregistrés* ; le runtime ouvre un panneau avec le titre et la note
du bloc, et le composant écrit à l'intérieur. Le module n'a besoin du jar runtime qu'à la compilation (il est dans
celui du serveur à l'exécution).

## 10. Limites

- **Modèles DMA** : le designer ne sait pas écrire lui-même la déclaration (déclarez la classe dans l'écran du modèle
  ou avec `set_form_layout`), ni la lire : l'état affiché dans la barre du haut indique alors *Modèle DMA : déclaration à
  vérifier*. Pour un modèle de module, l'état vient de son fichier XSD ; une déclaration s'applique au redémarrage suivant.
- **Aller au champ** suppose un message rattaché à un champ. Une règle posée sur la table (une contrainte sur plusieurs
  champs) porte sur la fiche entière : le message s'affiche, sans emplacement ni lien. Un bouton d'action lié à un
  champ n'apparaît donc jamais pour une telle règle.
- EBX n'évalue pas une **règle de champ** sur un champ vide : une règle « courriel ou mobile » se pose sur la table.
- La **carte** est uniquement OpenStreetMap (une iframe) : le navigateur de l'utilisateur doit joindre
  openstreetmap.org.
- Une **condition** impossible à évaluer (un XPath faux) laisse le bloc affiché et écrit un avertissement dans
  `kernel.log`.
- Une mise en page par table et par modèle : tous les datasets du modèle la partagent.
- Vu tourner sur EBX 6.3.0-rc2 uniquement, avec les langues en-US, fr-FR et it-IT.

## 11. Les explorateurs

Un explorateur est une page de recherche sur une table : une recherche, des facettes, des tuiles, un tri et une carte
par fiche. Ouvrez une table, puis **Actions › Concevoir l’explorateur** (ou *L’explorateur* dans la barre du designer de
fiche).

- **Palette** : les champs de la table (avec *+ Facette*, *+ Carte*, *+ Recherche*, *+ Tuile* ; un bouton déjà utilisé
  affiche ✓ et retire le champ quand on clique dessus), les valeurs calculées (le nombre de lignes de chaque
  association, les suspects de Match and Merge) et les éléments (une facette « À surveiller » de drapeaux, une tuile, une
  variante, les actions).
- **Canevas** : l’explorateur lui-même, sur jusqu’à 200 fiches de la table. Cliquez une zone pour la régler, ou
  directement un attribut sur une carte pour changer son widget, ses options, sa zone ou qui le voit. Glissez un champ
  sur une zone pour l’ajouter.
- **Longues listes** : un champ à nombreuses valeurs n’affiche jamais toute sa liste. Les facettes montrent les 10
  valeurs les plus fréquentes, puis 10 de plus jusqu’à 50, puis une recherche ; une couleur par valeur ne nomme que les
  valeurs choisies, toutes les autres prennent la couleur par défaut ; la valeur d’une condition se saisit avec des
  suggestions.
- **Qui voit quoi** : une facette, une tuile, un attribut de carte ou une action peut être réservé à des rôles ; une
  *variante* est une autre version de tout l’explorateur pour des rôles. *Aperçu en tant que* montre l’explorateur d’un
  rôle. Les droits EBX s’ajoutent, sur la vraie page : un utilisateur ne voit, ne compte et ne trouve que les fiches et les
  champs qu’EBX lui laisse lire.
- **Enregistrer** stocke l’explorateur ; il s’affiche à sa prochaine ouverture. Mettez-le dans une perspective avec une
  action sur le service *Explorateur* de la table, paramètre `ldxExplorer` = l’identifiant de l’explorateur (le lien est
  dans la barre du haut).

Le format est décrit dans [EXPLORER-FORMAT.md](EXPLORER-FORMAT.md) (en anglais).

