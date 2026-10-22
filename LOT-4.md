# Lot 4 : le moteur de formulaires (Suivis, champs additionnels OccTax)

*19 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, inchangée depuis. Tout le code est dans `core` ; aucun écran ne l'affiche encore.*

Le lot 4 porte la logique des formulaires (`INVENTAIRE.md` § 10) :
- le **formulaire des Suivis** : l'état que `FormulaireRenderer` gardait dans ses vues, les règles du schéma (`hidden`, `required` conditionnel, `change`, bornes min/max) et le contrat des valeurs envoyées ;
- le **champ espèce** de ce formulaire : propositions de la liste du protocole, résolution restreinte à cette liste, ligne de statut ;
- les **valeurs d'une visite** : leur enregistrement dans la file d'envoi des Suivis, et leur relecture quand on rouvre la saisie ;
- les **champs additionnels OccTax** : valeurs au rendu, encodage envoyé, champs requis ;
- le **filtre d'autocomplétion** des espèces.

**État.** Tout le moteur est porté et testé. Les tests passent de 658 à 797 sur la JVM, et de 648 à 787 sur le simulateur iOS (§ 8). Les écrans qui l'afficheront viennent avec les lots 6 (OccTax) et 7 (Suivis).

**Critère de sortie** (mêmes valeurs envoyées qu'Android) : **atteint le 19 octobre par les tests**, sur la JVM et le simulateur iOS (§ 9). La comparaison sur un vrai serveur se fera avec les écrans.

## 1. Ce qui est porté

| GeoMys-Android (`cdd5196`) | GeoMys | Remarque |
|---|---|---|
| `monitoring/form/ExpressionScanner.kt` | `monitoring/form/ExpressionScanner.kt` | Tel quel |
| `monitoring/form/EditableField.kt` | `monitoring/form/EditableField.kt` | Tel quel ; les commentaires qui décrivaient des vues parlent de l'état |
| `monitoring/form/WidgetMapping.kt` | `monitoring/form/WidgetMapping.kt` | Tel quel |
| `monitoring/form/HiddenExpr.kt` | `monitoring/form/HiddenExpr.kt` | § 7 |
| `monitoring/form/ChangeRules.kt` | `monitoring/form/ChangeRules.kt` | § 7 |
| `monitoring/form/ValidationExpr.kt` | `monitoring/form/ValidationExpr.kt` | § 7 |
| `monitoring/form/FormulaireRenderer.kt` : état, règles, champ espèce | `monitoring/form/FormulaireEtat.kt`, `FormatsDate.kt`, `ChampTaxon.kt` | §§ 2 à 4 ; les widgets deviennent des composables (lot 7) |
| `ui/NouvelleVisiteFragment.kt` : `valeurToJson`, `parseValeursJson`, `typerPourField`, étapes pures de l'enregistrement et de la réouverture | `monitoring/form/ValeursFormulaire.kt` | § 5 ; l'écran vient au lot 7 |
| `ui/saisie/AdditionalFieldsRenderer.kt` : valeurs, encodage, champs requis | `saisie/ChampsAdditionnels.kt` | § 6 ; les widgets deviennent des composables (lot 6) |
| `ui/saisie/SpeciesAutocomplete.kt` : le filtre | `saisie/Suggestions.kt` | § 4 ; la liste déroulante est un composable (lots 6 et 7) |

## 2. `FormulaireEtat` : l'état sorti des vues

GeoMys-Android gardait l'état du formulaire dans ses vues (leurs `tag`) et recalculait tout depuis leurs écouteurs. `FormulaireEtat` garde cet état et refait les mêmes recalculs, dans le même ordre. Les composables ne feront que le lire et lui passer les saisies.

- **État.** Une saisie par champ : texte, date ou heure, espèce, liste, boutons radio, choix multiple, cases, case unique, médias. `revision` (un `StateFlow`) change à chaque modification. `setOnChangement` reprend le rappel par lequel l'écran Android réactivait son bouton d'enregistrement.
- **Recalcul**, à chaque modification : visibilité (`hidden`) et astérisques (`required` conditionnel), puis règles `change` (sauf pendant leur propre application), puis bornes min/max ; l'écran est prévenu ensuite.
- **Frappe.** Le recalcul attend 220 ms après la dernière frappe, dans la portée de coroutines du formulaire. `flushChangementsEnAttente` l'applique tout de suite : l'écran devra l'appeler avant toute décision (enregistrer, quitter).
- **Champs modifiés par l'utilisateur** (`__dirty`). Mêmes références qu'Android : les valeurs au rendu, les dernières valeurs posées par une règle, et les champs déjà remplis d'une saisie rouverte, modifiés d'emblée.
- **Champ masqué.** Sa valeur est conservée mais ne part pas, sauf s'il est requis ou posé par une règle `change`. Le champ `masque` (option unique choisie d'office) suit la même règle, comme sous Android.

