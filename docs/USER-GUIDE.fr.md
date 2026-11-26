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
| Barre du haut | *Aperçu avec* (la fiche montrée dans l'aperçu), *Langue* (celle de l'aperçu et des textes saisis), *Traductions*, mode *Concevoir* / *Aperçu*, *Largeur* (tester un écran étroit), *Aide*, l'**état de la fiche** tel que le modèle le déclare (*Déclarée dans le modèle*, *La table utilise une autre fiche*, *Non utilisée par la table* ; *Modèle DMA : déclaration à vérifier*), *Activation*, *</> Code*, *Annuler*, *Enregistrer* |
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

## 4. Les blocs

| Bloc | Réglages |
|---|---|
| **Panneau** | titre, note sous le titre, champs, tout le panneau en lecture seule |
| **Bandeau d'identité** | champ titre, champs de sous-titre, puces, jauge (un champ de 0 à 100), pastille d'état. Affiche « Nouvel enregistrement » en création |
| **Chiffres clés** | une rangée de valeurs courtes, plus le nombre de lignes d'une association |
| **Rapport de validation** | voir section 5 |
| **Association** | *Grille native* (la grille d'EBX : barre d'outils, filtre, pagination, création) ou *Cartes* (champs choisis sur chaque carte, la grille complète sous *Gérer les lignes*) ; titre (par défaut, le libellé de l'association), libellé du bouton d'ajout |
| **Carte** | un champ latitude et un champ longitude ; OpenStreetMap dans un cadre de hauteur fixe, option *Grande carte* |
| **Horaires d'ouverture** | ouvert maintenant (dans le fuseau de la fiche), la semaine en cours une carte par jour, les prochains jours exceptionnels. Choisissez l'association des horaires de la semaine et ses champs jour, ouverture et fermeture (et un champ « fermé » facultatif), éventuellement les codes du champ jour, l'association des horaires exceptionnels et ses champs, le nombre d'exceptions listées et le champ fuseau horaire de la fiche. Détails dans la référence du format |
| **Structure du groupe** | la clé étrangère vers la maison mère et l'association des filiales : maison mère, la fiche, ses filiales |
| **Doublons** | l'état Match and Merge de la fiche (golden, fusionnée, groupe). Sans l'add-on, le bloc le dit |
| **Texte d'aide** | un paragraphe pour l'utilisateur |
| **Composant enregistré** | un bloc Java écrit par un développeur (section 8) |

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
| **Galerie d'images** | les images de champs média ou d'adresse, de la fiche ou d'une association |
| **Documents** | les fichiers d'une association : nom, type, date (en rouge une fois une échéance passée), lien |
| **Liens et actions** | des boutons vers une adresse construite avec les valeurs de la fiche (`https://boutique.exemple.fr/p/${./Sku}`) |
| **Période de validité** | à venir, en vigueur, bientôt échu ou expiré, avec les jours restants |
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

## 7. Enregistrer, déclarer, utiliser

- **Enregistrer** écrit la mise en page (JSON) comme nouvelle version dans `LayoutDesigner/FormLayout`. Elle s'affiche à
  la prochaine ouverture d'une fiche, **sans redémarrage**, dès que la table déclare la fiche conçue.
- **Activation** (*Activation* dans la barre du haut ; l'état affiché à côté dit si la table utilise la fiche) :
  - modèle packagé dans un module : *Déclarer la fiche conçue sur cette table* écrit la ligne `recordForm` dans le XSD,
    avec une copie de sauvegarde ; **redémarrez le serveur une fois**. *Remettre la fiche précédente* l'annule
    (nouveau redémarrage) ;
  - modèle DMA : déclarez `com.on.ebx.layout.runtime.ConfigurableForm` comme formulaire de la table dans l'écran du
    modèle (ou avec l'opération MCP `set_form_layout` du serveur `data-model`) et republiez ; pas de redémarrage.
- **</> Code** : le JSON enregistré, et la même mise en page en classe `UIForm` figée pour un projet qui ne veut pas
  du designer à l'exécution (facultatif).

## 8. Pour les développeurs : composants enregistrés

Un module peut ajouter son propre bloc. Implémentez `com.on.ebx.layout.runtime.LayoutComponent` (`getLabel(Locale)`,
`write(UIFormPaneWriter, UIFormContext, JsonNode block, Locale)`) et enregistrez-le une fois au démarrage :

```java
LayoutComponents.register("acme.creditScore", new CreditScoreComponent());
```

Il apparaît alors dans la palette sous *Composants enregistrés* ; le runtime ouvre un panneau avec le titre et la note
du bloc, et le composant écrit à l'intérieur. Le module n'a besoin du jar runtime qu'à la compilation (il est dans
celui du serveur à l'exécution).

## 9. Limites

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
