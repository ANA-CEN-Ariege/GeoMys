# Lot 7 : les Suivis (Monitoring)

*19 octobre 2026. Portage depuis GeoMys-Android `cdd5196` ; `d72b8da`, son HEAD, ne touche pas aux Suivis. Allure iOS reprise de GeoMys-IOS `5fda84c`. La logique est dans `core`, les écrans dans `ui`.*

Le lot 7 porte les six écrans des Suivis (`INVENTAIRE.md` § 10) :
- **Monitoring** : la liste des protocoles du serveur, leurs pictos, le bouton des données en attente ;
- le **détail d'un protocole** : ses sites, avec la fiche, la carte et le « + » d'une nouvelle saisie ;
- la **fiche d'un objet** : ses propriétés, ses enfants du serveur et les saisies locales qui s'y rattachent ;
- la **nouvelle saisie** (visite, passage, observation…), son formulaire dynamique, la chaîne d'observations, la complétion d'une visite « à compléter » ;
- **Mes visites** : la file d'envoi des Suivis, en arbre protocole → site → saisies ;
- la **carte des géométries** d'un objet ou d'un protocole.

Les écrans suivent la décision 15 ([`LOT-5.md`](LOT-5.md) § 2) : l'allure de GeoMys-Android sous Android, celle de GeoMys-IOS sous iOS ; les éléments, les textes et les comportements de GeoMys-Android sur les deux.

**État.** Tout le contenu du lot est porté. Les tests du socle passent de 890 à 941 sur la JVM, et de 880 à 931 sur le simulateur iOS. Ceux des écrans gagnent un premier test de mise en page, sur le simulateur iOS (§ 7).

**Critère de sortie** (recette terrain Monitoring) : **en cours**. Les écrans ont été essayés sur l'émulateur (Pixel 10) et sur le simulateur (iPhone 17 Pro), avec des données d'essai. L'appli est installée sur le Galaxy A12 et l'iPhone 16 : les premiers essais de l'utilisateur, le 19 octobre, n'ont rien relevé. La recette se poursuit contre le serveur GeoNature des lots précédents (§ 8).

## 1. Ce qui est porté

| GeoMys-Android | GeoMys-IOS (`5fda84c`) | GeoMys Common |
|---|---|---|
| `SuivisFragment.kt`, `fragment_suivis.xml`, `item_suivi_module.xml`, `PictoMonitoring.kt` | `SuivisView` (`UI/Monitoring/MonitoringViews.swift`) | `ui/…/suivis/SuivisScreen.kt` et `SuivisViewModel.kt` ; `core/…/monitoring/ListeProtocoles.kt`, `PictoMonitoring.kt` ; `ServicesGeoNature.cheminPicto` |
| `SuiviDetailFragment.kt`, `fragment_suivi_detail.xml` | `SuiviDetailView` (`MonitoringViews.swift`) | `ui/…/suivis/SuiviDetailScreen.kt` et `SuiviDetailViewModel.kt` ; `core/…/monitoring/AffichageSuivis.kt` |
| `FicheObjetFragment.kt`, `fragment_fiche_objet.xml` | `FicheObjetView` (`MonitoringViews.swift`) | `ui/…/suivis/FicheObjetScreen.kt` et `FicheObjetViewModel.kt` ; `core/…/monitoring/FicheObjet.kt` |
| `FilAriane.kt` : le fil des Suivis | `FilAriane` | `core/…/monitoring/FilSuivis.kt` ; `FilArianeSuivis` (`ui/…/composants/FilAriane.kt`) ; la page commune dans `ui/…/suivis/PageSuivi.kt` |
| `NouvelleVisiteFragment.kt`, `fragment_nouvelle_visite.xml` | `UI/Monitoring/NouvelleVisiteView.swift` | `ui/…/suivis/NouvelleVisiteScreen.kt` et `NouvelleVisiteViewModel.kt` ; `core/…/monitoring/NouvelleVisite.kt` |
| `monitoring/form/FormulaireRenderer.kt` : les widgets | `UI/Monitoring/FormulaireRenderer.swift` | `ui/…/suivis/FormulaireMonitoring.kt` ; l'état et les règles au lot 4 (`monitoring/form/FormulaireEtat.kt`) |
| `SaisiesEnAttenteFragment.kt`, `fragment_saisies_en_attente.xml` | `UI/Monitoring/SaisiesEnAttenteView.swift` | `ui/…/suivis/MesVisitesScreen.kt` et `MesVisitesViewModel.kt` ; `core/…/monitoring/MesVisites.kt` (dont `resumeOutbox`) |
| `CarteGeometrieFragment.kt`, `fragment_carte_geometrie.xml` | `UI/Monitoring/CarteGeometrieView.swift` | `ui/…/suivis/CarteSuiviScreen.kt` et `CarteSuiviViewModel.kt` ; `core/…/monitoring/CarteSuivis.kt` |
| `MainActivity.kt`, `nav_graph.xml` : les destinations des Suivis | — | `ui/…/navigation/NavigationGeoMys.kt` (§ 3) |

