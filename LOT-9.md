# Lot 9 : Explorer, Maps Manager, cartes MBTiles

*19 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, dont aucun commit suivant ne touche ces écrans. Allure iOS reprise de GeoMys-IOS `5fda84c`. La logique est dans `core`, les écrans dans `ui`.*

Le lot 9 porte les derniers écrans de GeoMys-Android (`INVENTAIRE.md` § 10) :
- l'**Explorer** : les observations de la synthèse GeoNature sur la zone visible, regroupées en cellules d'environ 110 m, filtrées par groupe taxonomique, et la liste d'une cellule ;
- le **Maps Manager** : la zone visible d'un fond téléchargée pour le hors-ligne, l'occupation et le vidage du cache, le ciblage des sites d'un protocole ;
- les **cartes MBTiles** : import, liste et suppression, et leur choix comme fond sur tous les écrans-cartes ;
- l'**attribution des fonds**, que ni GeoMys-Android ni GeoMys-IOS n'affichaient (défaut n° 8).

Les écrans suivent la décision 15 ([`LOT-5.md`](LOT-5.md) § 2) : l'allure de GeoMys-Android sous Android, celle de GeoMys-IOS sous iOS ; les éléments, les textes et les comportements de GeoMys-Android sur les deux.

**État.** Tout le contenu du lot est porté. À la demande de l'utilisateur, tous les fonds se téléchargent, OpenStreetMap compris, et un téléchargement s'annule (§ 5). La mise à jour intégrée par GitHub (`MiseAJour`) attend le lot 10, puisqu'elle dépend du canal de distribution (décision 14). L'écran d'attente des lots à venir disparaît : plus aucune entrée de l'accueil n'y mène. Les tests du socle passent de 987 à 1 016 sur la JVM et de 977 à 1 010 sur le simulateur iOS ; ceux des écrans de 29 à 55 sur la JVM (§ 7).

**Critère de sortie** (recette sur les téléphones) : **à faire**. Les écrans ont été essayés sur l'émulateur (Pixel 10) et sur le simulateur (iPhone 17 Pro), avec des données d'essai (§ 6).

## 1. Ce qui est porté

