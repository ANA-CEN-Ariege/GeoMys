# Lot 1 : les fondations

*18 octobre 2026. Portage depuis GeoMys-Android `cdd5196`. Tout le code est dans `core`, commun à Android et iOS sauf mention.*

Le lot 1 pose ce dont tous les lots suivants ont besoin (`INVENTAIRE.md` § 10) :
- le JSON et les modèles ;
- le stockage des saisies ;
- les réglages et le coffre à mot de passe ;
- le client HTTP et l'authentification GeoNature.

**État.** Tout le contenu du lot est porté et testé. Il passe sur la JVM (89 tests) et sur le simulateur iOS (83 tests), dont 369 cas de référence d'org.json comptés comme un seul test.

**Critère de sortie** (connexion et authentification sur un serveur GeoNature) : **atteint le 18 octobre** sur le Galaxy A12 et l'iPhone 16, contre un vrai serveur GeoNature 2.17.5, après un essai contre un GeoNature simulé (§ 8).

## 1. Ce qui est porté

| Android (`cdd5196`) | GeoMys | Remarque |
|---|---|---|
| `org.json` (bibliothèque d'Android) | `core/…/json/` | Portage des 5 classes depuis les sources du SDK (Apache 2.0) : § 2 |
| `network/JsonResponse.kt` | `network/JsonResponse.kt` | Tel quel |
| `util/DateAffichage.kt`, `util/DateHeureDefaut.kt` | `util/` | kotlinx-datetime |
| `UUID.randomUUID`, `UUID.nameUUIDFromBytes` | `util/Uuids.kt` | UUID v3 identique à Java, vérifié sur 500 tirages |
| `android.util.Log` | `journal/Journal.kt` | Même signature, Kermit derrière |
| `model/Models.kt`, `model/OccHab.kt`, `SaisieEnAttente` | `model/` | `@Serializable`, noms de propriétés inchangés |
| `store/JsonCollectionStore.kt`, écriture durable d'`OutboxMonitoring` | `store/io/` | Mutex, décodage élément par élément : § 4 |
| `store/GeoNatureConfig.kt` | `store/GeoNatureConfig.kt` | multiplatform-settings, mêmes clés : § 5 |
| `store/MdpChiffre.kt` | `androidMain/…/store/MdpChiffre.kt` + `iosMain/…/store/CoffreKeychain.kt` | Keystore sur Android, trousseau sur iOS |
| `network/HttpClient.kt` | `network/http/ClientsGeoNature.kt` | Ktor, deux moteurs : § 6 |
| `network/GNErreur.kt` | `network/GNErreur.kt`, `network/ErreursReseau.kt` | Erreurs réseau classées par plateforme |
| `network/GeoNatureAuth.kt` | `network/GeoNatureAuth.kt` | Ktor, Mutex, contrat inchangé : § 7 |

Le statut de chaque fichier est dans `inventaire/index-fichiers.tsv`, colonne `statut_portage`.

## 2. org.json : un portage plutôt qu'une couche d'adaptation

L'inventaire prévoyait une petite couche `JsonCompat` au-dessus de kotlinx.serialization (§ 3). J'ai préféré porter en Kotlin commun l'org.json d'Android lui-même (`JSONObject`, `JSONArray`, `JSONTokener`, `JSONStringer`), à partir des sources du SDK. Trois raisons :
- **les conversions implicites** : le code réseau de GeoMys-Android fait plus de 500 appels `opt*` et `get*`, avec des tests `is Int`, `is Long` et `JSONObject.NULL`. Une couche sur `JsonElement` ne garde pas le type des nombres lus.
- **la sortie octet pour octet**, qu'exige le bloc ANA-EVAL : `/` échappé en `\/`, nombres entiers sans `.0`.
- **un portage mécanique** : il suffit de changer les imports (`fr.ariegenature.geomys.json` au lieu de `org.json`).

kotlinx.serialization reste le format des fichiers (§ 4). Le portage d'org.json sert au JSON des serveurs.

**La référence est Android lui-même.** `outils/reference-org-json/generer.sh` :
1. compile les sources d'org.json du SDK Android (android-36.1) ;
2. les fait tourner sur les 369 cas de `cas.tsv` ;
3. écrit les résultats dans `JSONAndroidTest.kt`.

Ce test vérifie que le portage rend exactement la même chose, sur la JVM et sur iOS. Il passe du premier coup sur les deux. Une erreur volontaire dans l'échappement de `/` le fait échouer sur 4 cas.

Exemples de comportements reproduits :
- `optString` d'un `null` explicite rend `"null"` ;
- `optInt("12.7")` rend 12 ;
- `012` est lu en octal (10), et `08` devient le double 8.0 ;
- les commentaires et les virgules en trop sont acceptés.

**Deux écarts imposés par Kotlin**, couverts par `JSONComplementsTest` :
- `toString()` rend `"null"`, et non `null`, pour un tableau qui contient NaN ;
- `wrap` ne connaît pas les classes `java.*` (BigDecimal, Date…).

Les fichiers portés gardent leur licence Apache 2.0 et leur mention de copyright ; le texte de la licence est dans `licences/Apache-2.0.txt`.

## 3. Modèles et format des fichiers

Les modèles persistés (`Sortie`, `Observation`, `Denombrement`, `PointTrace`, `Taxon`, `OccHabSaisie`, `OccHabStation`, `OccHabHabitat`, `SaisieEnAttente`) deviennent `@Serializable`, sans renommer aucune propriété. `GsonRoundTripTest` est porté en `AllerRetourJsonTest`.

`JSON_STOCKAGE` (`store/io/FormatJson.kt`) reproduit la tolérance de Gson (`FormatJsonTest`) :
- les `null` ne sont pas écrits, les valeurs par défaut le sont ;
- une clé inconnue est ignorée ;
- une valeur absente, `null` ou d'énumération inconnue prend la valeur par défaut.

**Un piège évité.** Gson laissait à `null` un état d'envoi inconnu, et `OutboxMonitoring` écartait alors la saisie. kotlinx l'aurait ramené à `PENDING` : une saisie déjà envoyée serait repartie, en doublon. Les stores ont donc un filtre `accepter`, appliqué au JSON brut avant décodage, qui reprend les règles de rejet d'Android (testé dans `StoreFichierJsonTest`).

## 4. Stockage des saisies

- **`FichierAtomique`**. L'écriture se fait dans un `.tmp`, puis un `fsync`, puis un renommage atomique. Une coupure laisse donc l'ancien contenu ou le nouveau, jamais un fichier tronqué.
  - Sur iOS, `F_FULLFSYNC` force l'écriture jusqu'au support physique ; un `fsync` seul peut s'arrêter au cache du disque.
  - Le premier contenu illisible est copié en `.corrupt`, et cette copie n'est jamais écrasée.
- **`JsonCollectionStore`**. C'est la base de SortieStore, d'OccHabStore et de la file d'envoi des Suivis, qui seront portés avec leurs lots.
  - Les entrées sont décodées une par une : une entrée abîmée est écartée et le blob mis en quarantaine, sans perdre les autres.
  - Le lire-modifier-écrire se fait sous `Mutex`, avec des API `suspend` (décision 7 de l'inventaire).
- **`StoreFichierJson`** : le backend fichier, retenu pour tous les stores de saisies. Les SharedPreferences ne rendent pas de résultat d'écriture fiable sur toutes les plateformes.

Les cas de `CacheCorruptionTest` (partie file d'envoi) et de `StoreConcurrenceTest` sont portés. Huit écrivains ajoutent 60 saisies chacun en parallèle, sans aucune perte, sur un vrai disque des deux plateformes.

## 5. Réglages et coffre

- **`GeoNatureConfig`** passe sur multiplatform-settings, avec les mêmes clés et les mêmes défauts : SharedPreferences `gn_config` sur Android, suite NSUserDefaults `gn_config` sur iOS. Ses 11 tests sont portés.
  - Retiré : le nettoyage des anciens coffres d'Android, sans objet puisque GeoMys ne reprenait pas les données (décision du 18 octobre, revue le 22 : `LOT-9.md` § 30).
  - Reporté : `additionalFieldsOcctaxJsonActif`, qui attend `OcctaxFieldsConfig`.
- **Le coffre du mot de passe.**
  - **Android** : `MdpChiffre`, quasi inchangé (clé AES-256-GCM de l'Android Keystore). Le fichier `gn_secure_v2` est exclu des sauvegardes, comme dans GeoMys-Android : règles reprises dans `androidApp/src/main/res/xml/`.
  - **iOS** : le trousseau, accessible « après le premier déverrouillage, sur cet appareil seulement ». Il n'est donc ni sauvegardé ni migré.
  - Si le coffre est indisponible, le mot de passe reste en mémoire seulement, jamais en clair sur le disque (testé).

## 6. Client HTTP

`ClientsGeoNature` remplace les `HttpURLConnection` :
- les codes HTTP ne lèvent pas d'exception ;
- les délais se posent requête par requête ;
- un 401 sur n'importe quel appel vide la session.

Il fournit trois clients :

| Client | Usage | Moteur |
|---|---|---|
| `standard` | lectures, suit les redirections | rouvre une connexion fermée par le serveur |
| `sansRedirection` | login | même moteur que les lectures |
| `ecriture` | POST et DELETE qui créent des données | `Connection: close`, ne rejoue jamais rien |

**Un défaut trouvé à l'essai.** Ma première version coupait, sur tout le moteur, le rejeu automatique d'OkHttp. C'était pour qu'un POST ne parte jamais deux fois. Mais OkHttp ne pouvait plus rouvrir une connexion gardée ouverte que le serveur avait fermée entre-temps, ce que font les vrais serveurs avec les connexions inactives. Résultat : sur l'A12, toute lecture qui suivait le login échouait (`unexpected end of stream`).
- Les lectures ont maintenant leur propre moteur, qui rejoue. Rejouer une lecture ou un login est sans danger.
- Les écritures gardent un moteur sans rejeu, et chacune ouvre sa connexion, comme les POST de GeoMys-Android.
- `ConnexionMoteurReelTest` verrouille ce comportement avec le vrai OkHttp, face à un serveur qui ferme chaque connexion.

**Sur iOS :**
- **cookies** : NSURLSession ne gère pas les cookies, l'appli le fait. NSURLSession fusionne plusieurs `Set-Cookie` en une seule chaîne, que `cookiesDeSession` redécoupe sans couper les dates.
- **contrat « erreur réseau = IOException »**, dont dépend tout le mode hors ligne : il est vérifié avec le vrai moteur sur les deux plateformes. Une connexion refusée lève bien une `IOException` classée « connexion impossible ».

**Sécurité.** Ktor complète une URL relative avec `localhost`. `urlAbsolue` refuse donc toute URL qui n'est pas absolue en http(s) : une URL de serveur vide n'envoie pas les identifiants sur l'appareil lui-même. L'erreur est une `IOException`, comme le `MalformedURLException` d'Android.

## 7. Authentification

`GeoNatureAuth` est porté presque ligne à ligne : test de connexion avec ses diagnostics, login, cache de session de 5 minutes, verrou et double vérification. Le contrat est inchangé : `null` signifie un refus du serveur, une `IOException` un problème réseau.

Quatre différences :
- c'est une classe et non plus un objet : l'appli en crée une seule, dans `ServicesGeoNature` ;
- le login est `suspend` et protégé par un `Mutex` ;
- l'annulation d'une coroutine n'est plus avalée par les `catch (e: Exception)` ;
- les quatre tests d'Android sont portés, avec quinze autres qui couvrent le reste du contrat (19 tests).

**`VerificationConnexion`** enchaîne le test de connexion et un appel authentifié, avec le jeton et les cookies, sur `/api/gn_commons/modules`. Un serveur refuse cet appel sans session valide : c'est donc la preuve que l'authentification fonctionne de bout en bout.

## 8. Critère de sortie

L'écran provisoire « Connexion GeoNature » (bouton de l'accueil) appelle `VerificationConnexion`. Le résultat s'affiche et part aussi dans le journal, sur une ligne `CONNEXION_GEONATURE`, sans le mot de passe.

**Contre un GeoNature simulé** (`outils/geonature-simule/serveur.py`, qui reproduit le 302 d'un mauvais mot de passe et les deux cookies), avec les vrais moteurs :

| | Galaxy A12 (OkHttp) | Simulateur iOS (NSURLSession) |
|---|---|---|
| Bon mot de passe | réussi, v2.16.0, `id_role` 42, appel authentifié HTTP 200 | idem |
| Cookies reçus par le serveur | `session=s1; csrf=c2` | idem, malgré la fusion des `Set-Cookie` |
| Mauvais mot de passe | « Identifiant ou mot de passe incorrect », redirection non suivie | idem |

**Sur un vrai serveur**, les identifiants étant saisis à la main sur les deux téléphones :

| | Galaxy A12 (Android 12) | iPhone 16 (iOS 27.2) |
|---|---|---|
| Test de connexion | « Connexion réussie » | « Connexion réussie » |
| Version relevée | GeoNature 2.17.5 | GeoNature 2.17.5 |
| Utilisateur | nom et `id_role` lus | idem |
| Appel authentifié | HTTP 200 | HTTP 200 |

**Un défaut d'écran trouvé en route.** Le premier essai sur l'iPhone a échoué, avec « Identifiant ou mot de passe incorrect » : le clavier d'iOS met une majuscule en tête du champ, et peut aussi corriger le mot. GeoNature compare l'identifiant à la lettre près. Les champs de connexion désactivent donc la majuscule et la correction automatiques. C'est à reprendre dans l'écran de configuration du lot 5. La ligne de journal montre désormais l'identifiant envoyé, jamais le mot de passe.

## 9. Reste à faire

- **Pour les lots suivants** : les stores réels (SortieStore, OccHabStore, OutboxMonitoring) avec leurs règles `accepter`, et un User-Agent propre à l'appli pour les appels GeoNature (aujourd'hui `ktor-client`).

**Fait après le commit du lot :** le User-Agent des tuiles OSM, prévu « au lot 1, quand Ktor sera en place » (`PROTOTYPE-CARTE.md` § 2).
- L'appli télécharge elle-même les tuiles OSM avec Ktor (`ui/…/carte/TuilesHttp.kt`), en gardant le cache de MapLibre.
- Elles partent avec `GeoMys/<version> (<système>)` sur Android comme sur iOS, téléchargement hors-ligne compris.

## 10. Outils ajoutés

- `outils/verifier.sh` : la vérification avant de pousser, qui tient lieu d'intégration continue.
- `outils/reference-org-json/` : régénère les attendus d'org.json à partir des sources d'Android.
- `outils/geonature-simule/serveur.py` : un GeoNature réduit à l'authentification. Il est joignable depuis un téléphone par `adb reverse tcp:8766 tcp:8766`, et depuis le simulateur iOS par `localhost`.

Versions ajoutées : Ktor 3.6.0, kotlinx-datetime 0.8.0, Kermit 2.2.0, multiplatform-settings 1.3.0, okio 3.18.2, kotlinx-coroutines-test 1.11.0.