La file d'envoi, son envoi et le moteur de formulaires viennent des lots 2 à 4 (`MonitoringApi`, `OutboxMonitoring`, `OutboxEnvoi`, `FormulaireEtat`, `ValeursFormulaire`).

## 2. Deux allures

Ce que chaque plateforme fait elle-même :

| Élément | Android | iOS |
|---|---|---|
| Champs date, heure, date et heure | case au contour gris (`bg_carte_contour`) ouvrant `DatePickerDialog`, `TimePickerDialog` (24 h), ou l'un puis l'autre | `UIDatePicker` compact ; vide, l'invite d'Android dans la même pastille |
| Formulaire | lignes du `FormulaireRenderer` : libellé 13 sp, champ, barre rouge de 3 dp le long du seul champ | section d'un `Form` SwiftUI (fond #1C1C1E, lignes séparées) ; barre rouge de 3 pt le long de la ligne |
| Choix multiple (observateurs) | AlertDialog d'AppCompat à cases (`setMultiChoiceItems`), « Annuler » et « Valider » | feuille de GeoMys-IOS : barre de navigation, liste groupée aux cases SF |
| Liste déroulante | `Spinner` (« — choisir — ») | menu système, la valeur et les chevrons à droite de la ligne |
| Nombre | pavé numérique signé, décimal pour un widget décimal (l'`inputType` de GeoMys-Android) | clavier chiffres et ponctuation, comme les champs additionnels du lot 6 |
| Espèce | champ souligné à liste déroulante, icône d'erreur d'`EditText.setError` | champ de GeoMys-IOS, propositions sous le champ, symbole en tête de la ligne du taxon |
| Retour | bouton ou geste Retour : la garde de sortie | balayage désactivé, comme GeoMys-IOS : sortie par le fil d'Ariane ou « Terminer » |
| Carte : points | `ic_location_pin` teinté, rouge (objet) ou orange (enfants) | goutte évidée cerclée de blanc (`PinLocationMarker`) |

**Composants nouveaux** (`ui/…/composants`) : `ChampDateMonitoring`, `DialogueChoixMultiple`, `FilArianeSuivis`, `lireMiniature` (pictos des protocoles), `ClavierNumeriqueSigne` (§ 7) ; `DialogueAlerte` gagne `annulable` (`setCancelable(false)`), `IndicateurActivite` sa grande taille iOS, `ChampTexteFormulaire` un nombre de lignes maximal sous Android. `rememberValeurChamp` et `ProgressionEnvoi` passent du lot 6 aux composants communs.

**Premier test de mise en page.** Il tourne sur le simulateur iOS (Compose UI test, sans fenêtre), l'allure Android imposée (§ 7).

## 3. Navigation

- **Routes typées.** `RouteSuivis`, `RouteSuiviDetail(moduleCode)`, `RouteFicheObjet(moduleCode, objectType, id, fil)`, `RouteNouvelleVisite` (les arguments de `nouvelleVisiteFragment`), `RouteMesVisites`, `RouteCarteSuivi(moduleCode, objectType, id, titre, fil)`. `RouteAVenir` ne sert plus qu'à OccHab, à l'Explorer et aux cartes.
- **Fil d'Ariane.** Il voyage encodé dans les routes (`encoderFil`, mêmes séparateurs qu'Android). Toucher un niveau y remonte s'il est dans la pile, sinon l'ouvre (`naviguerVersFil`).
- **Remplacement.** Le formulaire de la saisie suivante (visite → observations), celui de la visite à compléter et la fiche du parent remplacent le formulaire courant (`popUpTo` inclusif), comme les actions `enchainer` et `to_fiche` d'Android : le retour ramène au fil.
- **Accueil.** « Monitoring » ouvre la liste des protocoles ; le menu « Mes visites » ouvre la file d'envoi, comme le bouton « Données en attente d'envoi » des Suivis.

