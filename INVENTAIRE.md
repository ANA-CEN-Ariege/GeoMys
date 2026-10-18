# GeoMys-Common — ce qui est partageable dans GeoMys-Android

*Inventaire établi le 18 octobre 2026 à partir de GeoMys-Android `cdd5196` (v1.4.7, versionCode 196), en lecture seule. GeoMys-IOS `64d303b` (v0.11) a été consulté pour repérer les équivalents iOS déjà écrits. Aucun fichier de ces deux applications n'a été modifié.*

> **Décisions du 18 octobre 2026**
> - **Application indépendante.** GeoMys Common a sa propre identité : identifiant `fr.ariegenature.geomys.common`, nom « GeoMys Common », même logo que GeoMys.
> - **Aucune donnée reprise.** Les données des applications existantes sont des données de test. Le § 6 ne s'applique donc plus, hormis les contrats côté serveur.
> - **Plan de rattrapage iOS hors sujet.**
>
> Le socle (lot 0) est en place : voir le [README](README.md).

**Objectif.** GeoMys-Common sera une nouvelle application. Elle aura une seule base de code, en Kotlin Multiplatform (KMP) et Compose Multiplatform, et produira GeoMys pour Android et pour iOS. Cet inventaire indique, fichier par fichier :
- ce qui peut être repris de GeoMys-Android ;
- à quel prix ;
- ce qu'il faudra écrire pour chaque plateforme.

**Contenu du dossier**
- Ce fichier, la synthèse : chiffres, dépendances, services plateforme, carte, données, tests, points durs, structure proposée, ordre de portage, décisions.
- [`inventaire/`](inventaire/), le détail par zone de code. Chaque fichier y a sa ligne : rôle, dépendances à remplacer avec les numéros de ligne Android, logique à extraire des écrans, points durs, formats de données.
  - [1. Réseau et synchronisation](inventaire/1-reseau-sync.md), dont la liste des endpoints GeoNature appelés ;
  - [2. Stockage, modèles, utilitaires, localisation](inventaire/2-stockage-modeles-utils.md), dont le schéma complet des données persistées ;
  - [3. Moteur de formulaires et écrans Monitoring](inventaire/3-formulaires-monitoring.md), dont le « renderer » Compose à écrire ;
  - [4. Écrans de saisie OccTax](inventaire/4-saisie-occtax.md) ;
  - [5. OccHab, édition géométrique et cartes](inventaire/5-occhab-cartes.md), dont le cahier des charges de la couche carte ;
  - [6. Interface transversale, variantes, ressources, build](inventaire/6-ui-transversale-ressources-build.md), dont la carte des 25 écrans ;
  - [7. Tests unitaires](inventaire/7-tests.md).
- [`inventaire/index-fichiers.tsv`](inventaire/index-fichiers.tsv), les 123 fichiers Kotlin avec leur catégorie. Il est prêt à servir de tableau de suivi du portage, avec les colonnes `commit_reference` et `statut_portage`.
- [`inventaire/releve-automatique.tsv`](inventaire/releve-automatique.tsv) et [`releve-tests.tsv`](inventaire/releve-tests.tsv), les relevés bruts (imports et API repérés par fichier).

**Catégories**

| Catégorie | Sens |
|---|---|
| COMMUN | Kotlin pur, qui passe tel quel, ou presque, dans `commonMain`. |
| COMMUN-ADAPTÉ | Passe dans `commonMain` une fois ses API JVM ou Android remplacées par leurs équivalents multiplateformes. |
| MIXTE | Mêle logique métier et UI ou plateforme. La logique part dans le code commun ; le reste est réécrit. |
| PLATEFORME | Dépend d'une API système. Il faut une interface commune, une implémentation Android et une implémentation iOS. |
| UI | Écran ou composant à réécrire en Compose. Le résultat est lui aussi commun aux deux plateformes. |
| ABANDON | Sans objet dans GeoMys-Common. |

---

## 1. En bref

- **Le cœur de l'application est portable.** Réseau, synchronisation, stockage, envois, moteur de formulaires et géométrie représentent environ **15 500 lignes de code sur 25 000 (62 %)**. Tout cela passe dans le code commun. Le plus gros du travail consiste en remplacements mécaniques, mais nombreux : plus de 500 appels org.json rien que dans `network/`, une cinquantaine d'appels HTTP, Gson, SharedPreferences, `java.io.File`, dates, verrous.
- **L'interface est entièrement à refaire en Compose.** Elle représente environ **6 750 lignes (27 %)**, et aucun des 40 layouts XML n'est réutilisable. Les nouveaux écrans seront communs aux deux plateformes. Mais les Fragments, les « renderers » de formulaires et les overlays de carte contiennent aujourd'hui **près de 6 000 lignes de logique métier**. Il faut d'abord les extraire dans des ViewModels communs : c'est là que se concentrent l'effort et le risque.
- **Le code propre à chaque plateforme est réduit.** Il compte environ **1 450 lignes (6 %)**, réparties en une vingtaine de services : position et suivi GPS en arrière-plan, boussole, dictée, photos, fichiers, coffre à mot de passe, tâches longues… GeoMys-IOS contient déjà la logique iOS de presque chacun d'eux : elle servira de modèle.
- **La carte est le point critique.** osmdroid, archivé depuis novembre 2024, sera remplacé par MapLibre. Mais maplibre-compose, encore en 0.x, n'offre pas de marqueur déplaçable prêt à l'emploi, alors que l'édition des géométries OccTax et OccHab en dépend. Il faut donc un **prototype préalable** avant tout écran carte (§ 5).
- **Aucune donnée à reprendre** (décision du 18 octobre). GeoMys Common est une application indépendante : ses formats de stockage peuvent être choisis librement. Seuls comptent les contrats avec le serveur : endpoints, anti-doublon, format ANA-EVAL lu par le plugin QGIS.
- **Les tests suivent.** 86 fichiers de test sur 99 (651 tests sur 718) passent dans `commonTest` après adaptation. 19 tests vérifient le texte du code des Fragments au lieu d'un comportement : ils sont à réécrire en vrais tests (§ 7).
- **L'inventaire a relevé des défauts dans le code Android actuel** (§ 8.2). Deux ont été vérifiés à la main :
  - en saisie mono-taxon, la naturalité et les champs « preuves, floutage, statut source » saisis dans Caractérisation sont perdus ;
  - un cache de champs additionnels devient illisible à chaque changement du mapping R8.

  Ils sont seulement signalés ici : rien n'a été touché dans GeoMys-Android.