| GeoMys-Android | GeoMys-IOS (`5fda84c`) | GeoMys Common |
|---|---|---|
| `ExplorerFragment.kt`, `fragment_explorer.xml`, `bottom_sheet_obs_explorer.xml`, `item_obs_explorer.xml` | `ExplorerView.swift`, `TaxonMarker` (`Map/MapView.swift`) | `ui/…/explorer/ExplorerScreen.kt` et `ExplorerViewModel.kt` ; `core/…/explorer/Explorer.kt` |
| `CacheManagerFragment.kt`, `fragment_cache_manager.xml` | `CacheManagerView.swift`, `CartesMbtilesView.swift` | `ui/…/cartes/MapsManagerScreen.kt` et `MapsManagerViewModel.kt` ; `core/…/carte/ZoneHorsLigne.kt`, `CiblageProtocole.kt`, `core/…/geo/TuileMath.kt` |
| téléchargement (`CacheManager` d'osmdroid), `store/MapTileCache.kt` | `TelechargeurTuiles`, `MapTileCache.swift` | `ui/…/carte/ZonesHorsLigne.kt` : les paquets hors ligne et le cache de MapLibre |
| `store/MbtilesStore.kt` | `MbtilesStore.swift` | `core/…/carte/CartesMbtiles.kt`, `LecteurMbtilesSqlite.kt` (SQLite du système, par androidx.sqlite) |
| `MapUtils.kt` : `appliquerFond` (une carte MBTiles), `choisirFondCarte` (les cartes importées dans le menu) | `menuFondCarte` | `ui/…/carte/FondsCartes.kt` : `rememberStyleFond`, `DialogueChoixFond`, `AttributionFond` |
| `ActivityResultContracts.OpenDocument` | `fileImporter` | `rememberSelecteurCarte` (`ui/…/composants/Fichiers.kt`) |
| `CompassView` passive de l'Explorer | `BoussoleBouton(actif: false)` | `rememberCapAppareil` (`ui/…/carte/Boussole.kt`) |
| `android.app.AlertDialog`, `DialogueProgression` (Maps Manager) | — | `DialogueSystemeAndroid` (`ui/…/composants`, § 2) |
| `nav_graph.xml` : l'Explorer et le Maps Manager | — | `ui/…/navigation/NavigationGeoMys.kt` (§ 3) |

La requête de la synthèse (`GeoNatureBrowse.recupererObsExplorer`) et la profondeur d'historique du serveur viennent du lot 2.

## 2. Deux allures

| Élément | Android | iOS |
|---|---|---|
| Barre des groupes (Explorer) | flottante à 96 dp du haut ; dix `MaterialButton` de 88 × 48 dp au moins (quatre et demi visibles), icône de 24 dp | sous le bandeau ; boutons de 36 pt espacés de 8, icône de 22 pt |
| Marqueurs | disque de 42 dp moins 2 px ; effectif de 21, 17 ou 13 dp (moins de 10, de 100, au-delà) | disque plein de 42 pt ; effectif de 17, 13 ou 11 pt (selon le nombre de chiffres) |
| Compteur et messages | compteur à 12 dp du bas, chargement et messages à 32 dp | une seule pastille, au-dessus de la rangée des boutons |
| Liste d'une cellule | `BottomSheetDialog` de Material 3 (coins de 28 dp, `surfaceContainerLow`) | feuille à mi-hauteur (`.medium`), sa poignée, liste simple à filets |
| Panneau du Maps Manager | `bg_accueil` ; « Charger les tuiles » en `Button` de Material 3, « Cartes hors-ligne (MBTiles)… » en bouton texte blanc | dégradé ; bouton bleu aux coins de 10, estompé à 35 % s'il est inactif ; lien de 14 pt demi-gras |
| Corbeille du cache | disque blanc de 48 dp, `ic_delete` #C62828 | disque blanc de 44 pt, symbole SF `trash` rouge foncé |
| Dialogues du Maps Manager | `AlertDialog` du **système** : Android le dessine lui-même (boutons orange en capitales, coins du système) ; progression avec le `LinearProgressIndicator` de Material 3, et « ANNULER » pendant le téléchargement | alertes du système ; feuille d'actions pour les protocoles ; `DialogueMaterial` pour les cartes MBTiles et le téléchargement (barre verte, `colorSecondary`, « Annuler » en blanc 70 %) ; voile sombre pour l'import |
| Sites d'un protocole ciblé | orange, traits de 4 et 6 px, `ic_location_pin` teinté ; leur nom en bulle au toucher | orange, traits de 4 et 6 pt, goutte orange ; leur nom en bulle au toucher |
| Retour | bouton ou geste Retour | balayage depuis le bord désactivé, comme dans GeoMys-IOS : la maison du bandeau ramène à l'accueil |

**Le dialogue du système.** Le Maps Manager est le seul écran de GeoMys-Android à employer `android.app.AlertDialog`, et non celui d'AppCompat que les autres écrans reproduisent en Compose. `DialogueSystemeAndroid` montre donc un vrai `AlertDialog` du système, construit comme dans `CacheManagerFragment` (liste à corbeilles, `DialogueProgression`). Pour qu'il ait les couleurs de GeoMys-Android, le thème de l'activité reçoit celles que le thème Material 3 y pose (`textColorPrimary`, `textColorSecondary`, `textColorAlertDialogListItem`, voile à 32 %). Les `DatePickerDialog` et `TimePickerDialog` des détails d'un relevé, qui les lisent aussi, s'en rapprochent de l'original.

**Composants nouveaux ou étendus** : `DialogueSystemeAndroid` ; `rememberSelecteurCarte` (le fichier choisi, lu en flux, avec son nom) ; `CarteGeoMys` gagne `bornesZoom` ; `rememberCapAppareil` ; `collectionGeometries` (sortie de la carte des Suivis, pour servir aussi au Maps Manager).

## 3. Navigation

- **Routes typées.** `RouteExplorer(essai)` et `RouteMapsManager(essai)`, ouvertes par le menu de l'accueil. `RouteAVenir` et l'écran d'attente sont retirés.
- **Fond de carte.** Les cinq écrans-cartes des lots 6 à 8 (carte multi-taxons, saisie mono-taxon, détail d'une saisie, carte OccHab, carte des Suivis) proposent maintenant les cartes importées dans leur menu « Fond de carte », comme l'Explorer et le Maps Manager. Une carte importée disparue cède la place à OpenStreetMap.

## 4. La logique sort des écrans

- `Explorer` : la garde de largeur (100 km), le regroupement par cellule de 0,001°, le filtre, le compteur et sa durée, le titre et le détail d'une observation, les textes ;
- `ZoneHorsLigne` : l'estimation de la zone visible (surface, niveaux, tuiles, poids), ses plafonds, l'occupation du cache et les textes du Maps Manager ; `TuileMath` : tuiles et surface d'une emprise ;
- `CartesMbtiles` : l'import validé avant de remplacer une carte de même nom, la liste, le nom affiché, les métadonnées lues une fois ;
- `CiblageProtocole` : les sites d'un protocole, leurs géométries et leur emprise.

Les ViewModels gardent l'état des écrans : requêtes de l'Explorer (délai de 800 ms après le dernier mouvement, la précédente abandonnée), dialogues et téléchargement du Maps Manager.

**Les niveaux de zoom.** MapLibre zoome en tuiles de 512 px : son zoom vaut le niveau de tuile de 256 px d'osmdroid moins un. Le panneau du Maps Manager compte en niveaux de tuiles, comme GeoMys-Android : au zoom de carte 14, il annonce « zooms 15–17 », et le paquet téléchargé couvre bien les niveaux 15 à 17. Le départ, le recadrage sur la position et les bornes de la carte du Maps Manager sont pris un cran plus bas (zooms de carte 11, 14, et 1 à 16), pour que le panneau affiche les mêmes niveaux qu'Android aux mêmes moments.

## 5. Écarts assumés

**Demandes de l'utilisateur (19 octobre) :**
- **tous les fonds se téléchargent par zones, OpenStreetMap compris**, comme sous GeoMys-Android : la décision 13 de l'inventaire (exclure OSM, que sa politique d'usage interdit de télécharger en masse) est écartée. Seule une carte importée n'a rien à télécharger ;
- **un téléchargement s'annule** : « Annuler » dans son dialogue l'arrête (GeoMys-Android ne le permettait pas). Retour et le voile restent sans effet, pour ne pas le perdre par mégarde. Le paquet de MapLibre est mis en pause : les tuiles déjà reçues restent en cache, et « Téléchargement annulé : les tuiles reçues restent en cache. » le dit.

**Défauts d'Android corrigés** (inventaire, § 8.2 ; décision 5) :
- **l'attribution des fonds s'affiche** (n° 8) : une ligne discrète au bas de chaque carte, entre les boutons, au-dessus des panneaux (« © OpenStreetMap contributors », « © OpenTopoMap (CC-BY-SA) · © OpenStreetMap contributors », « © IGN », la mention d'Esri). Rien pour une carte importée ;
- **la profondeur d'historique** (n° 10) : le compteur de l'Explorer et le titre d'une liste disent celle du serveur (`area_observation_duration`), « 12 mois » pour les 365 jours par défaut, et non « 12 mois » en dur.

**Le téléchargement passe par MapLibre** (paquets hors ligne, au lieu du `CacheManager` d'osmdroid) :
- la progression dit « 120 / 380 » (ressources reçues sur attendues) : MapLibre ne détaille pas les niveaux (« Zoom 15 · 120 / 380 » sous Android) ;
- une zone téléchargée n'expire pas ; seul « Vider le cache » la supprime, avec le cache des tuiles vues en ligne. Le cache d'osmdroid se purgeait au-delà de 800 Mo ;
- l'occupation compte les zones téléchargées, sur le même budget affiché d'1 Go ;
- au-delà du nombre de tuiles que MapLibre accepte, « Téléchargement incomplet » le dit (cas nouveau).

**Choix du portage :**
- **Explorer.** Pendant un chargement, la pastille « Chargement des observations… » remplace un message devenu périmé ; sous Android, le message restait affiché par-dessus elle. Le compteur s'efface tant qu'un message d'erreur est affiché ; sous Android, ils se chevauchaient.
- **Cartes MBTiles.** Une table `tiles` qui est une vue (schéma de mbutil et de MOBAC) est acceptée ; Android exigeait une table.
- **Sites d'un protocole.** Leur nom s'affiche dans la bulle des relevés de l'appli (sombre sous Android), et non dans la bulle blanche par défaut d'osmdroid.

**Comportements d'Android sous iOS :**
- le téléchargement se suit dans un dialogue que seul « Annuler » ferme, puis son issue s'affiche en alerte ; GeoMys-IOS les montrait dans le panneau ;
- une carte importée ne se choisit pas en touchant sa ligne : on la choisit dans « Fond de carte », comme sous Android ;
- les textes d'Android, dont le message du vidage et ceux des cartes MBTiles.

## 6. Données et essais

- `ecran=explorer-essai` : l'Explorer près de Foix, sur des observations **fictives** générées autour du centre de la zone demandée (dix cellules, une par groupe, de 1 à 150 observations), sans serveur ;
- `ecran=maps-manager`, avec `action=cartes-essai` : la carte MBTiles de test du prototype (tuiles colorées autour de Foix, zooms 10 à 16) importée comme par le sélecteur de fichiers ;
- `ecran=maps-cartes` : la liste des cartes MBTiles ouverte ; `ecran=maps-telechargement` : un téléchargement **simulé**, sans réseau, arrêté à 40 %, que « Annuler » interrompt.

Émulateur et simulateur seulement, jamais sur les téléphones.

## 7. Tests

**Socle : 29 cas de plus** sur la JVM, **33** sur le simulateur iOS. **Écrans : 26 cas de plus**, sur la JVM.

| Origine | Cas | Fichiers |
|---|---|---|
| Portés de GeoMys-Android | 5 | `CartesMbtilesSqliteTest` (3, iosTest : les cas de `MbtilesStoreTest`, sur le vrai SQLite du simulateur) ; `CartesMbtilesTest` (2, qui réécrivent sur le vrai système de fichiers les trois cas MBTiles de `DefautsPariteLot2Test`) |
| Écrits pour le portage, socle | 28 | `TuileMathTest` (6), `ZoneHorsLigneTest` (8, dont tous les fonds téléchargeables), `CartesMbtilesTest` (4), `CiblageProtocoleTest` (3), `ExplorerTest` (6, dont la durée du serveur), `CartesMbtilesSqliteTest` (1, iosTest : la table `tiles` en vue) |
| Écrits pour le portage, écrans | 26 | `ExplorerViewModelTest` (10) et `MapsManagerViewModelTest` (16, dont l'annulation), sur les vrais services, avec une source d'observations, des paquets MapLibre et un lecteur MBTiles factices |

GeoMys-Android n'a aucun test de ces deux écrans. Les tests des écrans ont été éprouvés par onze mutations du code, toutes détectées, dont une réponse de la synthèse arrivée sans attente et trois sur l'annulation.

**Essais d'affichage**, jamais sur les téléphones :
- émulateur Pixel 10 : Explorer (marqueurs, filtre, liste d'une cellule) ; Maps Manager (zone trop large, liste des cartes MBTiles en dialogue du système, téléchargement simulé puis annulé, ciblage du protocole des mares d'essai, bulle d'un site touché) ; l'attribution sur les sept écrans-cartes ;
- simulateur iPhone 17 Pro : les mêmes écrans, la liste des cartes MBTiles et le téléchargement (avec « Annuler ») dans leur `DialogueMaterial` ; l'attribution sur les sept écrans-cartes.

**Corrigé en cours de lot.** Sur l'émulateur, l'essai de l'Explorer plantait : la réponse d'essai, rendue sans attente, terminait la requête avant que `launch` ne rende la main, sur le répartiteur principal immédiat de l'appli. La requête la plus récente se reconnaît maintenant à son numéro (`ExplorerViewModelTest`).

## 8. Critère de sortie : la recette sur les téléphones

À faire sur le Galaxy A12 et l'iPhone 16, avec la configuration et les données chargées aux lots 2 et 5. L'utilisateur saisit lui-même ses identifiants ; aucune donnée d'essai n'est chargée sur les téléphones.

1. **Explorer** : observations de la zone, compteur et durée du serveur, filtre par groupe, liste d'une cellule, « Zoomez… » au-delà de 100 km, recentrage, boussole, fond.
2. **Maps Manager** : estimation (surface, niveaux, tuiles), téléchargement d'une petite zone (OSM compris), puis la carte de cette zone **en mode avion** sur un écran de saisie ; un téléchargement annulé en cours de route ; « Vider le cache ».
3. **Cartes MBTiles** : import d'un fichier `.mbtiles` depuis le téléphone, choix comme fond sur un écran de saisie, hors ligne, puis suppression (le fond repasse sur OSM).
4. **Cibler un protocole** : cadrage sur ses sites, nom d'un site touché, téléchargement de la zone.
5. **Partout** : l'attribution au bas des cartes, sans gêner les boutons ; sous Android, l'allure des dialogues du Maps Manager face à GeoMys-Android.

## 9. Reste à faire

- **La recette** (§ 8), avec celles des lots 6 à 8.
- **Lot 10** : la distribution, et avec elle la mise à jour intégrée (`MiseAJour`, pour un APK distribué hors Play).

## 10. Outils

- **Paramètres de lancement** (essais d'affichage) : `ecran=explorer`, `explorer-essai`, `maps-manager`, `maps-cartes`, `maps-telechargement` ; `action=cartes-essai` (§ 6).

## 11. Décisions du 20 octobre

Prises par l'utilisateur après le commit du lot, sur les divergences du § 8.3 de l'inventaire :
- **Liste des protocoles : le cache d'abord**, comme GeoMys-IOS. L'écran des Suivis et « Cibler un protocole » du Maps Manager lisent la liste que la dernière synchro a mise en cache, sans requête. GeoMys-Android l'interrogeait à chaque ouverture ; « Charger les données » la rafraîchit (`MonitoringModules.chargerModulesDepuisCache`, un cas de plus dans `ChargerModulesTest`). Le repli sur le serveur avant toute synchro a disparu avec la décision suivante.
- **Jeu de données et `id_digitiser` des formulaires des Suivis : GeoMys-Android fait référence**, ce que GeoMys Common faisait déjà : un jeu de données seul est choisi d'office, sinon l'utilisateur choisit ; `id_digitiser`, le compte connecté, est posé à l'envoi.
- **Boussole : le nord magnétique**, comme GeoMys-Android. Sous Android, le cap de maplibre-compose l'était déjà (capteur de rotation, sans déclinaison). Sous iOS, il passait au nord géographique dès que la position était connue (`CLHeading.trueHeading`), contrairement à ce qu'annonçait le lot 6 : la boussole et la carte qui suit le cap lisent maintenant `CLHeading.magneticHeading` (`rememberFournisseurCap`). L'écart, la déclinaison, est d'environ 1 à 2° en Ariège ; il reste à constater sur l'iPhone 16, puisque le simulateur n'a pas de compas.

- **Rien n'est téléchargé hors synchro** (« le cache d'abord. De façon générale, rien ne doit être téléchargé du serveur geonature et tout doit être pris dans le cache. Si des modifs ont été faites sur le serveur, ce sera pris en compte à la prochaine synchro. Si des modifs sont faites dans l'appli, elles doivent être répercutées dans le cache. ») — prise sur la lenteur d'ouverture d'un protocole, qui redemandait au serveur tous ses sites, son schéma et les listes de libellés. Les écrans lisent le cache que remplit la synchro, et rien d'autre ; cache vide, un message invite à « Recharger les données ».
  - **Suivis** : chaque chargement a sa lecture du cache (`lireEnfants`, `lireObjet`, `lireSchemaProtocole`, `lireOptionsDatalist`, `lireResolveurLabels`, `chargerModulesDepuisCache`), et le téléchargement (`charger…`) reste à la synchro. Le résolveur des libellés lit les listes que la synchro met déjà en cache (observateurs, options des nomenclatures et des jeux de données des formulaires, jeux OccTax et OccHab) au lieu de demander les nomenclatures et tous les jeux de l'instance. La synchro met en plus en cache les jeux de données de chaque protocole (`keyDatasetsModule` : liste taxonomique d'une nouvelle saisie, jeu d'une visite envoyée) et les listes d'observateurs propres à un champ. Les pictos ne sont plus téléchargés à l'affichage, d'où la fin de leur cache négatif.
  - **Historique** : la synchro ne charge que les objets structurels (sites, groupes), pas les visites passées (accord de l'utilisateur). La fiche d'une visite absente du cache est reconstituée depuis sa ligne chez son site (`lireObjetDepuisParent`, `MonitoringObjet.enfantsConnus`). Seule exception à la règle, à la demande de l'utilisateur (« quand on va dans une visite et s'il y a du réseau, télécharger les infos ») : la fiche d'une saisie (visite, observation : `MonitoringSync.estSaisie`) s'affiche depuis le cache, puis se met à jour depuis le serveur s'il répond, et reste en cache ; hors réseau, une note invite à la rouvrir avec du réseau (`FicheObjetViewModel`, `FicheVisiteEnLigneTest`). Les fiches de visites mises en cache par la navigation des versions précédentes sont ainsi rafraîchies à leur ouverture.
  - **Report des envois** : une visite ou une observation envoyée est écrite dans le cache, sa fiche et sa ligne sous son parent (`MonitoringObjets.reporterObjetCree`, appelé par `OutboxEnvoi`) ; une station OccHab l'était déjà (`StationsServeurCache.upsert`).
  - **OccHab** : la carte lit les stations du serveur dans le cache de la synchro (le message en donne la date). Les altitudes du MNT (`/api/geo/info`) ne sont plus demandées au dessin mais à l'envoi, pour la station qui n'en a aucune (`EnvoiOccHab.avecAltitudes`) ; un changement de géométrie efface les précédentes.
  - **Habitats** : la recherche ne lit que le cache HABREF de la synchro (`HabitatService`), sans repli sur le serveur.
  - **Restent en ligne** : la synchro, les envois et leurs contrôles anti-doublon, l'Explorer (la synthèse autour de soi, par nature), et dans Paramètres le test de connexion et la version de TaxHub.
  - **Lenteur** : la liste d'un protocole se prépare hors du fil de l'interface, et le schéma n'est plus analysé à chaque ligne pour le libellé « Nouvelle visite » (2 s pour 300 sites sous iOS, mesuré le 20 octobre).

Tests : 1 025 sur la JVM (dont `LectureCacheSuivisTest`), 55 tests d'écran sur la JVM et 4 sur le simulateur.