**Les écouteurs d'Android, rejoués à l'identique.** Le nombre de passes de recalcul dépend du moment où chaque widget Android prévenait. Le résultat de règles `change` qui dépendent les unes des autres en dépend aussi.
- Une **liste déroulante** prévient *après* coup : son `onItemSelected` arrive au passage de mise en page suivant.
  - Une liste changée par une règle déclenche donc une passe de plus, hors de la garde des règles. `FormulaireEtat` la lance dans sa portée de coroutines.
  - Au rendu, chaque liste visible prévient une fois de son choix initial.
- Une **case**, un **bouton radio** ou une **case de choix multiple** changés par une règle préviennent *pendant* son application : visibilité et bornes sont recalculées, pas les règles.
- Les **autres champs** changés par une règle (texte, dates, choix multiple) ne relancent rien.

**Le contrat des valeurs** (`lireValeurs`) :

| Type de champ | Valeur |
|---|---|
| TEXT, TEXTAREA | `String`, vide si rien |
| NUMBER | `Int` ; `Double` seulement pour un widget décimal (un widget entier tronque) ; virgule acceptée ; `null` si illisible |
| DATE | `String` `yyyy-M-d`, sans zéros ; `""` si rien |
| TIME | `String` `HH:MM` ; `""` si rien |
| DATETIME | `String` `yyyy-MM-dd HH:mm:ss`, secondes à 0 ; `""` si rien |
| TAXON | le cd_nom (`Int`), ou `null` |
| SELECT, RADIO | la valeur de l'option, `""` si aucune |
| SELECT_MULTIPLE, CHECKBOX_MULTIPLE | `List<String>`, `null` si vide |
| CHECKBOX | `true` ou `null`, jamais `false` |
| MEDIA | la liste des fichiers, jamais `null` ; retirée du corps envoyé (§ 5) |

Pour un champ MEDIA, `lireValeurs` rend la liste du champ elle-même, comme le `tag` d'Android : une lecture faite plus tôt voit les ajouts suivants.

## 3. Dates et heures

`SimpleDateFormat`, en mode tolérant (son mode par défaut), devient `FormatsDate`, une lecture écrite sur kotlinx-datetime. Elle accepte ce qu'il acceptait :
- **un préfixe** : `2026-05-21T10:00:00Z` se lit 2026-05-21 ;
- **des chiffres sans zéros obligatoires** : `2026-5-1` ;
- **des débordements, reportés** : `2026-02-30` donne le 2 mars, `2026-13-1` le 1er janvier 2027 ;
- **des heures** : `HH:MM`, `HH:MM:SS`, `10h30`, `10H`.

Les formats écrits sont ceux d'Android : `yyyy-M-d` pour une date, `HH:MM` pour une heure, `yyyy-MM-dd HH:mm:ss` pour une date-heure. L'affichage se fait en `dd/MM/yyyy` et `dd/MM/yyyy HH:mm`.

## 4. Le champ espèce

- **Propositions.**
  - Ce sont celles de la liste taxonomique du protocole, tirées de son index de résolution : un nom proposé est un nom accepté (règle du 17 septembre).
  - Chacune porte son cd_nom et son nom scientifique, affiché en italique dessous.
  - Sans liste imposée, ce sont les noms du cache.
  - Elles se calculent hors du fil de l'interface : une liste compte jusqu'à 45 000 taxons.