---

## 2. Chiffres

### 2.1 Par zone du code, selon la catégorie du fichier entier

Les colonnes de catégorie sont en lignes de code.

| Zone du code | Fichiers | Lignes | Lignes de code | COMMUN | COMMUN-ADAPTÉ | MIXTE | PLATEFORME | UI | ABANDON |
|---|---|---|---|---|---|---|---|---|---|
| `network` | 25 | 8 509 | 5 427 | 354 | 5 073 | — | — | — | — |
| `sync` | 1 | 136 | 85 | — | — | — | 85 | — | — |
| `store` | 19 | 4 482 | 2 560 | — | 2 304 | 86 | 97 | — | 73 |
| `model` | 2 | 473 | 202 | — | 202 | — | — | — | — |
| `util` | 5 | 731 | 397 | 9 | 388 | — | — | — | — |
| `gpx` | 1 | 207 | 165 | — | 165 | — | — | — | — |
| `location` | 2 | 294 | 193 | — | — | 118 | 75 | — | — |
| racine (`TaxRefLocal`, `GeoMysApplication`) | 2 | 353 | 205 | — | 161 | 44 | — | — | — |
| `monitoring/form` | 7 | 2 411 | 1 508 | 345 | 189 | 974 | — | — | — |
| `ui/saisie` (champs de saisie) | 9 | 1 269 | 842 | 24 | 24 | 584 | 119 | 91 | — |
| `ui` : écrans OccTax ¹ | 13 | 7 380 | 5 462 | — | 168 | 4 968 | — | 326 | — |
| `ui` : écrans Monitoring | 5 | 2 799 | 1 975 | 50 | — | 901 | — | 1 024 | — |
| `ui` : OccHab et cartes | 17 | 6 215 | 4 273 | 28 | 152 | 777 | 76 | 3 240 | — |
| `ui` : transversal | 11 | 2 362 | 1 487 | — | — | 909 | 108 | 394 | 76 |
| variantes `github` / `play` | 4 | 426 | 259 | — | — | — | 110 | 143 | 6 |
| **Total** | **123** | **38 047** | **25 040** | **810** | **8 826** | **9 361** | **670** | **5 218** | **155** |
| *Part des lignes de code* | | | | *3 %* | *35 %* | *37 %* | *3 %* | *21 %* | *1 %* |

¹ Cette zone comprend aussi « Mes visites » (`SaisiesEnAttenteFragment`), un écran Monitoring.

- « Lignes » compte les lignes physiques, commentaires et en-têtes de licence compris.
- « Lignes de code » compte les lignes non vides, hors commentaires.
- Le code Android est commenté à environ 35 %. Ces commentaires documentent souvent des décisions de terrain datées : ils accompagnent la logique qu'ils expliquent.

### 2.2 Après découpe des fichiers MIXTE et UI (estimation, en lignes de code)

| Zone | Lignes de code | Logique → `commonMain` | Présentation → Compose (`commonMain`) | Plateforme (`androidMain` / `iosMain`) | Abandon, en-têtes, colle |
|---|---|---|---|---|---|
| 1. Réseau et synchronisation | 5 512 | ≈ 5 430 | — | ≈ 85 | — |
| 2. Stockage, modèles, utilitaires, localisation | 3 722 | ≈ 3 380 | — | ≈ 240 | ≈ 100 |
| 3. Formulaires et écrans Monitoring | 4 325 | ≈ 2 450 | ≈ 1 600 | ≈ 250 | ≈ 25 |
| 4. Écrans OccTax | 5 462 | ≈ 2 170 | ≈ 2 640 | ≈ 310 | ≈ 340 |
| 5. OccHab et cartes | 4 273 | ≈ 1 570 | ≈ 1 950 | ≈ 240 | ≈ 515 |
| 6. UI transversale et variantes | 1 746 | ≈ 515 | ≈ 575 | ≈ 330 ² | ≈ 325 |
| **Total** | **25 040** | **≈ 15 500 (62 %)** | **≈ 6 750 (27 %)** | **≈ 1 450 (6 %)** | **≈ 1 300 (5 %)** |

² Dont ≈ 205 lignes de mise à jour intégrée, propres à la variante github d'Android.

- **Une fois porté, environ 90 % du code vit dans `commonMain`** (logique et écrans Compose). Environ 6 % est écrit par plateforme. Environ 5 % disparaît : colle osmdroid, liaisons de vues, insets, code mort.
- Ces chiffres sont des estimations à ±15 %, faites à la lecture du code. Chaque fichier de zone en donne le détail.
- « COMMUN-ADAPTÉ » ne veut pas dire « copier-coller ». Par exemple, dans la zone 2, environ 1 300 des 5 670 lignes adaptées sont à réécrire (persistance, entrées-sorties, JSON, verrous).

### 2.3 Ressources

| Ressource | Quantité | Devenir |
|---|---|---|
| Layouts XML | 40 (5 407 lignes) | réécriture en Compose |
| Drawables vectoriels | 40 | 30 repris tels quels dans `composeResources` ; 10 à normaliser (attributs de thème `?attr/`, `@android:color`) |
| Shapes, selectors, inset | 19 | recodés en Compose |
| Images | logo (5 densités), icônes de lanceur | logo → `composeResources` ; icônes → module Android et `AppIcon` iOS |
| Chaînes | 25 dans `strings.xml`, environ 1 200 en dur (≈ 190 dans les layouts, ≈ 1 030 dans le Kotlin) | à décider (§ 11) |
| Thème | Material 3, mode sombre **forcé** (`MODE_NIGHT_YES`), 20 couleurs, 2 styles de bouton | un `GeoMysTheme` sombre commun |
| Navigation | 25 destinations, 46 actions (dont une morte), 20 arguments déclarés, des clés non déclarées passées en `Bundle` | routes typées `@Serializable` (navigation-compose multiplateforme) |
| Asset | `nidification_oiseaux.json` (48 Ko, identique à celui de GeoMys-IOS) | `composeResources/files` |

---

## 3. Dépendances à remplacer

