# Lot 3 : les envois (OccTax, Suivis, OccHab)

*18 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, inchangée depuis. Tout le code est dans `core`, sauf les pastilles de l'accueil (`ui`).*

Le lot 3 porte ce qui transmet les saisies à GeoNature (`INVENTAIRE.md` § 10) :
- **OccTax** : relevés, occurrences et dénombrements, médias, anti-doublon par identifiant client, relevés orphelins ;
- **Suivis** : la file d'envoi (visites, observations…) et ses médias ;
- **OccHab** : stations et habitats, en création comme en mise à jour ;
- les **trois stores de saisie** que ces envois tiennent à jour (« Mes saisies », « Mes visites », « Mes stations »).

**État.** Tout le moteur d'envoi est porté et testé. Les tests passent de 467 à 658 sur la JVM, et de 460 à 648 sur le simulateur iOS (§ 6). Aucun écran ne déclenche encore d'envoi : ils viennent avec les lots 6 à 8.

**Critère de sortie** (tous les scénarios anti-doublon passent) : **atteint le 18 octobre**, sur la JVM et sur le simulateur iOS (§ 7).

## 1. Ce qui est porté

| GeoMys-Android (`cdd5196`) | GeoMys Common | Remarque |
|---|---|---|
| `network/GeoNatureUpload.kt` | `network/GeoNatureUpload.kt` | Classe ; fonctions pures dans son compagnon |
| `network/EnvoiSortie.kt` | `network/EnvoiSortie.kt` | Classe `EnvoiSortie` ; verrous et filet mémoire deviennent ses membres |
| `network/MonitoringEnvoi.kt` | `network/MonitoringEnvoi.kt` | Classe |
| `network/OutboxEnvoi.kt` | `network/OutboxEnvoi.kt` | Classe ; verrou et filet mémoire deviennent ses membres |
| `network/OccHabUpload.kt` | `network/OccHabUpload.kt` | Classe |
| `network/EnvoiOccHab.kt` | `network/EnvoiOccHab.kt` | Classe `EnvoiOccHab` ; verrous comme pour OccTax |
| `store/SortieStore.kt` | `store/SortieStore.kt` | Fichier `sorties_store.json` (SharedPreferences sous Android) |
| `store/OccHabStore.kt` | `store/OccHabStore.kt` | Fichier `occhab_store.json` (SharedPreferences sous Android) |
| `store/OutboxMonitoring.kt` | `store/OutboxMonitoring.kt` | Fichier `monitoring_outbox.json`, comme sous Android ; le modèle était porté au lot 1 |
| `store/RelevesOrphelins.kt` | `store/RelevesOrphelins.kt` | Réglages `releves_orphelins`, mêmes clés |
| `store/MediasOrphelins.kt` | `store/MediasOrphelins.kt` | Dossier `medias/` |
| `network/InvalidationCaches.kt` (caches de l'envoi) | `network/InvalidationCaches.kt` | Vide aussi les caches de `GeoNatureUpload` |
| `GeoMysApplication.onCreate` (préchauffe de TaxRef, balayage des médias) | `ServicesGeoNature.lancerTravauxDeDemarrage` | § 4 |
| `AccueilFragment` (`nbSaisiesEnAttente`, `nbVisitesEnAttente`, `nbStationsEnAttente`) | `store/EnvoisEnAttente.kt` ; pastilles de l'accueil | § 5 |

Les stores et les services d'envoi sont uniques dans `ServicesGeoNature` : `sortieStore`, `occHabStore`, `outbox`, `relevesOrphelins`, `mediasOrphelins`, `upload`, `envoiSortie`, `monitoringEnvoi`, `outboxEnvoi`, `occHabUpload` et `envoiOccHab`. Les fichiers sont des `FichierAtomique` à écriture durable : fichier temporaire, synchronisation sur le disque puis renommage (lot 1).

## 2. Ce qui change dans le portage

- **Objets, puis classes.** Ce qui était global au processus le reste, au niveau des services :
  - verrous par saisie : `VerrousEnvoiSortie`, `VerrousEnvoiOccHab`, et le `mutexEnvoi` des Suivis ;
  - filets mémoire : `EnvoisNonPersistes` pour OccTax, `creesNonPersistes` pour les Suivis.

  Les tests y gagnent : chacun part de services neufs, sans remise à zéro de caches statiques.
- **HTTP.**
  - Les écritures passent par le client `ecriture` de `ClientsGeoNature` (lot 1) : POST, DELETE et médias, avec `Connection: close` et jamais de rejeu.
  - Le multipart des médias, écrit à la main sous Android, est celui de Ktor (`MultiPartFormDataContent`) : mêmes champs, même ordre, même séparateur `----GeoMysBoundary<ms>`.
  - Le fichier est lu en entier, comme le faisait déjà HttpURLConnection faute de longueur fixe.
  - Ktor n'écrit les noms de champs entre guillemets que s'il le faut (`name=title_fr`), ce que Werkzeug (Flask) lit aussi.
- **Fichiers et types.**
  - `Uri.parse(…).path` devient `cheminDepuisUri` : URI `file:` ou chemin absolu, décodé, sans requête ni fragment.
  - `URLConnection.guessContentTypeFromName` devient la table `typeMime` : images, sons, vidéos.
  - Les candidats du type de média sont repris tels quels, y compris les libellés accentués (« vidéo… »), qui ne correspondent jamais après normalisation ; seul « video » trouve la vidéo, comme sous Android.
- **Dates.** `SimpleDateFormat` devient `DateEnvoi` : date et heure dans le fuseau de l'appareil.
- **Stores et marqueur.** L'API des stores est suspendue (verrou de coroutine), et le `MarqueurEnvoiOcctax` aussi.
- **Annulation.** `envoyerVisite` laisse passer l'annulation de coroutine au lieu de la renvoyer dans un `Result`, qu'`OutboxEnvoi` relançait aussitôt : même effet.
- **Journal.** Le corps d'une visite envoyée, journalisé sous Android dans les versions de mise au point (`BuildConfig.DEBUG`), ne l'est pas : l'appli commune n'a pas de drapeau équivalent, et ce corps porte des données métier.

## 3. Relecture des stores : la règle exacte de Gson

GeoMys-Android normalisait après Gson ce que Gson laissait à `null`. Or ce que Gson laisse à `null` dépend de la classe :
- **Classe dont tous les paramètres ont une valeur par défaut** (`Sortie`, `Denombrement`, `OccHabSaisie`, `OccHabStation`, `OccHabHabitat`). Gson passe par son constructeur sans argument. Une clé **absente** prend sa valeur par défaut (un id neuf, une liste vide) ; seule une clé explicitement `null` fait écarter l'entrée.
- **Classe à paramètres obligatoires** (`Observation`, `PointTrace`, `SaisieEnAttente`). Gson l'instancie sans constructeur : une clé absente vaut `null`, comme une clé nulle.

Les filtres de relecture (`accepter`, `preparer`, `stationNettoyee`) suivent maintenant cette règle. C'est le cas de `CacheCorruptionTest` : `{"date":123}` donne une sortie réparée, avec un id neuf ; `{"id":"s-ok","observations":null}` est écartée.

Le lot 2 écartait aussi une station ou un habitat à l'`id` absent, ou une station aux `habitats` absents. Le correctif vaut donc aussi pour la relecture des stations serveur. Le test « propre au portage » qui fixait l'ancienne règle est réécrit (`StationsServeurCacheTest`), et `LOT-2.md` renvoie ici.

## 4. Démarrage

`ServicesGeoNature.lancerTravauxDeDemarrage()` reprend la fin de `GeoMysApplication.onCreate`. Elle tourne une fois par processus, en tâche de fond :
1. la préchauffe des vues mémoïsées de TaxRef (taxons par cd_nom, noms vernaculaires, groupes, règnes) ;
2. le balayage des médias orphelins : fichiers de `medias/` que plus aucun store ne référence, et vieux de plus de 48 heures.

La racine de l'interface (`GeoMysApp`) l'appelle. Sous Android, `Application.onCreate` le faisait aussi quand le processus démarrait pour un service sans écran ; ici, le premier affichage le fait.

Essai sur le Galaxy A12, avec les 100 474 taxons du lot 2 : démarrage sans erreur ni exception au journal, 133 Mo de mémoire (PSS) dont 41 Mo de tas Java, 30 secondes après le lancement.

## 5. Accueil : les pastilles des envois en attente

`envoisEnAttente()` compte ce qui reste à envoyer, comme l'accueil de GeoMys-Android :
- saisies OccTax non envoyées, hors sorties importées ;
- saisies des Suivis en attente ou en erreur ;
- saisies OccHab non envoyées.

Le comptage se fait hors du fil principal, à chaque retour sur l'accueil. Les comptes précédents restent affichés jusqu'au nouveau.

**Pastille du bouton menu.** Elle suit maintenant la règle de GeoMys-Android (`updatePastilleMenu`) : il suffit qu'il reste quelque chose à envoyer, même sous une entrée masquée. Le lot 5 avait repris celle de GeoMys-IOS, qui ne comptait les visites et les stations que si leur entrée était visible : une visite en attente pouvait alors passer inaperçue après un « Vider le cache ».

Les pastilles restent éteintes tant qu'aucun écran ne crée de saisie (lots 6 à 8).

## 6. Tests

**Portés de GeoMys-Android : 186 cas** (26 fichiers).

| Domaine | Fichiers | Cas |
|---|---|---|
| Construction du JSON OccTax | `BuildOccurrence`, `ConstruireGeometrie`, `JsonDepuisMap`, `LotCAudit` | 23 |
| Envoi OccTax | `GeoNatureUploadEnvoyer`, `GeoNatureUploadReseauDegrade`, `EnvoiReleveAntiDoublon`, `EnvoiSortieConcurrence`, `EnvoiSortiePreMarquage`, `PertesEtDoublonsEnvoi`, `FiabiliteEnvoi` | 47 |
| Store OccTax | `SortieStore`, `SortieStoreNormalisation`, `CacheCorruption` (le reste du lot 1) | 17 |
| Suivis | `MonitoringEnvoi`, `OutboxEnvoi`, `MediasMonitoringReconciliation`, `OutboxMonitoring`, `OutboxRoundTrip` | 44 |
| OccHab | `OccHabAntiDoublon`, `EnvoiOccHab`, `StationServeurCacheApresEnvoi`, `OccHabSaisieStore`, `OccHabStoreRoundTrip`, `EmpreinteExhaustivite`, `OccHabPolygoneTrou` (12 cas sur 16) | 55 |

**Écrits pour le portage : 5 cas.** GeoMys-Android ne teste ni le balayage des médias ni les comptes de l'accueil : `MediasOrphelinsTest` (3) et `EnvoisEnAttenteTest` (2).

Tous ces tests tournent sur la JVM et le simulateur iOS, sauf 3 cas propres à la JVM :
- `FiabiliteEnvoiJvmTest` : les exceptions `java.net` ;
- `EmpreinteExhaustiviteTest` : il parcourt les champs du modèle par réflexion Java.

**Adaptations.**
- **Serveur simulé.** `ServeurSimule` gagne de quoi remplacer MockWebServer :
  - un relevé des requêtes sous verrou, pour les envois concurrents ;
  - la lecture des corps multipart (`corps`, `champMultipart`) ;
  - un délai de réponse (`delaiMs`), le `setBodyDelay` de MockWebServer ;
  - un aiguillage qui peut suspendre, pour modifier une saisie à l'instant exact d'un POST.
- **Disque.** « Ce que contient le disque au moment du POST » se lit directement dans le fichier du store.
- **Comparaisons.** Celles qu'Android faisait par réflexion (aller-retour des stores) passent par la forme JSON et l'égalité des data classes.
- **Noms de tests.** Les noms entre accents graves, avec espaces et tirets longs, deviennent des identifiants simples : Kotlin/Native ne les accepte pas.

**Reportés.**
- `ResumeOutboxTest` (5 cas) : il teste une fonction de l'écran « Mes visites », qui vient au lot 7.
- Les 4 derniers cas d'`OccHabPolygoneTrouTest` : `GeoJsonCoords.formatAnneaux`, `airePolygoneM2` et le ViewModel OccHab, qui viennent avec la carte d'édition OccHab (lot 8).

## 7. Critère de sortie

Chaque scénario anti-doublon des tests de GeoMys-Android passe, sur la JVM et sur le simulateur iOS.

**OccTax :**
- chaque occurrence porte son identifiant client ; le groupe est pré-marqué SUR LE DISQUE avant le premier POST, et chaque succès lève l'incertitude ;
- une occurrence restée sans réponse est retrouvée par son uuid au ré-envoi ; vérification impossible : aucun re-POST ;
- un relevé dont la création a été tentée est retrouvé par son uuid déterministe (`unique_id_sinp_grp`) et réutilisé, y compris un relevé sans espèce ;
- rien n'est créé quand le serveur ignore le filtre ou rend une réponse de forme inconnue ;
- un 5xx ne vaut pas refus : l'incertitude reste ; un vrai refus (4xx) la lève ;
- le rollback ne supprime jamais un relevé réutilisé, et un orphelin repeuplé n'est pas supprimé ; un orphelin vide l'est au prochain envoi ;
- deux envois concurrents de la même saisie ne créent qu'un relevé, et le second est refusé en le disant ;
- un envoi parti d'un instantané périmé ne reposte rien ;
- les marquages survivent à la réécriture d'un écran d'édition ;
- une observation ajoutée pendant l'envoi laisse la saisie à envoyer ;
- après un envoi partiel, seules les observations restantes repartent.

**Suivis :**
- deux envois concurrents ne postent qu'une fois ;
- une saisie restée SENDING repart, sans re-POST si l'objet était déjà créé ;
- une saisie déjà tentée est retrouvée chez les enfants de son parent ; vérification impossible : aucun re-POST ;
- un re-POST sans vérification possible est signalé comme doublon possible ;
- après un échec des médias, « Réessayer » ne renvoie que les médias, et seulement ceux que le serveur n'a pas déjà.

**OccHab :**
- une station créée sans réponse se retrouve par son uuid, sur les deux formes de réponse du serveur ; vérification en échec : aucun POST ;
- un 5xx ou un identifiant illisible laissent la station incertaine ;
- la mise à jour est un re-POST idempotent, sans vérification ;
- seules les stations restantes repartent.

**Pas encore essayé contre un vrai serveur.** Aucun écran ne crée encore de saisie : les premiers envois réels se feront à la recette des lots 6 à 8.

## 8. Reste à faire

- **Écrans d'envoi** : « Mes saisies », « Mes visites », « Mes stations » (lots 6 à 8). Les envois longs y seront protégés par `ExecutionLongue` (service au premier plan sous Android).
- **Alerte d'échec d'écriture** (`alerterEchecEcritureStore`) : avec le premier écran qui écrit dans un store (lot 6). Les stores renvoient déjà le succès de chaque écriture.
- **Tests reportés** : `ResumeOutboxTest` (lot 7), les 4 derniers cas d'`OccHabPolygoneTrouTest` (lot 8).

## 9. Outils et versions

Aucune dépendance nouvelle : le multipart vient de ktor-client (`io.ktor.client.request.forms`), la lecture des corps dans les tests de ktor-client-mock.