- **Filtre** (`saisie/Suggestions.kt`, celui des deux adaptateurs de `SpeciesAutocomplete`). Viennent d'abord les noms qui commencent par la requête, puis ceux qui la contiennent, sans tenir compte des accents ni de la casse.
- **Résolution.** Elle est restreinte à la liste du protocole : « Souci » y désigne le papillon, pas la plante. Une proposition choisie fixe son taxon tant que le texte ne change pas.
- **Ligne de statut.** Elle affiche l'un de ces textes :
  - « ✓ nom scientifique • cd_nom N » ;
  - le refus, à la sortie du champ ;
  - « Pas de données — rechargez les données » quand la liste manque au cache ;
  - « Chargement des données en cours… » quand elle manque pendant une synchro.
- **Saisie rouverte.** Un cd_nom absent du cache est gardé et s'affiche « cd_nom N ».
- **Le cache TaxRef n'est jamais écrit** par le champ (§ 8).

## 5. Les valeurs d'une visite

`ValeursFormulaire` reprend les fonctions de valeurs de `NouvelleVisiteFragment`.
- **Enregistrement** (`preparerEnregistrement`) :
  - les champs MEDIA sont retirés du corps ;
  - les fichiers du premier champ MEDIA rempli sont mis à part, avec sa table ;
  - les codes des champs texte sont notés : un commentaire « 42 » ne deviendra pas un nombre à l'envoi.
- **Écriture JSON** par le portage commun d'org.json, donc sous les mêmes formes qu'Android :
  - un `Double` entier s'écrit sans « .0 » (`12`) ;
  - « / » est échappé (`\/`) ;
  - un id de nomenclature reste une chaîne.
- **Relecture** (`parseValeursJson`, `typerPourField`, `champsPreremplis`) :
  - chaque valeur reprend la forme qu'attend son champ ;
  - les fichiers reviennent sur le premier champ MEDIA ;
  - les champs remplis sont modifiés d'emblée.