| Android aujourd'hui | Usage relevé | Remplaçant multiplateforme | À savoir |
|---|---|---|---|
| `HttpURLConnection` | 16 fichiers, ≈ 50 appels | Ktor client (moteur OkHttp sur Android, Darwin sur iOS) | Timeouts posés requête par requête, d'inactivité et non de durée totale. Login sans suivre les redirections. Aucun rejeu des POST. Liste des quelque 37 routes appelées : `inventaire/1-reseau-sync.md` § 3. |
| `org.json` | 20 fichiers ; plus de 500 appels `opt*`/`get*` dans `network/` | kotlinx.serialization (`JsonElement`) et une petite couche `JsonCompat` | La sémantique d'org.json doit être reproduite (`optString` renvoie `"null"`, conversions implicites…). Des tests de caractérisation la verrouillent. |
| Gson | 19 fichiers : persistance, `TypeToken`, lecture en flux | kotlinx.serialization (`@Serializable`) | Décoder élément par élément pour garder la quarantaine des entrées abîmées. Les règles R8 propres à Gson disparaissent. |
| `SharedPreferences` | 20 fichiers ; une dizaine de fichiers de préférences (`gn_config`, `GeoMys_prefs`, `sorties_store`, `occhab_store`, `taxref_cache`…) | multiplatform-settings | Pour les réglages simples. Les saisies (`sorties_store`, `occhab_store`) vont dans des fichiers atomiques. |
| `java.io.File`, `Context.filesDir` | ≈ 30 fichiers | okio (ou kotlinx-io), avec des racines fournies par la plateforme | Écriture atomique et quarantaine. Le `fsync` est à garantir sur iOS. |
| `synchronized`, `@Synchronized`, `@Volatile`, `ConcurrentHashMap`, `Atomic*` | 19 fichiers | `Mutex` (coroutines) ou atomicfu | **`@Synchronized` compile en code commun mais ne verrouille rien sur iOS.** Décision structurante (§ 11). |
| `SimpleDateFormat`, `Calendar`, `java.time` | 22 fichiers | kotlinx-datetime et de petits parseurs tolérants | Plusieurs formats « lenient » à reproduire. Le fuseau de l'appareil doit être explicite. |
| `java.util.UUID` | 15 fichiers | `kotlin.uuid.Uuid`, plus un MD5 commun pour l'UUID v3 | L'UUID v3 des relevés doit être identique au bit près : c'est lui qui évite les doublons. |
| `android.util.Log` | 27 fichiers | Kermit (ou Napier) | — |
| `String.format`, `Math.*`, `putIfAbsent`, `javaClass`, `System.currentTimeMillis`… | 49 fichiers | formatage maison, `kotlin.math`, stdlib, `Clock.System` | Petites API réservées à la JVM : elles cassent la compilation iOS. |
| `java.text.Normalizer`, `Collator` | 7 fichiers | expect/actual, ou clé de tri maison | Recherche insensible aux accents, tri à la française. |
| `javax.xml` (SAX, pour le GPX) | 1 fichier | xmlutil, ou un mini-parseur sans DTD | — |
| SQLite Android (MBTiles) | 1 fichier | androidx.sqlite KMP (pilote embarqué) | — |
| `LiveData`, `AndroidViewModel` | 9 fichiers | `StateFlow`, `ViewModel` multiplateforme (lifecycle ≥ 2.8) | Les listes aujourd'hui modifiées en place doivent devenir immuables, sinon `StateFlow` n'émet rien. |
| Fragments, ViewBinding, Navigation component, Material Components | 44 fichiers | Compose Multiplatform (Material 3) et navigation-compose | — |
| osmdroid | 18 fichiers (83 imports) | MapLibre (maplibre-compose, derrière une façade maison) | Voir § 5. |

---

## 4. Services à fournir par chaque plateforme

| Interface commune proposée | Rôle | Android (code actuel) | iOS (équivalent ; modèle dans GeoMys-IOS) |
|---|---|---|---|
| `SourcePosition`, avec un `EnregistreurParcours` commun | position, parcours, distance | `LocationManager` GPS + réseau (`location/LocationTracker.kt`). **Garder LocationManager** plutôt que le fournisseur « fusionné », pour accepter les positions fictives des récepteurs GNSS RTK externes | `CLLocationManager` — `Location/LocationTracker.swift` |
| `SuiviArrierePlan` | garder le GPS actif pendant une sortie | service au premier plan de type `location`, avec notification (`location/LocationForegroundService.kt`) | `allowsBackgroundLocationUpdates`, `UIBackgroundModes = location` — `LocationTracker.swift` |
| `ExecutionLongue` | mener à terme une synchro ou un envoi | service au premier plan de type `dataSync` (`sync/SyncForegroundService.kt`) | pas d'équivalent : `beginBackgroundTask` (≈ 30 s) et veille de l'écran désactivée (`Network/SyncRunner.swift`). `BGContinuedProcessingTask` (iOS 26) est à prototyper |
| `Permissions` | localisation, micro, notifications | `ActivityResultContracts` | autorisations CoreLocation, Speech, AVAudio |
| `CapAppareil` | boussole, carte qui suit le cap | `SensorManager` (`ui/MapCompassController.kt`) : nord magnétique | `CLHeading` (`LocationTracker.swift`) : nord géographique |
| `DicteeVocale` | dictée du nom d'espèce (fr-FR, 5 hypothèses, repli hors-ligne) | `SpeechRecognizer` (`ui/saisie/SpeechToTextHelper.kt`) | `SFSpeechRecognizer` — `UI/Saisie/DicteeVocale.swift` |
| `SelecteurMedias`, `CapturePhoto`, `ImportMedia` | galerie, appareil photo, recompression JPEG (2048 px, qualité 85) | `GetMultipleContents`, `TakePicture` + FileProvider, BitmapFactory + EXIF (`ui/MediaImport.kt`, `ui/PhotoCapture.kt`) | PhotosPicker, `UIImagePickerController`, ImageIO — `UI/Saisie/MediaPickerView.swift`, `Store/MediaLocal.swift` |
| `SelecteurFichier` | import GPX et MBTiles | `ACTION_OPEN_DOCUMENT` (SAF) | `fileImporter`, sélecteur de documents |
| `PartageFichier` | export GPX | FileProvider + `ACTION_SEND` | `UIActivityViewController` — `UI/SortieDetailView.swift` |
| `RetourHaptique` | vibration courte en saisie mono | `Vibrator`, 60 ms | `UIImpactFeedbackGenerator` (ou `LocalHapticFeedback` de Compose) |
| `CoffreMotDePasse` | mot de passe GeoNature | AES-256-GCM dans l'Android Keystore (`store/MdpChiffre.kt`), à garder tel quel | Keychain — `KeychainHelper` dans `Store/GeoNatureConfig.swift` |
| moteur HTTP et classement des erreurs réseau | client Ktor | OkHttp, exceptions `java.net` | Darwin, erreurs `NSURLErrorDomain`, cookies gérés à la main (`Network/HttpClient.swift`) |
| `Texte` | décomposition NFD, tri à la française | `Normalizer`, `Collator` | `decomposedStringWithCanonicalMapping`, `compare(options:locale:)` |
| `CacheCarte`, `TelechargeurZone` | cache des tuiles, zones hors-ligne | aujourd'hui osmdroid, demain l'`OfflineManager` de MapLibre Android | `MLNOfflineStorage` de MapLibre iOS |
| `RacinesStockage` | dossiers de données, de cache, de médias | `filesDir`, `cacheDir` | Application Support, Documents, Caches |
| `InfosApp` | version, build, canal, mode debug | `PackageManager`, `BuildConfig` | `Bundle`, `Version.swift` |
| `MiseAJourIntegree` (variante github seulement) | mise à jour par APK | `MiseAJour.kt` et son écran | aucun (App Store, TestFlight) |
| `Connectivite` | dictée en ligne ou hors-ligne | `ConnectivityManager` | `NWPathMonitor` |

