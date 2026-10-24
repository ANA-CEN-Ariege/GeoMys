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

| GeoMys-Android | GeoMys-IOS (`5fda84c`) | GeoMys |
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
- **Lot 10** : la distribution. La mise à jour intégrée hors Google Play (`MiseAJour` de la variante GitHub de GeoMys-Android) n'est pas reprise (décision du 21 octobre, § 18).

## 10. Outils

- **Paramètres de lancement** (essais d'affichage) : `ecran=explorer`, `explorer-essai`, `maps-manager`, `maps-cartes`, `maps-telechargement` ; `action=cartes-essai` (§ 6).

## 11. Décisions du 20 octobre

Prises par l'utilisateur après le commit du lot, sur les divergences du § 8.3 de l'inventaire :
- **Liste des protocoles : le cache d'abord**, comme GeoMys-IOS. L'écran des Suivis et « Cibler un protocole » du Maps Manager lisent la liste que la dernière synchro a mise en cache, sans requête. GeoMys-Android l'interrogeait à chaque ouverture ; « Charger les données » la rafraîchit (`MonitoringModules.chargerModulesDepuisCache`, un cas de plus dans `ChargerModulesTest`). Le repli sur le serveur avant toute synchro a disparu avec la décision suivante.
- **Jeu de données et `id_digitiser` des formulaires des Suivis : GeoMys-Android fait référence**, ce que GeoMys faisait déjà : un jeu de données seul est choisi d'office, sinon l'utilisateur choisit ; `id_digitiser`, le compte connecté, est posé à l'envoi.
- **Boussole : le nord magnétique**, comme GeoMys-Android. Sous Android, le cap de maplibre-compose l'était déjà (capteur de rotation, sans déclinaison). Sous iOS, il passait au nord géographique dès que la position était connue (`CLHeading.trueHeading`), contrairement à ce qu'annonçait le lot 6 : la boussole et la carte qui suit le cap lisent maintenant `CLHeading.magneticHeading` (`rememberFournisseurCap`). L'écart, la déclinaison, est d'environ 1 à 2° en Ariège ; il reste à constater sur l'iPhone 16, puisque le simulateur n'a pas de compas.

- **Rien n'est téléchargé hors synchro** (« le cache d'abord. De façon générale, rien ne doit être téléchargé du serveur geonature et tout doit être pris dans le cache. Si des modifs ont été faites sur le serveur, ce sera pris en compte à la prochaine synchro. Si des modifs sont faites dans l'appli, elles doivent être répercutées dans le cache. ») — prise sur la lenteur d'ouverture d'un protocole, qui redemandait au serveur tous ses sites, son schéma et les listes de libellés. Les écrans lisent le cache que remplit la synchro, et rien d'autre ; cache vide, un message invite à « Recharger les données ».
  - **Suivis** : chaque chargement a sa lecture du cache (`lireEnfants`, `lireObjet`, `lireSchemaProtocole`, `lireOptionsDatalist`, `lireResolveurLabels`, `chargerModulesDepuisCache`), et le téléchargement (`charger…`) reste à la synchro. Le résolveur des libellés lit les listes que la synchro met déjà en cache (observateurs, options des nomenclatures et des jeux de données des formulaires, jeux OccTax et OccHab) au lieu de demander les nomenclatures et tous les jeux de l'instance. La synchro met en plus en cache les jeux de données de chaque protocole (`keyDatasetsModule` : liste taxonomique d'une nouvelle saisie, jeu d'une visite envoyée) et les listes d'observateurs propres à un champ. Les pictos ne sont plus téléchargés à l'affichage, d'où la fin de leur cache négatif.
  - **Historique** : la synchro ne charge que les objets structurels (sites, groupes), pas les visites passées (accord de l'utilisateur). La fiche d'une visite absente du cache est reconstituée depuis sa ligne chez son site (`lireObjetDepuisParent`, `MonitoringObjet.enfantsConnus`). Seule exception à la règle, à la demande de l'utilisateur (« quand on va dans une visite et s'il y a du réseau, télécharger les infos ») : la fiche d'une saisie (visite, observation : `MonitoringSync.estSaisie`) s'affiche depuis le cache, puis se met à jour depuis le serveur s'il répond, et reste en cache ; hors réseau, une note invite à la rouvrir avec du réseau (`FicheObjetViewModel`, `FicheVisiteEnLigneTest`). Les fiches de visites mises en cache par la navigation des versions précédentes sont ainsi rafraîchies à leur ouverture.
  - **Report des envois** : une visite ou une observation envoyée est écrite dans le cache, sa fiche et sa ligne sous son parent (`MonitoringObjets.reporterObjetCree`, appelé par `OutboxEnvoi`) ; une station OccHab l'était déjà (`StationsServeurCache.upsert`).
  - **OccHab** : la carte lit les stations du serveur dans le cache de la synchro (le message en donne la date). Les altitudes du MNT (`/api/geo/info`) ne sont plus demandées au dessin mais à l'envoi, pour la station qui n'en a aucune (`EnvoiOccHab.avecAltitudes`) ; un changement de géométrie efface les précédentes.
  - **Habitats** : la recherche ne lit que le cache HABREF de la synchro (`HabitatService`), sans repli sur le serveur.
  - **Restent en ligne** : la synchro, les envois et leurs contrôles anti-doublon, l'Explorer (la synthèse autour de soi, par nature), et dans Paramètres le test de connexion et la version de TaxHub.
  - **Lenteur** : la liste d'un protocole se prépare hors du fil de l'interface, et le schéma n'est plus analysé à chaque ligne pour le libellé « Nouvelle visite » (2 s pour 300 sites sous iOS, mesuré le 20 octobre).
- **Topologie partagée de la carte OccHab** (« pour un polygone d'une station avec un sommet qui est aimanté sur le sommet d'une autre station, si je déplace ce sommet, le sommet de l'autre station doit suivre. Du coup, l'autre station doit aussi être mise à jour sur le serveur. De même, si une arête est commune à 2 stations et que j'ajoute un sommet sur cette arête, le même sommet doit être ajouté aux 2 stations ») : celle de GeoMys-Android du 30 août (v1.3.14), retirée le 18 octobre (cdd5196), revient. Au relâcher, les sommets des voisines confondus avec le sommet déplacé le suivent ; un sommet inséré sur une arête commune s'ajoute aux deux contours (`TopologiePolygone`, `CarteOccHab.voisineDeplacee`, `CarteOccHabViewModel.propagerAuxVoisines`). Elle s'étend aux stations de toutes les saisies et du serveur, et non plus de la seule saisie : une voisine du serveur sans copie locale est importée dans la saisie courante ; une voisine déjà envoyée repasse « à envoyer », en mise à jour ; celles d'autres saisies partent avec l'envoi de la saisie courante (`SessionOccHab.voisinesAEnvoyer`, `EnvoiOccHab` limité à ces stations). Contours extérieurs seulement, comme sous Android ; une voisine en plusieurs parties n'est pas touchée, avec un message. « Annuler » ne défait toujours que la station éditée (décision du 17 octobre).

Tests : 1 025 sur la JVM (dont `LectureCacheSuivisTest`), 55 tests d'écran sur la JVM et 4 sur le simulateur.

## 12. Corrections après l'audit du 20 octobre

Audit complet de l'appli le 20 octobre (`AUDIT-2026-10-20.md`, fichier local que git ignore). Corrigés le même jour, à la demande de l'utilisateur :
- **« Centrer » coupait le suivi GPS sous Android** (C1, hérité de GeoMys-Android).
  - La mesure ponctuelle prenait l'écouteur du suivi continu, et Android remplaçait l'un par l'autre. La trace et la position se figeaient ; en mono-taxon, les observations suivantes partaient à la dernière position reçue.
  - La mesure ponctuelle a maintenant son propre écouteur (`SourcePositionsAndroid`). Défaut et correction constatés sur l'émulateur, avec `dumpsys location`.
  - Sous iOS, `requestLocation()` ne fait rien quand le suivi tourne (documentation d'Apple) : rien à corriger.
- **Mise à jour d'une station OccHab fusionnée avec le serveur** (C2).
  - Le défaut : une station importée, rééditée ou voisine déplacée par la topologie partagée repartait avec la version de la dernière synchro. Le serveur supprimait alors un habitat ajouté sur le web entre-temps.
  - La station garde sa version d'origine (`OccHabStation.origineJson`), figée à l'import, à la remise en édition et au déplacement d'une voisine.
  - À l'envoi, `OccHabUpload` relit la station sur le serveur (`GET /stations/<id>/`). `FusionStation` garde ce que l'appli a changé ; le reste vient du serveur. Une voisine ne renvoie ainsi que sa géométrie.
  - Une station supprimée sur le web n'est pas recréée.
  - Après un envoi, les habitats prennent les ids que le serveur leur a donnés (`avecIdsHabitats`), et la version partie devient la copie locale et l'état du cache.
- **Sauvegardes iOS** (M1).
  - Le défaut : l'exclusion des caches HABREF et des stations du serveur visait la racine des données. Elle retirait aussi des sauvegardes de l'iPhone les saisies non envoyées et leurs médias.
  - Ces caches vont désormais dans `filesDir/caches`, seul dossier exclu. Au lancement, la racine est rendue aux sauvegardes, et les fichiers des installations existantes sont rangés dans `caches`.
  - Sous iOS, les cartes MBTiles importées sont exclues, comme sous Android et dans GeoMys-IOS.
  - Sous Android, `caches` rejoint les exclusions des règles de sauvegarde. La base de MapLibre est dans le dossier cache, qu'Android ne sauvegarde pas.
- **Envois iOS gardés de la mise en veille** (M6). Les trois envois (OccTax, Suivis, OccHab) passent par `ExecutionLongue.pendant`, une tâche d'arrière-plan sous iOS, comme dans GeoMys-IOS. Rien ne change sous Android, comme dans GeoMys-Android.
- **Gardes de sortie des formulaires** (M9, M11).
  - Écrans concernés : caractérisation, dénombrement, habitat OccHab, Détails intercalés.
  - Modifiés, ces écrans demandent « Quitter sans enregistrer ? » au retour système, au balayage depuis le bord et à la maison (`rememberGardeSortie`). Intacts, ils se quittent aussitôt.
  - Le fil d'Ariane et la maison du formulaire d'une visite passent par sa garde (`allerVers`). « Enregistrer et quitter » mène au niveau choisi.
  - Les médias retirés d'un dénombrement ne s'effacent qu'à sa validation. Ceux importés s'effacent si l'on quitte sans enregistrer.
  - Les Détails du relevé s'enregistrent dès leur validation.
  - « Vider le cache » demande confirmation.
  - Sous iOS, ces écrans acceptent de nouveau le balayage, désormais gardé ; GeoMys-IOS l'avalait.
- **Android 7** (M18).
  - Désucrage des bibliothèques Java (`desugar_jdk_libs` 2.1.5), dont kotlinx-datetime a besoin pour `java.time`.
  - Garde de version de la vibration.
  - Vérifié dans le code Dalvik de l'APK et par Lint ; faute d'appareil Android 7, pas d'essai réel.

## 13. Vérification des champs proposés (20 octobre)

Demande de l'utilisateur : « verifie que tous les champs proposés dans les saisies et dans monitoring soient exactement ceux fournis ar le serveur et/ou lié au protocle selectionné ». Référence : le formulaire web de GeoNature 2.17.5 (version du serveur de l'ANA), gn_module_monitoring 1.3.2 et pypnnomenclature 1.6.6. Presque tous les écarts venaient de GeoMys-Android.

- **Suivis.**
  - Champs : ceux du schéma qui ont un `type_widget`, specific puis generic, `medias` et `types_site` à la fin, puis l'ordre de `display_properties` (`ordreDesChamps`). Plus de liste de champs exclus par leur nom (`observers_txt`, `nb_observations` redéfinis visibles par un protocole sont proposés) ; un widget inconnu est écarté et signalé, plus saisi en texte.
  - Listes : restriction `cd_nomenclatures`, `params` de la requête, `filters` sur toute clé dans leur ordre, `orderBy`, libellé `label_fr` des nomenclatures, `code_list` des observateurs. Une liste vide reçue du serveur fait foi : plus de jeux de données sans le filtre `create`, ni de jeux OccTax du module.
  - Valeurs initiales du web : la `value` du schéma (cd_nom d'un champ espèce compris), l'option unique d'une liste ou d'un jeu de données requis (le champ reste affiché), le `default` d'une datalist seulement s'il porte la clé de valeur, la date du jour dans les seules dates de visite, l'utilisateur connecté dans le seul champ `observers`. Plus de date ni d'heure du moment dans les autres champs.
  - Les propriétés `hidden: true` partent avec leur `value` (`FormulairePrepare.valeursFixes`) : le cd_nom fixe d'un protocole mono-espèce, une nomenclature fixe résolue par le cache, le jeu de données unique requis.
  - Règles : une règle `change` qui pose un taxon le pose (POPAmphibien « Amphibiens ») ; `change` accepté en une chaîne ; une case à cocher vaut `false` une fois touchée ; `meta.dataset` (jeu de données masqué quand il n'y en a qu'un) ; comparaisons d'ordre à la JavaScript (un champ vide vaut 0).
  - Envoi : plus de jeu de données inventé (premier jeu du module, puis celui d'OccTax), qui partait aussi dans les données spécifiques d'une observation. La synchro ne met donc plus en cache les jeux de données de chaque protocole (`keyDatasetsModule`, § 11), qui ne servaient qu'à ces replis et à la liste taxonomique d'un jeu.
- **OccTax.**
  - Listes : plus de « Non renseigné » ajouté qui masquait la vraie valeur du serveur ; toutes ses valeurs, sans dédoublonnage ; une entrée vide distincte (« — Non renseigné — ») ; aucune liste de repli inventée. Les listes requises du web (technique d'observation, état biologique, objet du dénombrement, stade, sexe) bloquent la validation vides. Une valeur hors de la liste du taxon reste tant qu'on n'y touche pas.
  - Filtre taxonomique du serveur : règne, groupe 2 et groupe 3 réels du taxon, sur une même ligne ; une valeur sans ligne est exclue ; `ENABLE_NOMENCLATURE_TAXONOMIC_FILTERS` lu. Le groupe 3 des taxons est désormais en cache.
  - Champs additionnels : ceux du jeu de données du relevé (« Détails » les recalcule au changement de jeu), sans filtre par `id_list` ; dans l'ordre du serveur ; une nomenclature enregistre son `label_default` ; défaut = `default_value` seul ; `cd_nomenclatures`, `min` et `max`, `hidden` lus ; un bloc `html` n'est plus un champ ; des cases multiples requises en veulent une cochée. Une liste vide du serveur remplace le cache.
  - Preuves d'existence affichées et exigées seulement pour « Oui », adresse exigée (`digital_proof_validator`) ; médias selon `ENABLE_MEDIAS` ; effectif 0 permis ; heures envoyées seulement si le formulaire les montre ; technique d'observation vide = défaut du serveur (et non « Vu ») ; `form_fields` absent = défauts du schéma (10 champs masqués), et gardé si la synchro échoue ; profondeur max avec la profondeur min ; comportement seul pas ouvert si `behaviour` est masqué ; défauts de nomenclature gardés si leur appel échoue ; observateur par défaut à l'envoi celui que propose « Détails ».
- **OccHab.**
  - Champs ajoutés : profondeurs, exposition, type de sol (défaut du serveur). Les profondeurs s'ouvrent comme sur le web, par un « ＋ » au bout de la dernière altitude, offert si le serveur en montre une (`depth_min`, `depth_max`) ; le bloc est replié à chaque ouverture et garde ses valeurs, et le « ＋ » le referme.
  - Observateurs : texte libre si `OBSERVER_AS_TXT`, sinon la liste `OBSERVER_LIST_ID` (celle d'OccTax jusqu'à la synchro suivante).
  - « — Non renseigné — » vide la valeur d'un habitat ; une mise à jour envoie les valeurs vidées en null, comme le web (une création les omet, pour les défauts du serveur) ; une valeur absente de la liste en cache est gardée.
  - Défauts d'un nouvel habitat : type de détermination et technique de collecte seulement ; recouvrement masqué par défaut ; date de fin avant le début refusée ; jeu de données mémorisé repris seulement s'il est encore proposé.
- **Recharger les données** : `VERSION_DONNEES_REQUISE` passe à 2, pour que la synchro lise tout cela.
- **Gardé, contre le web** (décisions de la vérification) : un champ masqué par une condition n'est pas envoyé (audit du 14 septembre) ; `visit_date_max` copiée de `visit_date_min` ; les formats d'envoi ; observateurs OccHab en choix multiple ; listes de taxons des Paramètres ; libellés et ordre des blocs de GeoMys-Android ; défaut sans organisme ; un champ requis masqué ne bloque pas.
- **Non repris** : les widgets additionnels date, choix multiple, taxon et observateurs restent saisis en texte ; altitudes OccTax du MNT (réseau) ; clés `observers_txt`, `DATE_FORM_WITH_TODAY`, `EXPANDED_TAXON_ADVANCED_DETAILS`, `CD_TYPO_HABITAT`, `id_taxon_list` d'OccTax.

Tests : 1 062 sur la JVM et 1 058 sur le simulateur pour le module commun, 73 et 5 pour l'interface ; 17 mutations des nouvelles règles, plus 3 pour le « ＋ » des profondeurs, toutes détectées. Essayé sur l'émulateur (détails d'une station OccHab, caractérisation sans nomenclature en cache, nouvelle visite des Suivis) ; installé sur l'A12 et l'iPhone 16.

## 14. Alignement des formulaires sur le web (20 octobre)

Demandes de l'utilisateur : « Il faut respecter entierement ce que fournit le serveur », puis « supprime tous ces écarts pour s'aligner sur le web ». Les formulaires d'OccTax, d'OccHab et des Suivis suivent désormais le web (GeoNature 2.17.5, gn_module_monitoring 1.3.2) en tout : champs, libellés, ordre, valeurs, contrôles et envoi. L'allure reste celle de GeoMys-Android sous Android et de GeoMys-IOS sous iOS. Cette section remplace les lignes « Gardé, contre le web » et « Non repris » du § 13.

- **OccTax, relevé.**
  - Ordre et libellés du web : précision, nom du lieu, observateurs, « Jeux de données », « Date début » et son « ＋ » (date fin, « Heure début », « Heure fin » au format « hh:mm », facultatives), altitudes, profondeurs, regroupement, nature de l'objet, technique de collecte, habitat (« Tapez les premières lettres... », « Aucun élement trouvé », recherche à 400 ms, filtrée par `CD_TYPO_HABITAT`), commentaire, puis les champs additionnels sans titre. Plus de ligne « Géométrie ».
  - Contrôles du web : jeu de données et observateurs requis, dates et heures inversées, heure de début sans heure de fin, altitudes et profondeurs inversées (0 ne compte pas), nombres signés, dates du 1ᵉʳ janvier 1735 à aujourd'hui ; `date_min` masquée reste exigée.
  - Défauts du web : l'utilisateur connecté comme observateur (ou son nom complet, « NOM Prénom », en texte libre si `observers_txt`) et comme déterminateur ; défauts de nomenclature de son organisme (`defaultNomenclatures?organism=`, organisme et nom complet lus à la connexion).
  - Altitudes : celles du MNT du serveur (`/geo/altitude`) quand aucune n'est saisie, demandées à l'envoi (rien n'est téléchargé pendant la saisie) ; un relevé déplacé perd les siennes, que le web recalcule.
  - Liste de taxons d'un relevé : celle de son jeu de données, sinon `OCCTAX.id_taxon_list` (synchronisée), sinon toutes ; elle suit un changement de jeu dans « Détails ».
  - Erreur 409 d'un cadre d'acquisition fermé traduite (« Cadre d'acquisition fermé, action impossible »).
- **OccTax, espèce et dénombrement.**
  - Libellés du fr.json d'OccTax, orthographe comprise : « Technique de collecte du relevé (Campanule) », « Statut source », « Floutage », « Etat biologique », « Naturalite », « Preuve numérique », « Stade de vie » aussi pour une plante, « Dénombrement #N ». Section « Avancé » (ouverte d'emblée si `EXPANDED_TAXON_ADVANCED_DETAILS`), commentaire sans libellé.
  - Listes de nomenclatures : `label_fr`, triées par `label_default`, sans entrée vide (le libellé en invite, une croix vide le choix) ; statut d'observation, statut source et floutage non filtrés par taxon.
  - Messages du web : « Veuillez indiquer la technique d'observation », « … l'état biologique », « … l'objet du dénombrement », « … le stade de vie », « … le sexe », preuves (« Si 'preuve d'existence = Oui', … »), et « Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum » (plus de correction silencieuse des effectifs, requis).
  - Ordre du dénombrement : objet, type, effectifs, stade, sexe, champs additionnels, médias.
  - Taxon déjà dans le relevé : la confirmation du web (« Le taxon saisi est déjà dans la liste des taxons enregistrés… Voulez-vous continuer ? »).
  - « TAXREF - Nom valide » sous le taxon choisi si `display_nom_valide` (`nom_valide` mis en cache à la synchro).
- **Champs additionnels d'OccTax** (`ChampsAdditionnels`, `ChampsAdditionnelsVue`) : les widgets du formulaire dynamique du web (texte, zone de texte, nombre, date, heure, select avec « - », radio et bool_radio décochables, cases, bool_checkbox, multiselect, nomenclature, datalist, taxon, observateurs, observers-text, bloc html mis en forme) ; un widget que le web ne rend pas n'est pas montré, sa valeur par défaut part. Valeurs avec leur type JSON (`valeursBrutes`), `help` sous le champ, `definition` derrière « ⓘ », plus de « * » ni de description. Contrôles du web, champs masqués compris : requis, `pattern` et `pattern_message`, `max_length`, bornes. Champs globaux puis ceux du jeu de données. À l'envoi, chaque champ du formulaire part, avec sa valeur par défaut s'il n'a jamais été ouvert, `null` s'il est vide.
- **Paramètres** : plus de choix de la liste de taxons ni de l'observateur par défaut, devenus sans objet ; seul le jeu de données par défaut reste, parmi ceux que propose le web.
- **OccHab** : libellés et ordre du web, jeux de données (OCCHAB, créables), observateur unique effaçable, dates recopiées et bornées, nature requise, `label_fr`, entrée vide seulement pour l'intérêt communautaire et l'abondance, « N°. nom cité » des habitats, stations du serveur non modifiables (droits, cadre fermé) refusées avec le texte du web.
- **Suivis** : champs masqués envoyés et contrôlés ; une propriété masquée requise sans valeur (`id_dataset` de pyrales et suivi_nardaie avec plusieurs jeux) bloque l'enregistrement, comme sur le web ; options avec leur type JSON ; dates `aaaa-m-j` bornées ; vides du web (« "" » pour un texte ou une heure vidés, « [] » pour une liste vidée, sans retirer d'espaces) ; `pattern`, `max_length` ; « - » d'un select, « -- Aucun -- » d'une datalist à `nullDefault`, croix des nomenclatures, observateurs et jeux de données ; valeur hors liste d'un select gardée grisée ; libellés sans « * », `help` sous le champ, `definition` derrière « ⓘ », `rows` ; observers-text d'une ligne, « Observateurs » ; bloc html mis en forme (gras, liens) ; l'utilisateur connecté posé même hors de la liste ; jeux triés par nom ; recherche d'espèce dès 3 lettres, 20 propositions ; listes taxonomiques propres à un champ synchronisées.
- **Gardé, et pourquoi.**
  - Hors ligne : saisie « à compléter » des visites, médias envoyés après l'objet (titre, auteur et description automatiques), recherche de taxon dans le cache, taxon inconnu bloquant, altitudes demandées à l'envoi plutôt qu'au dessin, date du jour locale (le web la prend en UTC).
  - Décisions de terrain de l'utilisateur : station précédente comme défauts et station sans habitat (OccHab), parcours et flux d'OccTax (relevé puis espèces, mono-taxon, palette des groupes, écran intercalé, relevé d'absence, caractérisation « comportement » des nicheurs).
  - Défauts du web non repris : `Releve.GroupType` et `Taxon.NonDigitalProof` (clés absentes du fr.json, le web affiche la clé brute), case à cocher seule envoyée `[null]`, date d'un champ de dénombrement envoyée en objet.
- **Recharger les données** : `VERSION_DONNEES_REQUISE` passe à 3.

Tests : 1 080 sur la JVM et 1 076 sur le simulateur pour le module commun, 79 et 5 pour l'interface. Essayé sur l'émulateur (détails du relevé, caractérisation, dénombrement, protocoles et nouvelle visite des Suivis) et sur le simulateur (les mêmes écrans, détails d'une station OccHab) ; deux défauts trouvés ainsi et corrigés : l'invite des observateurs du relevé (« Observateur(s) » du web) et, sous iOS, la croix d'une date ou d'une heure remplie (Suivis, champs additionnels), que le sélecteur repoussait hors de la ligne. Installé sur l'A12 et l'iPhone 16.

## 15. Réponses du 20 octobre : sélections, barre rouge, Suivis

Réponses de l'utilisateur aux questions du § 14 : « 1. pas de de données par défaut. 2. non. 3. oui. 4. oui. 4. coorige. » (la seconde « 4. » répond à la question 5), avec trois demandes de plus : le bloc « Sélections par défaut » des Paramètres n'apparaît qu'à la fin du chargement des données ; « Aucune valeurs par défaut pour le LDD et la liste. Observateur par défaut : utilisateur connecté » ; « certaines champs obligatoires ne sont plus marqués avec le barre rouge, corrige ça ». Précisé ensuite : trois sélections, jeu de données et liste vides ; la barre rouge partout, comme le web ; puis « dans paramètres, les champs dans selections par défaut sont obligatoires ».

- **Paramètres, « Sélections par défaut »** (remplace la ligne « Paramètres » du § 14).
  - Le bloc n'apparaît qu'une fois des données chargées, et se cache pendant un chargement (`sectionDonneesVisible`).
  - Trois choix obligatoires, comme sous GeoMys-Android : le jeu de données, parmi ceux que propose le web ; la liste de taxons, celle qu'impose le jeu choisi, sinon toutes ; l'observateur par défaut.
  - Aucune valeur d'office pour le jeu et la liste : vides, ils portent la barre rouge, et la coche refuse de quitter les Paramètres (« Configuration incomplète : … puis choisissez jeu de données / liste / observateur. »). L'observateur est l'utilisateur connecté tant qu'on n'en choisit pas un autre.
  - Une liste imposée par le jeu choisi suffit ; sans aucune liste en cache, il n'y a rien à exiger (`GeoNatureConfig.listeParDefautValide`, `observateurParDefautValide`, `saisieOcctaxValide`).
  - Sous Android, plus de croix d'effacement : on change de choix en touchant le champ. Sous iOS, la croix de la pastille reste, comme dans GeoMys-IOS : elle vide le choix, et la barre revient.
  - À la mise à jour, le jeu, la liste et l'observateur des versions précédentes, qui pouvaient avoir été posés d'office, sont vidés une fois (`viderSelectionsParDefautUneFois`) : ils sont à choisir de nouveau. Les relevés en attente gardent le jeu figé à leur enregistrement.
  - Relevé sans jeu de données (filet de sécurité, le jeu des Paramètres étant obligatoire) : « Détails du relevé » s'ouvre d'office, et le relevé ne se termine pas sans jeu (« Choisissez un jeu de données »). Même règle en mono-taxon.
  - Jeu d'un relevé : celui choisi, sinon celui du relevé précédent de la saisie, sinon celui des Paramètres. Liste de taxons : celle du jeu, sinon celle des Paramètres, sinon `OCCTAX.id_taxon_list`.
  - Envoi : OccHab ne se rabat plus sur le jeu d'OccTax. Un jeu manquant arrête l'envoi, avec un message qui dit où le choisir.
  - L'observateur par défaut sert aussi de déterminateur et d'auteur des médias, comme l'utilisateur connecté sur le web.
- **Barre rouge des champs obligatoires, partout** (`BarreObligatoire.kt`). Comme le `ng-invalid` du web, elle marque dès l'ouverture chaque champ requis vide ou refusé. Sous Android, 3 dp de rouge collés au champ ; sous iOS, 3 pt de rouge foncé à 8 pt du champ.
  - Suivis : champs requis vides, valeurs refusées (motif, longueur, bornes), titre d'une photo.
  - OccTax : jeu de données, observateurs et heures mal formées des détails du relevé ; nomenclatures requises de la caractérisation et du dénombrement ; effectifs vides ; champs additionnels requis vides ou refusés.
  - OccHab : observateurs, jeu de données, dates et nature de la station ; habitat, technique de collecte et sa précision.
  - Écran intercalé : « Les champs marqués d'une barre rouge sont obligatoires. »
- **Fautes du web corrigées** : « État biologique », « Naturalité », « Habitat d'intérêt communautaire », « Aucun élément trouvé », « Privilégiez », « Enchaîner les saisies ». Elles remplacent l'orthographe du web citée au § 14.
- **Inchangés** (réponses 2 et 3) : le `nom_cite` d'OccTax reste le nom choisi. Une station OccHab du serveur saisie à plusieurs ne garde que le premier observateur une fois modifiée, comme sur le web.
- **Suivis, droits de création** (`DroitsSuivis`).
  - La synchro lit le C du CRUVED de chaque protocole et de ses objets de permission (`/api/gn_commons/modules`, `MONITORINGS.PERMISSION_LEVEL`).
  - Sans ce droit, le « + » est grisé ; touché, il affiche le message du web, « Vous n'avez pas les permissions nécessaires ». « Nouveau passage » sur la carte donne le même message, et le formulaire refuse la création.
  - Tant qu'aucune synchro ne les a lus, rien n'est bloqué : le serveur tranche à l'envoi.
- **Suivis, boutons et enchaînement du web.**
  - Interrupteur « Enchaîner les saisies » quand le type le permet (`chained`).
  - Boutons « Valider et saisir des … » (enfant unique, en création), « Annuler » et « Valider ». Ce dernier devient « Valider et enchaîner les saisies » quand l'interrupteur est mis.
  - Après l'enregistrement : le formulaire de l'enfant ; ou un nouveau formulaire du même type, qui reprend les champs `keep` ; sinon la fiche du parent.
  - Le mode chaîné de GeoMys-Android et son bouton « Terminer » disparaissent ; la saisie « à compléter » hors ligne reste.
- **Suivis, médias** : chaque photo a son titre (requis, barre rouge tant qu'il est vide), sa description et son auteur, envoyés tels quels à gn_commons. Les photos des saisies plus anciennes gardent le titre automatique. Seules les photos sont proposées : ni type de média, ni adresse URL.
- **Suivis, listes alimentées par une API** (`datalist` à `api`) : adresse de TaxHub, de GeoNature ou absolue ; requête POST avec `data` ; clés `value` et `label` par défaut ; options mises en cache par la synchro. Une datalist de taxons de TaxHub reste un champ espèce.
- **Défauts trouvés en route, corrigés.**
  - Une synchro qui échouait sur les jeux de données créables les vidait, et plus aucun jeu n'était proposé. `chargerIdsDatasetsCreables` rend désormais null en échec, et la liste précédente est gardée.
  - La caractérisation plantait sur un bloc html des champs additionnels.
- **Essais** : les données d'essai portent des droits (visites du STOM permises, passages des mares refusés), une photo sans titre, `chained`, `keep` et `redirect_to_parent`. `-ecran parametres-selections` ouvre les Paramètres sur leurs sélections.
- **Recharger les données** : `VERSION_DONNEES_REQUISE` passe à 4, pour que la synchro lise les droits et les listes alimentées par une API.

Tests : 1 095 sur la JVM et 1 091 sur le simulateur pour le module commun, 82 et 6 pour l'interface ; après le passage des sélections en obligatoires, 1 097 et 85 sur la JVM (dont `SelectionsObligatoiresTest`, trois mutations détectées), sans essai sur l'émulateur ni le simulateur, à la demande de l'utilisateur. Essayé sur l'émulateur : sélections des Paramètres, détails du relevé, caractérisation, dénombrement, nouvelle visite avec enchaînement, « + » grisé et messages du suivi des mares, champs d'une photo, station et habitat OccHab. Sur le simulateur, les mêmes écrans, sans les gestes ; les champs d'une photo sous iOS sont vérifiés par un test de mise en page, dont la contre-épreuve échoue comme prévu. Installé sur l'A12 et l'iPhone 16.

## 16. Retouches du 21 octobre

Demandes de l'utilisateur, essayées par lui sur les téléphones (pas d'essai sur l'émulateur ni le simulateur) :
- **Listes de choix sans clavier sous 20 valeurs** (`SEUIL_RECHERCHE_LISTE`) : le champ ne se tape plus, le toucher déroule toute la liste. À partir de 20 valeurs, rien ne change : on tape pour filtrer. Cela vaut pour le jeu de données et les observateurs des détails d'un relevé et d'une station OccHab, et pour les trois sélections des Paramètres. Sous iOS, la ligne de choix et le menu du système remplacent alors le champ de recherche.
- **Jeu de données des détails sous iOS** : à partir de 20 valeurs, toute la liste filtrée défile sous le champ. On n'en voyait que les cinq premières.
- **Détails en écran plein**, comme la caractérisation et le dénombrement, et non plus en dialogue (`FormulaireEnEcranPlein`) : le bandeau du flux, la coche de validation en haut à droite et la même garde de sortie (« Détails non enregistrés »). Le retour (bouton d'Android, balayage sous iOS) les annule, la maison les annule puis mène à l'accueil. Cela vaut pour les détails du relevé, ceux de la saisie mono-taxon, les détails communs (`EcranDetailsReleve`) et, demandé ensuite, les détails et les informations obligatoires d'une station OccHab (`EcranDetailsStation`). Le dialogue des informations obligatoires ne se fermait que par « Annuler » ; l'écran se quitte par le retour.
- **Bouton « Détails » d'une station OccHab sous iOS** : la pilule au contour jaune très arrondi du relevé OccTax (`BoutonDetailsIos`), au lieu du cadre aux coins de 8 pt.
- **Paramètres sous iOS** : la liste d'une sélection s'affiche au-dessus de son champ, que le clavier ne cache plus.

Tests : 1 097 sur la JVM pour le module commun, 87 pour l'interface (dont les gardes des détails d'un relevé et d'une station, mutations détectées) ; le test iOS du jeu de données à filtrer est adapté (25 valeurs, défilement), compilé mais pas lancé.

## 17. Réinstaller sans rien avoir à faire (21 octobre)

Demande de l'utilisateur : « avec geomys android, qd je réinstallais, tous les parametres etaient preservés […] et je n'avais rien à faire. De plus, recharger les données ne doit recharger taxref que si sa version a changé sur le serveur ». GeoMys-Android n'a jamais relevé sa version des données (restée à 1) ; GeoMys l'a relevée trois fois le 20 octobre (2, 3, 4), et chaque relèvement imposait un rechargement complet, TaxRef compris.
- **Plus de rechargement forcé après une mise à jour** : `armerRechargementSiRequis`, le drapeau `rechargementRequisApresMaj` et la purge de SyncRunner sont retirés. Une installation en retard n'est ni bloquée ni purgée ; ses données servent. Paramètres suggère seulement, sans rien bloquer, de recharger les données (« L'application reste utilisable d'ici là ») ; la synchro suivante efface ce bandeau.
- **TaxRef n'est retéléchargé que si nécessaire** : sa version a changé sur le serveur, son cache est vide, une liste à charger y manque ou y est restée incomplète, ou le format de son cache a changé (`TaxRefCache.VERSION_FORMAT`, 1 : groupes 3 et noms valides). Ce format remplace, pour TaxRef, le relèvement de la version des données ; un TaxRef chargé par une version qui lisait déjà les groupes 3 et les noms valides (données en version 3 ou plus) est tenu pour à jour. L'appui long sur « Recharger les données » force toujours le rechargement complet.
- **Les réglages sont gardés** : le vidage unique des sélections par défaut (`viderSelectionsParDefautUneFois`) est retiré.

- **Paramètres s'affiche sans attendre TaxRef** (constaté ensuite sur l'A12 : l'écran restait sans ses données ni ses sélections). Il savait qu'il y avait des données en chargeant tout le cache, de 10 à 30 s sur un téléphone d'entrée de gamme, et plus en version de débogage : jusque-là, depuis qu'aucun rechargement forcé ne le mettait en mémoire, l'écran restait vide. La présence du fichier suffit désormais (`TaxRefCache.aDesDonnees`) ; seuls les compteurs et le message de la liste attendent le chargement, à part, sans retenir le reste de l'écran ni le suivi de la synchro.

- **TaxRef se lit à la main, comme sous GeoMys-Android** (« je n'avais pas ce problème avec geomys-android ») : son gros fichier (`cache_v3.json`) était décodé en flux par kotlinx-serialization ; GeoMys-Android le parcourt champ par champ avec le JsonReader de Gson. `LecteurCacheTaxRef` fait de même sur les octets du fichier, sans arbre intermédiaire ni String géante : 15 ms au lieu de 59 pour 75 000 noms, 65 au lieu de 175 pour 225 000 (JVM, 21 octobre). Les compteurs de Paramètres et les premières propositions d'espèces arrivent d'autant plus tôt. La version installée sur l'A12 pour les essais était celle de débogage, plus lente qu'une version publiée comme celle de GeoMys-Android : depuis le 21 octobre, c'est la version optimisée `mesure` (R8, non débogable, clé de debug, qui remplace la précédente en gardant les données), avec l'accord de l'utilisateur.

Tests : 1 100 sur la JVM pour le module commun (dont TaxRef gardé à version égale après une mise à jour, retéléchargé pour un format ancien, l'application utilisable et le bandeau, la présence des données sans chargement, et le lecteur de TaxRef, qui relit à l'identique ce qu'écrit la synchro, noms difficiles compris ; trois mutations détectées), 87 pour l'interface.

## 18. Décisions du 21 octobre (suite)

Demandes de l'utilisateur : « Il ne doit pas y avoir de déterminateur par défaut », « un taxon déjà présent dans le relevé ne doit pas demandé confirmation », « Ne met pas en place la mise a jour has google play », et, pour les essais, de revenir à la version de débogage.
- **Déterminateur par défaut : l'utilisateur connecté.** Retiré d'abord (« Il ne doit pas y avoir de déterminateur par défaut »), il revient le même jour (« remet le déterminateur par défaut sur l'utilisateur connecté ») : son nom complet, comme le web (`currentUser.nom_complet`), sinon son identifiant (`determinateurParDefaut`), et non plus l'observateur par défaut des Paramètres (§ 15), comme GeoMys-Android. En relevé comme en saisie mono-taxon ; la caractérisation garde celui déjà saisi.
- **Un taxon déjà dans le relevé s'ajoute sans confirmation** : la question du web (« Le taxon saisi est déjà dans la liste des taxons enregistrés… Voulez-vous continuer ? », § 14) est retirée.
- **Pas de mise à jour hors Google Play** : la mise à jour intégrée de la variante GitHub de GeoMys-Android (`MiseAJour`) n'est pas reprise ; la distribution passera par Google Play.
- **Essais sur l'A12** : la version de débogage revient, à la place de la version optimisée `mesure` (§ 17).

Tests : 1 100 sur la JVM pour le module commun, 88 pour l'interface (dont le déterminateur par défaut, l'utilisateur connecté et non l'observateur par défaut, mutation détectée, et le taxon ajouté deux fois sans question) ; l'attente du test des sélections obligatoires, sensible au fil sur lequel l'écran reprend sous le dispatcher de test, est corrigée.

## 19. Respect de ce qu'envoie le serveur : corrections du 21 octobre

Question de l'utilisateur : « est ce que tous les écrans, formulaires, listes et taxons proposés en fonction du contexte (protocole, etc.), champs (libellés, comportement…) respectent bien les infos envoyées par le serveur geonature ». Quatre audits (OccTax, OccHab, Suivis, commun) ont comparé l'appli au web de GeoNature 2.17.5 et de gn_module_monitoring 1.3.2 ; le rapport, local, est `AUDIT-2026-10-21.md`. La saisie était conforme ; les écarts portaient surtout sur la consultation des Suivis. L'utilisateur a dit « fait les corrections », puis tranché : une station importée garde ses dates, les sites se trient comme sur le web ; ni accès sans droit OccTax, ni Explorer aligné, ni reprise des observateurs, dates et heures du relevé précédent.

Suivis :
- **Une liste par type enfant du module**, comme le web (`sites-groups.resolver.ts`) : stom et les quatre RHOMEO, dont `tree.module` porte les groupes de sites et les sites, montrent les deux listes ; un site sans groupe n'était joignable que par la carte.
- **Les valeurs multiples s'affichent** (observateurs d'une visite, nomenclatures et cases multiples) : les tableaux du serveur sont gardés à part (`valeursMultiples`), chaque identifiant résolu et les valeurs jointes par « , », comme `getUtils` du web.
- **Les identifiants affichés comme sur le web** : le champ des `display_field_names` du protocole pour chaque `type_util` (groupe de sites, site, habitat, module… par les listes en cache), les observateurs des listes propres aux champs, et un taxon selon `taxonomy` (« nom_vern,lb_nom » : le nom vernaculaire, à défaut le nom latin) au lieu de « nom latin (nom vernaculaire) ».
- **Dates en jj/mm/aaaa** dans les fiches, les lignes et les titres (une visite s'intitule par sa date).
- **« Visites », « Observations »** : sans `label_list`, le `label` suivi d'un « s », comme le web.
- **Propriétés spécifiques** : la fiche d'un site montre les champs propres à ses types de site (`config.specific`, cache de la datalist `types_site`), rangés par nom comme l'onglet du web. Aucun type de site du serveur de l'ANA n'en déclare aujourd'hui.
- **Tri des listes du protocole comme le web** : le premier critère de `sorts` (par exemple `last_visit desc`), un site jamais visité en tête comme sous PostgreSQL ; par nom, à la française, quand le protocole n'en déclare pas ou que la propriété n'est pas dans les données (le serveur n'envoie d'un enfant que sa `display_list`).
- **Liste des protocoles** : ceux que l'utilisateur peut lire (`R` > 0), comme le web, et non plus ceux où il a un droit quelconque.
- **Boutons radio sous iOS** (demande du même jour) : la règle d'Android, une option par ligne sauf peu d'options courtes (`radioEnLigne`) ; la fiabilité d'une visite STERF-eBMS s'écrasait sur une ligne. Les champs radio additionnels d'OccTax sous iOS passent aussi à une option par ligne, comme sous Android.

OccHab :
- **Une station importée du serveur garde ses dates**, comme le web ; GeoMys-Android lui donnait celles de la session, qui remplaçaient les siennes à l'envoi.
- **Dates d'une station de plusieurs jours** : changer l'une ne recopie plus l'autre, comme le web, qui les tient toutes deux pour modifiées à l'ouverture quand elles diffèrent.

OccTax et synchro :
- **Changement de jeu de données** : passer à un jeu sans champ de relevé n'envoie plus les champs de l'ancien jeu (sur le serveur de l'ANA, les 398 champs additionnels dépendent tous d'un jeu).
- **Observateurs** : la liste d'OccTax seule, comme le web ; le repli sur tous les utilisateurs (`/users/roles`) est retiré. Une liste illisible laisse celle du cache.
- **La config du formulaire du web (`gn_commons/config`) se lit même si `t_mobile_apps` échoue** : `form_fields`, filtre taxonomique, réglages OCCTAX et droits des Suivis ne restent plus figés sans message.
- **Les champs additionnels ne dépendent plus du réglage mobile `nomenclature.additional_fields`**, que le web ignore (il vaut vrai sur le serveur de l'ANA).

Rien de nouveau n'est lu à la synchro : `VERSION_DONNEES_REQUISE` reste à 4.

Tests : 1 110 sur la JVM pour le module commun (listes du module, valeurs multiples, tri, libellés, dates, résolveur en cache, taxons, propriétés spécifiques, changement de jeu, observateurs, config lue sans t_mobile_apps), 92 pour l'interface (dates d'une station, station importée, règle des boutons radio) ; cinq mutations détectées ; tests iOS compilés.

## 20. Outils du serveur repris de GeoMys-Android (21 octobre)

Question de l'utilisateur : « as tu bien repris toutes les infos utiles de geomys-android, en particulier les infos sur les endpoints geonature etc ». Toutes les routes de GeoMys-Android existent dans GeoMys (comparaison des sources) et la table de l'inventaire (`inventaire/1-reseau-sync.md` § 3) les décrit ; manquaient les deux scripts de `tools/` et les notes de travail sur le serveur. Avec l'accord de l'utilisateur :
- `outils/lister_endpoints_geonature.py` : la liste statique des routes qu'appelle GeoMys (OccTax, OccHab, Suivis, Explorer, envoi), avec leur accès (public, connecté, écriture), mise à jour d'après le code.
- `outils/verifier_serveur_geonature.py <url> [--public]` : un verdict OK / ATTENTION / ÉCHEC route par route, en lecture seulement, étendu aux Suivis, à OccHab, à l'Explorer et aux lectures de l'envoi. L'identifiant et le mot de passe se tapent au clavier, jamais en argument. Essayé sur un faux serveur local seulement.
- Les notes de travail (filtres lus dans le corps JSON ou dans l'adresse, sources de référence, portabilité, inspection des caches du téléphone, HABREF) sont reprises dans la mémoire de travail, hors du dépôt.

## 21. Essai sur un autre serveur : la démonstration de GeoNature (21 octobre)

Demande de l'utilisateur : utiliser le serveur de démonstration de GeoNature « pour vérifier qu'il n'y a pas de pb avec un autre serveur ». Le compte public de la démonstration (donné par le README de GeoNature) a servi ; rien n'a été envoyé au serveur.
- `outils/verifier_serveur_geonature.py` : 28 routes sur 28 conformes (GeoNature 2.17.2).
- `EssaiServeurReelTest` (module commun, essai JVM, ignoré sans `GEOMYS_ESSAI_URL`, `GEOMYS_ESSAI_IDENTIFIANT` et `GEOMYS_ESSAI_MOT_DE_PASSE`) fait tourner les vrais services de l'appli sur le serveur : connexion, « Recharger les données » complet (30 s, aucune étape en échec : 782 taxons, 421 nomenclatures, 70 jeux, 63 observateurs, 39 champs additionnels, 29 425 habitats, 15 stations OccHab, 33 protocoles), sélections de Paramètres, puis, pour chaque protocole, listes, fiche et formulaire de chaque type de saisie, règles comprises ; et l'Explorer.

Deux défauts de l'appli, corrigés :
- **Nom de fichier du cache trop long** : une liste dont l'adresse porte beaucoup de filtres (le « Code phénologique » de pt_ecoute_avifaune, restreint à 17 codes : 449 octets) ne s'écrivait pas, sans erreur, et restait vide ; le champ, obligatoire, bloquait l'observation. Au-delà de 200 caractères, le nom garde son début et prend une empreinte de l'adresse (`MonitoringCache.keyOptionsDatalist`). Défaut venu de l'alignement du 20 octobre, qui a mis la restriction d'une nomenclature dans l'adresse ; sur le serveur de l'ANA, la plus longue clé fait 103 caractères.
- **Explorer** : GeoNature 2.17 envoie le nom dans `nom_vern_or_lb_nom`, que l'appli ne lisait pas ; un taxon sans nom français en cache restait « Espèce inconnue ». Le reste de l'Explorer est inchangé (décision du même jour) : il ne montre toujours que les points (24 lignes et polygones sur 279 observations à la démonstration). Il écartait aussi une observation dont le taxon n'est pas dans le TaxRef du téléphone quand le serveur n'envoie pas son groupe (171 sur 279 à la démonstration, où la liste « Saisie Occtax » compte 8 taxons) : changé ensuite, § 23.

Ce que la démonstration montre de sa propre configuration, à l'identique sur le web : deux protocoles sans liste d'observateurs (`users/menu/None`), deux sans jeu de données (pas de visite possible), un champ « individus » non pris en charge (test_indi). À vérifier par l'utilisateur sur son serveur : le « Statut d'observation » masqué et obligatoire de micromam, dont le défaut ne désigne pas sa valeur par son identifiant, que ni le web ni l'appli n'appliquent.

Tests : 1 113 sur la JVM pour le module commun (le nom de cache d'une longue adresse, relu par un nouveau processus, et le nom de `for_web` ; deux mutations détectées), 92 pour l'interface ; tests iOS compilés.

## 22. Quinze autres serveurs GeoNature (21 octobre)

Demande de l'utilisateur : « fait les tests sur ces serveurs », quinze serveurs GeoNature d'autres structures (leurs adresses restent hors du dépôt). Sans compte : seules les routes publiques, en lecture, une dizaine de requêtes par serveur (version, formulaires, réglages mobiles, champs additionnels, TaxHub, listes, habitats). Aucune clé de formulaire inconnue de l'appli, aucun type de champ additionnel non géré. Puis, à sa demande (« Pour 2, si les champs additionnels sont obligatoires pour une espèce ou un dénombrement, est ce tu peux les afficher comme tels quand c'est le cas ? Pour 4, ok pour ne charger que les listes nécéssaires. Pour 5, refuse les serveurs trop anciens. Fait 1 et 3 ») :

1. **TaxHub du web d'abord.** La synchro interroge le TaxHub intégré (`<URL>/api/taxhub`, celui du web) et ne garde l'adresse publiée pour les applis mobiles (`sync.taxhub_url`) que s'il ne répond pas. Sur l'un des serveurs, celle-ci (un autre domaine) a un certificat invalide : aucun taxon ne se chargeait.
2. **Champs additionnels obligatoires d'une espèce ou d'un dénombrement montrés comme tels** (un serveur en a dix, quatre autres un) : un champ obligatoire, montré, sans valeur par défaut, du jeu du relevé (`RequisEspece`).
   - À l'ajout d'une espèce, la caractérisation (ou, à défaut, le dénombrement) qui le porte s'ouvre d'office, comme le web montre le formulaire du taxon avant de l'enregistrer.
   - Tant qu'il est vide, la ligne de l'espèce porte la barre rouge, et le « i » ou le « × N » qui y mène passe au rouge ; en saisie mono-taxon, la barre rouge sur les boutons « Caractérisation » et « Dénombrement ».
   - « Terminer » n'est pas refusé pour autant (non demandé).
3. **Date vide au départ quand le serveur le demande** (`OCCTAX.DATE_FORM_WITH_TODAY` à faux, sur un des serveurs) : dans « Détails », la date est à choisir, avec la barre rouge (Android : « — » qui ouvre le sélecteur ; iOS : « Choisir une date ») ; le relevé ou la série s'ouvrent sur leurs détails, et « Terminer » ou « Démarrer la saisie » sont refusés tant qu'elle manque (« Choisissez une date de début »).
4. **Seules les listes de taxons utiles se téléchargent** : la liste choisie dans Paramètres, celles des jeux de données, des protocoles et de la configuration d'OccTax, et non plus toutes les listes publiques (jusqu'à 416 000 taxons cumulés sur un serveur, plus de 280 000 sur deux autres). Un premier chargement sans liste utile laisse TaxRef vide mais daté (Paramètres propose alors les sélections), sans compter comme un échec ; choisir une liste absente du téléphone lance son chargement ; la configuration n'est complète qu'avec la liste de saisie chargée (`listeDeSaisieChargee`). Sur le serveur de démonstration : 7 listes au premier chargement, la 8ᵉ au choix de la liste, puis la configuration complète. Sur le serveur de l'ANA, les installations déjà chargées gardent leurs listes jusqu'au prochain changement de version de TaxRef. Précision demandée ensuite (« il faut charger toutes les listes nécessaires et potentiellement sélectionnées dans les saisies, les protocoles de suivis et occhab ») : sont chargées les listes de tous les jeux de données qu'un relevé peut prendre, la liste par défaut, celle de la configuration d'OccTax, et, pour chaque protocole lisible, celles du protocole, de ses types d'objets et de ses champs espèce (`listesDesSuivis`). Un champ espèce sans aucune liste (RHOMEOOrthoptere sur le serveur de l'ANA), auquel le web propose tout TaxRef, fait charger toutes les listes publiques, comme avant. OccHab ne demande aucune liste de taxons : sa liste d'habitats (`OCCHAB.ID_LIST_HABITAT`) se charge comme avant.
5. **Serveur trop ancien refusé** : une version illisible (`gn_commons/config` absent, d'avant la 2.12, sur un des serveurs) fait échouer le test de connexion, comme une version inférieure à 2.15 (deux des serveurs, en 2.13 et 2.14) ; le bénéfice du doute est retiré. La lecture de la version attend 10 s au lieu de 5.

Tests : 1 121 sur la JVM pour le module commun, 96 pour l'interface ; huit mutations détectées ; tests iOS compilés.

## 23. Explorer : les taxons absents du téléphone (21 octobre)

Question posée après l'essai sur la démonstration : « dans l'Explorer, je garde les observations dont le taxon n'est pas sur le téléphone, avec une icône neutre ? ». Réponse de l'utilisateur : « oui ».
- Une observation dont le groupe ne vient ni de la Synthèse (`group2_inpn`, que `for_web` n'envoie pas par défaut) ni du cache TaxRef du téléphone n'est plus écartée : elle est gardée sans groupe (`ObsExplorer.taxon` nul). De même pour une observation sans `cd_nom`, et pour un taxon du cache sans groupe, que GeoMys-Android rangeait aux oiseaux. Un groupe hors des six groupes d'animaux, dont le règne n'est pas en cache, n'est plus classé d'office parmi les autres invertébrés.
- Son marqueur : un disque gris (gris 600 de Material) et un œil blanc, sous Android et sous iOS. Le marqueur de plusieurs observations prend le groupe de la première qui en a un : les marqueurs déjà montrés gardent leur couleur, seul leur effectif augmente.
- Le filtre par groupe ne les montre pas ; sans filtre, elles sont comptées et listées comme les autres.
- Sur la démonstration : 255 observations affichées au lieu de 84, dont 171 sans groupe.

Tests : `GeoNatureBrowseTest` (groupe de la Synthèse, du cache ou absent : sept cas), `ExplorerTest` (filtre et couleur du marqueur) ; quatre mutations détectées. 1 123 tests sur la JVM et 1 118 sur le simulateur iOS pour le module commun, 96 tests d'écran sur la JVM et 6 sur le simulateur ; `outils/verifier.sh` sans erreur.

## 24. Corrections après l'audit complet du 21 octobre

Audit complet refait après la 0.2.0 (`AUDIT-2026-10-21-complet.md`, fichier local). Réponses de l'utilisateur aux questions : « 1 envoyer de vraies mises à jour après un envoi partiel. 2 version lue trop ancienne. 3 la demander sans jeton. 4 recopier la date de début comme la 0.1.0. 5 comme le web. 6 embarquer les certificats Let's Encrypt. 7 non », plus un défaut constaté sous iOS. Corrigé le même jour :

- **Paramètres sous iOS** (constat de l'utilisateur) : au-delà de 20 jeux de données, le champ ne proposait rien avant la première lettre. Toucher le champ de recherche déroule maintenant toute la liste, que la frappe filtre ; toucher la pastille d'une sélection la rouvre aussi.
- **Corrections après un envoi partiel transmises** (C3, réponse 1) :
  - Suivis : une saisie dont l'objet est déjà créé, corrigée par « Éditer », part en `PATCH /monitorings/object/<module>/<type>/<id>` avec toutes ses valeurs, avant ses médias. Ni compléments null ni `medias: []`, qui détacherait les photos ; un champ vidé part à null.
  - OccTax : chaque occurrence créée garde l'empreinte de ce qui est parti. Une espèce modifiée depuis part en mise à jour (`POST /occtax/OCCTAX/occurrence/<id>`, l'occurrence retrouvée par son identifiant, ses dénombrements gardant leur ligne), un relevé modifié aussi (`POST /occtax/OCCTAX/only/releve/<id>`, sans ses occurrences, que le serveur supprimerait). La saisie n'est close qu'une fois tout transmis. Limite : une photo ajoutée après l'envoi à une espèce déjà transmise ne part pas.
  - OccTax : une espèce ajoutée à un relevé déjà créé le rejoint, au lieu d'en ouvrir un second (M33).
  - OccHab : une station retrouvée après une création incertaine part en mise à jour avec sa copie locale, ses habitats appariés par leur identifiant SINP. Elle était marquée envoyée telle quelle.
- **Anti-doublon des Suivis** (C4) : la recherche par uuid passe par `GET /monitorings/list/<module>/<type>?<champ uuid>=…`. La fiche du parent ne donne pas l'uuid de ses enfants (gn_module_monitoring 1.3.2) : l'objet passait toujours pour absent, et un renvoi le recréait. L'envoi d'une saisie est aussi protégé de l'annulation.
- **Habitats OccHab** (C5) : chaque habitat reçoit son identifiant SINP dès sa création (les plus anciens, juste avant l'envoi), transmis aussi en création ; la fusion reconnaît celui qu'un envoi sans réponse a déjà créé.
- **Géométrie OccHab** (C6) : une station mise en plusieurs parties sur le web, ou dont le contour a changé des deux côtés depuis l'import, n'est plus renvoyée ; un message le dit.
- **Version du serveur** (M28, réponse 2) : seule une version lue et trop ancienne refuse le serveur. Une version non lue (réseau faible, proxy, portail captif) laisse l'état d'avant ; « v2.17 » se lit 2.17.
- **Jeton** (M27, réponse 3) : le jeton et les cookies ne partent que vers le serveur configuré, redirections comprises ; une liste d'une API sur un autre serveur est demandée sans eux.
- **Date de fin d'une visite** (réponse 4) : masquée ou vidée, elle reprend la date de début, comme dans la 0.1.0.
- **Champ espèce des Suivis** (réponse 5) : seule sa propre liste (`id_list`) le restreint, comme le web ; ni celle de son `api`, ni celle du protocole. PAPALTB et pelouses_seches proposent tout TaxRef chargé, et un tel champ fait charger toutes les listes publiques (le `cd_nom` masqué du type « module » ne compte pas).
- **Android 7.0** (M42, réponse 6) : les racines ISRG Root X1 et X2 de Let's Encrypt sont embarquées (`res/raw`, empreintes vérifiées) ; sans elles, aucun serveur sous Let's Encrypt n'était joignable.
- **Mot de passe ressaisi** (M29) : il ne vide plus les nomenclatures, les stations du serveur ni la version ; seul un changement d'adresse ou de compte le fait.
- **Liste choisie hors réseau** (M30) : si elle ne se charge pas, la liste précédente revient, avec un message.
- **« Continuer la saisie » pendant son envoi** (M35) : refusé, avec un message.
- **Tri des listes des Suivis** (M36) : un ordre total, en nombre ou en texte pour toute la colonne, sous un filet ; il pouvait fermer l'appli sous Android.
- **Heures sous Android** (M37) : « 1430 », « 930 », « 14h30 », « 14.30 » se lisent hh:mm (le pavé numérique n'a pas de « : »).
- **Barre rouge** (réponse 7) : inchangée.

## 25. Libellé des propositions d'espèce des Suivis (21 octobre)

Constat de l'utilisateur : dans « pelouses sèches », « blar » donne 2 propositions dans l'appli et 15 sur le web. Cause : le champ espèce de ce protocole n'a pas de `id_list` (seulement une `api` vers la liste 100, que le web ignore) ; le web cherche alors dans tout TaxRef du serveur (15 noms, dont des diatomées, des foraminifères et des lichens), l'appli dans les listes chargées (les 2 de la liste 100). Réponse : « On garde comme ça pour l'instant ».

- **Sans liste**, les propositions viennent de tout le cache avec leur taxon : c'étaient ses clés internes, en minuscules et sans cd_nom (« peziza sublaricina »). Elles portent maintenant leur graphie, leur cd_nom et, dessous en italique, le nom scientifique (ou le nom français sous un nom scientifique).
- Les noms valides sont lus au démarrage, hors du fil de l'interface (M40, en partie).
- Le libellé du web (`search_name` de TaxHub, « nom = *nom valide* - [rang - cd_nom] »), essayé le 21 et le 22 octobre avec la lecture du rang des taxons, est abandonné (§ 26) : TaxRef reste au format 1, rien à recharger.

## 26. Retour à l'affichage et à la chaîne de saisies de GeoMys-Android (22 octobre)

Constat de l'utilisateur : « dans les suivis tu as dégradé la saisie des obs. Quand je sélectionne une espèce, il n'y a rien affiché en dessous du champ à part la petite coche verte. » Et : « l'enchaînement des obs a disparu, on ne peut en saisir qu'une ». Demandes : « dans le sélecteur, mets le nom et en dessous en italique le nom latin. Une fois l'espèce sélectionnée, affiche le nom latin sous le champ (comme dans geomys android) », et pour l'enchaînement, « reprends le même mécanisme » que GeoMys-Android.

- **Champ espèce des Suivis**, comme GeoMys-Android : chaque proposition montre le nom, et dessous en italique le nom latin (le nom français sous un nom latin). Une fois l'espèce choisie, le champ montre le nom choisi, et la ligne verte dessous « ✓ nom latin  •  cd_nom ». Le libellé « nom = nom valide - [rang] » dans le champ et la coche seule dessous (essayés le matin) sont retirés, comme la lecture du rang : TaxRef reste au format 1 et la version des données requise à 4.
- **Chaîne de saisies** de GeoMys-Android : une nouvelle saisie dont le parent est lui-même une saisie (une observation sous une visite) s'enregistre par « Enregistrer + suivante », qui ouvre aussitôt le formulaire de l'observation suivante, sous la même visite. Les champs `keep` du protocole y sont repris, comme sur le web. « Terminer » sort de la chaîne :
  - formulaire intact, on sort ;
  - modifié et enregistrable, l'observation est enregistrée, puis on sort ;
  - modifié mais bloqué, l'appli demande avant de la perdre.

  En sortant, si la visite a été enregistrée « à compléter », son formulaire est proposé.
- « Terminer » et « Enregistrer + suivante » sont sur la même ligne, à la place d'« Annuler » et « Valider ». C'est le cas sous Android aussi, à la demande de l'utilisateur, alors que GeoMys-Android mettait « Terminer » dessous ; sous iOS, comme dans GeoMys-IOS. Le mode chaîne ne s'applique jamais en modification.
- Ailleurs, « Annuler » et « Valider » ; pour une visite, « Valider » enchaîne désormais sur ses observations (§ 28).
- Tests : `NouvelleVisiteTest.chaine_de_saisies_de_geomys_android`, `LigneContexteNouvelleVisiteTest` (enchaînement, et « Terminer » qui enregistre puis propose la visite à compléter) ; cinq mutations détectées.

## 27. Clavier et alignement des champs (22 octobre)

Demandes : « dans suivis, quand je sélectionne une espèce dans la liste, fermer le clavier. Tous les champs de formulaires doivent être justifiés à gauche ».

- **Suivis** : choisir une espèce dans la liste ferme le clavier et quitte le champ, sous Android et iOS.
- **Champs justifiés à gauche**, sur les deux plateformes. Deux champs ne l'étaient pas :
  - la liste déroulante des Suivis sous iOS : sa valeur et ses chevrons étaient calés à droite, comme le Picker de GeoMys-IOS ; ils sont à gauche, comme les autres listes ;
  - les effectifs d'OccTax (dénombrement) : le nombre était centré entre « − » et « + » ; il est à gauche. L'option `centre` de `ChampTexteFormulaire`, qui ne servait qu'à eux, disparaît.
- Test : `MiseEnPageFormulaireTest.la_valeur_d_une_liste_est_calee_a_gauche_sous_ios` (simulateur iOS).

## 28. « Valider » enchaîne sur les observations (22 octobre)

Demande : « dans nouvelle visite, ne garder que 2 boutons "Annuler" et "Valider". Ce bouton Valider remplace "Valider et saisir des observations", pour systématiquement enchaîner sur la saisie des obs. De même quand on édite une visite, le bouton Valider doit enchaîner sur d'éventuelles nouvelles obs ».

- Le formulaire d'une visite n'a plus que « Annuler » et « Valider ». « Valider et saisir des Observations » disparaît, comme l'interrupteur « Enchaîner les saisies » de ce formulaire.
- « Valider » enregistre la visite, puis ouvre aussitôt la saisie de ses observations (« Enregistrer + suivante », « Terminer »), comme GeoMys-Android. La règle vaut pour tout type qui a une saisie enfant.
- Une visite modifiée depuis « Mes visites » fait de même : « Valider » enregistre, puis ouvre la saisie de nouvelles observations sous elle. Il n'enchaîne pas dans deux cas :
  - le formulaire a été ouvert pour compléter les champs manquants ;
  - on quitte par la garde de sortie (« Enregistrer et quitter »).
- Sans le droit de créer des observations, « Valider » enregistre la visite et revient à la page du site, comme le « + » grisé du web.
- L'interrupteur du web reste pour un type `chained` sans saisie enfant.
- Tests : `NouvelleVisiteTest` (suites d'une création et d'une modification), `LigneContexteNouvelleVisiteTest` (nouvelle visite et visite modifiée, jusqu'à l'ouverture de la saisie des observations) ; six mutations détectées.

## 29. Seules les informations de fin d'une visite peuvent attendre (22 octobre)

Demandes :
- « dans monitoring, quand on démarre une visite, passage, etc, seule l'heure de fin, si elle est demandée, n'est pas obligatoire dans les champs obligatoires au début. Si elle n'est pas renseignée, c'est au moment de l'envoi que le formulaire permet de compléter cette information. Les autres champs obligatoires doivent être renseignés dès le début » ;
- « si l'heure de fin n'est pas obligatoire, ne pas enregistrer la visite à compléter » ;
- pour les autres informations de fin (température de fin, durée de la visite, durée du parcours, date de relevé du piège) : « oui, mais seulement si elles sont obligatoires ».

Avant, une visite pouvait s'enregistrer sans aucun de ses champs obligatoires, et partait « à compléter », comme dans GeoMys-Android.

- **Ce qui bloque** : tout champ obligatoire vide bloque « Valider », sauf les informations de fin de la visite.
- **« À compléter »** : une visite enregistrée sans une information de fin obligatoire part « à compléter ». L'envoi rouvre son formulaire pour la saisir, comme « Terminer » à la fin des observations. Une information de fin facultative, vide, ne met jamais la visite « à compléter ».
- **Les informations de fin** (`NouvelleVisite.estInfoDeFin`) se reconnaissent au code ou au libellé du champ. Dans les protocoles de l'ANA :
  - heure de fin : `heure_fin_prospection` (PAPALTB), `Heure_fin` (POPAmphibien et POPReptile, requise seulement si le site est accessible), `time_end` (chronoventaire, facultative), `end_hour` (STERF-EBMS) ;
  - température de fin : `end_temperature` (chronoventaire) ;
  - durée : `visit_time` (« Durée de la visite », RHOMEO Flore et Odonate), `duration` (« Durée de parcours du transect », STERF ; « Durée de la détermination », chronoventaire, facultative) ;
  - date de fin montrée : `visit_date_max` (« Date de relevé du piège », piégeages passifs ; « Date de la détermination des restes osseux », micromam, requise seulement s'il y a des pelotes). Ailleurs, elle est masquée et recopiée de la date de début.
- **Barre rouge** : une information de fin vide n'en a pas au début de la visite. Elle en a une quand on rouvre la visite pour la compléter. Les autres obligatoires vides gardent la leur.
- Une observation (saisie d'espèce) doit toujours être complète.
- **Données d'essai** : la visite du STOM d'essai a une « Heure de fin » obligatoire, et sa « Météo » devient obligatoire. La visite en attente n'attend plus que son heure de fin.
- **Tests** :
  - `NouvelleVisiteTest.seules_les_informations_de_fin_d_une_visite_peuvent_attendre` ;
  - `ContratValeursTest.seule_une_information_de_fin_obligatoire_met_la_visite_a_completer` ;
  - `LigneContexteNouvelleVisiteTest.seules_les_informations_de_fin_d_une_visite_peuvent_attendre` ;
  - les tests de visite, adaptés.

  Neuf mutations détectées.

## 30. GeoMys remplace GeoMys-Android sur Google Play (22 octobre)

Décision de l'utilisateur : « cette appli remplacera geomys-android, elle doit être vue comme une nouvelle release ». Réponses aux questions : reprendre les saisies non envoyées, nom « GeoMys », version 2.0.0 (code 200). La décision du 18 octobre (appli indépendante, sans reprise de données) est remplacée.

- **Identité de la release** : l'identifiant Play de GeoMys-Android, `fr.ariegenature.public.geomys`, et le nom « GeoMys ». La clé est la même que celle de GeoMys-Android (certificat 849e4dd3…), et le versionCode passera à 200 à la publication (Play exige plus que 196). Les versions de test (debug, mesure) gardent `fr.ariegenature.geomys.common`, sous le nom « GeoMys Common » jusqu'au renommage du projet (§ 31) : elles cohabitent avec la version de Google Play sur un même téléphone. Le nom affiché sur l'accueil est celui de l'icône (`InfosApp.nom`). L'iOS n'est pas touché.
- **Données de GeoMys-Android à la mise à jour** :
  - relues sur place, aux mêmes noms : réglages (`gn_config`, `GeoMys_prefs`…), mot de passe chiffré (`gn_secure_v2`, clé `geomys_mdp` du coffre Android), visites des Suivis en attente (`monitoring_outbox.json`), photos des saisies (`medias/`), cartes MBTiles (`mbtiles/`) ;
  - reprises au premier lancement (`RepriseGeoMysAndroid`) : relevés OccTax et stations OccHab non envoyés, que GeoMys-Android gardait dans ses préférences (`sorties_store` / `sorties_sauvegardees`, `occhab_store` / `saisies_sauvegardees`). Ce sont des tableaux JSON au format que GeoMys lit déjà, recopiés dans ses fichiers tant que ceux-ci n'existent pas. L'original reste en place ; une saisie effacée depuis ne revient pas. Tous les champs des modèles de GeoMys-Android existent dans GeoMys, sous le même nom et le même type ;
  - à refaire : les cartes hors ligne téléchargées (osmdroid), le moteur de carte n'étant plus le même ; les référentiels, si Paramètres le propose.
- **Avant le dépôt sur Play** : la fiche de GeoMys existe déjà, avec sa politique de confidentialité, sa fiche Sécurité des données, l'accès pour l'examen et les déclarations des services.
  - GeoMys a les mêmes permissions et services de premier plan (`location`, `dataSync`) que GeoMys-Android, moins `WRITE_EXTERNAL_STORAGE` : rien de nouveau à déclarer.
  - Restent la version 2.0.0 (code 200), l'AAB (`bundleRelease`) et les notes de version de Play.
  - Il faut aussi un essai de la vraie mise à jour par la piste de test interne, avant un déploiement progressif en production.
- **Tests** : `RepriseGeoMysAndroidTest` (reprise des relevés et stations, une seule fois, jamais par-dessus les saisies de GeoMys, visites des Suivis relues sur place) ; deux mutations détectées.
- **Version 2.0.0** (code 200) construite le 22 octobre et déposée sur la piste de test interne de GeoMys.
  - La production Play était alors en 1.4.4 (code 193). D'après l'historique de GeoMys-Android, la 1.4.4 range ses saisies comme la 1.4.7 : mêmes clés, mêmes modèles.
  - Avertissements de la Play Console : 8 appareils perdus, des processeurs Intel, car MapLibre n'est livré ici que pour ARM ; pas de symboles de débogage pour le code natif de MapLibre, livré sans.

## 31. Le projet devient GeoMys (22 octobre)

Demande : « remplace toutes les références à GeoMys-Common par GeoMys. Le nom de l'appli doit être GeoMys. Renomme le dépôt GitHub en GeoMys ».

- **Dans le code et la documentation**, « GeoMys-Common », « GeoMys Common » et « GeoMysCommon » deviennent « GeoMys » :
  - en-têtes de licence, commentaires et documentation (570 fichiers) ;
  - nom du projet Gradle (`rootProject.name`), thème Android (`Theme.GeoMys`), produit iOS (`GeoMys.app`) ;
  - en-tête User-Agent des tuiles (`GeoMys/<version>`) et outils.
- Les phrases qui disaient l'appli indépendante de GeoMys, sans reprise de données, sont réécrites : la décision du 18 octobre a été revue le 22 (§ 30).
- **Le nom de l'appli** est « GeoMys » partout, versions de test comprises, sous Android et sous iOS.
- **Les identifiants ne changent pas** : `fr.ariegenature.public.geomys` pour la release Android ; `fr.ariegenature.geomys.common` pour les versions de test, pour l'iOS et pour le paquet du code Android.
- **Le dépôt GitHub** s'appelle `ANA-CEN-Ariege/GeoMys` ; l'ancienne adresse redirige. Sa description et les titres des releases 0.1.0 à 0.3.0 disent « GeoMys ».
- **Ce qui garde l'ancien nom** :
  - les fichiers déjà publiés (`GeoMysCommon-0.x.0.apk`), pour ne pas casser les liens de téléchargement ;
  - la carte MBTiles de test, dans ses métadonnées (fichier binaire d'essai) ;
  - le dossier local du projet.

## 32. Dictée vocale : effacer puis redicter (23 octobre)

Constat de l'utilisateur : « avec la dictée vocale, si j'efface la proposition et que je redicte, la nouvelle proposition est ajoutée à l'ancienne dans le champ ».

- **La cause**, sous iOS : la reconnaissance accumule la transcription pendant toute la session d'écoute, et l'écoute reste ouverte après une phrase. Le champ effacé à la main, la phrase suivante y revenait derrière l'ancienne. GeoMys-IOS arrêtait la session au choix d'une espèce, comme GeoMys, mais pas à l'effacement.
- **La correction** : toute modification du champ espèce à la main (effacer, retoucher) prévient la dictée (`Dictee.texteModifie`). Sous iOS, une écoute en cours qui a déjà transcrit quelque chose est relancée, micro ouvert, et sa transcription repart de zéro.
- Sous Android, chaque écoute s'arrête d'elle-même à la fin de la phrase, et la suivante repart de zéro : rien à faire.
- Test : `DicteeChampEspeceTest` (simulateur iOS, sous les deux allures) ; contre-épreuve faite.

## 33. Dictée vocale : seulement les noms proposés (23 octobre)

Constat de l'utilisateur : la reconnaissance vocale propose parfois des termes absents de TaxRef (« rouge-gorge », « psychorame » à la place de « tichodrome »). Question : « Est-il possible de rechercher uniquement dans la liste de noms dans TaxRef ? » Réponse à la proposition faite : « oui ».

La reconnaissance du téléphone ne peut pas être enfermée dans une liste : elle rend toujours ce qu'elle a cru entendre. C'est donc l'appli qui ne garde, de ce qui a été entendu, que les noms proposés.

- **Résolution par le son** (portée de GeoMys-IOS, `PhonetiqueFr` et `resoudreVocal`) : après le nom exact et les mots, la dictée compare les sons dans les propositions du périmètre (« pic et pêche » → Pic épeiche), puis l'orthographe approchée, puis quelques sons de travers selon la longueur du nom (« fauvettes mélano acéphale » → Fauvette mélanocéphale). Deux taxons aussi proches l'un que l'autre : rien n'est reconnu.
  - Ajouts : « œ » et « æ » sont lus en deux lettres, et « ç » est lu « s ».
  - Fichiers : `saisie/PhonetiqueFr.kt` et `saisie/IndexPhonetique.kt`.
- **Espèces proches** : quand rien n'est reconnu, la liste déroulante montre les cinq espèces du périmètre qui ressemblent le plus à ce qui a été entendu (« psychorame » → Tichodrome échelette, « rouge-gorge » → Rougegorge familier). Le texte entendu n'est jamais pris pour une espèce.
  - La ligne sous le champ dit « Entendu : « … » — choisissez l'espèce ci-dessus » (iOS, comme GeoMys-IOS) ou « … dans la liste » (Android), à la place de « Nom invalide ».
  - Sous Android, la liste s'ouvre sans que le champ ait le focus.
  - Une frappe, une autre dictée, un choix, un autre groupe ou la bascule des noms scientifiques effacent ces espèces.
  - Le classement compare aussi les sons mot à mot et les mots collés ou séparés, ce que GeoMys-IOS ne faisait pas : « huppe » amène Huppe fasciée, « grand duc » Grand-duc d'Europe.
- **Saisie mono** : une espèce reconnue à la dictée est posée sous son nom proposé, comme un choix dans la liste. Avant, le texte entendu restait dans le champ et était recherché de nouveau, à l'exact seulement : une espèce reconnue par les mots ou l'approché finissait en « Nom invalide ».
- **Android** : la reconnaissance reçoit aussi les espèces proposées (`EXTRA_BIASING_STRINGS`, Android 13 et plus), comme iOS depuis le lot 6.
- La fin de la dictée ne lance plus la recherche de la frappe sur le texte entendu, et annule celle des résultats partiels : son « Nom invalide » pouvait arriver après l'espèce reconnue (vu dans les tests, où l'interface n'a pas de fil à elle). Le statut du texte entendu n'est cherché que s'il n'est pas reconnu. Une recherche annulée arrête aussi sa roue.
- Tests : `IndexPhonetiqueTest` (≈ 190 noms d'oiseaux) et `DicteeParLeSonTest` (socle), quatre cas dans `ChampEspeceReglesTest`, deux dans `DicteeChampEspeceTest` (simulateur iOS, deux allures). Contre-épreuves faites.

Retour de l'utilisateur, le même jour, sur l'iPhone (micro du champ) : « quand je dis rougegorge, il me propose toujours rouge-gorge » ; le texte entendu restait dans le champ.

- **La cause** : la reconnaissance d'iOS ne signale pas d'elle-même la fin d'une phrase, elle écoute jusqu'à ce qu'on l'arrête. Le résultat final, qui porte les hypothèses, n'arrivait donc jamais, et la recherche parmi les noms proposés ne se lançait pas : le champ gardait le texte provisoire. Recouper le micro arrêtait l'écoute en jetant ce résultat. C'est aussi ce qui faisait s'accumuler les phrases (§ 32). GeoMys-IOS avait le même défaut.
- Le rejeu de « rouge-gorge », « rouge gorge » et « rougegorge » sur une copie du référentiel de l'A12 (oiseaux, liste 100) donne bien Rougegorge familier (cd_nom 4001) : la recherche n'était pas en cause.
- **La correction** (`FinDePhrase`, `Dictee.ios.kt`) : 1,5 s sans nouveau mot finit la phrase. Le micro se ferme, et la session attend le résultat final et ses hypothèses (2 s au plus ; sinon la transcription en tient lieu). Recouper le micro après avoir parlé finit la phrase de la même façon ; sans rien d'entendu, il arrête l'écoute.
- Sous Android, la reconnaissance finit la phrase d'elle-même : rien ne change.
- Test : `FinDePhraseTest` ; contre-épreuve faite. Le reste se vérifie sur l'iPhone.

## 34. « Donnée exceptionnelle » : les profils de taxons du serveur (23 octobre)

Question de l'utilisateur : le serveur GeoNature avertit quand une espèce est saisie à un endroit ou à une période inhabituels ; est-ce possible sur le téléphone ? Sa proposition, retenue : « faire la vérification s'il y a du réseau, et s'il n'y en a pas au moment de la saisie, le faire à chaque fois qu'on entre dans "Mes saisies" pour avoir l'avertissement avant d'envoyer ».

C'est une exception voulue à la règle « le cache seulement ». Elle ne bloque jamais la saisie : un réseau faible compte comme une absence de réseau.

- **Ce que fait le web** : au choix d'une espèce, OccTax envoie au serveur le `cd_ref`, les dates, les altitudes et la géométrie du relevé, avec les stades de vie (`POST /gn_profiles/check_observation`). Le serveur les compare au profil du taxon, calculé sur les données validées. S'il y a un écart, l'encadré « Donnée exceptionnelle » liste ses phrases.
- **Essai sur le serveur de l'utilisateur** :
  - Il calcule des profils pour les espèces essayées. Les routes `gn_profiles`, `geo/altitude` et `taxhub/api/taxref/<cd_nom>` répondent sans connexion.
  - Hirondelle rustique en janvier : « jamais observé à cette période ». Rougegorge à 3 000 m : altitude. À Paris : zone.
  - Sans altitude, le serveur répond 400 ; pour un taxon sans profil, 404.
- **Vérification** (`occtax/ProfilsTaxons.kt`, `network/ApiProfils.kt`) :
  - Mêmes valeurs qu'à l'envoi : dates du relevé (ou de « Détails »), géométrie, altitudes saisies (une seule vaut pour les deux), sinon celles du MNT (une fois par relevé). Les stades de vie des dénombrements sont résolus comme à l'envoi.
  - Le `cd_ref` est demandé à TaxHub, une fois par taxon : les profils sont rangés par `cd_ref`, l'appli ne garde que le `cd_nom`. 2 % des taxons du référentiel de l'A12 sont des synonymes, dont des fauvettes (Sylvia → Curruca).
  - Résultat gardé par observation dans `profils_taxons.json`, avec une clé de ce qui a été vérifié : une observation modifiée (espèce, date, lieu, altitude, stade de vie) est revérifiée. Rien n'est gardé sans réponse : on retentera.
  - Sans profil (404) ou invérifiable (400) : rien à signaler, comme sur le web. Les observations envoyées, les relevés sans espèce et les espèces non reconnues ne sont pas vérifiés.
  - Les phrases du serveur ne servent qu'à savoir s'il y a un écart. Partout, l'appli affiche le texte voulu par l'utilisateur (même jour) : « Donnée exceptionnelle. Le taxon n'a jamais été observé dans cette zone et/ou à cette date ». « Donnée » est au singulier, comme le titre du web : l'avertissement porte sur une observation.
- **Quand** :
  - Pendant la saisie, 1,5 s après chaque changement des observations (`SessionSaisieViewModel`) ;
  - à chaque entrée dans « Mes saisies », pour les saisies à envoyer ;
  - avant chaque envoi (15 s au plus, puis l'envoi n'attend plus), depuis « Mes saisies » comme depuis le détail d'une saisie.
- **Où** :
  - Relevé : l'avertissement sous l'espèce.
  - Saisie mono : sous le résumé, précédé de l'espèce, pour la dernière observation enregistrée, jusqu'au « + » suivant.
  - « Mes saisies » : « ⚠ N donnée(s) exceptionnelle(s) » sur la saisie ; dans la liste des espèces (ici et dans le détail d'une saisie), l'espèce marquée ⚠ et l'avertissement dessous.
  - Envoi : dialogue « Données exceptionnelles » (l'avertissement, puis une ligne par observation : espèce et date), « Envoyer quand même » ou « Annuler ». « Tout envoyer » ajoute ces lignes à sa confirmation.
- OccTax seulement, comme le web : ni Suivis ni OccHab.
- **Serveur sans profils** (demande du même jour : « ne plus faire de requêtes pour ce serveur tant qu'on n'en a pas changé ou rechargé les données ») : `DisponibiliteProfils`.
  - Les routes `gn_profiles` existent sur tous les serveurs pris en charge (blueprint toujours enregistré). Un serveur « sans la fonctionnalité » est donc l'un des trois cas suivants :
    - `FRONTEND.ENABLE_PROFILES` à faux dans `gn_commons/config`, lu à chaque « Recharger les données ». Le web n'interroge pas les profils dans ce cas, l'appli non plus : aucune requête.
    - La route est introuvable (404 sans « No profile », 405) : reconnu à la première réponse.
    - Aucun profil n'a été calculé : les 10 premiers taxons différents répondent tous « No profile ». Un seul profil trouvé écarte ce cas pour de bon.
  - Le serveur est retenu par son adresse : un autre serveur dans les Paramètres est interrogé. « Recharger les données » repart de zéro.
- Tests : `ProfilsTaxonsTest` (socle, serveur simulé) et `DonneesExceptionnellesTest` (relevé et « Mes saisies »). Contre-épreuves faites.

## 35. Le vrai flou des matériaux de GeoMys-IOS (24 octobre)

Question de l'utilisateur : « est-il possible d'utiliser une interface avec transparence sur iPhone ? » Deux niveaux lui ont été proposés : le flou des matériaux de GeoMys-IOS, puis du Liquid Glass sur les éléments posés sur la carte. Il a choisi le premier : « 1 ».

L'interface est dessinée par Compose, et les effets d'iOS ne s'appliquent d'eux-mêmes qu'aux éléments natifs (alertes, interrupteurs, menus système). GeoMys-IOS employait trois matériaux, que le portage rendait par leur seule teinte :

- **Menu déroulant de l'accueil** (`.regularMaterial`), au-dessus du logo et du nom, dessinés par Compose.
  - Le contenu de l'accueil, fond et voile compris, est enregistré dans une couche graphique (`sourceFlou`). Le menu la redessine floutée (30 pt) et recalée sous lui, découpée à sa forme, puis pose sa teinte sombre (`fondFlou`, `composants/FlouArrierePlan.kt`).
  - La teinte passe de 90 à 70 % : le dessous se devine.
- **Boussole des cartes** (`.ultraThinMaterial`), au-dessus de la carte, vue native.
  - C'est le matériau d'iOS lui-même : une `UIVisualEffectView` au style `systemUltraThinMaterial`, sombre comme l'appli, posée sous le canevas de Compose (`MateriauUltraFin`). Elle floute la carte ; l'aiguille et le cercle sont dessinés par-dessus, et les touchers restent au bouton.
  - Cela vaut pour la saisie multi-taxons et mono, OccHab et l'Explorer.
- **Cadre des champs additionnels** de l'écran intercalé (`.ultraThinMaterial`) : inchangé. Il ne passe au-dessus que du dégradé uni du fond, dont le flou est identique à la teinte.
- Rien ne change sous Android (allure de GeoMys-Android, sans transparence).
- Test : `FlouArrierePlanTest` (simulateur iOS). Une frontière rouge/bleu sous le menu doit y paraître mélangée, et à sa place. Contre-épreuves faites, sans flou et sans recalage. Le matériau natif de la boussole se vérifie sur l'iPhone.

## 36. Les visites envoyées restent sous « Envoyées » (24 octobre)

Remarque de l'utilisateur : « dans mes visites, quand j'envoie une visite etc, elle ne se retrouve pas dans "Envoyées" ».

- Cause : à la fin de chaque envoi, `OutboxEnvoi` purgeait les saisies envoyées de la file (`purgerSent`, porté de GeoMys-Android ; GeoMys-IOS fait de même). L'onglet « Envoyées » de « Mes visites » ne montrait donc jamais rien. Un groupe dont une observation restait à envoyer était gardé, mais il restait sous « À envoyer ».
- Correctif : la purge est retirée. Une visite envoyée reste dans la file, avec ses observations, et passe sous « Envoyées » quand tout son groupe est parti, comme dans « Mes saisies » (OccTax) et « Mes stations » (OccHab). Le compteur de l'accueil ne compte toujours que ce qui reste à envoyer.
- « Retirer de la liste » emporte maintenant la saisie envoyée **et ses descendants** (`OutboxMonitoring.retirerEnvoyee`). L'action n'est proposée, et n'agit, que si tout le groupe est parti : une observation à envoyer a besoin de sa visite locale, qui porte sa clé et son groupe.
- Les photos d'une visite envoyée restent sur le téléphone jusqu'à ce qu'elle soit retirée de la liste, comme pour OccTax.
- Les visites déjà envoyées avant ce correctif ont été purgées : elles n'apparaîtront pas.
- Tests : `OutboxEnvoiTest` (une visite et son observation envoyées apparaissent sous « Envoyées », l'une sous l'autre ; les autres tests vérifient désormais l'état envoyé au lieu d'une file vide), `OutboxMonitoringTest` et `MesVisitesTest` (le retrait et sa garde). Contre-épreuves faites : purge remise, retrait sans garde, retrait sans les descendants, option proposée sans garde.
