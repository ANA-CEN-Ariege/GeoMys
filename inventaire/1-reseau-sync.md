# 1. Réseau et synchronisation (`network/`, `sync/`)

> Détail de l'inventaire GeoMys. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys est décrite au § 9 de la synthèse.


Base : GeoMys-Android `cdd5196`, GeoMys-IOS `64d303b`. Chemins relatifs à `app/src/main/java/fr/ariegenature/geomys/`. « Lignes » = lignes physiques, commentaires compris (le code est commenté à environ 37 % ; les lignes de code seules sont en §6).
Cibles proposées (GeoMys est vide) : `cM:` = `shared/src/commonMain/kotlin/fr/ariegenature/geomys/`, `aM:` = androidMain, `iM:` = iosMain.

Règles valables pour toutes les lignes du tableau, non répétées ensuite :
- `HttpURLConnection` est remplacé par Ktor (moteur OkHttp en aM, Darwin en iM), et `org.json` par `JsonElement` de kotlinx.serialization.
- `withContext(Dispatchers.IO)` reste utilisable en commonMain, à trois conditions : cibles JVM et Native uniquement, `import kotlinx.coroutines.IO`, coroutines 1.7 ou plus.
- `GeoNatureConfig` (store, hors périmètre) est passé à presque toutes les fonctions. Il devra reposer sur multiplatform-settings.
- Le relevé automatique (`releve-automatique.tsv`) signale `GeoPoint/osm` dans 6 fichiers et `Location` dans GeoNatureAuth. Ce sont des faux positifs : il n'y a ni osmdroid ni `android.location` dans ce périmètre, et `Location` est un en-tête HTTP.
- `app/src/github/.../network/MiseAJour.kt` (126 l., mise à jour automatique de l'APK, variante github) n'entre pas dans ce périmètre. Il n'est pas inventorié ici.

## 1. Tableau par fichier

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys | Remarques |
|---|---|---|---|---|---|---|
| `network/HttpClient.kt` | 101 | Prépare les `HttpURLConnection` (timeouts, `Accept`, `Bearer`, `Cookie`). Les POST portent `Connection: close`. `lireCode()` vide la session sur un 401. | COMMUN-ADAPTÉ | **Connexion** : `HttpURLConnection` et `URL.openConnection` → `io.ktor.client.HttpClient`.<br>**Timeouts** (posés appel par appel) → plugin `HttpTimeout` par requête. `socketTimeoutMillis` équivaut au readTimeout. Ne pas poser de `requestTimeoutMillis`.<br>**En-têtes** → `bearerAuth()` et `header(HttpHeaders.Cookie, …)`.<br>**Anti-rejeu des POST** : garder `Connection: close`, régler OkHttp avec `retryOnConnectionFailure(false)`, et ne jamais appliquer `HttpRequestRetry` à un POST.<br>**`lireCode`** → intercepteur `HttpSend` : sur un 401, il appelle `GeoNatureAuth.invaliderCache()`.<br>**Divers** : `expectSuccess = false`. | `cM:network/http/GnHttp.kt`, plus en aM/iM une fabrique de moteur (environ 20 l.) | Aucun nouvel essai sur 401 aujourd'hui : le cache est invalidé, c'est tout. Équivalent iOS : `Network/HttpClient.swift`. |
| `network/JsonResponse.kt` | 37 | `String.parserTableauJson(clés…)` lit un tableau, qu'il soit direct ou enveloppé (`data`, `items`, `features`…). | COMMUN-ADAPTÉ | `JSONArray(String)` et `JSONObject(String)` → `Json.parseToJsonElement`, `JsonArray`, `JsonObject`. Le `JSONTokener` d'Android est plus tolérant que kotlinx en mode strict. | `cM:network/json/JsonCompat.kt` | Bon point d'ancrage pour la couche « opt* » compatible org.json (§4.7-1). Équivalent iOS : `JsonHelpers.swift`. |
| `network/GNErreur.kt` | 86 | Erreurs typées (`AuthEchouee`, `EnvoiEchoue`, `EnvoiIncertain`, `DatasetInvalide`…), `parseErreur` (lit `description`, `msg` ou `message`) et `humaniserErreurReseau` (messages en français). | COMMUN-ADAPTÉ | `JSONObject` (l. 40).<br>Les tests `java.net.UnknownHostException/SocketTimeoutException/ConnectException` (l. 76-81) deviennent `expect fun classerErreurReseau(Throwable)` :<br>• aM : les `java.net.*` passent tels quels à travers OkHttp ;<br>• iM : `DarwinHttpRequestException.origin`, un NSError de NSURLErrorDomain (−1009, −1003, −1001, −1004…) ;<br>• dans les deux cas, ajouter les exceptions Ktor `HttpRequestTimeoutException`, `ConnectTimeoutException` et `SocketTimeoutException`.<br>`javaClass.simpleName` (l. 82) → `::class.simpleName`. | `cM:network/GNErreur.kt`, plus aM/iM `ErreursReseau.kt` (environ 15 l.) | Toutes les UI d'envoi affichent ces messages. Équivalent iOS : `ErreursReseau.swift`. |
| `network/CompatibiliteServeur.kt` | 55 | Fixe la version minimale de GeoNature (2.15) et compare des versions segment par segment. | COMMUN | Aucune | `cM:network/CompatibiliteServeur.kt` | Se reprend tel quel. Équivalent iOS : `CompatibiliteServeur.swift`. |
| `network/InvalidationCaches.kt` | 44 | `invaliderCachesSession()` purge en cascade les caches liés à l'identité du serveur (auth, monitoring, nomenclatures, stations OccHab). | COMMUN | Aucune (simples appels de singletons) | `cM:network/InvalidationCaches.kt` | Dépend du portage des stores. Équivalent iOS : `GeoNatureAuth.swift:44`. |
| `network/GeoNatureAuth.kt` | 299 | Test de connexion (diagnostic 3xx, HTML ou JSON ; version lue dans `gn_commons/config` ; garde ≥ 2.15). Login avec cache de session de 5 min, extraction du jeton, de l'`id_role`, du nom et des cookies. | COMMUN-ADAPTÉ | **POST sans redirection** (`instanceFollowRedirects=false`, l. 40 et 230) → seconde instance Ktor `followRedirects = false`.<br>**En-têtes de réponse** : `getHeaderField("Location")` et `headerFields["Set-Cookie"]` (l. 166, 242-244) → `response.headers` et `getAll(HttpHeaders.SetCookie)`. Darwin pose problème ici (§4.1).<br>**Corps** : `OutputStreamWriter` → `setBody`.<br>**Verrou** : `synchronized(verrouLogin)` (l. 223) → `Mutex`. `loginAvecCookies` devient donc `suspend`, ce qui touche 27 appels.<br>**Cache** : `@Volatile` (l. 193) → `kotlin.concurrent.Volatile` ou atomicfu.<br>**Horloge** : `System.currentTimeMillis()` (l. 206, 226) → `Clock.System`.<br>**Exception réseau** : `java.io.IOException` (l. 262) → `kotlinx.io.IOException`. | `cM:network/auth/GeoNatureAuth.kt` | Écrit `gn_nom_utilisateur`, `gn_id_role_utilisateur`, `gn_version_serveur` et `gn_serveur_compatible`. Contrat à garder : `null` signifie un refus, une `IOException` signifie un problème réseau. Équivalent iOS : `GeoNatureAuth.swift`, qui se comporte autrement (§4.8). |
| `network/GeoNatureSync.kt` | 693 | Synchro TaxRef paginée (6 listes en parallèle, homonymes résolus, index par groupe, rien à faire si la version n'a pas changé). Synchro des nomenclatures et de leurs défauts Occtax. Settings OCCTAX (`t_mobile_apps`, `form_fields`). | COMMUN-ADAPTÉ | `Gson().fromJson(TypeToken<List<GeoNatureListe/GeoNatureDataset>>)` (l. 181-209) → `Json.decodeFromString`.<br>`java.util.concurrent.atomic.AtomicInteger` (l. 267-268) → atomicfu.<br>`android.util.Log` (l. 624-640) → Kermit.<br>org.json : 54 appels. | `cM:network/sync/TaxRefSync.kt`, `NomenclaturesSync.kt`, `SettingsOcctaxSync.kt` | Écrit 7 clés de `gn_config` et les caches TaxRef et nomenclatures (store). Toutes les pages TaxRef restent en mémoire avant la fusion (l. 269-310) : à mesurer sur iOS. Équivalent iOS : `GeoNatureTaxRef.swift`, `GeoNatureNomenclatures.swift`, `GeoNatureSettings.swift`. |
| `network/GeoNatureBrowse.kt` | 543 | Charge les référentiels : jeux de données (JDD, lus en flux), JDD créables (CRUVED C), HABREF complet, biblistes, observateurs (menu UsersHub ou roles), clés de `gn_commons/config` (OCCTAX, OCCHAB), Synthèse de l'Explorer. Définit les modèles `GeoNatureDataset`, `GeoNatureListe`, `GeoNatureObservateur` et `ObsExplorer`. | COMMUN-ADAPTÉ | **Lecture en flux** : Gson `JsonReader`, `JsonToken`, `MalformedJsonException` (l. 24-25, 81-83, 110-118, 128-204, 227-268) → DTO `@Serializable` décodés en flux (kotlinx-serialization-json-io).<br>**Tri** : `java.text.Collator(Locale.FRANCE, PRIMARY)` (l. 121-123) → expect/actual (`Collator` en aM ; en iM, `NSString.compare` avec `caseInsensitive` et `diacriticInsensitive`, locale fr_FR), ou une clé de tri sans accents.<br>**Dates** : `SimpleDateFormat`, `Date`, `currentTimeMillis` (l. 419-420) → kotlinx-datetime, fuseau de l'appareil.<br>**JSON construit par gabarit de chaîne** (l. 78, 429-430) → `buildJsonObject`.<br>**Flux de lecture** : `BufferedReader`, `InputStreamReader`, `Charsets` → corps de réponse Ktor. | `cM:network/referentiels/GeoNatureBrowse.kt`, modèles dans `cM:model/` | Les 3 modèles sont persistés par Gson (§5). Équivalent iOS : `GeoNatureBrowse.swift`, `StreamingJsonArrayParser.swift`, `OccHabConfigApi.swift`. |
| `network/GeoNatureUpload.kt` | 1 460 | Envoi Occtax : relevé, occurrences, dénombrements et médias. Anti-doublon (uuid v3 du relevé, uuid d'occurrence, pré-marquage via `MarqueurEnvoiOcctax`), rollbacks, relevés orphelins, résolution des nomenclatures, GeoJSON (Point, LineString, Polygon à trous), typage des champs additionnels. Upload des médias monitoring avec réconciliation. | COMMUN-ADAPTÉ | **HTTP** : 12 sites d'appel → Ktor.<br>**Multipart écrit à la main** (l. 1282-1360) → `submitFormWithBinaryData` ou `MultiPartFormDataContent`, avec le fichier lu en flux (kotlinx-io).<br>**Fichiers** : `java.io.File` (l. 1293) et `android.net.Uri.parse(…).path` (l. 497, 1220, 1291) → parseur commun d'URI `file:` et `SystemFileSystem`.<br>**MIME** : `URLConnection.guessContentTypeFromName` (l. 498, 1221, 1303) → `ContentType.defaultForFilePath` de Ktor, ou une table.<br>**UUID** : `UUID.nameUUIDFromBytes` (l. 646) → MD5 multiplateforme, bits v3, puis `kotlin.uuid.Uuid`.<br>**Normalisation** : `java.text.Normalizer` en NFD (l. 1042) → expect/actual.<br>**Dates** : `SimpleDateFormat` et `Date` (l. 176-177, 259-261, 294-297) → kotlinx-datetime.<br>**Divers** : Gson (l. 968-969) ; `@Volatile` (l. 1091) ; 17 `android.util.Log` → Kermit. | `cM:network/occtax/OcctaxPayloads.kt` (fonctions pures), `OcctaxEnvoi.kt`, `OcctaxVerifications.kt`, `MediasUpload.kt` | Fichier le plus critique (risque de doublons et de pertes). Environ 375 lignes sont pures (§2). Équivalent iOS : `OcctaxUpload.swift` et `MediaUpload.swift`. |
| `network/HabitatService.kt` | 92 | Autocomplétion HABREF : cherche d'abord dans le cache local (OCCTAX ou OccHab), sinon interroge `habref/habitats/autocomplete`. Définit le modèle `HabitatSuggestion`. | COMMUN-ADAPTÉ | `URLEncoder.encode` (l. 61) → `parameter("search_name", …)` de Ktor.<br>`JSONArray` → `JsonArray`. | `cM:network/referentiels/HabitatService.kt` | `HabitatSuggestion` est persisté par Gson (§5). Équivalent iOS : `HabitatService.swift`. |
| `network/AdditionalFields.kt` | 344 | Champs additionnels Occtax : modèle `AdditionalFieldDef` et `WidgetType`, lecture tolérante des variantes serveur, options des widgets nomenclature (chargées en parallèle), valeurs par défaut. | COMMUN-ADAPTÉ | org.json : 44 appels, dont des tests `is Int`, `is Long` et `JSONObject.NULL` (l. 219-223, 303-315) → `JsonElement`.<br>`Log` (l. 287). | `cM:network/referentiels/AdditionalFields.kt`, modèle dans `cM:model/` | `AdditionalFieldDef` est persisté par Gson. Son `List<Pair>` est sérialisé en `{"first","second"}` (§5). Équivalent iOS : `AdditionalFields.swift`. |
| `network/MonitoringApi.kt` | 282 | Modèles du module Suivis (`MonitoringPropertySchema`, `MonitoringSchemaObjet`, `MonitoringObjet`, `MonitoringEnfant`, `OptionDatalist`), `LabelResolver` et façade. | COMMUN | Aucune (osmdroid n'apparaît que dans un commentaire, l. 243) | `cM:network/monitoring/MonitoringModeles.kt` | Rien n'est persisté : le cache disque garde le JSON brut du serveur. Équivalent iOS : `MonitoringApi.swift`. |
| `network/MonitoringModules.kt` | 283 | Liste des protocoles (`monitorings/modules`) : filtrage CRUVED, repli sur le cache disque, lectures du cache sans réseau. | COMMUN-ADAPTÉ | org.json : 22 appels.<br>`java.io.IOException` (l. 195) → `kotlinx.io.IOException`, plus le classement des erreurs réseau.<br>`@Volatile` (l. 74).<br>`Log` (l. 241). | `cM:network/monitoring/MonitoringModules.kt` | Écrit `monitoring/modules.json` (items bruts, après filtrage). Équivalent iOS : `MonitoringApi.swift`. |
| `network/MonitoringObjets.kt` | 347 | Arborescence Suivis (`monitorings/object/…`) : aplatit les propriétés, lit en cache les libellés, les genres et la chaîne des parents, formate la géométrie, trie. | COMMUN-ADAPTÉ | org.json : 47 appels.<br>`"%.5f".format` et `Math.abs` (l. 308) → formatage maison et `kotlin.math.abs`. Le `format` de la JVM suit la locale, d'où une virgule décimale en français.<br>`IOException` (l. 67, 256). | `cM:network/monitoring/MonitoringObjets.kt` | Environ 200 lignes pures. Équivalent iOS : `MonitoringApi.swift`, `MonitoringObjetsCache.swift`. |
| `network/MonitoringSchemas.kt` | 592 | Schéma d'un protocole (`monitorings/config/<m>`) : fusion generic/specific, lecture des propriétés, substitution des `__MODULE.*`, dérivation des `api` implicites. | COMMUN-ADAPTÉ | org.json : 97 appels, dont une modification du JSON sur place (l. 550-574) → réécriture fonctionnelle sur un `JsonElement` immuable.<br>`URLDecoder.decode` (l. 252) → `decodeURLQueryComponent(plusIsSpace = true)`.<br>`IOException` (l. 74). | `cM:network/monitoring/MonitoringSchemas.kt` | Environ 485 lignes de parsing pur : un bon fichier pour commencer. Équivalent iOS : `MonitoringSchema.swift`. |
| `network/MonitoringDatalists.kt` | 392 | Options dynamiques des formulaires (datalists, nomenclatures, JDD en POST), observateurs UsersHub, `LabelResolver`, écriture immédiate dans le cache hors-ligne. | COMMUN-ADAPTÉ | **POST bricolé** à partir de `HttpClient.get` (l. 311-326) → `client.post`.<br>`URLDecoder` (l. 288).<br>Gson (l. 231-233).<br>**Cache partagé** : `@Volatile` sur une `MutableMap` modifiée depuis plusieurs coroutines (l. 47, 148) → `Mutex`.<br>`Log` (l. 335). | `cM:network/monitoring/MonitoringDatalists.kt` | Équivalent iOS : `MonitoringDatalist.swift`. |
| `network/MonitoringEnvoi.kt` | 316 | POST de création d'un objet Suivis : normalisation des types, champs vides ajoutés pour Marshmallow, `id_digitiser`, `id_dataset`. Recherche anti-doublon par uuid, JDD du module. | COMMUN-ADAPTÉ | org.json et valeurs `Any?` (l. 293-315) → `JsonElement`.<br>Gson (l. 255-256).<br>`fr.ariegenature.geomys.BuildConfig.DEBUG` (l. 212) → `expect val` ou BuildKonfig.<br>3 `Log`. | `cM:network/monitoring/MonitoringEnvoi.kt` | Équivalent iOS : `MonitoringEnvoi.swift`. |
| `network/MonitoringSync.kt` | 232 | Pré-chargement hors-ligne : parcours en largeur des objets « structurels » (5 requêtes en parallèle, profondeur 8 au plus), puis observateurs, options des datalists et pictos. | COMMUN | `MutableMap.putIfAbsent` (l. 113) vient de l'API Java → `getOrPut` ou un simple test. Le reste n'utilise que des coroutines communes. | `cM:network/monitoring/MonitoringSync.kt` | Appelle `store.PictoCache.prefetch` (hors périmètre ; endpoint des pictos en §3). Équivalent iOS : `MonitoringSync.swift`. |
| `network/OccHabApi.kt` | 505 | OccHab en lecture : altitudes du MNT (`geo/info`), droits par module (`gn_commons/modules`), défauts de nomenclatures, stations de l'utilisateur (FeatureCollection, polygones à trous, MultiPolygon, filtre numérisateur/observateur). | COMMUN-ADAPTÉ | org.json : 68 appels.<br>**Dates** : 3 `SimpleDateFormat` qui acceptent un simple préfixe (l. 216-228) → kotlinx-datetime et parseur maison.<br>`UUID.randomUUID` (l. 357) → `Uuid.random()`.<br>`AtomicInteger` passé en paramètre de sortie (l. 186, 263) → valeur de retour ou atomicfu.<br>`currentTimeMillis` (l. 359).<br>2 `Log`. | `cM:network/occhab/OccHabApi.kt` | Environ 290 lignes pures. Équivalent iOS : `OccHabApi.swift`, `ModulesGeoNature.swift`, `OccHabConfigApi.swift`. Je n'ai trouvé aucun appel à `geo/info` côté iOS. |
| `network/OccHabUpload.kt` | 312 | Envoi d'une station OccHab (POST `/stations/` pour créer, POST `/stations/<id>/` pour mettre à jour), anti-doublon par `unique_id_sinp_station`. | COMMUN-ADAPTÉ | `OutputStreamWriter`.<br>org.json : 19 appels.<br>`SimpleDateFormat` et `Date` (l. 104-106).<br>`IOException` (l. 204, 268).<br>`javaClass.simpleName` (l. 212, 282).<br>`Log`. | `cM:network/occhab/OccHabUpload.kt` | Réutilise `GeoNatureUpload.construireGeometrie`. Équivalent iOS : `OccHabUpload.swift`. |
| `network/OutboxEnvoi.kt` | 520 | File d'envoi Suivis : ordre parent → enfant, états PENDING/SENDING/ERROR/SENT, `dejaTentee` et `objetCree`, recherche par uuid, renvoi des seuls médias. | COMMUN-ADAPTÉ | `java.util.concurrent.ConcurrentHashMap` (l. 337) → map protégée par un `Mutex`.<br>`UUID.randomUUID` (l. 225).<br>`org.json.JSONObject(valeursJson)` (l. 420-425) → `JsonObject`.<br>2 `Log`. | `cM:network/monitoring/OutboxEnvoi.kt` | Aucune API Android. La logique, critique, est déjà testée (OutboxEnvoiTest…). Équivalent iOS : `OutboxEnvoi.swift`. |
| `network/EnvoiSortie.kt` | 248 | Envoi d'une sortie Occtax de bout en bout : un verrou par saisie (`tryLock`), relecture sous verrou, marquage au fil de l'eau, `NonCancellable`, récapitulatif. | COMMUN-ADAPTÉ | `ConcurrentHashMap.computeIfAbsent` (l. 60-62) et `Collections.synchronizedSet` (l. 37) → `Mutex` ou atomicfu.<br>`@androidx.annotation.VisibleForTesting` (l. 40) → à retirer. | `cM:network/occtax/EnvoiSortie.kt` | Équivalent iOS : `EnvoiSortie.swift`, qui protège l'envoi par `beginBackgroundTask` (l. 219-233). |
| `network/EnvoiOccHab.kt` | 207 | Envoi d'une saisie OccHab station par station : persistance au fil de l'eau, statut incertain, un 5xx est traité comme incertain. | COMMUN-ADAPTÉ | `ConcurrentHashMap.computeIfAbsent` (l. 33-35) → `Mutex` ou atomicfu. | `cM:network/occhab/EnvoiOccHab.kt` | Équivalent iOS : `EnvoiOccHab.swift`. |
| `network/SyncRunner.kt` | 345 | Orchestre « Recharger les données ». Phase A en parallèle : JDD, listes, observateurs, champs additionnels, settings, HABREF, OccHab. Puis purge après mise à jour. Phase B en parallèle : TaxRef, nomenclatures, Suivis. Expose un état observable. | COMMUN-ADAPTÉ | **Context** : `android.content.Context` (l. 69-73) ne sert qu'à construire `GeoNatureConfig(context)` → injection.<br>**État observable** : `LiveData`, `MutableLiveData.postValue` (l. 53-64, 320, 331, 343) → `MutableStateFlow`.<br>**Garde contre le double lancement** : `AtomicBoolean` (l. 57) → atomicfu.<br>**Persistance** : 6 `Gson.toJson` → `Json.encodeToString`.<br>**Divers** : `javaClass.simpleName` (l. 265, 328) ; `OutOfMemoryError` (l. 330). | `cM:network/sync/SyncRunner.kt` | Écrit environ 15 clés de `gn_config` (§5). Équivalent iOS : `SyncRunner.swift`. |
| `network/TaxRefService.kt` | 153 | Résout un nom saisi ou dicté uniquement à partir du cache local : aucun appel réseau, malgré le nom du paquet. | COMMUN | Aucune | `cM:taxref/TaxRefService.kt` | Dépend de TaxRefCache et TaxRefLocal (autres périmètres). Côté iOS, la logique est dans `Store/PropositionsTaxon.swift` (non vérifié en détail). |
| `sync/SyncForegroundService.kt` | 135 | Service Android au premier plan (type `dataSync`) qui exécute `SyncRunner` et affiche la progression dans une notification. | PLATEFORME | **API Android** :<br>• `Service` ;<br>• `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` ;<br>• `NotificationCompat`, sur le canal `GeoMys_sync` créé dans `ui/MainActivity.kt:112` ;<br>• `PendingIntent` vers `MainActivity` ;<br>• `ContextCompat.startForegroundService` ;<br>• `LiveData.observeForever` ;<br>• `R.drawable.ic_settings`.<br>**iOS** : pas de service au premier plan. On passe par `beginBackgroundTask(withName:expirationHandler:)` et `isIdleTimerDisabled` (§4.6). | `cM:platform/ExecutionLongue.kt` (interface) ; `aM:sync/SyncForegroundService.kt` (collecte le StateFlow) ; `iM:sync/ExecutionLongueIos.kt` | Lancé depuis `ui/ConfigGeoNatureFragment.kt:731`. Le manifest déclare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` et `POST_NOTIFICATIONS`. Équivalent iOS : `Network/SyncRunner.swift:62-67`. |

## 2. Logique à extraire

Ce périmètre ne contient **aucun fichier MIXTE ni UI** : il n'y a ni vue ni widget. SyncRunner (Context et LiveData) et GNErreur (messages pour l'utilisateur) sont classés COMMUN-ADAPTÉ, car le Context n'y sert qu'à construire la config.

Pour ordonner le portage et commencer par ce qui se teste sans réseau, voici le découpage pur / I/O des fichiers COMMUN-ADAPTÉ de plus de 300 lignes.

- **GeoNatureUpload.kt (1 460 l.)**
  - **Pur** (environ 380 l.) :
    - modèles et contrats : `EnvoiResult` et `MarqueurEnvoiOcctax` (39-94), `ReleveExistant` (651-655), `EtatOrphelin` (734) ;
    - identités et vérifications : `uuidReleveGroupe` (645-646), `extraireFeaturesReleves` (718-731), `estRejetFranc` (764-765), `idDepuisFeatureReleve` (767-769), `erreurMediaLocale` (812-813) ;
    - construction du payload : `buildOccurrence` (815-891), `construireGeometrie` et `fermerAnneau` (904-960), `champsAdditionnelsTexteLibre` (967-972), `jsonDepuisMap` (977-994), `buildCounting` (996-1030) ;
    - nomenclatures et médias : `normaliserLabelNomenclature` (1041-1046), `resolverIdNomenclature` (1048-1086), `titreMedia` (1132-1135), `idTypeMediaPour` (1245-1253).
  - **À sortir de `envoyer()`** (114-639, environ 525 l. d'orchestration) : les `properties` du relevé (288-333, environ 45 l.), les bornes de dates (258-261), le regroupement et le filtre anti-doublon (223-250).
  - **I/O** (environ 450 l.) : `relevePresent` (668-710), `etatReleveOrphelin` (739-759), `occurrencePresente` (777-806), `resoudreIdTableLocationCounting` (1093-1120), `mediasDejaPresents` (1141-1161), `uploaderMediaMonitoring` (1172-1240), `resoudreIdTableLocationPour` (1260-1278), `uploaderMediaFile` (1282-1360), les `tenterSupprimer*` (1365-1388), `resolverNomenclatures` (1393-1459).
- **MonitoringSchemas.kt (592 l.)**
  - **I/O** : seulement `chargerSchemaProtocole` (45-77, environ 33 l.).
  - **Pur** (environ 485 l.) :
    - assemblage et dérivation : assemblage des `MonitoringSchemaObjet` (78-185), `derirverApiSiManquant` (195-280) ;
    - propriétés : `parserPropertiesFusionnees`, `fusionnerChamp` et `parserBlocProperties` (288-334), `parserUnePropriete` (342-497) ;
    - utilitaires : `bornBrute`, `infererCodeNomenclature` et `extraireIdListeAllnamebylist` (503-529), `extraireInt` (585-591) ;
    - substitutions : `substituerVariablesModule`, `substituerRecursif` et `appliquerSubstitutions` (535-582), à réécrire en transformation immuable.
- **GeoNatureSync.kt (693 l.)**
  - **Pur** (environ 260 l.) :
    - sélection du cd_nom : `meilleurCdNomPrefListe` (46-53), `meilleurCandidatVernaculaire` (70-78) ;
    - agrégation : fusion et résolution des homonymes dans `synchroniserTaxRef` (240-350, environ 110 l.), à extraire en `agregerTaxRef(resultats, listeConfig)` ;
    - parsing : nomenclatures (469-507), settings (565-607) ;
    - index : `construireIndexTaxon` (646-692).
  - **I/O** : `verifierVersionTaxRef` (80-91), `fetchListeTaxons` (112-157), la préparation et les écritures de cache (166-239, 356-429), les appels de nomenclatures (432-458), `fetchDefaultsNomenclatures` (616-644).
- **GeoNatureBrowse.kt (543 l.)**
  - **Pur** (environ 230 l.) : les modèles (42-62) ; les lecteurs en flux (128-204 et 234-268, environ 110 l.), remplacés par des DTO `@Serializable` ; `parseObsExplorer` et `obsFromProps` (441-542), qui associent un groupe à un `Taxon`.
  - **I/O** : 70-124, 209-232 et 270-439.
- **OccHabApi.kt (505 l.)**
  - **Pur** (environ 290 l.) : `parserDate` (216-228), les lecteurs sûrs (235-249), `parserFeatureCollection` (258-399), `GeomParse`, `parserGeometrie`, `parserPolygone` et `anneauSommets` (403-504).
  - **I/O** : 82-214.
- **OutboxEnvoi.kt (520 l.)** : tout le fichier est de l'orchestration sur `OutboxMonitoring` : `envoyerSelection` (77-321) et `envoyerUne` (345-519). Rien ne part vers une UI ; seuls `valeursJson` (420-425) et `creesNonPersistes` (337) changent.
- **MonitoringDatalists.kt (392 l.)**
  - **Pur** : `extraireOptions` (364-391) ; la conversion d'une query-string en corps JSON pour les JDD (276-309).
  - **I/O** : `chargerResolveurLabels` (60-151), `chargerObservateursDeListe` (156-183), le reste de `chargerOptionsDatalist` (189-359).
- **MonitoringObjets.kt (347 l.)**
  - **Pur** (environ 200 l.) : l'aplatissement, les heuristiques et les lectures du cache (97-223), `formatGeometrie` (295-325), `trierEnfants` (330-346).
  - **I/O** : `chargerEnfants` (45-92), `chargerObjet` (229-291).
- **MonitoringEnvoi.kt (316 l.)**
  - **Pur** : la construction du payload (128-206, environ 80 l.), à extraire en `construirePayload(...)` ; `normaliserPourJson` (293-315).
  - **I/O** : `chercherEnfantParUuid` (63-104), le POST (207-238), `trouverDatasetPourModule` et `chargerDatasetsDuModuleLive` (250-288).
- **OccHabUpload.kt (312 l.)**
  - **Pur** : la construction du Feature (104-195, environ 90 l.), `trouverIdParUuid` (290-311).
  - **I/O** : le POST (197-246), `verifierStationExistante` (258-286).
- **AdditionalFields.kt (344 l.)**
  - **Pur** (environ 240 l.) : le modèle et `visiblePour` (35-91), `parserChamps` (151-254), `extraireDefautToutesClés` (256-294), `extraireDefaut` (303-315).
  - **I/O** : `charger` (99-141), `fetchValeursNomenclature` (319-342).
- **SyncRunner.kt (345 l.)** : l'orchestration se garde telle quelle. Seuls l'état (`Etat`, 42-51, et sa publication, 53-64) et l'entrée `executer(context, …)` (69-73) changent.

## 3. Endpoints GeoNature (contrat du futur client Ktor)

Chemins relatifs à `{base}` (URL serveur sans `/` final). `{taxhub}` vaut `config.urlTaxhub` : le `settings.sync.taxhub_url` publié par le serveur, sinon `{base}/api/taxhub`.

Légende de la colonne Auth : `B` = en-tête `Authorization: Bearer <token>` ; `C` = `Cookie: <nom=valeur; …>` repris des `Set-Cookie` du login. Chacun n'est envoyé que s'il n'est pas vide. `(B+C)` = envoyés si le login a réussi, l'appel part quand même sinon. `—` = aucune auth.

| Méthode | Chemin | Auth | Timeouts conn./lecture | Fichier:ligne | Usage / remarques |
|---|---|---|---|---|---|
| POST | `/api/auth/login` (corps `{"login","password"}`) | — | 10 s / 10 s, sans redirection | GeoNatureAuth.kt:34 | Test de connexion : diagnostic d'un 3xx (`Location`), d'une page HTML, d'un JSON sans jeton. |
| POST | `/api/auth/login` | — | 15 s / 15 s, sans redirection | GeoNatureAuth.kt:228 | Login réel, mis en cache 5 min. Lit `Set-Cookie` et `Content-Type`. |
| GET | `/api/gn_commons/config` | — | 5 s | GeoNatureAuth.kt:142 | Lit `GEONATURE_VERSION` (garde ≥ 2.15). |
| GET | `/api/gn_commons/config` | B+C | 10 s | GeoNatureSync.kt:593 | `OCCTAX.form_fields` → `gn_form_fields`. |
| GET | `/api/gn_commons/config` | B+C | 10 s | GeoNatureBrowse.kt:337 | `OCCTAX.id_observers_list`. |
| GET | `/api/gn_commons/config` | B+C | 10 s | GeoNatureBrowse.kt:348 | `<MODULE>.ID_LIST_HABITAT` (OCCTAX et OCCHAB). |
| GET | `/api/gn_commons/config` | B+C | 10 s | GeoNatureBrowse.kt:377 | `OCCHAB.formConfig`. |
| GET | `/api/gn_commons/t_mobile_apps` | (B+C) | 15 s | GeoNatureSync.kt:561 | App `OCCTAX` : `settings.sync.taxhub_url` et `page_size`, `area_observation_duration`, `input.date.*`, `nomenclature`. |
| GET | `/api/gn_commons/modules` | B+C | 15 s | OccHabApi.kt:126 | CRUVED C et R des modules OCCHAB et OCCTAX. |
| GET | `{taxhub}/api/taxref/version` | — | 5 s | GeoNatureSync.kt:83 | Permet de sauter la synchro si la version n'a pas changé. |
| GET | `{taxhub}/api/taxref?orderby=cd_nom&fields=listes&id_liste={id}&limit={page_size}&page={n}` | — | 60 s / 60 s | GeoNatureSync.kt:119 | Paginé jusqu'à une page incomplète ou vide. 6 listes en parallèle. |
| GET | `{taxhub}/api/biblistes` | — | 10 s | GeoNatureBrowse.kt:272 | Listes de taxons. |
| GET | `/api/nomenclatures/nomenclatures/taxonomy`, à défaut `/api/nomenclatures/nomenclature/taxonomy`, puis `/api/nomenclatures/taxonomy` | B+C | 15 s | GeoNatureSync.kt:441-443 | Nomenclatures et leurs restrictions taxref. |
| GET | `/api/occtax/defaultNomenclatures` | B+C | 10 s | GeoNatureSync.kt:619 (appelé l. 515) | Défauts Occtax. La casse reçue est aussi essayée. |
| GET | `/api/nomenclatures/nomenclature/{CODE}` | B+C | 10 s | AdditionalFields.kt:326 | Options des champs additionnels de type nomenclature (en parallèle). |
| GET | `/api/nomenclatures/nomenclature/{TYPE}`, puis `/api/nomenclatures/nomenclature?id_type={id}&limit=200`, `/api/nomenclatures/nomenclatures?id_type={id}&limit=200`, `/api/nomenclatures/nomenclature?code_nomenclature_type={TYPE}&limit=200`, `/api/nomenclatures/nomenclatures?code_type={TYPE}&limit=200` | B+C | 10 s | GeoNatureUpload.kt:1433, 1441, 1442, 1444, 1445 | Solution de secours à l'envoi, si le cache de nomenclatures est vide. |
| GET | `/api/occhab/defaultNomenclatures` | B+C | 15 s | OccHabApi.kt:161 | Défauts OccHab. |
| GET | `/api/meta/datasets?active=true&module_code={OCCTAX ou OCCHAB}&fields=modules` | B (sans cookie) | 15 s / 90 s | GeoNatureBrowse.kt:99 | Lu en flux, trié avec un Collator français. |
| POST | `/api/meta/datasets?active=true` (corps `{"create":"{MODULE}"}`) | B | 15 s / 90 s | GeoNatureBrowse.kt:77 | Identifiants des JDD créables (CRUVED C). |
| GET | `/api/meta/datasets?module_code={m}&active=true&fields=modules` | B+C | 10 s | MonitoringEnvoi.kt:279 | JDD d'un protocole (essai en deux casses). |
| GET | `/api/users/menu/{id_liste}` | B+C | 10 s | GeoNatureBrowse.kt:391 | Observateurs Occtax. |
| GET | `/api/users/roles` | B+C | 10 s | GeoNatureBrowse.kt:310 | Utilisé s'il n'y a pas de menu. |
| GET | `/api/users/menu/{id_liste}` | B+C, sinon cache | 15 s | MonitoringDatalists.kt:167 | Observateurs Suivis → `observateurs_{id}.json`. |
| GET | `/api/gn_commons/additional_fields?module_code=OCCTAX` | B+C | 10 s | AdditionalFields.kt:105 | Un 404 donne une liste vide. |
| GET | `/api/habref/habitats/autocomplete?limit=200000[&id_list={id}]` | — | 20 s / 90 s | GeoNatureBrowse.kt:222 | HABREF complet (environ 29 000 entrées), lu en flux. |
| GET | `/api/habref/habitats/autocomplete?search_name={t}&limit={n}[&id_list={id}]` | — | 8 s | HabitatService.kt:60 | Seulement si aucun cache local n'est disponible. |
| POST | `/api/synthese/for_web?limit=1000` (corps `{"geoIntersection":<Feature Polygon>,"date_min":"yyyy-MM-dd"}`) | B | 20 s | GeoNatureBrowse.kt:432 | Explorer. L'emprise est refiltrée côté client. |
| POST | `/api/occtax/OCCTAX/only/releve` (corps `{geometry, status:"to_sync", properties}`) | B+C | 30 s | GeoNatureUpload.kt:387 | Création du relevé : `unique_id_sinp_grp`, `t_occurrences_occtax: []`, `meta_device_entry: "mobile"`, `observers`, `id_digitiser`… |
| POST | `/api/occtax/OCCTAX/releve/{id}/occurrence` | B+C | 30 s | GeoNatureUpload.kt:540 | Occurrence, avec `unique_id_occurence_occtax` et `cor_counting_occtax[].medias[]`. |
| GET | `/api/occtax/OCCTAX/releves?unique_id_sinp_grp={uuid}&limit=1` | B+C | 30 s | GeoNatureUpload.kt:674 | Anti-doublon du relevé : trouvé, absent ou indéterminable. |
| GET | `/api/occtax/OCCTAX/releve/{id}` | B+C | 30 s | GeoNatureUpload.kt:743, 782 | L'orphelin est-il encore vide ? L'occurrence (par uuid) est-elle présente ? |
| DELETE | `/api/occtax/OCCTAX/releve/{id}` | B+C | 15 s | GeoNatureUpload.kt:1368 | Rollback et purge des orphelins. Un 2xx ou un 404 vaut succès. |
| GET | `/api/gn_commons/get_id_table_location/pr_occtax.cor_counting_occtax` | B+C | 10 s | GeoNatureUpload.kt:1096 | Mis en cache en mémoire. |
| GET | `/api/gn_commons/get_id_table_location/{schema.table}` | B+C | 10 s | GeoNatureUpload.kt:1263 | Médias Suivis (`schema_dot_table`). |
| POST | `/api/gn_commons/media` (multipart) | B+C, `Connection: close` | 30 s / 60 s | GeoNatureUpload.kt:1305 | Champs `id_table_location`, `id_nomenclature_media_type`, `uuid_attached_row`, `author`, `title_fr`, `description_fr`, `file`. |
| GET | `/api/gn_commons/medias/{uuid_attached_row}` | B+C | 20 s | GeoNatureUpload.kt:1146 | Réconciliation par `title_fr` avant un nouvel envoi. |
| DELETE | `/api/gn_commons/media/{id_media}` | B+C | 15 s | GeoNatureUpload.kt:1382 | Rollback. |
| GET | `/api/monitorings/modules` | B+C | 10 s | MonitoringModules.kt:175 | Un 403 ou 404 donne une liste vide ; une erreur réseau fait servir le cache. |
| GET | `/api/monitorings/config/{module_code}` | B+C | 10 s | MonitoringSchemas.kt:55 | Schéma du protocole. |
| GET | `/api/monitorings/object/{module_code}/module?depth=1` | B+C | 15 s | MonitoringObjets.kt:54 | Enfants du module. |
| GET | `/api/monitorings/object/{module_code}/{object_type}/{id}?depth=1` | B+C | 15 s | MonitoringObjets.kt:244 ; MonitoringEnvoi.kt:76 | Fiche et enfants. Sert aussi à l'anti-doublon par uuid (lecture en direct, jamais en cache). |
| POST | `/api/monitorings/object/{module_code}/{object_type}` (corps `{"properties":{…}}`) | B+C | 20 s | MonitoringEnvoi.kt:208 | Création d'une visite, d'une observation… |
| GET | `/api/nomenclatures/nomenclature/{code}` et `/api/meta/datasets` | B+C | 15 s | MonitoringDatalists.kt:95 (appels l. 115, 131) | Alimente `LabelResolver`. |
| GET ou POST | `/api/{apiUrl du schéma}` | B+C | 15 s | MonitoringDatalists.kt:295/298, 311-315 | Options d'une datalist. Pour `meta/datasets` : POST JSON (`module_code`, `active`, `create`), puis nouvel essai sans `create`. Formes dérivées (MonitoringSchemas.kt:195-280) : `meta/datasets?…`, `nomenclatures/nomenclature/<TYPE>`, `users/menu/<id>`, `biblistes/<id>`, `monitorings/list/<module>/<type>?fields=…`. `biblistes/<id>` est résolu sous `{base}/api` et non sous `{taxhub}` (à vérifier sur un serveur 2.15+). |
| POST | `/api/geo/info` (corps Feature) | B+C | 10 s (défaut) | OccHabApi.kt:94 | Altitudes min et max (MNT). |
| GET | `/api/occhab/stations/?format=geojson&habitats=1&nomenclatures=1[&id_dataset=]` | B+C | 30 s | OccHabApi.kt:193 | Stations, filtrées côté client (numérisateur ou observateur). |
| POST | `/api/occhab/stations/` (création) ou `/api/occhab/stations/{id_station}/` (mise à jour) | B+C | 30 s | OccHabUpload.kt:198-199 | Feature : station, `observers[{id_role}]`, `habitats[]`, `id_nomenclature_*`. |
| GET | `/api/occhab/stations/?format=json[&id_dataset=]` | B+C | 30 s | OccHabUpload.kt:263 | Anti-doublon par `unique_id_sinp_station`. |
| GET | `/api/media/monitorings/{module_code}/img.jpg` (ou `module_picto`) | session en cache si encore valide | — | store/PictoCache.kt:68-76, 90 (hors périmètre) | Déclenché par MonitoringSync.kt:220. |

Volume : environ 37 routes distinctes (méthode et chemin) et environ 50 sites d'appel.

Les URL sont construites par simple interpolation, sans encodage : uuid, codes module, et `apiUrl` du schéma, qui arrive déjà encodée. Avec Ktor, il faut analyser l'`apiUrl` (`URLBuilder().takeFrom(...)`) plutôt que de la ré-encoder.

## 4. Points durs

### 4.1 Authentification

**Le flux.** Login par `POST /api/auth/login`, sans suivre les redirections (GeoNatureAuth.kt:220-271).
- Un login réussit si la réponse est en HTTP 200, si son `Content-Type` contient `json`, et si elle porte un jeton **ou** un `id_role`.
- Le jeton est cherché dans `access_token` ou `token`, à la racine puis sous `user` (l. 292-298). L'`id_role` est cherché sous `user` puis à la racine (l. 250-252).
- Chaque `Set-Cookie` est réduit à `nom=valeur`, et les cookies sont joints par `; ` (l. 242-244).
- Les appels suivants envoient `Authorization: Bearer` et `Cookie` (HttpClient.kt:97-98).
- Trois appelants n'envoient que le Bearer, car `GeoNatureAuth.login()` jette les cookies (l. 273-274) : GeoNatureBrowse.kt:77, 103 et 433.

**Le cache de session.**
- Il vit en mémoire, 5 minutes, par couple (base, login) (l. 182-209).
- Les logins sont sérialisés par un verrou global, avec une seconde vérification du cache sous verrou (l. 211-226).
- `sessionEnCache` lit la session sans jamais déclencher de login ; il sert aux pictos.

**Pas de ré-authentification dans la requête.** Un 401, sur n'importe quel appel, vide le cache (HttpClient.kt:80-84), et c'est l'appel suivant qui se reconnecte. Le mot de passe ne fait pas partie de la clé du cache : quand il change, c'est l'écran de configuration qui appelle `invaliderCachesSession()`.

**Le contrat à préserver.** `null` signifie un refus : HTTP différent de 200, réponse non JSON, ou réponse 200 sans jeton ni `id_role` (l. 254-258). Une `IOException` relancée signifie un problème réseau (l. 262-268). Tout le mode hors-ligne repose sur cette distinction ; par exemple, MonitoringDatalists.kt:85-87 et 263-265 rattrapent l'`IOException` pour servir le cache.

**Le portage Ktor.**
- Un client dédié avec `followRedirects = false`. Sans cela, la redirection vers la SPA `/#/login` masque l'échec d'authentification.
- Sur Darwin, couper la gestion automatique des cookies (`HTTPShouldSetCookies = false`, `HTTPCookieAcceptPolicy = never` via `configureSession`), comme le fait GeoMys-IOS (HttpClient.swift:58-59). Sinon les cookies partent en double.
- Toujours sur Darwin, `NSHTTPURLResponse` fusionne plusieurs `Set-Cookie` en une seule chaîne séparée par des virgules. Il faut un découpage robuste, ou `NSHTTPCookie.cookiesWithResponseHeaderFields` en iM.
- `loginAvecCookies` devient `suspend` (27 appels) et utilise un `Mutex`.
- Décision à prendre : ajouter ou non un nouvel essai unique sur 401 via `HttpSend`, comme `requete401Aware` côté iOS. C'est sans risque pour un POST, puisqu'un 401 n'a rien créé.

**Le mot de passe.** Il est lu via `GeoNatureConfig.motDePasse`, qui s'appuie sur `MdpChiffre` (AES-GCM dans le Keystore Android). Il faut un expect/actual de type « coffre », avec le Keychain sur iOS.

### 4.2 Erreurs, timeouts, hors-ligne

**Les timeouts.**
- Ils sont fixés appel par appel, de 5 s à 90 s (§3).
- Le `readTimeout` d'HttpURLConnection mesure l'inactivité entre deux octets ; il n'y a pas de plafond sur la durée totale.
- En Ktor : `HttpTimeout` par requête, `socketTimeoutMillis` pour le readTimeout, et **pas** de `requestTimeoutMillis`. Sinon `meta/datasets` (90 s), les pages TaxRef (60 s) et le HABREF complet seraient coupés.
- Il faut écraser les valeurs par défaut des moteurs : 10 s pour OkHttp, 60 s de `timeoutIntervalForRequest` pour Darwin.
- Selon la documentation Ktor, Darwin ne prend pas en charge `connectTimeoutMillis` (à vérifier).

**Les erreurs HTTP.** Le corps est lu dans `errorStream`, puis passé à `parseErreur` (`description`, `msg`, `message`). En Ktor : `expectSuccess = false` et `bodyAsText()`.

**Réseau, HTTP et parsing ne sont pas traités pareil.** C'est le cœur des solutions de repli :
- une `IOException` fait servir le cache disque (MonitoringModules.kt:195-197, MonitoringObjets.kt:67-69 et 256-258, MonitoringSchemas.kt:74-76, MonitoringDatalists.kt:263-267) ;
- une réponse 200 non JSON (portail captif) n'est jamais mise en cache (MonitoringObjets.kt:62-64, MonitoringSchemas.kt:66-71, MonitoringDatalists.kt:174-178) ;
- un 403 ou un 404 donne une liste vide (MonitoringModules.kt:184, AdditionalFields.kt:108).

En Ktor 3, `kotlinx.io.IOException` peut servir de type commun. Il faut vérifier, dans la version retenue, que `DarwinHttpRequestException` et `HttpRequestTimeoutException` en héritent bien. `humaniserErreurReseau` a besoin du classement par plateforme décrit en §1 (ligne GNErreur).

**Fragilité existante, à corriger ou à reproduire en connaissance de cause.** `SyncRunner.kt:140-143` lance `synchroniserSettingsOcctax` hors de tout try, et `GeoNatureSync.kt:557` appelle `loginAvecCookies` avant le try. Hors-ligne, l'`IOException` annule donc toute la phase A (`coroutineScope`), et le résumé affiché devient « Chargement interrompu : <exception> ».

### 4.3 Médias

**L'envoi (multipart écrit à la main, GeoNatureUpload.kt:1282-1360).**
- Boundary `----GeoMysBoundary<ms>`.
- Champs :
  - `id_table_location` ;
  - `id_nomenclature_media_type`, seulement s'il a été résolu ;
  - `uuid_attached_row`, pour le monitoring ;
  - `author`, `title_fr` ;
  - `description_fr`, qui vaut « Saisie GeoMys mobile » ;
  - `file`, avec le nom du fichier et le MIME deviné (`image/jpeg` par défaut).
- En-tête `Connection: close`, timeouts 30 s / 60 s.
- Faute de `setFixedLengthStreamingMode`, HttpURLConnection garde tout le corps en mémoire, ce qui pèse pour les vidéos.
- En Ktor : `formData { …; append("file", InputProvider/ChannelProvider { source kotlinx-io }, Headers{ContentDisposition filename, ContentType}) }`.
- Le streaming réel sur Darwin reste à vérifier. GeoMys-IOS passe par un fichier temporaire (MediaUpload.swift:96-145).

**Occtax.**
- Les médias partent **avant** l'occurrence (l. 466-535). Le JSON renvoyé par le serveur est inclus dans `cor_counting_occtax[i].medias`.
- `id_table_location` est celui de `pr_occtax.cor_counting_occtax`, mis en cache en mémoire (l. 1091-1120).
- Le type de média est déduit du préfixe MIME et des libellés de TYPE_MEDIA (l. 1245-1253).
- Si un média échoue pour une raison réseau ou serveur, toute l'observation est reportée, et ses médias déjà envoyés sont supprimés (DELETE, l. 523-534).
- Si le fichier local a disparu, l'occurrence part sans lui (l. 812-813).
- Si aucune occurrence du groupe n'aboutit, les médias du groupe sont supprimés (l. 594-598).

**Suivis (monitoring).**
- Le média est rattaché après la création de l'objet, par `uuid_attached_row` = `uuidPayload`. `id_table_location` vient de `schema_dot_table`.
- `title_fr` est déterministe : « <titre> — <nom du fichier> » (l. 1132-1135). Il sert à la réconciliation par `GET /api/gn_commons/medias/<uuid>` lors d'un nouvel essai (l. 1200-1216).
- Les chemins effectivement transmis sont renvoyés à l'appelant, qui les persiste (`mediasEnvoyes`, OutboxEnvoi.kt:500-504).

**Les chemins.**
- Ce sont des URI `file:` produites par `File.toURI()` (ui/MediaImport.kt:62), lues ensuite par `android.net.Uri.parse().path` (l. 497, 1220, 1291). Il faut un parseur commun.
- Le MIME vient de `URLConnection.guessContentTypeFromName` (l. 498, 1221, 1303). Il faut une table commune, et vérifier que photos, sons et vidéos restent bien classés. Sinon `id_nomenclature_media_type` manque et le serveur renvoie un 500 côté monitoring (commentaire l. 1192-1195).

### 4.4 Anti-doublon et reprises : invariants à transposer à l'identique

**Couche HTTP.**
- `Connection: close` sur tous les POST (HttpClient.kt:46-62, GeoNatureUpload.kt:1313-1315), contre le rejeu silencieux d'HttpURLConnection.
- En Ktor avec OkHttp : `retryOnConnectionFailure(false)`, et jamais de `HttpRequestRetry` sur un POST.
- Sur Darwin, `Connection` est un en-tête réservé d'URLSession et peut être ignoré. Le comportement de reprise des POST est à vérifier.

**Occtax (`GeoNatureUpload.envoyer` et `EnvoiSortie`).**
- Verrou par sortie avec `tryLock`, relecture sous verrou, union des acquis (EnvoiSortie.kt:87-122). Filet en mémoire `EnvoisNonPersistes`. `NonCancellable`.
- Pré-marquage du relevé (l. 380) et des occurrences (l. 459), avant le POST.
- Relevé : uuid déterministe `unique_id_sinp_grp` (l. 287-292), vérifié par `releves?unique_id_sinp_grp=` avec trois issues : trouvé, absent, ou indéterminable, auquel cas rien n'est créé (l. 668-710).
- Occurrence : uuid `unique_id_occurence_occtax`, cherché dans le dump du relevé (l. 777-806).
- Rejet franc : un 4xx sauf 408 et 429 (l. 764-765). Un 5xx laisse l'envoi incertain.
- Relevés vides : rollback ; les orphelins sont mémorisés par serveur (`RelevesOrphelins`) et purgés au prochain envoi, seulement s'ils sont toujours vides (l. 158-174, 734-759). Un relevé réutilisé n'est jamais supprimé (l. 606).

**Suivis (`OutboxEnvoi`).**
- Mutex global. Une saisie restée en SENDING passe en ERROR (l. 89-98).
- `dejaTentee` et `uuidPayload` sont persistés avant le POST ; si l'écriture échoue, rien n'est envoyé (l. 216-238).
- Avant tout nouveau POST, `chercherEnfantParUuid` vérifie l'existence en direct (MonitoringEnvoi.kt:63-104).
- `objetCree` est persisté juste après le POST (l. 460), avec le filet `creesNonPersistes` (l. 337).
- En cas de nouvel essai, seuls les médias repartent.

**OccHab.**
- Verrou par saisie. `envoiIncertain` est persisté avant un POST de création (EnvoiOccHab.kt:98-104).
- Vérification par `unique_id_sinp_station` (OccHabUpload.kt:94-102, 258-286). Si elle est impossible, on lève `EnvoiIncertain`.
- Un 5xx est traité comme incertain (EnvoiOccHab.kt:160-168).
- La mise à jour par `/stations/<id>/` est idempotente.
- Si l'id est illisible après une création, il est recherché aussitôt par uuid (OccHabUpload.kt:239-244).

**Tests.** Ces invariants sont couverts par 19 tests MockWebServer (EnvoiReleveAntiDoublonTest, GeoNatureUploadReseauDegradeTest, PertesEtDoublonsEnvoiTest, OccHabAntiDoublonTest, MediasMonitoringReconciliationTest, OutboxEnvoiTest, EnvoiSortieConcurrenceTest…). Il faut les transposer en `commonTest` avec le `MockEngine` de Ktor, ou les garder d'abord en androidUnitTest.

### 4.5 Dépendances à `Context`

- **Une seule dans `network/`** : `SyncRunner.executer(context, …)` (SyncRunner.kt:69-73). Le Context ne sert qu'à construire `GeoNatureConfig(context.applicationContext)`, c'est-à-dire les SharedPreferences `gn_config` et le mot de passe chiffré dans le Keystore. Pas de ressources, pas de Toast.
- **`SyncForegroundService`** : Context de service (notification, `PendingIntent`, `R.drawable`, `NotificationManager`).
- **Dépendances indirectes** : tout le paquet utilise des singletons du store initialisés ailleurs avec un Context (`filesDir` ou SharedPreferences) : `MonitoringCache` (`filesDir/monitoring`), `RelevesOrphelins` (prefs `releves_orphelins`), `TaxRefCache`, `NomenclatureCache`, `HabitatCache` et `HabitatCacheOccHab`, `StationsServeurCache`, `PictoCache`, `OutboxMonitoring`, `SortieStore` et `OccHabStore`.

Dans GeoMys, la plateforme fournira une racine de fichiers et un objet `Settings`.

### 4.6 SyncForegroundService et iOS

**Ce que fait le service.**
- Démarrage depuis `ConfigGeoNatureFragment.kt:731` via `ContextCompat.startForegroundService`.
- `onCreate` passe au premier plan (type DATA_SYNC) avec la notification « GeoMys — synchronisation » (canal `GeoMys_sync`, id 2). Il s'abonne à `SyncRunner.etat` pour mettre à jour le texte.
- `onStartCommand` lance une seule fois `SyncRunner.executer(applicationContext, forcer)`, dans un scope `SupervisorJob + Dispatchers.IO`, puis arrête le service (`stopForeground(REMOVE)` et `stopSelf`). Il renvoie `START_NOT_STICKY`.
- `onDestroy` annule le scope.

Son rôle : garder le processus et le réseau actifs, écran éteint ou appli en arrière-plan, pendant une synchro de plusieurs minutes.

**Contraintes Android.** Le type `dataSync` est déclaré dans le manifest (l. 52-53). Android 15 limite les services dataSync à 6 h sur 24 h, via le callback `onTimeout`, qui n'est pas implémenté. Sans impact pratique ici.

**Côté iOS, pas de service au premier plan.** Les options :
- `beginBackgroundTask(withName:expirationHandler:)` accorde environ 30 s après le passage en arrière-plan. C'est trop court pour une synchro TaxRef de plusieurs minutes, mais suffisant pour finir un envoi.
- `isIdleTimerDisabled = true` pendant la synchro empêche le verrouillage automatique de l'écran. C'est ce que fait GeoMys-IOS (`Network/SyncRunner.swift:62-67`).
- `BGProcessingTask` est planifié par le système : inadapté à une action immédiate de l'utilisateur.
- `BGContinuedProcessingTask` (iOS 26) permet de poursuivre en arrière-plan une tâche lancée par l'utilisateur, avec une progression affichée par le système. À prototyper et confirmer.
- Une `URLSession` d'arrière-plan est réservée aux transferts de fichiers ; elle ne convient pas à une suite d'appels JSON orchestrée par l'appli.

**Recommandation.**
- Une interface commune `ExecutionLongue` (début, fin, progression), utilisée par `SyncRunner` **et** par les envois (EnvoiSortie, EnvoiOccHab, OutboxEnvoi). iOS protège déjà les envois, Android non.
- En aM : démarrage du service au premier plan.
- En iM : `beginBackgroundTask` **avec** un `expirationHandler` qui annule proprement le Job, plus l'idle timer. GeoMys-IOS (`SyncRunner.swift:62`) n'en passe pas, or iOS termine l'appli si la tâche n'est pas close à l'expiration.

**Une interruption ne met pas les données en danger.** TaxRef n'est remplacé qu'après un téléchargement complet (GeoNatureSync.kt:356-369), et `SyncRunner` relance la `CancellationException` (l. 321-323). En revanche tout est à refaire : il n'y a pas de reprise liste par liste.

### 4.7 Autres points durs

1. **La sémantique de l'org.json d'Android n'est pas celle de kotlinx** (environ 560 appels `opt*`, `get*`, `keys`, `isNull` et constructeurs, sans compter les `put`) :
   - `optString` renvoie `"null"` pour une valeur JSON null, d'où les filtres `!= "null"` (AdditionalFields.kt:237, MonitoringSchemas.kt:168, 435, 441, GeoNatureSync.kt:634) ;
   - `optInt`, `optDouble` et `optBoolean` convertissent les chaînes et tronquent les décimaux ;
   - `opt()` renvoie des types Java, testés par `is Int`, `is Long`, `is Number`, `is Boolean` ou `JSONObject.NULL` (AdditionalFields.kt:219-223, 303-315 ; MonitoringSchemas.kt:354-357, 384-391, 434-446, 503-508, 585-591 ; MonitoringModules.kt:274-278 ; OccHabApi.kt:235-242 ; MonitoringEnvoi.kt:293-314) ;
   - `opt(k)?.toString()` sert de lecture brute (MonitoringDatalists.kt:104-105, 124-125, 373-377 ; AdditionalFields.kt:198, 277, 310-311, 334 ; GeoNatureSync.kt:87-89, 634 ; OccHabApi.kt:99-100), or `JsonPrimitive.toString()` entoure les chaînes de guillemets ;
   - plusieurs fonctions **modifient** le JSON sur place (MonitoringSchemas.kt:550-574 ; MonitoringDatalists.kt:307 ; `fermerAnneau`, GeoNatureUpload.kt:949-960), alors qu'un `JsonObject` est immuable.

   Proposition : une petite couche `JsonCompat` qui reproduit `opt*`, `isNull` et `keys` à l'identique de l'org.json **d'Android**, avec des tests de caractérisation. Attention : les tests JVM actuels déclarent `org.json:json:20240303` (app/build.gradle.kts), dont `optString` sur un null renvoie la valeur par défaut, pas `"null"`.
2. **UUID v3 déterministe** (GeoNatureUpload.kt:645-646). `kotlin.uuid` n'a pas d'équivalent à `UUID.nameUUIDFromBytes`.
   - Il faut un MD5 multiplateforme : `ByteString.md5()` d'okio ou KotlinCrypto.
   - Puis poser les bits de version et de variante : `b[6] = (b[6] and 0x0f) or 0x30` et `b[8] = (b[8] and 0x3f) or 0x80`.
   - Le résultat doit être identique bit à bit : un relevé « tenté » par l'ancienne appli doit être retrouvé par la nouvelle.
   - GeoMys-IOS l'a déjà réimplémenté (OcctaxUpload.swift:711-718) : on peut s'en servir pour des vecteurs de test communs.
3. **Normalisation NFD** (GeoNatureUpload.kt:1041-1046). Elle sert à apparier les libellés de nomenclature d'une instance à l'autre. Il n'existe pas d'API commune : expect/actual (`java.text.Normalizer` d'un côté, `NSString.decomposedStringWithCanonicalMapping` de l'autre) ou une bibliothèque (Doist x-normalize, par exemple, à évaluer). Il faut aussi vérifier que le moteur Regex de Kotlin/Native gère `\p{Mn}`.
4. **Tri par Collator français** (GeoNatureBrowse.kt:121-123). expect/actual, ou une clé de tri en minuscules sans accents. Sinon l'ordre d'affichage des JDD change.
5. **Lecture JSON en flux.** Gson `JsonReader` sert pour `meta/datasets` (plus de 2 500 JDD, avec `fields=modules`) et pour le HABREF complet (GeoNatureBrowse.kt:81-83, 110-118, 227-229).
   - Remplacement : kotlinx-serialization-json-io (`decodeFromSource` ou `decodeSourceToSequence`) sur le flux de réponse Ktor. Le passage de `ByteReadChannel` à `Source` est à valider.
   - À défaut, décoder directement en DTO `@Serializable` avec `ignoreUnknownKeys`, sans construire d'arbre `JsonElement`.
   - Même conseil pour les pages TaxRef : leur `page_size`, publié par le serveur, peut atteindre 10 000.
6. **Dates et fuseaux.**
   - `SimpleDateFormat` sans fuseau explicite prend celui de l'appareil. C'est le cas pour `date_min`, `date_max`, `hour_min` et `hour_max` (GeoNatureUpload.kt:176-177, 294-297), pour OccHab (OccHabUpload.kt:104, 168-169) et pour l'Explorer (GeoNatureBrowse.kt:419-420). En commun : `TimeZone.currentSystemDefault()` explicite.
   - `OccHabApi.parserDate` (l. 216-228) accepte un simple **préfixe**, car `SimpleDateFormat.parse` ignore la fin de la chaîne : `…T10:00:00.123+02:00` passe avec le premier motif. Ses instances sont partagées dans un `object` et ne sont pas thread-safe. Il faut un parseur maison.
7. **Concurrence.**
   - Un verrou bloquant est tenu pendant un appel réseau (GeoNatureAuth.kt:223) → `Mutex`.
   - Des caches `@Volatile` mutables sont partagés entre coroutines (MonitoringDatalists.kt:47 et 148 ; GeoNatureUpload.kt:99 et 1091 ; MonitoringModules.kt:74 ; GeoNatureAuth.kt:193).
   - API Java à remplacer : `ConcurrentHashMap` et `computeIfAbsent` (EnvoiSortie.kt:60-62, EnvoiOccHab.kt:33-35, OutboxEnvoi.kt:337), `Collections.synchronizedSet` (EnvoiSortie.kt:37), `AtomicBoolean` (SyncRunner.kt:57), `AtomicInteger` (GeoNatureSync.kt:267-268, OccHabApi.kt:186 et 263). Remplacement : `Mutex` ou atomicfu.
8. **Mémoire et performances sous Kotlin/Native.** `synchroniserTaxRef` garde toutes les listes en mémoire avant la fusion (GeoNatureSync.kt:269-310). Le code rattrape `OutOfMemoryError` (SyncRunner.kt:264-265, 324-334), mais sur iOS un OOM n'est en pratique pas rattrapable (à vérifier). Il faut mesurer le pic sur un référentiel complet et préférer des DTO à des arbres JSON.
9. **Points mineurs.**
   - `BuildConfig.DEBUG` (MonitoringEnvoi.kt:212) → `expect val` ou BuildKonfig.
   - `"%.5f".format` (MonitoringObjets.kt:308) dépend de la locale.
   - `javaClass.simpleName` (6 occurrences) → `::class.simpleName`.
   - `putIfAbsent` (MonitoringSync.kt:113).
   - `@VisibleForTesting` (EnvoiSortie.kt:40).

### 4.8 Divergences Android ↔ iOS à trancher

Le code commun remplacera les deux applications ; ces écarts doivent donc être tranchés.

| Sujet | Android | iOS |
|---|---|---|
| 401 | Le cache est seulement invalidé (HttpClient.kt:80-84). | Reconnexion et un nouvel essai (OcctaxUpload.swift:100-127, MediaUpload.swift:167). |
| Cookies | `Set-Cookie` découpés en `nom=valeur` (GeoNatureAuth.kt:242-244). | L'en-tête brut est renvoyé tel quel (GeoNatureAuth.swift:216). |
| Échec du login | Redirections désactivées, diagnostic des 3xx. | Redirections suivies, puis détection d'une page HTML (GeoNatureAuth.swift:175-217). |
| Clé du cache d'auth | (base, login). | (url, login, mot de passe) (GeoNatureAuth.swift:75-85). |
| uuid d'occurrence | v4 aléatoire, persisté (model/Models.kt:156). | Dérivé de `"geomys-occurrence:"+obs.id` (OcctaxUpload.swift:706-708). Pour migrer les saisies iOS en statut incertain, il faudra dériver leur uuid. |
| Titres des médias Suivis | Déterministes, avec réconciliation. | Suffixe « (i) », sans réconciliation (MediaUpload.swift:355). Un nouvel essai après migration pourrait doubler les photos des saisies iOS en attente. |
| Altitudes OccHab (`geo/info`) | Présentes. | Absentes. |
| Corps multipart | Gardé en mémoire. | Écrit dans un fichier temporaire. |
| Protection des envois en arrière-plan | Aucune. | `beginBackgroundTask` (EnvoiSortie.swift:219-233). |

## 5. Compatibilité des données

Ce que GeoMys doit relire, sur Android avec le même applicationId, pour succéder à l'appli actuelle.

### 5.1 Identité d'installation

Il existe deux variantes : `fr.ariegenature.geomys` (github) et `fr.ariegenature.public.geomys` (play) (app/build.gradle.kts:43-51). Il faut conserver les deux applicationId et les mêmes clés de signature. C'est ce qui permet d'hériter de `/data/data/<appId>/`, c'est-à-dire des SharedPreferences, de `filesDir` et de la clé Keystore.

### 5.2 SharedPreferences `gn_config` (store/GeoNatureConfig.kt)

Clés écrites par ce périmètre :

| Clé | Type | Écrite par | Contenu |
|---|---|---|---|
| `gn_nom_utilisateur` | String | GeoNatureAuth.kt:99 | `prenom_role nom_role` |
| `gn_id_role_utilisateur` | Int (défaut −1) | GeoNatureAuth.kt:100 | id_role |
| `gn_version_serveur` | String | GeoNatureAuth.kt:107 | version GeoNature |
| `gn_serveur_compatible` | Boolean (défaut true) | GeoNatureAuth.kt:114/122 | résultat de la garde ≥ 2.15 |
| `gn_taxhub_url` | String | GeoNatureSync.kt:576-577 | `settings.sync.taxhub_url` (ou "") |
| `gn_obs_duration_days` | Int (365) | GeoNatureSync.kt:581-582 | `area_observation_duration` |
| `gn_taxref_page_size` | Int (1000) | GeoNatureSync.kt:583-584 | `settings.sync.page_size` |
| `gn_date_hours` / `gn_date_end` | Boolean | GeoNatureSync.kt:587-588 | `input.date.enable_hours/enable_end_date` |
| `gn_form_fields` | String (JSON brut) | GeoNatureSync.kt:592-598 | `OCCTAX.form_fields` |
| `gn_cache_settings_occtax` | String (JSON brut) | GeoNatureSync.kt:601/604 | `settings.nomenclature` |
| `gn_cache_datasets` | String (Gson) | SyncRunner.kt:106 | `List<GeoNatureDataset>` |
| `gn_cache_ds_creables` | String (CSV d'entiers) | SyncRunner.kt:113 | ids des JDD créables en Occtax |
| `gn_cache_listes` | String (Gson) | SyncRunner.kt:119 | `List<GeoNatureListe>` |
| `gn_cache_obs` | String (Gson) | SyncRunner.kt:126 | `List<GeoNatureObservateur>` |
| `gn_cache_add_fields_occtax` | String (Gson ou `[]`) | SyncRunner.kt:133-134 | `List<AdditionalFieldDef>` |
| `gn_occtax_creer`, `gn_occhab_dispo`, `gn_occhab_creer` | Boolean | SyncRunner.kt:165-168 | droits CRUVED |
| `gn_cache_datasets_occhab` | String (Gson) | SyncRunner.kt:174 | `List<GeoNatureDataset>` |
| `gn_cache_ds_creables_occhab` | String (CSV) | SyncRunner.kt:176-177 | ids des JDD créables en OccHab |
| `gn_occhab_id_list_habitat` | Int (−1) | SyncRunner.kt:180-181 | `OCCHAB.ID_LIST_HABITAT` |
| `gn_occhab_formconfig` | String (JSON brut) | SyncRunner.kt:192 | `OCCHAB.formConfig` |
| `gn_occhab_defauts_nomenc` | String (Gson `Map<String,Int>`) | SyncRunner.kt:194 | `{"MNEMONIQUE": id}` |
| `gn_version_donnees_chargees` | Int | SyncRunner.kt:317 | version des données |
| `gn_rechargement_requis_maj` | Boolean | SyncRunner.kt:318 | rechargement requis après mise à jour |

- **Clés seulement lues** : `gn_url`, `gn_login`, `gn_dataset`, `gn_taxa_liste`, `gn_obs_defaut_nom`.
- **Mot de passe** : un blob AES-GCM. Clé Keystore `geomys_mdp`, prefs `gn_secure_v2`, clé `gn_mdp_chiffre`, IV de 12 octets en préfixe (store/MdpChiffre.kt:50-54). Il faut un coffre expect/actual capable de relire ce blob en aM, et le Keychain en iM.
- **Relecture** : `SharedPreferencesSettings(getSharedPreferences("gn_config", MODE_PRIVATE))` de multiplatform-settings relit tout tel quel, avec les mêmes types Int, Boolean et String.

### 5.3 Modèles sérialisés par Gson

Les noms de champs sont ceux des propriétés Kotlin. Ils sont protégés par `proguard-rules.pro`, et Gson omet les null.

Côté kotlinx : `@Serializable` **sans renommage**, `ignoreUnknownKeys = true`, `explicitNulls = false`, et une valeur par défaut sur chaque champ.
- `GeoNatureDataset` (`gn_cache_datasets`, `gn_cache_datasets_occhab`) : `{"id","nom","idTaxaList"?,"actif","moduleCodes":[…]}`. org.json relit aussi les clés `id` et `actif` (GeoNatureConfig.kt:329, 349, 368).
- `GeoNatureListe` (`gn_cache_listes`) : `{"id","nom"}`.
- `GeoNatureObservateur` (`gn_cache_obs`) : `{"idRole","nomComplet"}`.
- `AdditionalFieldDef` (`gn_cache_add_fields_occtax`) :
  - champs : `idField`, `fieldName`, `fieldLabel`, `widget`, `fieldValues`, `fieldValueCodes`, `required`, `description?`, `defaultValue?`, `objectsCode`, `datasetsIds`, `idList?`, `widgetServeur`, `codeNomenclatureType?`, `nomenclatureOptions` ;
  - `widget` est le nom d'enum : TEXT, TEXTAREA, NUMBER, SELECT, RADIO, CHECKBOX, NOMENCLATURE ou INCONNU ;
  - `nomenclatureOptions` vaut `[{"first":id,"second":label}]`, la même forme que le `PairSerializer` de kotlinx.
- `HabitatSuggestion` (fichiers `habitats_v1.json` et `habitats_occhab_v1.json` de `filesDir`, écrits par store/HabitatCache) : `{"cdHab","libelle","cdTypo"?}`.

Différence bénigne avec un vieux cache auquel manque un champ récent : Gson laissait la valeur par défaut de la JVM (par exemple `actif=false` ou une liste null), là où kotlinx appliquera la valeur par défaut Kotlin.

### 5.4 JSON bruts dans `filesDir/monitoring/`

Fichiers (store/MonitoringCache.kt:118-131). `safeKey` remplace par `_` tout caractère hors `[A-Za-z0-9._-]` (l. 61).
- `modules.json` : items serveur filtrés par CRUVED (MonitoringModules.kt:253-257).
- `schema_<m>.json`, `enfants_<m>.json`, `objet_<m>_<type>_<id>.json`.
- `observateurs_<idListe>.json`.
- `datalist_<safeKey(apiUrl)>.json`.

Ce sont les réponses du serveur, telles quelles. Les parseurs communs doivent donc accepter exactement les mêmes formes.

La clé `datalist_…` dépend de l'`apiUrl` **après** dérivation (MonitoringSchemas.kt:195-280). Le moindre changement de dérivation, par exemple dans l'ordre de `module_code`, `active` et `create`, rend introuvables les caches hors-ligne déjà présents.

### 5.5 Prefs `releves_orphelins`

Clé `orphelins_<base sans / final>` → CSV d'`id_releve_occtax` (store/RelevesOrphelins.kt:33-58).

### 5.6 Identités dérivées, liées aux données en attente

- `unique_id_sinp_grp` = UUID v3 de `"geomys-releve:" + (releveId ?: obs.id)`, en minuscules (GeoNatureUpload.kt:645-646).
- Le `title_fr` des médias Suivis vaut `"<objectType> <8 premiers caractères de uuidPayload> — <nom du fichier>"` (OutboxEnvoi.kt:488 et GeoNatureUpload.kt:1132-1135). La réconciliation des saisies en attente en dépend.
- Identités persistées ailleurs mais envoyées par ce périmètre : `uuidOccurrence` (model/Models.kt:156), `uuidStation`, `uuidPayload`.

### 5.7 Chemins des médias

Ce sont des URI `file:` produites par `File.toURI()` (ui/MediaImport.kt:49-62), de la forme `file:/data/user/0/<appId>/files/medias/<photo|audio|…>_<ms>_<i>.<ext>`, avec une seule barre après `file:`. Elles sont stockées dans les saisies et dans l'outbox.

Le parseur commun doit accepter `file:/…` et `file:///…`, et décoder les `%xx`. Ces chemins restent valides tant que `filesDir/medias/` est conservé.

### 5.8 Formats alimentés ici mais définis par le store

À inventorier par le périmètre store :
- `filesDir/taxref/*.json` et les prefs `taxref_cache` (`listesSynchronisees`, `listesIncompletes`, `versionSauvegardee`) ;
- les prefs `nomenclature_cache` ;
- `stations_serveur_occhab_v1.json` ;
- `monitoring_outbox.json` (`SaisieEnAttente` : `etat`, `dejaTentee`, `objetCree`, `idServeur`, `uuidPayload`, `mediasEnvoyes`, `valeursJson`…) ;
- dans les sorties, les champs `Observation.envoyeeServeur`, `idReleveIncertain` et `releveTente` ;
- les pictos de `filesDir/monitoring/pictos/`.

## 6. Bilan chiffré

| Catégorie | Fichiers | Lignes physiques | Lignes de code* |
|---|---|---|---|
| COMMUN | 5 (CompatibiliteServeur, InvalidationCaches, MonitoringApi, MonitoringSync, TaxRefService) | 766 | 353 |
| COMMUN-ADAPTÉ | 20 | 7 722 | 5 070 |
| MIXTE | 0 | 0 | 0 |
| PLATEFORME | 1 (SyncForegroundService) | 135 | 85 |
| UI | 0 | 0 | 0 |
| ABANDON | 0 | 0 | 0 |
| **Total** | **26** | **8 623** | **5 508** |

\* Hors lignes vides et commentaires (estimation par grep).

- **Partageable** (COMMUN et COMMUN-ADAPTÉ) : 8 488 lignes sur 8 623, soit 98 %.
- **À écrire par plateforme** : les 135 lignes du service, plus environ 60 lignes d'expect/actual nouvelles. Cela couvre les deux moteurs HTTP, le classement des erreurs réseau, la normalisation NFD, le Collator, les cookies Darwin, le coffre du mot de passe et l'exécution longue sur iOS.
- **Volume de réécriture mécanique** :
  - environ 50 sites d'appel HTTP ;
  - environ 560 appels org.json (`opt*`, `get*`, `keys`, `isNull`, constructeurs), sans compter les `put` ;
  - 27 appels `loginAvecCookies`/`login` qui deviennent `suspend` ;
  - Gson : 6 `toJson` de persistance, 5 `fromJson`, 3 lecteurs `JsonReader` ;
  - 31 `android.util.Log` ;
  - 7 `SimpleDateFormat`.
- **Code qui disparaît plutôt qu'il ne se porte** (environ 200 l.) : les lecteurs Gson en flux (GeoNatureBrowse.kt:128-204 et 234-268, environ 110 l.), le multipart écrit à la main (environ 40 l.), et la configuration d'HttpURLConnection (HttpClient.kt, environ 50 l. de code).
- **Logique pure, testable sans réseau** : environ 2 350 lignes (§2), auxquelles s'ajoutent environ 1 320 lignes d'orchestration sans API plateforme (OutboxEnvoi, EnvoiSortie, EnvoiOccHab, SyncRunner).