Une saisie enregistrée puis rouverte rend exactement le même corps (test d'aller-retour).

## 6. Les champs additionnels OccTax

`EtatChampsAdditionnels` garde les valeurs qu'`AdditionalFieldsRenderer` gardait dans ses vues. `collecter` rend l'encodage qu'envoyait Android, et que l'envoi relit (lot 3) :

| Widget | Valeur envoyée |
|---|---|
| texte, nombre, widget inconnu | le texte |
| liste déroulante | le code choisi (code serveur, ou libellé dans les anciens caches), `""` si aucun |
| nomenclature | l'id de nomenclature choisi, `""` si aucun |
| boutons radio | le code coché, `""` si aucun ; recliquer le bouton coché le décoche |
| cases multiples (plus d'une valeur proposée) | le tableau JSON des codes cochés, `["1","3"]` |
| case unique | `"true"` ou `"false"` |

- **Valeur au rendu.** C'est la valeur saisie ; à défaut, le défaut serveur ; à défaut, la date ou l'heure du jour pour les widgets date et heure. La chaîne `"null"` vaut une valeur vide.
- **Cases multiples.** Seul un tableau enregistré les coche : un défaut serveur scalaire ne coche rien, comme sur le web.
- **Champs requis vides.** Ils bloquent la validation, sauf les cases, qui ne sont jamais vides.
- **Écran « Détails du relevé ».**
  - `aDesChampsReleveRequisSansDefaut` décide s'il s'intercale avant la saisie.
  - Quand on le saute, `defautsChampsReleve` pré-remplit la session (sans les cases).

## 7. Ce qui change dans le portage

- **Expressions régulières.** `RegexOption.DOT_MATCHES_ALL` n'existe pas dans le Regex commun : le drapeau `(?s)` le remplace. Les expressions sont compilées une fois, et non à chaque évaluation. La grammaire est la même, mais un formulaire réévalue tous ses champs à chaque saisie.
- **Journal.** `Log` devient `Journal`.
- **Textes.** Ceux de la ligne de statut venaient des ressources Android (`taxref_pas_de_donnees`, `taxref_chargement_en_cours`). Ils sont écrits dans le socle, comme les autres textes portés jusqu'ici (décision 10 de l'inventaire, encore ouverte).

**Approximation connue.** Sous Android, une liste déroulante masquée au rendu prévenait de son choix initial à son premier affichage, ce qui faisait une passe de recalcul de plus. `FormulaireEtat` ne rejoue pas cette passe. Seules des règles `change` enchaînées, qui comptent sur elle pour se propager, pourraient en dépendre.

## 8. Tests

**Portés de GeoMys-Android : 85 cas** (5 fichiers).

| Fichier | Cas | Remarque |
|---|---|---|
| `HiddenExprTest` | 30 | |
| `ChangeRulesTest` | 15 | |
| `ValidationExprTest` | 17 | |
| `WidgetMappingTest` | 9 | |
| `FormulaireRendererTest` | 14 sur 15 | Réécrit contre l'état commun : `FormulaireEtatTest` |

**Écrits pour le portage : 54 cas.** GeoMys-Android ne teste ni le contrat des valeurs, ni leur enregistrement, ni les champs additionnels :
- `ContratValeursTest` (20) : pour chaque type de champ, la valeur lue, la valeur initiale, la valeur posée par une règle, et l'envoi des champs masqués. Les attendus sont lus ligne à ligne dans le code Android ;
- `ValeursFormulaireTest` (6) : le JSON écrit et relu, et l'aller-retour d'une saisie rouverte ;
- `FormatsDateTest` (6) : lecture tolérante et formats ;
- `ChampsAdditionnelsTest` (11) : encodages, valeurs au rendu, champs requis, « Détails du relevé » ;
- `SuggestionsTest` (4) : le filtre ;
- `ChampTaxonTest` (7) : propositions, ligne de statut, et la garde ci-dessous.

**Garde textuelle réécrite.** `ResolutionPurementLocaleTest.aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref` lisait des sources d'Android, dont `FormulaireRenderer.kt`. Pour le formulaire des Suivis, elle devient un test de comportement. Le test tape un nom, choisit une proposition et fait refuser un nom inconnu, dans un champ libre et dans un champ restreint au protocole. Le dossier du cache TaxRef reste identique, octet pour octet. La part OccTax de la garde suivra avec ses écrans (lot 6).

Tous ces tests tournent sur la JVM et le simulateur iOS. Ils vérifient aussi que les expressions régulières et l'écriture des nombres donnent le même résultat sur les deux.

**Adaptations.**
- Les saisies dans les vues deviennent les actions de l'état (`saisirTexte`, `choisirOption`…), le Looper de Robolectric le planificateur de `runTest`.
- La barre rouge du mode complétion se lit dans `barresRouges`.
- `runCurrent` (API expérimentale de kotlinx-coroutines-test, d'où l'opt-in des deux classes) fait passer les recalculs différés des listes déroulantes.

**Reporté.** Le cas de `FormulaireRendererTest` qui vérifie la mise en page (la barre rouge accolée au champ, pas au libellé) suivra avec l'écran (lot 7).

## 9. Critère de sortie

« Mêmes valeurs envoyées qu'Android » est établi par les tests, sur la JVM et le simulateur iOS :
- les tests du moteur de GeoMys-Android passent : expressions `hidden`, règles `change`, bornes, correspondance des widgets, et 14 des 15 cas du formulaire ;
- pour chaque type de champ, la valeur lue, la valeur initiale et la valeur posée par une règle sont celles du code Android ;
- le corps enregistré dans la file d'envoi s'écrit comme sous Android, et une saisie rouverte rend le même corps ;
- les champs additionnels rendent l'encodage que l'envoi OccTax relit.

**Pas encore essayé contre un vrai serveur.** Aucun écran n'affiche encore de formulaire : la comparaison se fera à la recette des lots 6 et 7.

## 10. Reste à faire

- **Écrans.** Les composables liront `FormulaireEtat` et `EtatChampsAdditionnels` :
  - le formulaire des Suivis et « Nouvelle visite » (lot 7) ;
  - les champs additionnels et l'autocomplétion OccTax (lot 6).
- **Ce qui relève de l'écran** : les sélecteurs de date et d'heure, le défaut « fantôme » des champs nombre (vidé au focus, remis à la sortie), la sélection des médias (lot 7).
- **Test reporté** : le cas de mise en page de `FormulaireRendererTest` (lot 7).
- **Fichiers voisins.** Ils viennent avec leurs écrans :
  - `ui/saisie/ChampsTaxon.kt`, `TaxRefLookup.kt`, `OcctaxFieldsRenderer.kt` et `PreferencesSaisie.kt`, avec les écrans OccTax (lot 6) ;
  - `ui/PictoMonitoring.kt`, avec les Suivis (lot 7).

## 11. Outils et versions

Aucune dépendance nouvelle.