## 4. La logique sort des écrans

Elle vivait dans les Fragments ; elle devient des objets du socle, testés sans écran :
- `NouvelleVisite` : choix du type, champ du parent retiré, options des datalists chargées en parallèle, défauts, utilisateur connecté, liste taxonomique en cascade jusqu'au serveur, saisie nouvelle ou modifiée, ce qui suit l'enregistrement, phrases accordées au genre du type ;
- `MesVisites` : arbre et onglets, résumé, actions d'une ligne, fil d'une saisie rouverte ;
- `CarteSuivis` : lecture des géométries, types « macro », actions au toucher, cadrage ;
- `AffichageSuivis`, `FicheObjet`, `FilSuivis`, `ListeProtocoles`, `PictoMonitoring`.

## 5. Écarts assumés

**Défauts d'Android reproduits, à trancher** (inventaire, § 8.2) :
- après « Enregistrer + suivante », la ligne de contexte « Sur : … — Protocole : … » disparaît (n° 14) ;
- un ajout de photos seul ne compte pas comme une modification : « Terminer » et Retour quittent le formulaire sans demander, et les photos sont perdues (n° 15).

Tous deux ont été corrigés depuis dans GeoMys-Android (`5b981f3`, `e69543d`) ; les corrections sont reportées au lot 8 ([`LOT-8.md`](LOT-8.md) § 7).

**Défaut d'Android non reproduit :** les trous des polygones sont rendus en creux, comme sous GeoMys-IOS (n° 9).

**Pas repris :**
- le **mode démo** de la nouvelle visite (inventaire des formulaires, § 2.2) : quand le schéma manque, le contexte affiche les diagnostics d'Android, sans formulaire ni bouton d'enregistrement ;
- la bulle d'`EditText.setError` sur un nom d'espèce refusé : l'icône d'erreur reste, la ligne du taxon dit déjà pourquoi ;
- de GeoMys-IOS, ce qu'Android n'a pas : « À remplir : … » sous le bouton d'enregistrement, « Chargement des protocoles… », « Chargement du schéma… » et « Chargement de la géométrie… », la recherche dans la feuille des observateurs, la ligne « À compléter : … » de « Mes visites », 🧩 pour un protocole sans picto, « Aucun objet rattaché à ce protocole ».

