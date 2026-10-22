# Lot 2 : référentiels et synchro

*18 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, inchangée depuis. Tout le code est dans `core`, commun à Android et iOS sauf mention.*

Le lot 2 porte tout ce que télécharge « Recharger les données » (`INVENTAIRE.md` § 10) :
- TaxRef et ses index, les nomenclatures ;
- les jeux de données, listes, observateurs et champs additionnels ;
- HABREF, OccHab (modules, défauts, stations du serveur) ;
- les Suivis (modules, schémas, objets, datalists, pictogrammes) ;
- l'orchestration (`SyncRunner`) et son exécution en arrière-plan (`ExecutionLongue`).

**État.** Tout le contenu du lot est porté et testé : 441 tests sur la JVM, 435 sur le simulateur iOS (89 et 83 au lot 1).

**Critère de sortie** (synchro complète sur un serveur de test, en ligne et hors-ligne) : **atteint le 18 octobre** sur le Galaxy A12 et l'iPhone 16, contre le serveur GeoNature 2.17.5 du lot 1 (§ 7).

## 1. Ce qui est porté

| Android (`cdd5196`) | GeoMys | Remarque |
|---|---|---|
| `store/TaxRefCache.kt`, `NomenclatureCache.kt` | `store/` | Fichiers lus et écrits en flux, verrous atomicfu |
| `store/HabitatCache.kt` (deux objets) | `store/HabitatCache.kt` | Deux instances de `HabitatCacheStore` |
| `store/StationsServeurCache.kt`, `MonitoringCache.kt`, `PictoCache.kt`, `OcctaxFieldsConfig.kt` | `store/` | okio et `FichierAtomique` |
| `TaxRefLocal.kt`, `network/TaxRefService.kt`, `network/HabitatService.kt` | même place | Classes injectées |
| `network/GeoNatureBrowse.kt` | `network/GeoNatureBrowse.kt` | Modèles dans `model/Referentiels.kt` et `model/ObsExplorer.kt`, lecture en flux : § 3 |
| `network/AdditionalFields.kt` | `network/AdditionalFields.kt` | Modèles dans `model/AdditionalFields.kt` |
| `network/OccHabApi.kt` | `network/OccHabApi.kt` | Dates par kotlinx-datetime |
| `util/AnaEval.kt` | `util/AnaEval.kt` | Format du plugin QGIS inchangé |
| `network/GeoNatureSync.kt` | `network/GeoNatureSync.kt` | Une seule classe, comme l'objet d'origine |
| `network/Monitoring*.kt` (six fichiers, sans l'envoi) | `network/Monitoring*.kt` | Une classe par fichier, `MonitoringApi` reste la façade |
| `network/InvalidationCaches.kt`, `store/CachesSynchronises.kt` | même place | Classes sur les caches qu'elles vident |
| `configurationComplete` (`ui/ConfigGeoNatureFragment.kt`) | `store/ConfigurationComplete.kt` | Logique pure, passée des écrans au socle |
| `network/SyncRunner.kt` | `network/SyncRunner.kt` | `StateFlow` au lieu de `LiveData` |
| `sync/SyncForegroundService.kt` | `androidApp/…/SyncForegroundService.kt` + `ExecutionLongue` | § 5 |

## 2. Les objets deviennent des services

Les `object` d'Android deviennent des classes, créées une seule fois par `ServicesGeoNature` sur le `Socle` de la plateforme. Les dossiers et les noms de réglages restent ceux d'Android : `taxref/`, `monitoring/`, `monitoring/pictos/`, `habitats_v1.json`, `taxref_cache`, `nomenclature_cache`…

Les fonctions pures restent appelables comme avant, dans le compagnon de leur classe :
- `TaxRefCache.normaliser` ;
- `GeoNatureSync.construireIndexTaxon` ;
- `MonitoringSchemas.parserUnePropriete` ;
- `OccHabApi.parserFeatureCollection`, etc.

Les tests portés gardent donc leurs appels.

## 3. Lecture en flux des longues réponses

GeoMys-Android lit les jeux de données (jusqu'à 2 500 avec leurs modules) et HABREF (29 425 habitats) avec le `JsonReader` de Gson, sans jamais matérialiser toute la réponse. GeoMys fait de même :
- la réponse est reçue morceau par morceau dans un tampon okio (`getEnFlux`, `corpsEnTampon`), sans la copie complète que garde Ktor pour une réponse ordinaire ;
- `lireTableauEnFlux` la décode élément par élément avec kotlinx-serialization, que le tableau soit à la racine ou enveloppé sous `data`, `items` ou `results`.

**Un écart, documenté et testé.** Sous l'une de ces clés, une valeur qui n'est ni un tableau ni `null` fait échouer la lecture ; Gson la sautait. Un élément qui n'est pas un objet est écarté seul, quand Gson faisait échouer toute la lecture.

## 4. Équivalences de plateforme

| Android | GeoMys |
|---|---|
| `Collator` (FRANCE, PRIMARY) | `comparateurFrancais()` : `Collator` sous Android, `compare(_:options:locale:)` sous iOS |
| `"%.5f".format` (virgule sur un appareil français) | `formaterDecimal` |
| `SimpleDateFormat` (heure locale, fin de chaîne ignorée) | kotlinx-datetime, mêmes règles ; une date impossible donne `null` au lieu d'être reportée |
| Caches JSON des réglages écrits par Gson | `listeVersJson` / `listeDepuisJson` |
| `AtomicInteger`, `AtomicBoolean` | atomicfu |
| `runCatching` autour du réseau | `essayer`, qui laisse passer l'annulation de la coroutine |
| `URLDecoder` | `decodeURLQueryComponent` de Ktor |

Les caches JSON des réglages ne dépendent plus des noms de champs de `kotlin.Pair`, que R8 renomme : le défaut noté au § 8.2 de l'inventaire (champs additionnels illisibles après un changement de mapping) disparaît.

**Relecture des stations serveur.** GeoMys-Android écarte, à la relecture, une station sans `id` ou sans liste `habitats`, et un habitat sans `id` (`normaliserStation`). kotlinx leur donnerait les valeurs par défaut. `stationDepuisJson` les écarte donc avant le décodage. Il resservira à `OccHabStore`.

*Précisé au lot 3* ([`LOT-3.md`](LOT-3.md) § 3) : sous Gson, « sans » voulait dire explicitement `null`. Une clé absente prenait sa valeur par défaut, car ces deux classes ont un constructeur sans argument. Seul le `null` explicite écarte désormais l'élément.

## 5. Synchro en arrière-plan : ExecutionLongue

`SyncRunner.lancer()` confie la synchro à l'`ExecutionLongue` fournie par la plateforme.

**Android.** C'est `SyncForegroundService` de GeoMys-Android, dans le module de l'appli qui en porte les ressources :
- service au premier plan de type `dataSync` ;
- canal `GeoMys_sync` « Synchronisation », créé au démarrage de l'activité ;
- icône `ic_settings` ;
- titre « GeoMys — synchronisation », texte qui suit l'avancement.

**iOS.** C'est ce que fait GeoMys-IOS :
- tâche d'arrière-plan « Synchronisation GeoNature » ;
- veille de l'écran suspendue tant que la synchro dure.

On y ajoute le gestionnaire d'expiration que GeoMys-IOS ne prévoit que pour ses envois : sans lui, iOS termine l'appli quand le délai est dépassé.

**Repris du manifeste de GeoMys-Android :**
- `largeHeap`, condition de la décision 8 pour TaxRef ;
- `network_security_config` (HTTP autorisé partout) ;
- les permissions du service.

Côté iOS, le HTTP reste limité au réseau local, comme GeoMys-IOS (décision 9).

## 6. Tests

**Portés de GeoMys-Android :**

| Domaine | Tests |
|---|---|
| Navigation et champs additionnels | `GeoNatureBrowseTest` ; `AdditionalFieldsChargerTest`, `ParsingTest`, `ServerParsingTest`, `AdditionalFieldVisibiliteTest`, `ExtraireDefautTest` |
| AnaEval et OccHab | `AnaEvalTest` (45 cas), `OccHabParserTest`, `OccHabRechercheHabitatTest` |
| TaxRef et nomenclatures | `TaxRefSyncPaginationTest`, `CollisionCdNomTest`, `IndexParGroupeTest`, `ResolutionParGroupeTest`, `NomenclatureCacheTest`, `TaxonSansCleTest`, `EcritureIndexEnEchecTest`, `ResolutionPurementLocaleTest` |
| Caches, compatibilité, purges | `PictoCacheTest`, `CompatibiliteServeurTest` (ses huit cas réseau), `InvalidationServeurTest`, `StationsServeurCacheTest`, `PurgeRechargementApresMajTest`, `RechargementRequisTest`, `ListeIncompleteTest` |
| Suivis | `ChargerModulesTest`, `DatalistHorsLigneTest`, `MonitoringDatalistDatasetTest`, `MonitoringCacheLabelTest`, `MonitoringApiParsingTest`, `SchemaFusionTest`, `SubstituerVariablesModuleTest`, `AplatirProprietesTest`, `ExtraireNomHeuristiqueTest`, `FormatGeometrieTest`, `EstTypeSaisieTest` |

S'y ajoutent les tests portés en début de lot : `TaxRefCacheTest`, `ResolutionDansListeTest`, `OcctaxFieldsConfigTest`, `ProposeEgaleAccepteTest`, `SuggestionPorteSonCdNomTest`, `VersionApresEcritureGroupesTest` et `TaxRefServiceVoixTest`.

**Gardes textuelles devenues de vrais tests.** Sous Android, quatre tests lisaient la source de la synchro, faute de pouvoir la lancer. Portée, elle tourne sur un GeoNature simulé (`ServeurSimule`, `routerGeoNatureComplet`) :
- chaque index de taxons bloqué tour à tour fait échouer la synchro sans poser la version (`EcritureIndexEnEchecTest`) ;
- l'index des noms scientifiques est écrit (`TaxonSansCleTest`) ;
- le rechargement après mise à jour épargne HABREF et les stations (`PurgeRechargementApresMajTest`) ;
- une liste tronquée bloque la configuration (`ListeIncompleteTest`).

**Gardes non portées, reportées aux écrans (lot 5).** Celles qui lisaient l'écran Paramètres et son layout, et les deux cas d'`EstTypeSaisieTest` sur `estObjetDeNiveauVisite`.

**Propres au portage :**
- `SyncRunnerTest` : la synchro complète sur le GeoNature simulé, puis hors ligne, avec et sans session en cache ;
- `TableauJsonEnFluxTest` et `TexteTest` (tri à la française) ;
- les cas nominaux d'image de `PictoCacheTest`, impossibles sous Android avec le stub de `BitmapFactory`.

## 7. Critère de sortie

La synchro se lance sans écran, par un paramètre caché : `action=synchroniser`, ou `action=etat-donnees` pour lire ce que l'appareil sait sans réseau. Le code est dans `outils/DiagnosticSynchro.kt`. L'appli écrit des lignes `SYNCHRO_*` et `DONNEES_HORS_LIGNE`, lues dans logcat ou dans la console de `devicectl`, comme au lot 1. Elle utilise les identifiants saisis au lot 1.

**En ligne**, synchro complète :

| | Galaxy A12 | iPhone 16 |
|---|---|---|
| Résultat | réussie, sans avertissement | réussie, sans avertissement |
| Durée | 223 s | 77 s |
| TaxRef | v17, 100 474 taxons (127 631 clés), 10 listes, aucune incomplète | idem |
| Nomenclatures | 444 valeurs | 444 valeurs |
| Jeux de données, listes, observateurs | 531, 10, 259 | 531, 10, 259 |
| Champs additionnels | 398 | 398 |
| Habitats HABREF (Occtax, OccHab) | 29 425, 29 425 | 29 425, 29 425 |
| Suivis | 14 protocoles | 14 protocoles |
| OccHab | détecté, aucune station serveur | idem |

**Hors ligne** (mode avion), après redémarrage de l'appli :
- les deux appareils relisent exactement les mêmes données ;
- une synchro lancée sans réseau échoue aussitôt (« Chargement interrompu : UnknownHostException… » sur l'A12, l'équivalent `NSURLErrorDomain -1009` sur l'iPhone) et n'efface rien.

**Le message hors ligne est celui de GeoMys-Android.** La lecture de la configuration Occtax s'authentifie hors de son `try` : appli fraîchement lancée, l'erreur réseau remonte au filet final. Avec une session encore en cache (moins de 5 minutes après une synchro), les étapes échouent une à une et le compte-rendu les énumère (`SyncRunnerTest`). C'est le défaut n° 6 du § 8.2 de l'inventaire, qu'on tenait pour « à confirmer » : il l'est. Il est corrigé au lot 8, la décision 5 tranchée : hors ligne, chaque étape échoue désormais à son tour, sans rien effacer ([`LOT-8.md`](LOT-8.md) § 5).

`configuration_complete=false` est attendu : aucun jeu de données, liste ni observateur n'est encore choisi. Ce sera le rôle de l'écran Paramètres.

## 8. Reste à faire

- **Lot 5, juste après** (décision 15 : écrans à l'identique de GeoMys-Android) :
  - Accueil et Paramètres, avec « Charger les données » branché sur `SyncRunner.lancer()` ;
  - la demande de permission de notification d'Android 13 ;
  - les gardes d'écran ci-dessus.
- **Lot 3** :
  - les caches de l'envoi dans `InvalidationCaches` ;
  - l'envoi Monitoring (`MonitoringEnvoi.kt`) ;
  - `OccHabStore`, avec `stationDepuisJson`.
- **Écrans Suivis.** Les résolveurs de libellés vides créés par les écrans devront recevoir le cache TaxRef (`LabelResolver(taxRef = …)`), global sous Android.

## 9. Outils et versions

- `outils/DiagnosticSynchro.kt` (core) et les paramètres `action=synchroniser`, `action=etat-donnees`.
- `ServicesDeTest`, `MonitoringDeTest`, `routerGeoNatureComplet` : les services réels sur doublures, pour les tests.
- `inventaire/releve-tests.tsv` gagne une colonne `statut_portage` : 48 tests Android sur 99 sont portés, lots 1 et 2 compris.
- Version ajoutée : androidx.core 1.18.0, déjà tirée par androidx.activity et désormais déclarée pour le service de synchro. atomicfu 0.33.0 est arrivé en début de lot.