- **Android** : ces services représentent environ 1 450 lignes de code aujourd'hui. Elles sont pour l'essentiel réutilisables presque telles quelles dans `androidMain`.
- **iOS** : l'équivalent est à écrire en Kotlin (`iosMain`), ou en Swift appelé depuis Kotlin. GeoMys-IOS en fournit déjà la logique et les réglages : autorisations, clés `Info.plist`, mode d'arrière-plan `location`.

---

## 5. La carte : cahier des charges et prototype préalable

Le détail, avec les références dans le code, est dans [`inventaire/5-occhab-cartes.md`](inventaire/5-occhab-cartes.md) § 3 et [`4-saisie-occtax.md`](inventaire/4-saisie-occtax.md) § 3.

**Ce que les écrans utilisent aujourd'hui**
- **Fonds** :
  - raster en ligne : OSM, OpenTopoMap, IGN Plan, SCAN 25 et Ortho (WMTS Géoplateforme), Esri ;
  - choix mémorisé dans la clé `fond_carte` ;
  - fonds MBTiles hors-ligne importés par l'utilisateur ;
  - cache des tuiles et téléchargement de zones (≤ 200 km², jusqu'au zoom 17).
- **Dessin** :
  - marqueurs à icône ;
  - polylignes, dont la trace GPS, prolongée au fil de l'eau ;
  - polygones à trous ;
  - tap sur une forme (hit-test).
- **Marqueurs et sommets déplaçables** : appui long puis glisser, forme redessinée en direct, aimantage au relâcher (à 44 dp).
- **Navigation dans la carte** : position de l'utilisateur, boussole et rotation selon le cap, cadrage sur une emprise, événements de caméra (Explorer, Maps Manager).
- **Regroupement** des observations de l'Explorer, sur une grille fixe de 0,001°.

**Ce que MapLibre apporte**
- **Couvert nativement** : sources raster, couches, trous, cadrage, caméra, paquets hors-ligne.
- **Incertain ou absent de maplibre-compose** :
  - le **glisser de marqueurs**, absent, donc à construire ;
  - l'accès aux paquets hors-ligne et au cache ;
  - l'en-tête User-Agent ;
  - les sources `mbtiles://` sur les deux systèmes ;
  - le point de localisation ;
  - sur iOS, le partage des gestes entre Compose et la vue carte native.

**Recommandation : un prototype de quelques jours avant tout écran carte**, sur cinq sujets :
1. glisser un sommet, avec la forme redessinée en direct ;
2. afficher un fond MBTiles ;
3. télécharger une zone hors-ligne ;
4. fixer l'en-tête User-Agent ;
5. suivre le cap et faire tourner la carte.

Isoler ensuite maplibre-compose derrière une façade `carte/` propre à GeoMys-Common, puisque son API peut encore changer. Cette couche carte commune est estimée à 400 à 600 lignes.

> **Résultat du prototype, le 18 octobre** ([`PROTOTYPE-CARTE.md`](PROTOTYPE-CARTE.md)). **MapLibre est retenu.**
>
> | Essai | Android | iOS |
> |---|---|---|
> | Glisser d'un sommet | ✅ (dont un Galaxy A12) | ✅ (iPhone 16, au doigt) |
> | Fond MBTiles | ✅ | ✅ |
> | Zone hors-ligne | ✅ | ✅ |
> | Cap et rotation | ✅ | ✅ (iPhone 16 ; rotation fluide grâce à un lissage commun) |
> | User-Agent | ❌ | ❌ |
>
> Pour le User-Agent, les en-têtes de l'intercepteur ne sont pas appliqués en 0.20.0. Le contournement prévu consiste à servir les tuiles par un `MapResourceProvider` basé sur Ktor.
>
> Avec maplibre-compose 0.20.0, la carte est dessinée dans une surface Compose sur les deux plateformes : le partage des touchers avec une vue native n'est plus un sujet.

**Conséquences à prévoir**
- Le cache osmdroid (`cache/MapTiles/cache.db`) est illisible par MapLibre. Les zones déjà téléchargées seront perdues, sauf à les convertir en MBTiles (conversion à évaluer).
- L'application actuelle autorise le téléchargement en masse de tuiles openstreetmap.org, ce que la politique d'usage d'OSM interdit. À régler dans GeoMys-Common : exclure OSM du téléchargement de zones, ou changer de fournisseur.
- L'attribution des fonds ne semble jamais affichée aujourd'hui (§ 8.2, n° 8) ; MapLibre sait l'afficher nativement.

---

## 6. Données à reprendre si GeoMys-Common remplace l'application Android

> **Sans objet depuis la décision du 18 octobre** : GeoMys Common est indépendante et ne reprend aucune donnée. Cette section reste comme référence des formats de GeoMys-Android.
>
> Deux règles continuent de s'appliquer :
> - les identités envoyées au serveur restent déterministes **au sein de GeoMys Common**, pour l'anti-doublon : UUID des relevés, titres des médias ;
> - le bloc ANA-EVAL garde le format lu par le plugin QGIS.

**Conditions**
- Les mêmes applicationId, une par variante : github `fr.ariegenature.geomys`, play `fr.ariegenature.public.geomys`.
- La même clé de signature.
- Un versionCode ≥ 197 (l'actuel est 196).
- Pour la variante github, une release publiée sur le même dépôt GitHub : c'est là que l'écran de mise à jour actuel ira chercher la nouvelle version.

| Donnée | Emplacement Android | Format | Reprise |
|---|---|---|---|
| Configuration GeoNature | `shared_prefs/gn_config.xml` (34 clés) | types simples, JSON Gson, JSON serveur brut, CSV | **obligatoire** : relue telle quelle par multiplatform-settings |
| Mot de passe | `shared_prefs/gn_secure_v2.xml`, plus l'Android Keystore (alias `geomys_mdp`) | AES-256-GCM, IV de 12 octets en tête, Base64 | **obligatoire** : code Android repris tel quel |
| Saisies OccTax (« Mes saisies ») | `shared_prefs/sorties_store.xml`, clé `sorties_sauvegardees` | Gson, `List<Sortie>` | **obligatoire** (données irremplaçables) ; migration unique vers un fichier atomique |
| Saisies OccHab (« Mes stations ») | `shared_prefs/occhab_store.xml`, clé `saisies_sauvegardees` | Gson, `List<OccHabSaisie>` | **obligatoire** ; même migration |
| File d'envoi Monitoring (« Mes visites ») | `files/monitoring_outbox.json` | Gson, `List<SaisieEnAttente>` | **obligatoire** |
| Médias joints | `files/medias/`, référencés par URI absolue `file:/data/user/0/<appId>/files/medias/…` | fichiers | **obligatoire** : résolveur tolérant ; chemins relatifs pour les nouveaux médias |
| Fonds MBTiles importés | `files/mbtiles/*.mbtiles`, et `fond_carte` dans `GeoMys_prefs` | SQLite MBTiles | **obligatoire** : l'application ne peut pas les re-télécharger |
| Préférences | `GeoMys_prefs` : `enregistrer_trace`, `saisie_dernier_taxon`, `saisie_nom_sci`, `fond_carte` | types simples | **obligatoire** (mêmes clés que dans l'iOS actuel) |
| Relevés orphelins | `releves_orphelins` | CSV par serveur | recommandée |
| TaxRef | `files/taxref/` (8 fichiers), plus `taxref_cache` | JSON | recommandée : sinon, rechargement réseau obligatoire avant toute saisie hors-ligne |
| Nomenclatures, HABREF, stations serveur, caches Monitoring, pictos | `nomenclature_cache`, `files/habitats_*.json`, `files/stations_serveur_occhab_v1.json`, `files/monitoring/` | JSON | recommandée (tout est re-téléchargeable) |
| Cache de tuiles osmdroid | `cache/MapTiles/` | SQLite propre à osmdroid | **perdue** (illisible par MapLibre), sauf conversion |

**Règles pour relire sans rien perdre** (détail : [`inventaire/2-stockage-modeles-utils.md`](inventaire/2-stockage-modeles-utils.md) § 4)
- **Désérialisation** : kotlinx.serialization avec les **mêmes noms de champs** que les classes Kotlin actuelles, et `ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`.
- **Décodage élément par élément** : une entrée abîmée est écartée et mise en quarantaine, au lieu de faire perdre toute la liste.
- **Identités dérivées, identiques au bit près** :
  - l'UUID v3 des relevés (`"geomys-releve:" + id`, en MD5) ;
  - les clés normalisées de TaxRef (`normaliser()`) ;
  - l'empreinte d'origine des stations OccHab (`Double.toString` est à vérifier sous Kotlin/Native) ;
  - les clés des caches Monitoring ;
  - les titres des médias Monitoring.
- **Cache `gn_cache_add_fields_occtax`** : il contient des `Pair` dont R8 a renommé les champs (`f`, `g`). L'ignorer et le reconstruire à la première synchro.
- **Vecteurs de test** : avant la première publication, rejouer des fichiers réels produits par la version 1.4.7.

**Côté iOS.** GeoMys-Common pourrait aussi devoir reprendre les données de GeoMys-IOS, en gardant son bundle id `fr.ariegenature.GeoMys-IOS`. Les formats et les clés y diffèrent :
- 18 des 34 clés de configuration sont renommées ;
- certains champs sont typés au lieu d'être rangés dans des maps ;
- les dossiers ne sont pas les mêmes.

Il faudrait alors une table de correspondance. L'autre solution est une installation neuve, après avoir fait envoyer les saisies en attente (§ 11).

---

## 7. Tests

Le détail est dans [`inventaire/7-tests.md`](inventaire/7-tests.md). Les 99 fichiers de test sont des tests JVM (JUnit 4) ; il n'y a aucun test instrumenté.

| Cat. | Nature | Fichiers | Lignes | `@Test` | Devenir |
|---|---|---|---|---|---|
| T1 | code de test portable tel quel | 20 | 2 566 | 199 | `commonTest` (passage à kotlin.test) |
| T2 | fixtures JSON à convertir | 9 | 812 | 60 | `commonTest` |
| T3 | Robolectric utilisé seulement pour `Context`, SharedPreferences, fichiers ou `GeoPoint` | 38 | 5 386 | 241 | `commonTest`, avec `FakeFileSystem` (okio) et `MapSettings` |
| T4 | réseau simulé par MockWebServer | 19 | 4 404 | 151 | `commonTest`, avec le `MockEngine` de Ktor (mêmes scénarios) |
| T5 | code propre à Android (vues, `LocationTracker`, `MediaImport`) | 6 | 1 059 | 35 | `androidUnitTest` ou réécriture |
| T6 | surtout des gardes textuelles | 6 | 735 | 29 | réécriture en tests de comportement |
| T7 | contrat Gson | 1 | 227 | 3 | remplacé par un test d'aller-retour kotlinx des formats de GeoMys Common |

- **Gardes textuelles.** 19 tests, répartis dans 12 fichiers, lisent le code source des Fragments (`File("src/main/java/…").readText()`). Ils protègent des règles impossibles à tester tant qu'elles vivent dans les écrans. Dans GeoMys-Common, chacune devient un vrai test sur le ViewModel qui la porte (liste : `7-tests.md` § 7.3).
- **Référence des tests JSON.** Les tests actuels tournent sur JSON-java (`org.json:json:20240303`), qui ne se comporte pas comme l'org.json d'Android : par exemple, `optString` sur un null. Les tests de caractérisation de `JsonCompat` devront prendre le comportement **réel** d'Android comme référence.

---

## 8. Points durs, défauts relevés, divergences

### 8.1 Points durs, du plus risqué au moins risqué

1. **La carte** (§ 5) : glisser de sommets, arbitrage des gestes, aimantage en pixels (projection de milliers de cibles), hors-ligne, MBTiles, cap.
2. **La logique enfouie dans les écrans** : près de 6 000 lignes à extraire des Fragments, des renderers et des overlays.
   - Les écrans communiquent par `savedStateHandle` et JSON Gson : une quinzaine de clés de résultat, une trentaine de clés d'argument. Un ViewModel de session les remplace.
   - La sauvegarde automatique des brouillons est déclenchée par l'affichage. Elle doit passer dans le ViewModel, sur événement.
3. **La sémantique d'org.json** : elle est à reproduire dans `JsonCompat`. Le bloc ANA-EVAL doit même produire le même JSON octet pour octet (échappement de `/`, nombres entiers sans `.0`).
4. **La concurrence.** `synchronized` n'existe pas en code commun pour iOS, et `@Synchronized` y compile sans rien verrouiller. Il faut choisir tôt entre des verrous atomicfu et des API `suspend` protégées par `Mutex`.
5. **La durabilité des saisies.**
   - Le résultat de `commit()` remonte aujourd'hui jusqu'à l'UI, et multiplatform-settings ne l'expose pas : les saisies passent donc en fichiers atomiques.
   - Le `fsync` est à garantir sur iOS.
6. **Les identités dérivées** (UUID des relevés, titres des médias), qui doivent rester déterministes : l'anti-doublon côté serveur en dépend.
7. **La mémoire sur iOS.**
   - Les index TaxRef montent à 100 000 taxons et plus, et iOS n'a pas d'équivalent à `largeHeap` : GeoMys-IOS a déjà observé un pic de plus de 200 Mo.
   - Des Regex sont recompilées à chaque frappe.
   - Le ramasse-miettes de Kotlin/Native se comporte autrement que celui de la JVM.
   - Mesurer tôt, sur un vrai iPhone.
8. **L'arrière-plan sur iOS** : rien n'équivaut au service au premier plan de la synchro (§ 4).
9. **HTTP et authentification avec le moteur Darwin.**
   - Couper la gestion automatique des cookies.
   - Découper les `Set-Cookie` fusionnés.
   - Ne pas suivre les redirections au login.
   - Ne pas fixer de plafond de durée totale aux requêtes.
   - `Connection: close` est un en-tête réservé d'URLSession, à vérifier.
10. **Les formulaires dynamiques.**
    - L'état vit aujourd'hui dans les vues (tags). Il faut un `FormulaireEtat` explicite, qui respecte exactement le contrat de types des valeurs envoyées.
    - Les dates sont saisies et relues en mode tolérant (« lenient »).
    - Le champ taxon porte sur 15 000 à 50 000 noms.
11. **La navigation.**
    - Il faut des routes typées.
    - La règle « quitter le flux de saisie arrête le GPS » est à reproduire.
    - Le blocage du retour (`BackHandler` est expérimental) et le geste de retour d'iOS sont à gérer.
12. **Les médias** : passer des URI absolues aux chemins relatifs, et recompresser les photos sur chaque plateforme.

### 8.2 Défauts constatés dans le code Android actuel

Ils sont signalés pour décision : les corriger dans GeoMys-Android, qui reste en production, et ne pas les reproduire dans GeoMys-Common. « Vérifié » signifie relu à la main pendant la synthèse ; les autres viennent de la lecture du code, avec leurs références.

| # | Constat | Références | Statut |
|---|---|---|---|
| 1 | En saisie mono-taxon, la naturalité et les champs `occExtraJson` (statut source, floutage, preuves) renvoyés par Caractérisation ne sont jamais lus ni posés sur l'observation : ils sont perdus. | `CaracterisationFragment.kt:228-251` ; aucune lecture dans `SaisieRapideFragment.kt` | vérifié |
| 2 | R8 renomme les champs de `kotlin.Pair` (`a`/`b` jusqu'en 1.4.2, `f`/`g` depuis la 1.4.3). Le cache Gson `gn_cache_add_fields_occtax` devient donc illisible après chaque changement de mapping, jusqu'à la synchro suivante. | `mappings/mapping-1.4.7.txt` (`kotlin.Pair -> mf0`) ; `AdditionalFieldDef.nomenclatureOptions` | vérifié |
| 3 | `HabitatCache.kt` contient un caractère NUL littéral : grep et d'autres outils prennent le fichier pour un binaire. | `store/HabitatCache.kt:100` | vérifié |
| 4 | La date des « Détails communs » vaut toujours l'heure d'ouverture du dialogue, et elle écrase une date a posteriori armée ensuite. iOS ne pose la date que si elle a été modifiée. | `DetailsReleveDialog.kt:373-375, 481-490` ; `SaisieObservationFragment.kt:285-306` | à confirmer |
| 5 | Le total d'individus diffère selon l'écran : tous les dénombrements dans le relevé, le seul dénombrement n° 0 dans les listes. | `SaisieObservationFragment.kt:405` contre `ObservationsFragment.kt:50`, `SortiesFragment.kt:292`, `SortieDetailFragment.kt:127` | à confirmer |
| 6 | Hors-ligne, une `IOException` levée par la synchro des settings OccTax (appel lancé hors de tout try) annule toute la phase A de « Recharger les données ». | `SyncRunner.kt:140-143` ; `GeoNatureSync.kt:557` | à confirmer |
| 7 | « Continuer la saisie » ouvre toujours la carte multi-taxons, même pour une sortie saisie en mono. | `SortiesFragment.kt:111-117` | à confirmer |
| 8 | Le téléchargement de zones contourne la politique d'osmdroid et télécharge en masse des tuiles openstreetmap.org, contrairement à la politique d'usage d'OSM. L'attribution des fonds n'est jamais affichée. | `ui/MapUtils.kt:106-110, 132, 187, 196` | à confirmer |
| 9 | La carte Monitoring dessine les trous des polygones comme des polygones pleins superposés. | `CarteGeometrieFragment.kt:312-317` | à confirmer |
| 10 | L'Explorer affiche « 12 mois » en dur, alors que la profondeur d'historique vient du serveur. | `ExplorerFragment.kt:392, 490` | à confirmer |
| 11 | Deux écritures simultanées de la même clé du cache Monitoring partagent le même fichier `.tmp`. | `store/MonitoringCache.kt` | à confirmer |
| 12 | La permission `WRITE_EXTERNAL_STORAGE` n'est utilisée nulle part. | `AndroidManifest.xml` | à confirmer |
| 13 | Les tests JVM ne tournent pas sur l'org.json d'Android mais sur JSON-java, dont le comportement diffère. | `app/build.gradle.kts` (`org.json:json:20240303`) | à confirmer |

S'y ajoutent du code mort et quelques commentaires ou documents périmés. Par exemple, le README décrit encore la « topologie partagée » entre polygones, retirée le 18 octobre. Ils sont listés dans les fichiers de zone.

### 8.3 Divergences entre Android et iOS à trancher

GeoMys-Common remplacera les deux applications : il faut une seule règle par sujet.

**Proposition par défaut** : suivre Android, qui est la référence fonctionnelle, sauf si iOS a corrigé un vrai défaut.

| Sujet | Android | iOS | Proposition |
|---|---|---|---|
| Réponse 401 | le cache de session est seulement invalidé | reconnexion, puis un nouvel essai | nouvel essai unique (sans risque : un 401 n'a rien créé) |
| Cookies, échec du login | `Set-Cookie` réduits à `nom=valeur` ; redirections désactivées ; diagnostic des 3xx | en-tête brut ; redirections suivies ; détection d'une page HTML | Android |
| Clé du cache d'authentification | (serveur, identifiant) | (serveur, identifiant, mot de passe) | Android (l'écran Paramètres invalide le cache) |
| UUID d'occurrence | v4 aléatoire, persisté | dérivé de l'id de l'observation | Android ; dériver les UUID des saisies iOS en attente si on les migre |
| Titres des médias Monitoring | déterministes, avec réconciliation | suffixe « (i) », sans réconciliation | Android |
| Altitudes OccHab (MNT) | présentes | absentes | Android |
| Protection des envois en arrière-plan | aucune | `beginBackgroundTask` | iOS (`ExecutionLongue` pour les deux) |
| Passerelle saisie multi → mono | supprimée le 17 octobre (décision produit) | encore présente (appui long sur ➕) | Android |
| JDD et `id_digitiser` des formulaires Monitoring | option unique auto-sélectionnée ; `id_digitiser` posé à l'envoi | JDD OccTax présélectionné ; `id_digitiser` posé à l'ouverture | à trancher |
| Date des « Détails communs » | toujours posée (§ 8.2, n° 4) | posée seulement si modifiée | iOS |
| Nord de la boussole | magnétique | géographique | à trancher (géographique conseillé) |
| Dessin OccHab : tap sur une station voisine | la sélection gagne (le tracé est abandonné) | l'aimantage gagne | iOS |
| Arrêt du GPS en quittant la saisie | sur 7 destinations (multi et mono) | seulement en quittant la carte multi | Android |
| Liste des protocoles Monitoring | interroge le serveur à chaque ouverture | cache d'abord | à trancher |
| `http://` | accepté, avec avertissement | refusé (ATS) | à trancher (§ 11) |
| Clavier numérique | décimales et négatifs possibles | `numberPad` : ni décimale ni signe moins (défaut iOS) | Android |

---

## 9. Structure proposée pour GeoMys-Common

```
GeoMys-Common/
├── core/          KMP (Android + iOS), sans Compose : modèles, réseau, stockage, synchro,
│                  envois, TaxRef, moteur de formulaires, géométrie, GPX, ViewModels,
│                  interfaces des services plateforme
│   └── src/commonMain · commonTest · androidMain · iosMain
├── ui/            KMP + Compose Multiplatform : écrans, thème, navigation, composants,
│                  façade carte (MapLibre), ressources (composeResources)
│   └── src/commonMain · androidMain · iosMain
├── androidApp/    application Android : MainActivity, manifeste, services au premier plan,
│                  variantes github et play (mise à jour intégrée dans github seulement)
├── iosApp/        projet Xcode : point d'entrée qui affiche l'UI Compose, Info.plist
│                  (autorisations, mode d'arrière-plan « location »)
├── gradle/libs.versions.toml · settings.gradle.kts · build.gradle.kts
└── INVENTAIRE.md · inventaire/
```

**Pourquoi cette découpe**
- **`core` ne dépend pas de Compose.** La logique se compile et se teste sans l'UI, ce qui est plus rapide, surtout sous Kotlin/Native. Le sens des dépendances est garanti : le métier ne voit jamais l'écran. dolibarr-mobile suit déjà ce principe avec `core-domain`.
- **Les ViewModels vont dans `core`.** lifecycle-viewmodel est multiplateforme et indépendant de Compose. On peut donc tester en `commonTest` les règles aujourd'hui protégées par des gardes textuelles.
- **`androidApp` est un module séparé.** Avec AGP 9, le plugin Android application ne se combine plus avec KMP dans un même module : les variantes github et play vivent donc dans ce module à part. Point à confirmer dans la documentation AGP 9 au moment du squelette.
- **La façade `carte/` isole maplibre-compose**, dont l'API peut encore changer.
- **Les colonnes « Cible » des fichiers de zone** utilisent parfois d'autres noms de module (`shared`, `composeApp`) : elles indiquent le paquetage visé, la répartition ci-dessus fait foi.

**Bibliothèques pressenties** (versions à figer au moment du squelette, sur les dernières stables) : Compose Multiplatform (Material 3), lifecycle-viewmodel et navigation-compose multiplateformes, kotlinx-coroutines, kotlinx-serialization-json (et json-okio), kotlinx-datetime, Ktor client (OkHttp, Darwin), okio, multiplatform-settings, atomicfu, androidx.sqlite (pilote embarqué), maplibre-compose, Kermit, Coil 3. Pour les tests : kotlin.test, kotlinx-coroutines-test, Ktor `MockEngine`, okio `FakeFileSystem`.

---

## 10. Ordre de portage proposé

Chaque lot suit le même cycle : il porte son code depuis le commit Android noté, porte ou écrit ses tests, et compile et tourne sur Android **et** iOS avant d'être clos.

| Lot | Contenu | Tests | Critère de sortie |
|---|---|---|---|
| 0 | Squelette Gradle (core, ui, androidApp, iosApp). **Prototype carte** (§ 5). Mesure mémoire de TaxRef sur iPhone. CI | premier test commun (`CompatibiliteServeur`) | **squelette et prototype carte faits le 18 octobre** ([`PROTOTYPE-CARTE.md`](PROTOTYPE-CARTE.md)). Restent la mesure mémoire et la CI |
| 1 | Fondations : modèles `@Serializable`, `JsonCompat`, stockage atomique et quarantaine, verrous, dates, UUID, logs, coffre, client Ktor et authentification | T1, T2, aller-retour JSON | connexion et authentification sur un serveur GeoNature de test |
| 2 | Référentiels et synchro : TaxRef (index et stockage), nomenclatures, JDD, listes, observateurs, HABREF, Monitoring (modules, schémas, objets, datalists), `SyncRunner`, `ExecutionLongue` | T3, T4 (synchro) | synchro complète sur un serveur de test, en ligne et hors-ligne |
| 3 | Envois : OccTax (anti-doublon, médias, orphelins), file Monitoring, OccHab | T4 (envois et réseau dégradé) | tous les scénarios anti-doublon passent |
| 4 | Moteur de formulaires : `FormulaireEtat`, champ taxon, champs additionnels | tests du moteur | mêmes valeurs envoyées qu'Android |
| 5 | UI socle : thème sombre, navigation typée, composants (fil d'Ariane, messages, dialogues, listes à recherche), Accueil, Paramètres | gardes textuelles de Paramètres réécrites | configuration et synchro depuis l'UI |
| 6 | OccTax : carte multi, saisie mono, relevé, dénombrement, caractérisation, détails, Mes saisies, détail d'une sortie, GPX | gardes textuelles OccTax réécrites | recette terrain OccTax |
| 7 | Monitoring : Suivis, détail, fiche, nouvelle visite, Mes visites, carte des géométries | FormulaireRendererTest réécrit | recette terrain Monitoring |
| 8 | OccHab : carte d'édition (point dur), station, habitat, Mes stations | tests de l'éditeur (modèle : `OccHabEditeurTests` iOS) | recette terrain OccHab |
| 9 | Explorer, Maps Manager (hors-ligne, MBTiles) | — | — |
| 10 | Distribution : APK de test ou piste interne Play pour Android, TestFlight pour iOS | recette sur appareils réels | première version distribuée aux testeurs |

---

## 11. Décisions à prendre

1. ~~Remplacer l'application Android en place~~. **Tranché le 18 octobre** : application indépendante, sans reprise de données.
2. ~~Sort de GeoMys-IOS~~. **Tranché le 18 octobre** : GeoMys Common a son propre bundle id et ne reprend pas les données de GeoMys-IOS.
3. ~~Le plan de rattrapage iOS~~. **Hors sujet** (décision du 18 octobre).
4. **Référence fonctionnelle.** Android, recommandé ; puis trancher chaque divergence du § 8.3.
5. **Défauts Android** (§ 8.2) : les corriger dans GeoMys-Android, dans GeoMys-Common seulement, ou dans les deux ?
6. **Carte.** MapLibre via maplibre-compose, derrière une façade, après le prototype : recommandé.
7. **Concurrence.** API `suspend` protégées par `Mutex` pour les stores, puisque tous leurs appelants seront réécrits (ViewModels) ; atomicfu seulement pour les caches en mémoire. Recommandé.
8. **Stockage.** Garder des fichiers JSON pour un portage fidèle, en sortant les saisies des SharedPreferences. Pour TaxRef, rester en JSON au départ, et passer à SQLite seulement si la mesure mémoire sur iPhone l'impose.
9. **`http://` sur iOS.** Le refuser (statu quo iOS), ou ouvrir une exception ATS ?
10. **Chaînes.** Garder le français en dur dans le code commun, comme aujourd'hui (portage plus fidèle et plus rapide), ou externaliser les quelque 1 200 libellés dans `composeResources` ?
11. **Restauration après la mort du processus.** Des brouillons sauvegardés automatiquement dans le code commun, qui fonctionnent sur les deux OS, plutôt que `SavedStateHandle`. Recommandé.
12. **Synchro en arrière-plan sur iOS.** `beginBackgroundTask` et écran maintenu allumé dans un premier temps ; `BGContinuedProcessingTask` (iOS 26) à prototyper.
13. **Tuiles OSM.** Les exclure du téléchargement de zones.
14. **Distribution Android.** APK de test ou piste interne Play ? La mise à jour intégrée par GitHub n'est utile que si l'APK est distribué hors Play.

---

## 12. Suivre les évolutions de GeoMys-Android pendant le portage

GeoMys-Android continue d'évoluer : dix commits le 18 octobre. GeoMys-Common ne la modifie jamais ; elle la lit seulement.

- Chaque fichier porté garde dans [`inventaire/index-fichiers.tsv`](inventaire/index-fichiers.tsv) le commit Android dont il provient (`commit_reference`, aujourd'hui `cdd5196`).
- Avant de clore un lot, on liste ce qui a changé depuis, en lecture seule :
  `git -C ../GeoMys-Android log --oneline cdd5196..HEAD -- app/src/main/java/fr/ariegenature/geomys/<chemins du lot>`

  On reporte ces changements, puis on met à jour `commit_reference` et `statut_portage`.
- Les tests portés servent de filet : un comportement modifié côté Android doit d'abord changer un test.