**Comportements d'Android sous iOS :**
- textes d'Android : « — choisir — », « Choisir… », « Valider » et « Annuler », « Aucune photo », « Ajouter des photos », l'aide des champs (que GeoMys-IOS n'affichait pas) ;
- détail d'un protocole : pendant le chargement, la ligne « Sites : … », et non l'indicateur plein écran de GeoMys-IOS ;
- fiche : le type et le nom en tête, sans titre « Infos » ; les saisies locales en « ⏳ Visite (local) », « jj/MM HH:mm · à envoyer » ; tous les types d'enfants, là où GeoMys-IOS ne gardait que les types « macro » ;
- « Mes visites » : le bandeau « Mes visites » au lieu du fil de GeoMys-IOS, ⏳ et non ⚠ pour une saisie « à compléter » (sa flèche d'envoi passe au rouge), une liste vide sans icône ;
- un bouton radio ne se décoche pas.

**Divergences du § 8.3 de l'inventaire** : Android, comme le propose l'inventaire. La liste des protocoles interroge le serveur à chaque ouverture, le cache prenant le relais hors ligne. Un jeu de données seul est choisi d'office et masqué ; `id_digitiser` est posé à l'envoi (lot 3).

**Choix du portage :**
- **Saisie suivante d'une chaîne.** Le bouton reste grisé jusqu'au formulaire suivant : un second toucher pendant le rechargement ne peut plus enregistrer deux fois la même observation.
- **Position sur la carte.** Le récepteur démarre avec la carte et s'arrête en la quittant, sauf pendant un parcours OccTax ; cercle de précision, comme osmdroid. Sous iOS, la carte n'apparaît qu'une fois les géométries chargées, comme dans GeoMys-IOS.
- **Mort du processus (Android).** Comme au lot 6, la restauration des écrans n'est pas portée : un formulaire en cours de saisie est perdu si Android ferme l'appli en arrière-plan, par exemple derrière l'appareil photo. Une saisie déjà enregistrée reste dans la file d'envoi.

## 6. Données d'essai

`action=monitoring-essai` (émulateur et simulateur seulement) écrit dans le cache des Suivis trois protocoles fictifs, comme après une synchro :
- **STOM Ariège** : trois points d'écoute, deux visites du Col de Port et leurs observations, les observateurs, un jeu de données unique (choisi d'office et masqué) ; une visite en attente d'envoi, « à compléter » ;
- **Suivi des mares** : deux mares en polygone, dont une à trou ;
- **Chiroptères en gîtes** : sans site.

Les oiseaux du référentiel d'essai du lot 6 (`action=taxref-essai`, à lancer avant) forment la liste taxonomique du protocole STOM.

## 7. Tests

**Socle : 51 cas de plus**, sur la JVM et le simulateur iOS. **Écrans : 1 cas**, sur le simulateur iOS (nouveau jeu `iosTest` du module `ui`).

| Origine | Cas | Fichiers |
|---|---|---|
| Portés de GeoMys-Android | 19 | `PictoMonitoringTest` (6), `FilArianeTest` (5), `ResumeOutboxTest` (5), `EstTypeSaisieTest` (les 2 cas d'écran laissés au lot 2 : `estObjetDeNiveauVisite`), `MiseEnPageFormulaireTest` (1 : le dernier cas de `FormulaireRendererTest`, « la barre est accolée au champ, pas au libellé ») |
| Écrits pour le portage | 33 | `AffichageSuivisTest` (8), `FicheObjetTest` (5), `NouvelleVisiteTest` (9), `MesVisitesTest` (6), `CarteSuivisTest` (5) |

`FormulaireRendererTest` est ainsi entièrement porté : 14 cas au lot 4, contre l'état commun, et le cas de mise en page ici.

**Le test de mise en page** compose le formulaire sur le simulateur iOS (Compose UI test, sans fenêtre), l'allure Android imposée. Il vérifie que la barre rouge borde le seul champ : le libellé n'est ni dedans ni décalé, et le champ se décale de 3 dp. Il tourne sur le simulateur que choisit Gradle, démarré puis arrêté par lui ; `-Pgeomys.simulateur` en impose un autre, comme pour le socle.

**Essais d'affichage**, jamais sur les téléphones :
- émulateur Pixel 10 :
  - Monitoring, détail, fiche d'un site, fiche d'une visite et taxons de ses observations ;
  - nouvelle visite : observateurs, garde de sortie, « Enregistrer et quitter » ;
  - chaîne d'observations : taxon proposé et retenu, effectif, distance, « Enregistrer + suivante », « Terminer », puis « Compléter la visite ? » et la visite en complétion ;
  - « + » du détail et de la fiche, fil d'Ariane, dialogue des photos ;
  - Mes visites : arbre, menu d'une ligne, flèche rouge vers la complétion, « Configuration requise », envoi d'un groupe sans serveur (récapitulatif, ligne en erreur) ;
  - cartes : protocole des mares, site du Col de Port, menus au toucher ;
- simulateur iPhone 17 Pro : les mêmes écrans, à l'allure iOS, dont la feuille des observateurs et le mode chaîne.

**Corrigé en cours de lot.**
- Comme au lot 6, Compose sautait les widgets du formulaire, dont l'état restait le même objet : un choix multiple validé ne s'affichait pas. Les composables qui lisent l'état sont marqués `@NonSkippableComposable`.
- Les dialogues d'un champ sont posés à la racine de l'écran : composés dans la ligne, ils s'y trouvaient enfermés.
- Sous Android, les champs nombre perdaient le drapeau « signé » de GeoMys-Android : les types de clavier de Compose ne le posent pas. Selon le clavier du téléphone, le signe moins pouvait manquer, pour une température négative par exemple. `ClavierNumeriqueSigne` le rétablit : l'émulateur reçoit le même `inputType` que GeoMys-Android et accepte « -3.5 ». Le correctif vaut aussi pour les champs additionnels d'OccTax, portés au lot 6 sans ce drapeau.

## 8. Critère de sortie : la recette terrain

À faire sur le Galaxy A12 et l'iPhone 16, avec la configuration et les données chargées aux lots 2 et 5, sur un protocole réel. L'utilisateur saisit lui-même ses identifiants ; aucune donnée d'essai n'est chargée sur les téléphones.

1. **Parcours.** Monitoring, un protocole, un site : fiche, carte, retour par le fil d'Ariane.
2. **Nouvelle visite** sur un site : dates et heures, listes, observateurs (l'utilisateur connecté présélectionné), photos ; enregistrement avec un champ obligatoire de fin laissé vide (« à compléter »).
3. **Observations** de cette visite, en chaîne : espèce proposée, effectif, « Enregistrer + suivante », puis « Terminer » et « Compléter la visite ».
4. **Mes visites** : modification, suppression d'une observation, « Envoyer ce groupe », « Tout envoyer » ; une visite incomplète renvoyée vers son formulaire.
5. **Côté serveur** : visites, observations et photos dans GeoNature, sans doublon.
6. **Carte** d'un protocole à polygones, si le serveur en a.
7. **Points à éprouver** : les corrections des défauts n° 14 et 15 (§ 5, reportées au lot 8) ; le signe moins et la virgule des champs nombre, sur le clavier Samsung et sous iOS ; un formulaire hors ligne (datalists du cache, options « non rechargées »).

## 9. Reste à faire

- **La fin de la recette terrain** (§ 8), avec celle du lot 6, et les premiers envois réels des Suivis depuis les écrans.
- **À trancher** : la mort du processus sous Android. Les défauts n° 14 et 15 sont corrigés (§ 5) ; ceux du lot 6 et le n° 11, au lot 8 ([`LOT-8.md`](LOT-8.md) § 5).
- **Lots 8 et 9** : OccHab ([`LOT-8.md`](LOT-8.md)), l'Explorer et les cartes.

## 10. Outils

- **Dépendance nouvelle** (tests seulement) : `org.jetbrains.compose.ui:ui-test` pour le jeu `iosTest` du module `ui`. `outils/verifier.sh` lance aussi `:ui:iosSimulatorArm64Test`.
- **Ressources** : `ic_eye`, reprise de GeoMys-Android.
- **Paramètres de lancement** (essais d'affichage) :
  - `action=monitoring-essai` (§ 6), avec `action=taxref-essai` avant ;
  - `ecran=` : `suivis`, `suivi-detail`, `fiche-objet`, `nouvelle-visite`, `nouvelle-visite-observateurs` (choix des observateurs ouvert), `nouvelle-observation` (chaîne de saisies), `mes-visites`, `carte-site`, `carte-mares`.
