# 2. Stockage, modèles, utilitaires et localisation (`store/`, `model/`, `util/`, `gpx/`, `location/`, racine)

> Détail de l'inventaire GeoMys. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys est décrite au § 9 de la synthèse.

## 1. Inventaire fichier par fichier

Racine : `/Users/claude/Developpements/GeoMys-Android/app/src/main/java/fr/ariegenature/geomys/` (commit cdd5196). « Lignes » = `wc -l`, en-tête GPL de 18 lignes compris. Cible proposée : module `shared/src/{commonMain,androidMain,iosMain}/kotlin/fr/ariegenature/geomys/…`. Numéros entre parenthèses = lignes du fichier source.

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys | Remarques |
|---|---|---|---|---|---|---|
| `store/CachesSynchronises.kt` | 91 | Purge les caches synchronisés (« Vider le cache », rechargement après mise à jour) et arme au lancement le drapeau « rechargement requis ». | COMMUN-ADAPTÉ | `Context` (84-85), utilisé seulement pour construire `GeoNatureConfig(context)` → injecter l'instance. Appelle `network.MonitoringApi.invaliderCaches()` (hors périmètre). | commonMain `store/` | Aucune I/O propre. Garder l'asymétrie phase A / phase B (41-55). iOS : pas de fichier dédié (UI/ConfigGeoNatureView.swift:658-666, Network/SyncRunner.swift:88-90). |
| `store/GeoNatureConfig.kt` | 449 | Paramètres de connexion et sélections, caches JSON de config serveur, règles de validité (`saisiePossible`, `saisieOcctaxValide`…). | COMMUN-ADAPTÉ (+ ≈35 l. androidMain) | `SharedPreferences "gn_config"` (37 ; 34 clés en `apply()`) → multiplatform-settings ; `org.json.JSONObject/JSONArray` (32, 179, 193, 328, 348, 365) → `Json.parseToJsonElement` ; `MdpChiffre` (45, 63-69) → interface `CoffreMotDePasse` ; `@Volatile` (415, 420) → `kotlin.concurrent.Volatile` ; `synchronized(this)` (427) → atomicfu ; nettoyage legacy `File(dataDir, "shared_prefs/gn_secure.xml")`, `deleteSharedPreferences`, `KeyStore.deleteEntry("_androidx_security_master_key_")`, `Log` (425-448) → androidMain ou abandon. | commonMain `store/` ; androidMain `plateforme/` (nettoyage legacy) | 40 `GeoNatureConfig(context)` dans 28 autres fichiers → singleton injecté. `champFormVisible` de niveau fichier (30-33) sert aussi à l'UI. iOS : Store/GeoNatureConfig.swift (622 l.) ; 18 des 34 clés Android y portent un autre nom. |
| `store/HabitatCache.kt` | 195 | Deux caches HABREF (Occtax, OccHab) en fichier JSON, avec index de recherche locale (contains sans accents, ou réplique du `ilike` serveur). | COMMUN-ADAPTÉ | `Context.filesDir` (56) → racine fournie par la plateforme ; `java.io.File` + tmp/`renameTo` (61-82) → okio `FileSystem` + `atomicMove` ; Gson `TypeToken<List<HabitatSuggestion>>` (46, 65, 79-80) → kotlinx ; `java.text.Normalizer` NFD + regex `\p{Mn}` (103-108, 157-163) → `expect fun nfd()` (Android `Normalizer`, iOS `decomposedStringWithCanonicalMapping`) puis filtre `CharCategory.NON_SPACING_MARK` ; `@Volatile` (51, 54). | commonMain `store/habref/` + `expect nfd()` | ⚠ caractère NUL littéral dans le source (100 `' '`, et dans le commentaire 94) : l'écrire `'\u0000'`. Déplacer `network.HabitatSuggestion` dans `model/`. iOS : Store/HabitatCache.swift. |
| `store/JsonCollectionStore.kt` | 118 | Base des stores « liste JSON » : cache process-wide, normalisation post-Gson, quarantaine, lire-modifier-écrire atomique sous verrou. | COMMUN-ADAPTÉ | Gson + `java.lang.reflect.Type` (44, 53, 71, 94) → `KSerializer<T>` avec décodage élément par élément ; `synchronized(verrou)` (65, 93, 104, 112) → `kotlinx.atomicfu.locks.SynchronizedObject` (API synchrone conservée) ou `Mutex` (API suspend) ; `android.util.Log` (77, 83, 96) → Kermit. | commonMain `store/io/` | Pièce maîtresse de la fiabilité des saisies : à porter en premier, avec StoreConcurrenceTest et CacheCorruptionTest. iOS : Store/Quarantaine.swift + logique répétée dans chaque store. |
| `store/MapTileCache.kt` | 127 | Paramètre le cache de tuiles osmdroid (dossier, plafond 1 Go → 800 Mo), le mesure, le vide et purge chaque semaine les fichiers de plus de 30 jours. | ABANDON (la fonction est à refaire en PLATEFORME pour MapLibre) | `org.osmdroid.config.Configuration` (48-50, 57, 77, 100), `SqlTileWriter.purgeCache()/onDetach()` (69-73), `Context.cacheDir` (46), prefs `GeoMys_prefs` / `derniere_purge_tuiles` (95-96, 124), scope `Dispatchers.IO` (92), `Log`. Équivalents : Android `OfflineManager.setMaximumAmbientCacheSize / clearAmbientCache / resetDatabase`, iOS `MLNOfflineStorage` (mêmes opérations). | androidMain / iosMain `carte/`, derrière une interface commune `CacheCarte` (taille, vider, plafond) | Seules les constantes 1 Go / 800 Mo survivent. Le `cache/MapTiles/cache.db` d'osmdroid contient aussi les zones téléchargées pour le hors-ligne et MapLibre ne sait pas le lire (cf. §3). Aussi appelé par ui/MainActivity.kt:48 et ui/CacheManagerFragment.kt:207-208. iOS : Store/MapTileCache.swift (cache disque maison). |
| `store/MbtilesStore.kt` | 144 | Importe (tmp → validation → rename) et lit les métadonnées des fonds de carte MBTiles hors ligne. | MIXTE (≈120 l. en commun après adaptation, ≈15 l. plateforme) | `android.net.Uri` + `ContentResolver.openInputStream` / `query(OpenableColumns.DISPLAY_NAME)` (53-66, 134-137) → PLATEFORME (Android : SAF ; iOS : URL de `fileImporter` / `UIDocumentPickerViewController` + `startAccessingSecurityScopedResource`) ; `SQLiteDatabase.openDatabase(OPEN_READONLY)` + `rawQuery` (96-114, 122-132) → androidx.sqlite KMP (`BundledSQLiteDriver`, ouverture en lecture seule) ou driver SQLDelight ; `java.io.File` (43-84) → okio ; `System.currentTimeMillis` (54) ; `Log` (78). | commonMain `carte/mbtiles/` + androidMain / iosMain `ImportFichier` | Réutilisable avec MapLibre : les fichiers `files/mbtiles/*.mbtiles` tels quels, et `info()` (zooms min/max, nom) pour déclarer la source. `format` : tout ce qui n'est pas jpg devient « png » (103) ; à étendre (`pbf` = tuiles vectorielles). MapLibre Native lit des sources `mbtiles://` locales dans ses versions récentes ; à vérifier avec maplibre-compose. iOS : Store/MbtilesStore.swift (sqlite3) + UI/Map/MbtilesOverlay.swift (`MKTileOverlay`). |
| `store/MdpChiffre.kt` | 155 | Chiffre le mot de passe GeoNature avec une clé AES-256-GCM de l'Android Keystore et range le blob en SharedPreferences. | PLATEFORME | `KeyStore.getInstance("AndroidKeyStore")` ; `KeyGenerator` + `KeyGenParameterSpec` (alias `geomys_mdp`, ENCRYPT + DECRYPT, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, 256 bits, sans authentification utilisateur ni StrongBox) (134-154) ; `Cipher("AES/GCM/NoPadding")` + `GCMParameterSpec(128, IV de 12 o)` (78-83, 98-101) ; `android.util.Base64.NO_WRAP` ; prefs `gn_secure_v2` / `gn_mdp_chiffre` ; `@Synchronized` + `@Volatile` (cache process). iOS : Keychain `kSecClassGenericPassword` (`SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete` par cinterop Security, ou `KeychainSettings` de multiplatform-settings). | interface commune `CoffreMotDePasse` (lire / écrire / effacer / disponible) ; androidMain = ce fichier quasi inchangé ; iosMain = Keychain | Pour relire le blob existant : mêmes alias, fichier, clé et format, ET même applicationId + même certificat de signature (la clé Keystore est liée à l'UID de l'appli). iOS existant : `KeychainHelper` (Store/GeoNatureConfig.swift:580-622 ; service `fr.ariegenature.geomys`, compte `gn_password`, `kSecAttrAccessibleAfterFirstUnlock`) ; la variante `…ThisDeviceOnly` reproduirait l'exclusion de sauvegarde d'Android. |
| `store/MediasOrphelins.kt` | 84 | Au démarrage, supprime de `files/medias/` les médias que plus aucun store ne référence et qui ont plus de 48 h. | COMMUN-ADAPTÉ | `Context.filesDir` (49) ; `listFiles` / `lastModified` / `delete` (50, 76) → okio `list` + `metadata().lastModifiedAtMillis` + `delete` ; `System.currentTimeMillis` (73) → `Clock.System` ; `Log` (79) ; construit `SortieStore(context)` (62). | commonMain `store/medias/` | Comparaison par nom de fichier (57-60) : tolère les URI absolues `file:/data/user/0/<appId>/files/medias/…` stockées dans les saisies. Pas d'équivalent iOS (Store/MediaLocal.swift supprime à la demande). |
| `store/MonitoringCache.kt` | 132 | Cache disque write-through des réponses JSON brutes du module Monitoring, avec mémo en lecture protégé par un compteur de génération. | COMMUN-ADAPTÉ | `Context.filesDir` (55) ; `File` + tmp/rename (65-99) → okio ; `java.util.concurrent.ConcurrentHashMap` (48) et `putIfAbsent` (74, JVM seulement) → map sous verrou atomicfu (ou `ConcurrentMutableMap` de Stately) ; `@Volatile generation` + `generation++` (52, 96, 111) → `atomic(0)`. | commonMain `store/monitoring/` | Contenus opaques (aucune classe sérialisée). `vider()` ne supprime que les fichiers : le sous-dossier `pictos/` est préservé. Deux `setJson` simultanés sur la même clé partagent le même `.tmp` (risque déjà présent). iOS : Store/MonitoringCache.swift. |
| `store/NidificationOiseaux.kt` | 92 | Table embarquée des mois de nidification (285 espèces), pour proposer le statut nicheur d'un oiseau à la saisie. | COMMUN-ADAPTÉ | `Context.assets.open("nidification_oiseaux.json")` (57) → ressources Compose `Res.readBytes("files/nidification_oiseaux.json")` (API suspend : précharger au démarrage) ; Gson + `@SerializedName` (42-48, 59) → `@Serializable` ; `putIfAbsent` (69) ; `table` non volatile (51). | commonMain `store/` + `composeResources/files/` | Dépend de `TaxRefCache.normaliser` et de `entreesParCdNom`. Asset de 48 Ko identique côté iOS (GeoMys-IOS/Resources/nidification_oiseaux.json, Store/NidificationOiseaux.swift). |
| `store/NomenclatureCache.kt` | 164 | Cache des valeurs de nomenclature (avec restrictions taxonomiques) et des défauts, filtrage par groupe et règne, listes de groupes INPN. | COMMUN-ADAPTÉ | prefs `nomenclature_cache` (88), clés `nom_cache_v1` et `nom_defauts_v1` → Settings ; Gson `TypeToken<Map<String, List<NomValeur>>>` et `Map<String, String>` (139-162) → kotlinx ; `lateinit prefs` + `init(context)` → injection ; `@Volatile` (45-46). | commonMain `store/` (+ `NomValeur`, `TaxrefRestriction` → `model/`) | `filtrerPourGroupes` et `regno` sont de la logique pure. iOS : Store/NomenclatureCache.swift (UserDefaults.standard, valeurs de type `Data`). |
| `store/OccHabStore.kt` | 347 | Store « Mes stations » : saisies OccHab, empreinte d'intégrité, états d'envoi par station, normalisation post-Gson. | COMMUN-ADAPTÉ | prefs `occhab_store` / `saisies_sauvegardees` en `commit()` (39, 63-64) → fichier atomique + migration ; quarantaine `File(filesDir, "occhab_store.corrupt.json")` (65-70) → okio ; Gson TypeToken (61) ; `synchronized(VERROU)` (47) ; `java.util.UUID` (288) → `kotlin.uuid.Uuid` ; `@androidx.annotation.VisibleForTesting` (79). | commonMain `store/occhab/` | La logique (83-252) se porte telle quelle. Les normaliseurs exhaustifs (255-347) se simplifient avec les défauts kotlinx mais doivent continuer à écarter une entrée invalide. Purge unique de l'ancienne clé `stations_sauvegardees` (43-53). iOS : Store/OccHabStore.swift (fichier `occhab_store.json`). |
| `store/OcctaxFieldsConfig.kt` | 338 | Registre des champs de nomenclature Occtax, avec leur visibilité et leur ordre calculés d'après le `settings.json` serveur. | COMMUN-ADAPTÉ (presque COMMUN) | Uniquement `com.google.gson.JsonParser` / `JsonObject` (21, 284-337) → `Json.parseToJsonElement` + `jsonObject` / `jsonArray` / `jsonPrimitive`. | commonMain `store/` | Le registre (80-222) est fait de données pures. La conversion booléenne n'est pas la même : avec Gson, `asBoolean` rend vrai pour la chaîne « TRUE » et faux pour un nombre ; `booleanOrNull` de kotlinx ne se comporte pas pareil. À aligner. iOS : Network/GeoNatureNomenclatures.swift + Store/GeoNatureConfig.swift:440-500. |
| `store/OutboxMonitoring.kt` | 289 | File d'attente des saisies Monitoring (`SaisieEnAttente`) : store fichier durable, suppression en cascade parent/enfants, purge des saisies envoyées. | COMMUN-ADAPTÉ | `Context.filesDir` (142) ; `FileOutputStream` + `fd.sync()` + rename (155-180) → okio + `atomicMove` (fsync à garantir sur iOS) ; quarantaine par `copyTo` (183-188) ; `UUID.randomUUID()` (37) ; `System.currentTimeMillis()` (54) ; `Charsets.UTF_8` (163) → `encodeToByteArray()` ; Gson TypeToken (138) ; `synchronized` (141) ; `Log` (157, 173-177, 227). | commonMain `store/monitoring/` ; `SaisieEnAttente` → `model/` | C'est l'écriture la plus exigeante du périmètre. L'enum `Etat` est sérialisée par son nom. iOS : Store/OutboxMonitoring.swift (« squelette », dans Application Support). |
| `store/PictoCache.kt` | 182 | Cache disque des pictogrammes de protocole : téléchargement, cache négatif de 24 h, en-têtes d'authentification limités à la même origine. | COMMUN-ADAPTÉ | `HttpURLConnection` (90-109 ; délais de 10 s ; `Authorization` / `Cookie`) → client Ktor + `HttpTimeout` ; `java.net.URL` (`memeOrigine`, 157-164) → `io.ktor.http.Url` ; `BitmapFactory.decodeByteArray(inJustDecodeBounds)` (136-140) → test des signatures JPEG / PNG / GIF / WebP / BMP en commun (ou expect/actual `UIImage(data:)`) ; `File` / `lastModified` (152) → okio ; `Context.filesDir` (40) ; `@VisibleForTesting` (45). | commonMain `store/monitoring/` | L'API bloquante devient `suspend`. iOS : Store/PictoCache.swift. |
| `store/RelevesOrphelins.kt` | 58 | Mémorise, serveur par serveur, les relevés Occtax restés vides côté serveur pour retenter leur suppression. | COMMUN-ADAPTÉ | prefs `releves_orphelins` (33), clés `orphelins_<url sans / final>` (liste d'entiers séparés par des virgules) → Settings. | commonMain `store/` | Lire-modifier-écrire sans verrou (`apply()`). iOS : Store/RelevesOrphelins.swift (UserDefaults.standard ; retire « / » et espaces aux deux bouts, donc la clé peut différer). |
| `store/SortieStore.kt` | 292 | Store « Mes saisies » Occtax : sorties, fusion des acquis d'envoi, pré-marquages anti-doublon, normalisation post-Gson. | COMMUN-ADAPTÉ | prefs `sorties_store` / `sorties_sauvegardees` en `commit()` (34, 45-46) → fichier atomique + migration ; quarantaine `files/sorties_store.corrupt.json` (47-52) ; Gson TypeToken (43) ; `java.util.UUID` (272) ; `@VisibleForTesting` (64). | commonMain `store/occtax/` | La logique (68-192) se porte telle quelle. iOS : Store/SortieStore.swift (fichier `sorties_sauvegardees.json`). |
| `store/StationsServeurCache.kt` | 178 | Cache hors ligne, versionné, des stations OccHab du serveur appartenant à l'utilisateur, par JDD. | COMMUN-ADAPTÉ | `Context.filesDir` (78) ; `File` + tmp/rename (109-118) → okio ; Gson `Contenu` (68-74, 88) → `@Serializable` ; `@Synchronized` (108, 123, 129, 146, 170) → verrou explicite ; `System.currentTimeMillis` (125, 132, 151). | commonMain `store/occhab/` | ⚠ `@Synchronized` compile en commonMain mais n'a aucun effet sur iOS. `VERSION_FORMAT = 2` (61) : un fichier d'une autre version est supprimé. iOS : Store/StationsServeurCache.swift. |
| `store/TaxRefCache.kt` | 1028 | Référentiel TaxRef local (cache par nom normalisé + 7 index annexes) : résolution de noms (par groupe, par liste, recherche vocale) et propositions. | COMMUN-ADAPTÉ (gros morceau) | prefs `taxref_cache` (153 ; 5 clés) → Settings ; `File(filesDir, "taxref")` + 8 fichiers JSON (92-107, 166-199) → okio ; lecture/écriture Gson en flux `JsonReader` / `JsonWriter` (943-966, 1005-1017) → `kotlinx-serialization-json-okio` ; `GsonBuilder` + `TaxRefEntryDeserializer` (56-76, 119-121) → kotlinx ; `BufferedReader` / `FileReader` / `FileWriter` ; `synchronized(verrou)` (539, 933, 982, 984) ; 15 champs `@Volatile` ; `javaClass.simpleName` (194, 1024) → `::class.simpleName` ; `Log`. | commonMain `taxref/`, scindé en `TaxRefIndex` (pur) et `TaxRefStockage` | ≈470 l. de logique pure (cf. §2). `normaliser()` (911-930) doit rester identique, les clés étant persistées. Empreinte mémoire importante (§3). iOS : Store/TaxRefCache.swift (755 l.) + Store/PropositionsTaxon.swift. |
| `model/Models.kt` | 183 | Modèles Occtax persistés : `Taxon`, `PointTrace`, `Denombrement`, `Observation`, `Sortie`. | COMMUN-ADAPTÉ | `java.util.UUID.randomUUID()` (34, 49, 156, 173) → `Uuid.random().toString()` ; `System.currentTimeMillis()` (64, 174) → `Clock.System.now().toEpochMilliseconds()` ; import `java.util.Date` inutilisé (21) ; ajouter `@Serializable`. | commonMain `model/` | Aucun type osmdroid. `PointTrace(latitude, longitude)` peut devenir le type commun « LatLon ». Garder les noms de propriétés, qui forment le format disque. iOS : Model/Models.swift. |
| `model/OccHab.kt` | 288 | Modèles OccHab (`OccHabHabitat`, `OccHabStation`, `OccHabSaisie`), empreinte de contenu et test de géométrie. | COMMUN-ADAPTÉ | `java.util.UUID` (35, 80, 86, 280) ; `System.currentTimeMillis` (92, 282) ; `org.json.JSONArray` (247-267) → `JsonArray` de kotlinx. | commonMain `model/` | osmdroid n'est cité qu'en commentaire (110). `empreinteContenu()` repose sur `Double.toString()` : c'est cohérent tant qu'on compare des empreintes calculées sur la même plateforme. iOS : Model/OccHabModels.swift. |
| `util/AnaEval.kt` | 473 | Encode, décode et valide le bloc `[ANA-EVAL] {json}` (champs Natura 2000) porté par `comment` et `technical_precision` ; portage du plugin QGIS. | COMMUN-ADAPTÉ | parsing `org.json.JSONObject/JSONArray` (247-265) → `Json { isLenient = true }.parseToJsonElement` + conversion en Map / List / Int / Long / Double ; `JSONObject.quote` et `numberToString` (411-418) → réécriture fidèle ; `sortedMapOf` (289, 434 ; JVM seulement) → `LinkedHashMap` + tri des clés ; `RegexOption.DOT_MATCHES_ALL` (54 ; absent de commonMain) → motif `[\s\S]*?`. | commonMain `util/` | Logique pure à ≈90 %. AnaEvalTest (495 l., sans Robolectric) se porte en commonTest, mais tourne sur JSON-java (`org.json:json:20240303`) et non sur l'org.json d'Android : il faut choisir la référence (§3). iOS : Util/AnaEval.swift (469 l.). |
| `util/DateAffichage.kt` | 39 | Affiche les dates ISO de GeoNature (JJ/MM/AAAA, ou tronquées à la date). | COMMUN | Aucune. | commonMain `util/` | Copie directe. |
| `util/DateHeureDefaut.kt` | 39 | Valeurs par défaut « aujourd'hui / maintenant » des champs date et heure, en heure locale. | COMMUN-ADAPTÉ | `SimpleDateFormat` / `Date` / `Locale.US` (21-38 ; fuseau par défaut) → kotlinx-datetime (`Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())` + `LocalDate` / `LocalTime` / `LocalDateTime.Format {}`). | commonMain `util/` | DateHeureDefautTest (sans Robolectric) se porte tel quel. |
| `util/GeoJsonCoords.kt` | 107 | Lit et écrit les coordonnées internes `[[lon,lat],…]` et les anneaux de trous, et calcule le centroïde. | COMMUN-ADAPTÉ | `org.json.JSONArray` (37-97) → `JsonArray` ; `org.osmdroid.util.GeoPoint` (22, 34-106) → type commun (LatLon / `PointTrace`, ou `Position` de spatial-k, utilisé par maplibre-compose ; à vérifier selon la version). | commonMain `util/geo/` | Le texte produit change (org.json écrit `1` pour 1.0, kotlinx `1.0`) sans conséquence : ces chaînes sont toujours re-parsées, et je n'ai trouvé aucune comparaison textuelle. Utilisé par 6 fichiers UI. iOS : Util/Geo/GeoJsonCoords.swift. |
| `util/TopologiePolygone.kt` | 68 | Égalité de sommets (à 1e-9° près), milieu d'arête et test point-dans-anneau pour la topologie partagée d'OccHab. | COMMUN-ADAPTÉ (trivial) | `org.osmdroid.util.GeoPoint` (21, 37-66) → type commun. | commonMain `util/geo/` | KDoc orpheline (44-45). Utilisé par ui/EditeurGeometrie.kt. iOS : Util/Geo/TopologiePolygone.swift. |
| `gpx/GpxUtils.kt` | 206 | Exporte en GPX (waypoints + trace) et importe un GPX en `Sortie` (SAX durci, borne de 20 Mo, calcul de distance). | COMMUN-ADAPTÉ | `SAXParserFactory` + `org.xml.sax.DefaultHandler` / `Attributes` (24-30, 89-174) → xmlutil (lecteur en flux) ou mini-parseur ; `SimpleDateFormat` en UTC (36-38, 121-123) → `Instant.parse` / `Instant.fromEpochSeconds(ms / 1000).toString()` ; `android.location.Location.distanceBetween` (182) → fonction géodésique commune (portage de l'algorithme AOSP, WGS84) ; `java.io.InputStream` / `ByteArrayOutputStream` (75-87) → `Source` okio ; `System.currentTimeMillis` (199, 203). | commonMain `gpx/` (+ `util/geo/Distance`) | `<type>Observation ornithologique</type>` codé en dur (51). GpxUtilsTest (sans Robolectric) se porte. iOS : Network/GpxImport.swift (`XMLParser`) + Network/GpxExport.swift. |
| `TaxRefLocal.kt` | 269 | Calcule et mémorise les propositions d'espèces par groupe, mode d'affichage et liste, et résout un nom saisi dans ce périmètre. | COMMUN-ADAPTÉ | LRU `LinkedHashMap(8, 0.75f, true)` + `removeEldestEntry` (53-56 ; JVM seulement) → petite LRU maison ; `synchronized(memPropositions)` (62) → atomicfu ; `putIfAbsent` (45). | commonMain `taxref/` | Les règles par groupe (110-268) sont pures. iOS : Store/PropositionsTaxon.swift. |
| `GeoMysApplication.kt` | 82 | Point d'entrée Android : mode sombre forcé, `init(context)` de 9 stores, configuration d'osmdroid, pré-chauffe de TaxRef et purge des médias orphelins. | MIXTE | `android.app.Application` ; `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)` (46) → thème Compose sombre imposé ; `Configuration.getInstance().load(… "osmdroid" …)` + `userAgentValue` (59-60) et `MapTileCache` (61-62) → abandon ; `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (68) → scope applicatif commun. | androidMain `GeoMysApplication` (allégée) + commonMain `Demarrage` | iOS : GeoMys_IOSApp.swift (ne lance que l'entretien du cache de tuiles). |
| `location/LocationForegroundService.kt` | 114 | Service Android de premier plan (type location) qui maintient le GPS pendant une sortie et affiche la distance parcourue. | PLATEFORME | `Service` ; `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION)` (66-69) ; `NotificationCompat` (canal `GeoMys_location` créé par ui/MainActivity.kt:102-120) ; `PendingIntent` vers `ui.MainActivity` ; `R.drawable.ic_walk` ; `LiveData.observeForever` (70-71) → collecte d'un `StateFlow` ; `"%.0f".format` (104) ; `START_NOT_STICKY` (82). iOS : pas de service, mais `allowsBackgroundLocationUpdates` + `showsBackgroundLocationIndicator` pendant l'enregistrement, avec `UIBackgroundModes=location`. | androidMain `location/`, derrière une interface commune `SuiviArrierePlan` | iOS existant : Location/LocationTracker.swift (`demarrerParcours` / `arreterParcours`) + GeoMys-IOS/Info.plist (`UIBackgroundModes` = location). |
| `location/LocationTracker.kt` | 178 | Fournit la position courante (LocationManager GPS ou réseau) et enregistre le parcours (filtre au-delà de 50 m de précision, distance cumulée), exposés en LiveData. | MIXTE (≈85 l. en commun, ≈65 l. plateforme) | `LocationManager.requestLocationUpdates(provider, 2000 ms, 5 m)`, `getLastKnownLocation`, `requestSingleUpdate` (déprécié), `isProviderEnabled` (76-129) → PLATEFORME (iOS `CLLocationManager` : `distanceFilter = 5`, `desiredAccuracy = kCLLocationAccuracyBest`, `requestLocation()`, `location`) ; `android.location.Location` exposé (34-35, 101) → type commun `Position(lat, lon, precisionM)` ; `Location.distanceTo` (64) → fonction commune ; `MutableLiveData.postValue` → `MutableStateFlow` ; `synchronized` (59, 132, 156, 165) ; `@Volatile` (46-48). | commonMain `location/EnregistreurParcours` + interface `SourcePosition` ; implémentations androidMain / iosMain | Garder LocationManager (et non le fournisseur Fused) sur Android : c'est un choix délibéré, pour accepter les positions « mock » des récepteurs GNSS RTK externes (voir l'en-tête de Location/LocationTracker.swift). L'UI ne lit que latitude et longitude. iOS : Location/LocationTracker.swift (199 l., gère aussi le cap). |

## 2. Logique à extraire

Aucun fichier MIXTE ou UI de ce périmètre ne dépasse 300 lignes : les trois MIXTE font de 82 à 178 lignes. Voici les découpages recommandés pour les deux gros fichiers COMMUN-ADAPTÉ et pour les trois MIXTE.

**`store/TaxRefCache.kt` (1 028 l.)**
- **Index en mémoire.** Tous sont `@Volatile` ; `version++` invalide les mémos externes.
  - `mem` : `Map<clé normalisée, TaxRefEntry>`, HashMap de capacité initiale 2^18. Une entrée par nom scientifique ou vernaculaire, et un seul taxon par clé.
  - `memGroupes`, `memGroupes1`, `memRegnes` : cd_nom (en String) → group2_inpn, group1_inpn, règne.
  - `memIndexTaxon` : nom d'enum `Taxon` → cd_nom.
  - `memListes` : cd_nom (en String) → id_liste.
  - Dérivés :
    - `memEntreesParCdNom` : Int → entrée représentative, plus les taxons sans clé tirés de `sci_v1.json`.
    - `memVernsParCdNom` : Int → noms français ordonnés.
    - `memSciParCdNom` : Int → nom scientifique.
    - `memCdNomsDansListe`, pour la dernière liste demandée.
    - `memTousLesNoms` : toutes les clés.
    - `memNomsParListe`.
    - `memIndexResolutionListe`, pour la dernière liste ; vide au-delà de 60 000 taxons.
    - `memIndexMots` : mot → cd_nom, pour la recherche vocale.
  - S'y ajoute, dans `TaxRefLocal`, une LRU de 4 `Propositions` (liste, paires normalisées, index).
- **Taille.** Je ne l'ai pas mesurée. Les commentaires du code citent « 15k+ entrées » pour un cache courant, « 200k+ » et « ~400k entrées » pour les gros référentiels, et une liste « toutes espèces » de 100 454 taxons. Le portage iOS signale un pic de plus de 200 Mo lors d'un décodage en un seul bloc. Chaque index annexe instancie ses propres chaînes, sans déduplication. Sur un référentiel complet, l'ensemble se compte probablement en dizaines de Mo : c'est un ordre de grandeur, à mesurer.
- **Logique pure** (≈470 l.), transférable sans autre changement que les verrous et les annotations :
  - `nettoyerSuffixeArticle` (201-205), `get(nom, cdNomsAutorises)` (224-263), `get` (265-269), `variantesCle` (277-289) ;
  - `getDansListe`, `listeAbsenteDuCache`, `nomsProposablesListe`, `propositionsListe`, `indexResolutionListe` (308-415) ;
  - recherche vocale : `indexMots`, `segmentations`, `chercherParMots`, `chercherApproche`, `distanceBornee` (417-537) ;
  - `entreesParCdNom` (556-576), `getVernaculaireParCdNom` et `nomAffichageParCdNom` (661-668) ;
  - `cdNomsDansListe`, `comptesParListe`, `nomsSuggestion` (748-785) ;
  - `normaliser` et `deplier` (911-930), types `TaxRefEntry` et `SuggestionTaxon` (35-54).
- **Persistance à réécrire** (≈420 l.) :
  - constantes et init (78-164), `lireFichier` / `ecrireFichier` (166-199), `ajouter` (539-543) ;
  - lecture des index `vernsParCdNom` et `sciNomsParCdNom` (582-629), `ajouterSciNoms` et `ajouterVerns` (633-659), `comptesGroupes` (670-678) ;
  - groupes, règnes, listes et index par taxon (685-743, 787-843), `vider` (845-871), propriétés en prefs (873-904) ;
  - lecture et écriture en flux (932-1028), désérialiseur legacy (56-76).
- **Recommandation.** Séparer un `TaxRefIndex` pur, construit à partir de maps et testable en commonTest sans fichiers, d'un `TaxRefStockage` (okio + Settings).

**`store/GeoNatureConfig.kt` (449 l.)**
- **Règles pures** (≈110 l.) : `champFormVisible` (30-33), `occhabChampVisible` (176-180), `occhabDefautNomenclature` (190-194), `urlTaxhub` (251-253), `heuresVisibles`, `dateFinVisible` et `additionalFieldsOcctaxJsonActif` (290-300), puis de `connexionConfiguree` à `saisieOcctaxValide` (311-398). Seul `org.json` est à remplacer.
- **Accesseurs** (≈260 l., 50-310) : 34 propriétés `prefs.getX` / `putX`, à passer mécaniquement sur `Settings` en gardant les noms et les défauts.
- **Spécifique Android** (≈35 l.) : `init` (39-48 : migration et purge de `gn_mdp`), puis `motDePasseMemoire`, `migrationTentee` et `migrerAncienMotDePasse` (412-448).

**`location/LocationTracker.kt` (178 l., MIXTE)**
- Vers un `EnregistreurParcours` commun (≈85 l.) : l'état (34-49, en StateFlow), le traitement d'une position (55-69 : filtre de précision, ajout du point, distance), et `demarrerParcours`, `arreterParcours`, `reprendreParcours`, `reinitialiser`, `restaurerParcours`, `definirDistance` (131-177).
- Vers une `SourcePosition` de plateforme (≈65 l.) : `demarrer`, `positionConnue`, `rafraichirPosition`, `arreter` (76-129).

**`store/MbtilesStore.kt` (144 l., MIXTE)**
- En commun : `Info`, `dossier`, `liste`, `supprimer`, `nomAffichage`, `info`, `estMbtilesValide`, `ouvrir`, `nettoyerNom`, ainsi que la séquence tmp → validation → rename d'`importer` (62-81).
- En plateforme (≈15 l.) : obtenir un flux et un nom depuis l'Uri ou l'URL (64-66, 134-137).

**`GeoMysApplication.kt` (82 l., MIXTE)**
- En commun, dans `Demarrage` (≈25 l.) : la liste ordonnée des initialisations (47-55, qui deviennent de l'injection), la pré-chauffe de TaxRef et la purge des médias (68-80).
- Reste Android : la classe `Application` et le mode nuit (46).
- Abandonné : la configuration d'osmdroid (59-62).

## 3. Points durs

1. **Verrous en code commun.**
   - `synchronized(…)` n'existe pas en commonMain pour les cibles natives. Il est utilisé dans JsonCollectionStore.kt:65-117, TaxRefCache.kt:539/933/982-984, TaxRefLocal.kt:62, OccHabStore.kt:47, OutboxMonitoring.kt:141, GeoNatureConfig.kt:427 et LocationTracker.kt:59-169.
   - Plus piégeux : `@Synchronized` (StationsServeurCache.kt:108-170, MdpChiffre.kt:67-114) compile en commonMain mais ne verrouille rien sur iOS. `@Volatile` doit venir de `kotlin.concurrent`.
   - Décision structurante, à prendre tôt :
     - soit des verrous atomicfu (`SynchronizedObject`, `ReentrantLock`), qui gardent synchrones les API aujourd'hui appelées depuis le thread UI ;
     - soit des stores `suspend` + `Mutex`, ce qui oblige à modifier tous les appelants.
2. **Durabilité des saisies.**
   - OutboxMonitoring écrit en tmp + `fd.sync()` + rename (OutboxMonitoring.kt:155-180).
   - SortieStore et OccHabStore reposent sur `commit()`, dont le booléen remonte jusqu'à l'UI (SortieStore.kt:46, OccHabStore.kt:64). Le contrat est explicite : « false = l'appelant DOIT le signaler » (SortieStore.kt:102-105).
   - multiplatform-settings n'expose pas ce résultat. Il faut donc passer ces deux blobs en fichiers atomiques (le même mécanisme que l'outbox), avec une migration unique depuis `sorties_store.xml` et `occhab_store.xml`.
   - Sur iOS, vérifier que `flush()` d'okio fait un vrai `fsync` ; sinon, appeler `fsync()` POSIX par cinterop.
3. **Passer de Gson à kotlinx.serialization sur des données existantes.**
   - Les normaliseurs exhaustifs (SortieStore.kt:208-292, OccHabStore.kt:260-347, OutboxMonitoring.kt:195-222, StationsServeurCache.kt:98-104) compensent le fait que Gson instancie les classes sans passer par le constructeur.
   - kotlinx applique les valeurs par défaut, ce qui simplifie. En revanche, il lève une exception sur un champ requis manquant, un type inattendu ou une valeur d'enum inconnue. Une liste décodée d'un seul bloc serait donc entièrement perdue à cause d'une seule entrée abîmée.
   - Il faut décoder élément par élément (`JsonArray`, puis `decodeFromJsonElement` sous try) pour garder le comportement « écarter + quarantaine », avec `ignoreUnknownKeys`, `explicitNulls = false` et `coerceInputValues = true`.
   - Rejouer GsonRoundTripTest, OccHabStoreRoundTripTest et OutboxRoundTripTest sur des fichiers produits par l'appli actuelle.
4. **R8 et `kotlin.Pair`.**
   - Les classes persistées gardent leurs noms de champs (proguard-rules.pro:43-69 ; vérifié : aucun champ renommé dans mappings/mapping-1.4.7.txt).
   - Exception : `kotlin.Pair`, dont les champs sont obfusqués : `a` / `b` jusqu'en 1.4.2, puis `f` / `g` de 1.4.3 à 1.4.7. `AdditionalFieldDef.nomenclatureOptions` (`List<Pair>`) est donc écrit `{"f":…,"g":…}` dans `gn_cache_add_fields_occtax` en release, et devient illisible à chaque changement de mapping, jusqu'à la synchro suivante. Ce défaut existe déjà dans l'appli actuelle.
   - Pour GeoMys : accepter ces clés, ou ignorer ce cache, qui est réécrit à chaque synchro (network/SyncRunner.kt:133).
5. **TaxRefCache : volume et performances.**
   - 8 fichiers sont chargés en entier dans des HashMap, avec 15 mémos. La lecture et l'écriture se font en flux avec Gson (TaxRefCache.kt:935-975, 997-1028).
   - `kotlinx-serialization-json-okio` évite la String géante, mais construit la map en entier, comme aujourd'hui.
   - Sous Kotlin/Native, construire des maps de centaines de milliers de String coûte plus cher, et le GC se comporte autrement : mesurer tôt sur un iPhone.
   - Autre option : SQLite (androidx.sqlite ou SQLDelight, avec un index sur la clé normalisée), au prix d'une migration.
   - `normaliser()` (911-930) doit rester identique au caractère près si l'on relit `cache_v3.json`, puisque ses clés y sont persistées.
   - `Regex("\\s+")` est recompilée à chaque appel de `variantesCle` (282), c'est-à-dire à chaque frappe.
6. **Normalisation Unicode.** `java.text.Normalizer` NFD (HabitatCache.kt:103-108, 159-163) n'a pas d'équivalent en Kotlin commun : passer par `expect` / `actual` ou par une bibliothèque. Le NUL littéral (HabitatCache.kt:100) doit devenir `'\u0000'`.
7. **Comportements propres à org.json.**
   - AnaEval.kt:406-419 écrit dans un texte envoyé au serveur un JSON « canonique », via `JSONObject.quote` et `numberToString`.
   - L'implémentation d'Android échappe `/` en `\/`, écrit les caractères de contrôle en `\u00XX` minuscule et écrit les Double entiers sans `.0`. Il faut le reproduire pour obtenir les mêmes octets.
   - Les tests unitaires tournent sur JSON-java, qui échappe `/` autrement : il faut trancher quelle implémentation fait référence.
   - Le parsing d'org.json est tolérant (AnaEval.kt:244-254).
   - `DOT_MATCHES_ALL` (AnaEval.kt:54) et `sortedMapOf` (289, 434) n'existent pas en commonMain.
8. **Petites API réservées à la JVM** (elles casseront la compilation iOS) :
   - `putIfAbsent` (MonitoringCache.kt:74, NidificationOiseaux.kt:69, TaxRefLocal.kt:45) ;
   - la LRU `LinkedHashMap(…, true)` + `removeEldestEntry` (TaxRefLocal.kt:53-56) ;
   - `ConcurrentHashMap` (MonitoringCache.kt:48) ;
   - `Charsets.UTF_8` (OutboxMonitoring.kt:163, MdpChiffre.kt:83, 100) ;
   - `javaClass.simpleName` (TaxRefCache.kt:194, 1024) ;
   - `String.format` (LocationForegroundService.kt:104, qui reste côté Android) ;
   - `java.util.UUID` et `System.currentTimeMillis`, présents partout.
9. **Secret.**
   - Keystore Android côté Android, Keychain côté iOS (MdpChiffre.kt:134-154).
   - Conserver le repli « en mémoire seulement, jamais en clair » (GeoNatureConfig.kt:58-71, 412-415).
   - Relire le mot de passe existant suppose le même applicationId ET la même clé de signature.
   - Le nettoyage de `gn_secure` (GeoNatureConfig.kt:425-448) ne concerne qu'Android.
10. **Carte.**
    - En passant d'osmdroid à MapLibre, MapTileCache.kt devient sans objet et GeoMysApplication.kt:59-62 disparaît.
    - Les zones téléchargées pour le hors-ligne vivent dans `cache/MapTiles/cache.db`, la base SQLite d'osmdroid (table `tiles` d'après MapTileCache.kt:64-67 ; les colonnes ne sont pas vérifiées). MapLibre ne sait pas les lire. Deux options :
      - les convertir en androidMain (en MBTiles, par source) ;
      - demander à l'utilisateur de re-télécharger ses zones avant de partir sur le terrain.
    - Avantage de MapLibre : ses packs hors ligne ne sont pas évincés, alors que la LRU d'osmdroid (1 Go → 800 Mo, MapTileCache.kt:36-50) pouvait évincer des zones téléchargées.
    - MBTiles :
      - l'import par Uri (MbtilesStore.kt:53-82, 134-137) est à écrire pour chaque plateforme ;
      - le support de `mbtiles://` est à valider avec maplibre-compose, à défaut d'une source de tuiles maison ;
      - les tuiles vectorielles (`format = pbf`, MbtilesStore.kt:103) sont à gérer.
11. **Localisation.**
    - Android :
      - garder `LocationManager` (pas le fournisseur Fused), pour les récepteurs RTK « mock » ;
      - conserver le service de premier plan, la notification et les permissions `FOREGROUND_SERVICE_LOCATION` / `POST_NOTIFICATIONS` (LocationForegroundService.kt:64-73, 98-114).
    - iOS :
      - n'activer `allowsBackgroundLocationUpdates` que pendant l'enregistrement : l'appli plante si `UIBackgroundModes` est absent ;
      - gérer les autorisations.
    - `postValue` est asynchrone : LocationTracker.kt:58 lit `_estEnCours.value`, qui peut ne pas encore refléter `demarrerParcours()`. Un StateFlow change ce comportement (en mieux) : à tester.
    - Distances : `Location.distanceTo` / `distanceBetween` (LocationTracker.kt:64, GpxUtils.kt:181-183) et `CLLocation.distance(from:)` ne donnent pas exactement les mêmes valeurs. Une fonction commune unique garantit des distances identiques sur les deux plateformes.
12. **GPX.**
    - Remplacer le SAX durci (GpxUtils.kt:89-105) par un lecteur qui ne résout ni DTD ni entités externes.
    - `SimpleDateFormat` n'accepte que `yyyy-MM-dd'T'HH:mm:ss'Z'` (GpxUtils.kt:121-123, 154), alors qu'`Instant.parse` acceptera aussi les millisecondes et les décalages horaires : un élargissement à assumer.
    - À l'export, tronquer à la seconde (GpxUtils.kt:36-38, 45).
13. **Initialisation et injection.**
    - Neuf singletons `object` sont en `lateinit` + `init(context)` (GeoMysApplication.kt:47-55).
    - `GeoNatureConfig(context)` est instancié 40 fois, `SortieStore(context)` 7 fois et `OccHabStore(context)` 9 fois.
    - Il faut un conteneur d'injection commun et des racines de stockage fournies par la plateforme.
    - La ressource de nidification, lue aujourd'hui de façon synchrone (NidificationOiseaux.kt:57), passe à une API `suspend`.
14. **Chemins de médias persistés en absolu.**
    - Les saisies stockent `file:/data/user/0/<appId>/files/medias/<nom>` (ui/MediaImport.kt:62, via `File.toURI()`).
    - Sur Android, garder `files/medias/` ; ou résoudre par nom de fichier, comme MediasOrphelins.kt:57-60.
    - iOS stocke des chemins relatifs `medias/<nom>`.
15. **Tests.**
    - Sur 20 classes de test liées au périmètre que j'ai examinées, 14 dépendent de Robolectric, souvent pour `Context` ou `GeoPoint`. Elles se portent en commonTest avec `FakeFileSystem` (okio) et `MapSettings` (multiplatform-settings).
    - 6 sont déjà en JVM pur : AnaEvalTest, GpxUtilsTest, GsonRoundTripTest, OcctaxFieldsConfigTest, DateHeureDefautTest, CollisionCdNomTest.

## 4. Compatibilité des données

`<appId>` = `fr.ariegenature.geomys` (flavor github) ou `fr.ariegenature.public.geomys` (flavor play). GeoMys doit reprendre les deux flavors et les mêmes clés de signature. Chemins : `files/` = `/data/data/<appId>/files`, `shared_prefs/` = `/data/data/<appId>/shared_prefs`, `cache/` = `/data/data/<appId>/cache`.

| Donnée | Emplacement | Nom / clés | Format et classes | Concurrence / corruption | Relecture par GeoMys |
|---|---|---|---|---|---|
| Config GeoNature | `shared_prefs/gn_config.xml` | 34 clés, détaillées sous ce tableau ; l'ancienne `gn_mdp` (en clair) est purgée | SharedPreferences typées : String (dont du JSON Gson, du JSON serveur brut et des CSV), Int, Boolean | `apply()`, sans verrou ; JSON lus sous try/catch avec repli permissif | OBLIGATOIRE (sinon toute la configuration est à refaire) |
| Mot de passe | `shared_prefs/gn_secure_v2.xml` + Android Keystore | clé `gn_mdp_chiffre` ; alias Keystore `geomys_mdp` | Base64 NO_WRAP de IV (12 o) ‖ chiffré ‖ tag GCM (16 o), AES-256-GCM | `@Synchronized` ; erreurs avalées → repli mémoire ; exclu des sauvegardes | OBLIGATOIRE pour éviter une re-saisie : code androidMain identique, même certificat. Legacy : `gn_secure.xml` + alias `_androidx_security_master_key_`, supprimés au démarrage |
| Saisies Occtax | `shared_prefs/sorties_store.xml` ; quarantaine `files/sorties_store.corrupt.json` | `sorties_sauvegardees` | Gson `List<Sortie>` (→ `PointTrace`, `Observation` → `Denombrement` ; `Taxon` par nom) | `commit()` synchrone ; verrou process `VERROU` ; lire-modifier-écrire atomique ; entrée illisible écartée et blob copié au premier incident | OBLIGATOIRE (données irremplaçables) |
| Saisies OccHab | `shared_prefs/occhab_store.xml` ; quarantaine `files/occhab_store.corrupt.json` | `saisies_sauvegardees` (l'ancienne `stations_sauvegardees` est purgée) | Gson `List<OccHabSaisie>` → `OccHabStation` → `OccHabHabitat` | idem SortieStore | OBLIGATOIRE |
| File Monitoring | `files/monitoring_outbox.json` (+ `.tmp`) ; quarantaine `files/monitoring_outbox.json.corrupt` | — | Gson `List<SaisieEnAttente>` (`Etat` par nom ; `valeursJson` = JSON org.json stocké en chaîne) | tmp + fsync + rename (repli delete + rename) ; verrou objet ; quarantaine = copie du fichier au premier incident | OBLIGATOIRE |
| Médias | `files/medias/` (`photo_<horodatage>_<i>.jpg`, etc. ; écrits par ui/MediaImport.kt et ui/PhotoCapture.kt, hors périmètre) | référencés par URI absolue dans `Observation.mediaUrisCounting0`, `Denombrement.mediaUris`, `SaisieEnAttente.mediaPathsLocal` / `mediaPathLocal` / `mediasEnvoyes` | binaire | orphelins de plus de 48 h purgés au démarrage | OBLIGATOIRE (mêmes chemins absolus) |
| Relevés orphelins | `shared_prefs/releves_orphelins.xml` | `orphelins_<url sans / final>` | CSV d'entiers | `apply()`, sans verrou | Recommandé (sinon des relevés vides restent à supprimer à la main) |
| TaxRef | `files/taxref/` + `shared_prefs/taxref_cache.xml` | `cache_v3.json` : objet `{clé normalisée: {cdNom, sciNom, nomFrOriginal?}}` ; `groupes_v1.json`, `groupes1_v1.json`, `regnes_v1.json`, `sci_v1.json` : `{"<cd_nom>": "<texte>"}` ; `verns_v1.json` : `{"<cd_nom>": [noms fr]}` ; `listes_v1.json` : `{"<cd_nom>": [id_liste]}` ; `index_taxon_v1.json` : `{"OISEAU": [cd_nom], …}` ; prefs `gn_taxref_version_cache` (String), `gn_taxref_comptes_v1` (JSON `Map<String,Int>`), `gn_taxref_liste_sync` (String), `gn_taxref_listes_sync_v1` et `gn_taxref_listes_incompletes_v1` (CSV) | JSON UTF-8 (champ null omis) ; la lecture accepte aussi `nomFr` et, en repli, `vernNoms` / `vernNom` | tmp + rename ; verrou pour le cache principal seulement ; échec de lecture → cache vide, sans quarantaine. Anciennes clés de prefs purgées : `gn_taxref_cache_v1/_v2`, `gn_taxref_groupes_v1`, `gn_taxref_groupes1_v1`, `gn_taxref_regnes_v1`, `gn_taxref_index_taxon_v1` | Recommandé : sinon un rechargement réseau est obligatoire avant toute saisie (blocage hors ligne). Si relu, `normaliser()` doit rester identique |
| Nomenclatures | `shared_prefs/nomenclature_cache.xml` | `nom_cache_v1`, `nom_defauts_v1` | Gson `Map<String, List<NomValeur>>` ; `Map<String, String>` | `apply()` ; échec de lecture → vide (relu à chaque appel) | Recommandé |
| HABREF | `files/habitats_v1.json`, `files/habitats_occhab_v1.json` | — | Gson `List<HabitatSuggestion>` | tmp + rename ; best-effort | Recommandé (sans lui, la saisie OccHab hors ligne est impossible) |
| Stations serveur OccHab | `files/stations_serveur_occhab_v1.json` | — | Gson `Contenu{dateChargement, stations: List<OccHabStation>, versionFormat}` | tmp + rename ; écritures en `@Synchronized` ; `versionFormat` ≠ 2 → fichier supprimé | Recommandé (re-téléchargeable) |
| Monitoring | `files/monitoring/` | `modules.json`, `schema_<code>.json`, `enfants_<code>.json`, `objet_<code>_<type>_<id>.json`, `observateurs_<idListe>.json`, `datalist_<apiUrl>.json` (tout caractère hors `[A-Za-z0-9._-]` remplacé par `_`) | JSON serveur brut | tmp + rename fichier par fichier | Recommandé (sinon les formulaires de suivi sont indisponibles hors ligne) |
| Pictos | `files/monitoring/pictos/` | `<module_code avec / → _>.jpg` + marqueurs vides `.absent` (TTL de 24 h d'après la date de modification) | octets bruts | tmp + rename | Optionnel (re-téléchargés) |
| Fonds MBTiles | `files/mbtiles/*.mbtiles` | nom nettoyé `[A-Za-z0-9._-]` ; fond choisi dans `GeoMys_prefs` / `fond_carte` = `mbtiles:<fichier>` (ui/MapUtils.kt:50-59, hors périmètre) | SQLite MBTiles standard | tmp + validation + rename | OBLIGATOIRE à conserver (importés par l'utilisateur, l'appli ne peut pas les re-télécharger) |
| Cache de tuiles osmdroid | `cache/MapTiles/cache.db` (+ fichiers éventuels) ; `shared_prefs/osmdroid.xml` | `GeoMys_prefs` / `derniere_purge_tuiles` (Long) | SQLite propre à osmdroid | géré par osmdroid (LRU 1 Go → 800 Mo) | MapLibre ne sait pas le relire : à convertir (zones hors ligne) ou à abandonner, en prévenant l'utilisateur |

**Les 34 clés de `gn_config`** (type, défaut) :
- **String** (défaut `""`) : `gn_url`, `gn_login`, `gn_dataset`, `gn_dataset_nom`, `gn_taxa_liste`, `gn_nom_utilisateur`, `gn_version_serveur`, `gn_obs_defaut_nom`, `gn_obs_defaut_id`, `gn_taxhub_url`.
- **JSON Gson** :
  - `gn_cache_datasets` et `gn_cache_datasets_occhab` : `List<GeoNatureDataset>` ;
  - `gn_cache_listes` : `List<GeoNatureListe>` ;
  - `gn_cache_obs` : `List<GeoNatureObservateur>` ;
  - `gn_cache_add_fields_occtax` : `List<AdditionalFieldDef>` ;
  - `gn_occhab_details_prec` : `OccHabDetailsSession` ;
  - `gn_occhab_defauts_nomenc` : `Map<String,Int>`.
- **JSON serveur brut** :
  - `gn_cache_settings_occtax` : objet `nomenclature` du settings Occtax ;
  - `gn_form_fields` : `OCCTAX.form_fields` ;
  - `gn_occhab_formconfig` : `OCCHAB.formConfig`.
- **CSV d'entiers** : `gn_cache_ds_creables`, `gn_cache_ds_creables_occhab`.
- **Int** : `gn_id_role_utilisateur` (-1), `gn_version_donnees_chargees` (0), `gn_occhab_id_list_habitat` (-1), `gn_obs_duration_days` (365), `gn_taxref_page_size` (1000).
- **Boolean** : `gn_rechargement_requis_maj` (false), `gn_occhab_dispo` (false), `gn_occhab_creer` (false), `gn_occtax_creer` (true), `gn_date_hours` (false), `gn_date_end` (false), `gn_serveur_compatible` (true).

**Schémas JSON.** Les noms de champs Gson sont les noms des propriétés Kotlin, gardés par R8, sauf pour `kotlin.Pair` (cf. §3, point 4).
- `Sortie` : `{id, date, pointsParcours[{latitude, longitude}], observations[], distanceTotale, envoyeGeoNature, estImportee, derniereErreurEnvoi}`.
- `Observation` (49 champs) : `{id, espece, taxon, cdNom, latitude, longitude, geometryType, geometryCoordsJson, date, notes, nombre, sexe, stadeVie, objDenbr, typDenbr, techniqueObs, statutBio, etaBio, preuveExist, comportement, methDetermin, naturalite, determinateur, releveId, nombreMax, denombrementsAdditionnels[], statutObs, mediaUrisCounting0, additionalFieldsReleve, champsOccExtra, additionalFieldsOccurrence, additionalFieldsCounting0, idDatasetReleve, observateursReleveIds, observateursReleveNoms, observateurReleveId, observateurReleveNom, commentReleve, cdHabReleve, habitatReleveLabel, typGrpReleve, dateDebutReleve, dateFinReleve, champsReleveExtra, envoyeeServeur, releveSansEspece, uuidOccurrence, idReleveIncertain, releveTente}`. `taxon` vaut OISEAU, MAMMIFERE, REPTILE, BATRACIEN, POISSON, INSECTE, FONGE, MOLLUSQUE, INVERTEBRES ou PLANTE ; null signifie OISEAU.
- `Denombrement` : `{id, nombreMin, nombreMax, sexe, stadeVie, objDenbr, typDenbr, mediaUris, additionalFields}`.
- `OccHabSaisie` : `{id, date, stations[], envoyeGeoNature, derniereErreurEnvoi}`.
- `OccHabStation` (37 champs) : `{id, uuidStation, idStationServeur, date, geometryType, latitude, longitude, geometryCoordsJson, geometryTrousJson, geometryPartielle, idDataset, observateursIds, observateursNoms, observateursTxt, stationName, comment, anaEvalJson, dateMin, dateMax, altitudeMin, altitudeMax, profondeurMin, profondeurMax, surface, precision, idNomExposition, idNomCalculSurface, idNomObjetGeographique, idNomTypeSol, idNomTypeMosaique, habitats[], envoyeGeoNature, origineServeur, derniereErreurEnvoi, envoiIncertain, empreinteOrigine, origineEnvoyee}`.
- `OccHabHabitat` (15 champs) : `{id, idHabitatServeur, uuidHabitat, cdHab, habitatLabel, nomCite, determiner, recouvrement, precisionTechnique, anaEvalJson, idNomTypeDetermination, idNomTechniqueCollecte, idNomAbondance, idNomSensibilite, idNomInteretCommunautaire}`.
- `SaisieEnAttente` (23 champs) : `{uuid, moduleCode, objectType, parentObjectType, parentIdServeur, parentUuidLocal, parentIdField, nomsChampsSchema, champsTexteLibre, valeursJson, dateLocale, etat, messageErreur, idServeur, objetCree, dejaTentee, uuidPayload, uuidFieldName, mediaPathLocal, mediaPathsLocal, mediaSchemaDotTable, mediasEnvoyes, champsManquants}`. `etat` vaut PENDING, SENDING, SENT ou ERROR.
- `NomValeur` : `{id, label, taxref[{regne, group2Inpn}], cd}`.
- `HabitatSuggestion` : `{cdHab, libelle, cdTypo}`.
- `GeoNatureDataset` : `{id, nom, idTaxaList, actif, moduleCodes}`.
- `GeoNatureListe` : `{id, nom}`.
- `GeoNatureObservateur` : `{idRole, nomComplet}`.
- `AdditionalFieldDef` : `{idField, fieldName, fieldLabel, widget, fieldValues, fieldValueCodes, required, description, defaultValue, objectsCode, datasetsIds, idList, widgetServeur, codeNomenclatureType, nomenclatureOptions[Pair]}`. `widget` vaut TEXT, TEXTAREA, NUMBER, SELECT, RADIO, CHECKBOX, NOMENCLATURE ou INCONNU.
- `OccHabDetailsSession` : `{idDataset, nomDataset, observateursIds, observateursNoms, observateursTxt, dateMin, dateMax, idNomCalculSurface, idNomObjetGeographique, comment, masquerStationsServeur}`.

**Conventions de Gson à reproduire en lecture.** Gson omet les champs null, échappe le HTML (`<`, `=`…) sans gêner la relecture, sérialise les enums par leur nom et `Pair` par ses champs. La configuration kotlinx conseillée : `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`, avec un décodage élément par élément pour les listes de saisies.

**Sauvegarde Android.** backup_rules.xml et data_extraction_rules.xml excluent `gn_secure.xml`, `gn_secure_v2.xml`, `files/taxref`, `files/monitoring`, `files/mbtiles` et `files/medias`. Les autres données sont sauvegardées, y compris les stores de saisies. GeoMys doit reprendre ces règles, en les adaptant si les blobs passent des prefs à des fichiers.

**Si GeoMys doit aussi remplacer GeoMys-IOS** (constat rapide, à approfondir côté iOS) :
- Les emplacements diffèrent :
  - `Documents/` : `habitats_*.json`, `monitoring/`, `monitoring/pictos/`, `medias/` ;
  - `Application Support/` : `taxref/`, `mbtiles/`, `monitoring_outbox.json`, `occhab_store.json`, `stations_serveur_occhab_v1.json`, `sorties_sauvegardees.json` ;
  - `Caches/MapTiles/<source>/<z>/<x>/<y>.tile`.
- Les clés diffèrent aussi :
  - UserDefaults de la suite `gn_config` : 18 des 34 noms changent (ex. `gn_observateur_id` au lieu de `gn_obs_defaut_id`, `gn_occhab_disponible` au lieu de `gn_occhab_dispo`) ;
  - UserDefaults.standard pour TaxRef, les nomenclatures (valeurs de type `Data`) et les relevés orphelins ;
  - Keychain : service `fr.ariegenature.geomys`, compte `gn_password`.
- Il faudra donc une table de clés et de chemins par plateforme. Les formats Codable d'iOS n'ont pas été vérifiés.

## 5. Bilan chiffré

| Catégorie | Fichiers | Lignes (`wc -l`) | % | Lignes de code (colonne `code` du relevé automatique) |
|---|---|---|---|---|
| COMMUN | 1 | 39 | 0,6 % | 9 |
| COMMUN-ADAPTÉ | 24 | 5 670 | 87,1 % | 3 220 |
| MIXTE | 3 | 404 | 6,2 % | 248 |
| PLATEFORME | 2 | 269 | 4,1 % | 172 |
| UI | 0 | 0 | 0 % | 0 |
| ABANDON | 1 | 127 | 2,0 % | 73 |
| **Total** | **31** | **6 509** | 100 % | **3 722** |

**Par sous-dossier**
- `store/` (19 fichiers, 4 463 l.) : 4 037 COMMUN-ADAPTÉ (16 fichiers), 144 MIXTE (MbtilesStore), 155 PLATEFORME (MdpChiffre), 127 ABANDON (MapTileCache).
- `model/` (471 l.) : COMMUN-ADAPTÉ.
- `util/` (726 l.) : 39 COMMUN, 687 COMMUN-ADAPTÉ.
- `gpx/` (206 l.) : COMMUN-ADAPTÉ.
- Racine (351 l.) : 269 COMMUN-ADAPTÉ (TaxRefLocal), 82 MIXTE (GeoMysApplication).
- `location/` (292 l.) : 114 PLATEFORME, 178 MIXTE.

**Projection (estimations).**
- **commonMain** : environ 5 940 lignes, soit ≈91 % du périmètre (COMMUN + COMMUN-ADAPTÉ + ≈230 l. utiles des MIXTE).
  - Sur les 5 670 l. COMMUN-ADAPTÉ, environ 1 300 sont à réécrire : persistance, I/O, JSON, verrous. Dont ≈400 pour TaxRefCache, ≈140 pour GeoNatureConfig (surtout mécanique), ≈120 pour GpxUtils, ≈60 pour GeoJsonCoords et ≈70 pour PictoCache.
  - Les ≈4 400 autres passent quasi telles quelles. Les en-têtes de licence représentent ≈560 lignes du périmètre.
- **androidMain** : ≈390 l. réutilisées quasi telles quelles (MdpChiffre, LocationForegroundService, la partie plateforme des MIXTE, le nettoyage legacy de GeoNatureConfig).
- **iosMain** : ≈250 à 350 l. nouvelles à écrire (Keychain, `CLLocationManager` + arrière-plan, import MBTiles depuis une URL, NFD, éventuellement `fsync`).
- **Abandon** : 127 l. (MapTileCache) + 4 l. osmdroid dans GeoMysApplication, mais avec des données à traiter (zones hors ligne de `cache.db`).

Méthode : lecture seule de GeoMys-Android et de GeoMys-IOS ; seule commande git utilisée : `git log`.
