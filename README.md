# GeoMys

GeoMys est une application mobile Android et iOS alternative à Occtax-mobile (https://github.com/PnX-SI/gn_mobile_occtax/) et Monitoring-mobile (https://github.com/RNF-SI/gn_mobile_monitoring). Elle permet une saisie naturaliste sur un serveur [GeoNature](https://github.com/PnX-SI/GeoNature), en ligne comme hors-ligne. Android et iOS partagent une seule base de code : Kotlin Multiplatform et Compose Multiplatform.

Elle permet de saisir des observations naturalistes dans le module Occtax, en enchaînant les relevés multi-taxons ou mono-taxon, avec la trace GPS du parcours si on la lance, puis de les envoyer vers le module Occtax du serveur GeoNature auquel elle est connectée.

Elle permet de saisir des visites et des observations dans les sous-modules de Monitoring, puis de les envoyer vers le module Monitoring du serveur GeoNature auquel elle est connectée.

Elle permet de saisir des stations d'habitats dans le module OccHab (géométrie point ou polygone dessinée sur la carte, habitats du référentiel HABREF), ainsi que de reprendre et mettre à jour ses stations déjà présentes sur le serveur.

Elle permet de consulter les observations récentes présentes dans le serveur GeoNature auquel elle est connectée (profondeur d'historique pilotée par le serveur, 365 jours par défaut).

GeoMys succède à [GeoMys-Android](https://github.com/ANA-CEN-Ariege/GeoMys-android), dont elle reprend les écrans : depuis la version 2.0.0, elle la remplace sur Google Play ([`LOT-9.md`](LOT-9.md) § 30).

Développée par l'[ANA - CEN Ariège](https://ariegenature.fr/).

👉 **Documentation** :
- [`MODES-SAISIE.html`](MODES-SAISIE.html) : le mode pro et le mode simplifié, pour les utilisateurs ([`MODES-SAISIE.md`](MODES-SAISIE.md) pour le détail).
- [Notes de version](https://github.com/ANA-CEN-Ariege/GeoMys/releases) : les nouveautés de chaque version.

## Installation

| Canal | Identifiant | Ce qui est publié | Mise à jour |
|---|---|---|---|
| Google Play | `fr.ariegenature.public.geomys`, celui de GeoMys-Android sur Google Play | l'application | gérée par le Store |
| GitHub | `fr.ariegenature.public.geomys` | un APK signé, joint à chaque [version publiée](https://github.com/ANA-CEN-Ariege/GeoMys/releases) (étiquette `vX.Y.Z`), pour processeurs ARM 64 et 32 bits | à la main : l'application ne cherche pas de mise à jour |

Les versions de test (debug, mesure) ont leur propre identifiant, `fr.ariegenature.geomys.common`, et cohabitent avec la version publiée sur un même téléphone.

**Reprise de GeoMys-Android.** Installée par-dessus GeoMys-Android de Google Play, GeoMys reprend au premier lancement ses relevés OccTax et ses stations OccHab non envoyés, et relit sur place ses réglages, son mot de passe chiffré, ses visites des Suivis en attente, ses photos et ses cartes MBTiles (`RepriseGeoMysAndroid`). Une installation déjà configurée garde le mode pro. GeoMys-Android installé depuis GitHub (`fr.ariegenature.geomys`) est une autre application : ses saisies ne sont pas reprises, il faut les envoyer avant de changer.

**iOS.** Les versions publiées sur GitHub ne contiennent que l'APK Android. L'application iOS se compile avec Xcode (voir [Développement](#développement)).

Il faut un compte sur une instance GeoNature 2.15 ou plus récente, et un téléphone sous Android 7 (API 24) ou plus récent, ou un iPhone sous iOS 17 ou plus récent.

## Deux modes de saisie : pro et simplifié

Le mode se choisit en tête des Paramètres, dans la carte « Mode de saisie ». Une nouvelle installation démarre en mode simplifié ; une application déjà configurée, y compris par GeoMys-Android, garde le mode pro.

| | Mode pro | Mode simplifié |
|---|---|---|
| Public | Naturalistes habitués à GeoNature | Utilisateurs amateurs |
| Modules | Saisie multi-taxons, saisie mono-taxon, Monitoring, OccHab | Saisie multi-taxons seulement |
| Champs de l'observation | Tous ceux que le serveur montre | Nombre (et nombre max), comportement, sexe, stade de vie, type d'observation, état biologique, commentaire, photo, audio |
| Données chargées | Tout | Seulement ce qui sert à la saisie multi-taxons |

Le détail est dans [`MODES-SAISIE.md`](MODES-SAISIE.md). Explorer, Maps Manager, la carte, la trace GPS et l'envoi sont les mêmes dans les deux modes.

## Fonctionnalités principales

### Saisie libre (OccTax)
- **Trace GPS** : lancée par « Démarrer la trace GPS » sur la carte multi-taxons et tracée sur la carte. Pendant la trace, la position est suivie même écran éteint (service au premier plan sous Android, positions en arrière-plan sous iOS) ; sous Android, sans trace, le GPS ne tourne que l'application à l'écran.
- **Saisie multi-taxons** géolocalisée : un relevé porte plusieurs espèces. Géométries point, ligne ou polygone au doigt sur la carte. À la réédition d'une saisie, les lignes et polygones sont redessinés et leurs sommets se déplacent (appui long puis glisser) ; un toucher sur la forme ouvre la liste des espèces du relevé. Après validation d'un relevé, la carte repasse **directement en mode positionnement** pour enchaîner le relevé suivant.
- **Fiche de l'observation** : chaque espèce ajoutée ouvre sa fiche, un seul écran qui porte tous ses champs ; le crayon de sa ligne la rouvre. En mode pro : comportement et statut biologique en tête, puis les dénombrements (plusieurs par espèce, avec photos et sons), puis « Plus de détails ».
- **Saisie mono-taxon** (mode pro) : on règle le groupe, l'espèce, la caractérisation et le dénombrement, puis chaque toucher du « + » de la carte (« Enregistrer ici ») ajoute une observation de cette espèce, au point touché sur la carte, sinon à la position GPS.
- **Champs additionnels** : si le serveur en déclare (`OCCTAX_RELEVE`, `OCCTAX_OCCURENCE`, `OCCTAX_DENOMBREMENT`), l'application les gère au niveau du relevé, de l'occurrence ou du dénombrement, dans les deux saisies. En mode simplifié, seuls les obligatoires de l'occurrence et du dénombrement sont proposés.
- **Saisie d'espèce : l'ensemble des noms acceptés est exactement l'ensemble des noms proposés.** Le périmètre est le groupe taxonomique choisi, la liste de taxons (celle du jeu de données, sinon celle des Paramètres, sinon celle de la configuration d'OccTax) **et** le mode d'affichage ; un nom qui n'a pas été proposé est refusé (« Nom invalide — choisissez parmi les propositions »). Chaque proposition s'affiche sur deux lignes, le nom puis son nom scientifique (ou français). L'interrupteur « Nom scientifique » est mémorisé et partagé entre les deux saisies. Le texte tapé survit au changement de groupe comme au changement de mode.
- **Résolution purement locale** : aucun appel réseau pendant la frappe ; les propositions sortent du cache TaxRef chargé, donc hors-ligne et sans latence.
- **Dictée vocale** du nom d'espèce (bouton « Dictée vocale »), en français : elle propose des espèces, sans en choisir une d'office.
- **Donnée exceptionnelle** : comme le formulaire web, l'application interroge les profils de taxons du serveur (`gn_profiles`) et signale « Donnée exceptionnelle. Le taxon n'a jamais été observé dans cette zone et/ou à cette date ». L'envoi d'une saisie qui en contient demande confirmation (« Données exceptionnelles », « Envoyer quand même »).
- **Oiseaux nicheurs** : le référentiel embarqué `nidification_oiseaux.json` (285 espèces, périodes corrigées d'après les tableaux des codes atlas, [`LOT-9.md`](LOT-9.md) § 52) sert au bouton « Chant » du mode simplifié : pour un oiseau, il pose « Chant », ou l'indice « Mâle chanteur en période de reproduction » si l'espèce est en période de nidification et que le serveur a les codes atlas.
- **Fil d'Ariane** (maison › « Saisie multi-taxons » ou « Saisie mono-taxon ») sur tous les écrans de chaque saisie ; la maison ramène à l'accueil.
- **Mes saisies** : onglets « À envoyer », « Envoyées », « Importées ». Une saisie en attente se reprend, se supprime, s'envoie seule ou avec « Tout envoyer ». Le détail d'une saisie l'exporte en **GPX** (« Partager (GPX) ») ; « Importer GPX » en importe un.
- **Envoi GeoNature** : relevés OccTax avec occurrences, médias et géométries. Avant de renvoyer ce qui a déjà été tenté, l'application vérifie ce que le serveur a reçu, pour ne pas créer de doublon. Une correction faite après un envoi partiel part en mise à jour du relevé ou de l'occurrence.

### Stations d'habitats (OccHab)

Mode pro seulement. Le bouton **OccHab** de l'accueil n'apparaît que si le module est installé sur le serveur et que l'utilisateur a le droit d'y créer des stations.

- **Informations obligatoires** au démarrage d'une saisie (jeux de données, observateurs, dates, nature de l'objet géographique), reprises de la station précédente.
- **Géométrie dessinée sur la carte** : mode **point** (un toucher le place, appui long pour le déplacer) ou **polygone** (un toucher ajoute un sommet, appui long pour en déplacer un). Sur une station rouverte, une poignée « + » au milieu de chaque arête y insère un sommet. « Annuler » revient sur la dernière opération de la station en cours ; les stations voisines déplacées par la topologie partagée ne sont pas défaites.
- **Aimantage et topologie partagée** : un point ou un sommet posé s'aimante au sommet le plus proche parmi les stations à envoyer et les stations serveur affichées. Un sommet partagé par deux polygones mitoyens les fait suivre tous les deux, et un sommet inséré sur une arête commune s'ajoute aux deux : la voisine modifiée part avec la saisie en cours.
- **Polygones à trou** : les anneaux intérieurs d'une station tracée sous QGIS sont lus, dessinés en creux, éditables sommet par sommet et renvoyés à la mise à jour. L'application ne crée pas de trou.
- **Surface et altitudes** : la surface est calculée **localement** (donc hors-ligne), trous déduits. Les altitudes min et max sont demandées au MNT du serveur **à l'envoi**, pour une station qui n'en a pas ; sans réponse, la station part quand même. Un changement de géométrie les efface. Elles se corrigent à la main dans les détails de la station, si le formulaire du serveur montre ces champs.
- **Habitats HABREF** : de 0 à N habitats par station (une station sans habitat reste valide et envoyable), avec les champs que montre le formulaire OccHab du serveur. La recherche lit un **cache HABREF local**, restreint à la liste déclarée par le module quand le serveur en publie une.
- **Stations déjà sur GeoNature** : les stations du serveur dont l'utilisateur est numérisateur ou observateur s'affichent sur la carte (en violet) et servent de cibles d'aimantage. Les toucher propose de les **importer pour modification** (« Modifier cette station du serveur ? »), géométrie et habitats ; l'envoi part en **mise à jour** de la station existante, jamais en doublon. La station est relue sur le serveur avant la mise à jour : une station supprimée sur le web n'est pas recréée, et l'envoi est refusé si la géométrie a changé des deux côtés. Une station n'entre dans « Mes stations » qu'à la **première modification réelle**, et en ressort si l'on annule toutes ses modifications ; il ne peut y avoir qu'une copie locale non envoyée par station serveur.
- **Évaluations ANA / Natura 2000** : les stations et habitats qui portent un bloc `[ANA-EVAL]` (plugin QGIS maison) affichent une section « Évaluation ANA / Natura 2000 » éditable. Le texte libre du commentaire n'est jamais altéré : le bloc est extrait à la lecture et refusionné à l'envoi.
- **Mes stations** : onglets « À envoyer » et « Envoyées », envoi d'une saisie ou « Tout envoyer ».

### Suivis protocolés (`gn_module_monitoring`)

Mode pro seulement.

- **Piloté par le schéma** : aucun protocole en dur. L'application lit `/api/monitorings/config/<module>` et construit formulaires, fils d'Ariane et navigation selon l'arborescence du protocole (`sites_group → site → visit → observation`, `zone → station → point`, etc.). Les types qui se saisissent dans l'application (visites, observations, relevés, dénombrements) sont reconnus par leur code ; il n'y a pas de création de sites.
- **Formulaire** : les widgets du formulaire web `text`, `textarea`, `number`, `date`, `time`, `select`, `radio`, `bool_radio`, `multiselect`, `checkbox`, `bool_checkbox`, `datalist`, `nomenclature`, `observers`, `dataset`, `taxonomy`, `html` et `medias`. Valeurs initiales comme sur le web (la `value` du schéma, l'option unique d'une liste requise, le `default` d'une datalist, la date du jour dans `visit_date_min` et `first_use_date`, l'utilisateur connecté dans `observers`). Masquage via `hidden` (booléen ou expression), `required` conditionnel, remplissage de champs dépendants par les règles `change`.
- **Champ espèce** : les propositions viennent de la liste du champ (son `id_list` ou la liste de son `api`), sinon de tout le TaxRef chargé ; comme en OccTax, **seuls les noms proposés sont acceptés**. Résolution sur le cache local, donc hors-ligne.
- **Photos** : plusieurs par objet, prises avec l'appareil ou choisies dans la galerie, avec un titre (obligatoire), un auteur et une description. Elles partent vers `gn_commons` après la création de l'objet (uuid généré dans l'application, envoyé comme `uuid_attached_row`).
- **Datalists** : chargées par « Charger les données » (listes de GeoNature et de TaxHub) et lues dans le cache. Une liste absente du cache est signalée dans le formulaire (« ⚠ Datalists non chargées : … »).
- **Droits CRUVED** : seuls les protocoles que l'utilisateur peut lire (droit R > 0, comme le web) sont gardés, dès la lecture de `/api/monitorings/modules` ; un module sans bloc `cruved` reste visible. Sans droit de création sur un type, le « + » est grisé (« Vous n'avez pas les permissions nécessaires »).
- **Carte interactive** : sur la carte d'un protocole ou d'un site, toucher un point, une ligne ou un polygone ouvre un dialogue au nom de l'objet, avec « Voir la fiche » et, si le schéma le permet, une nouvelle saisie.
- **Validation** : le bouton d'enregistrement est inactif tant qu'un champ numérique sort de ses bornes `min`/`max` (littérales ou pointant vers un autre champ, message sous le champ), qu'un champ obligatoire visible reste vide ou qu'une espèce n'est pas reconnue. **Exception pour les informations de fin d'une visite** (champ `visit_date_max`, codes ou libellés qui disent la fin ou la durée) : obligatoires, elles peuvent attendre ; la visite s'enregistre « à compléter », « Compléter la visite ? » le rappelle, et le formulaire les redemande avant l'envoi. Tous les autres champs obligatoires sont exigés dès le début.
- **Chaîne de saisies** : « Enregistrer + suivante » ouvre aussitôt une nouvelle saisie du même type sous le même parent, avec les champs `keep` repris.
- **Mes visites** : un fil d'Ariane par groupe, édition, suppression en cascade, « Envoyer ce groupe », onglets « À envoyer » et « Envoyées », « Réessayer », « Tout envoyer ». À l'envoi, l'application cherche l'objet par son uuid sur le serveur avant de le recréer ; une correction faite après un envoi partiel part en `PATCH`.

### Explorer
Consultation cartographique des observations récentes du serveur (synthèse, 1 000 observations au plus) : requête sur l'emprise visible, rien au-delà de 100 km de large. Les observations sont regroupées par cellules d'environ 110 m, un marqueur par cellule avec l'icône de son groupe taxonomique et son effectif ; une barre filtre par groupe. Toucher un marqueur liste les observations de la cellule. La profondeur d'historique est celle publiée par le serveur (365 jours par défaut).

### Hors-ligne et envoi
- **Saisies enregistrées au fil de l'eau** dans le téléphone (OccTax, visites des Suivis, stations OccHab), et **envoyées à la demande** uniquement, jamais automatiquement. Une pastille rouge sur le menu de l'accueil rappelle qu'il reste des données à envoyer.
- **Caches** : « Charger les données » met en cache les référentiels (TaxRef, nomenclatures, jeux de données, listes, observateurs, champs additionnels), les protocoles des Suivis avec leurs objets structurels (groupes de sites, sites, points…, pas les visites passées), le référentiel HABREF et les stations OccHab de l'utilisateur. Les écrans lisent ces caches ; restent en ligne le chargement lui-même, les envois et leurs contrôles anti-doublon, l'Explorer, le test de connexion, la vérification « Donnée exceptionnelle », et la fiche d'une visite ou d'une observation des Suivis, mise à jour depuis le serveur à son ouverture s'il répond.
- **Envois et chargement protégés de la mise en veille** : service au premier plan sous Android (avec une notification pour le chargement), tâche d'arrière-plan sous iOS.

### Cartographie
Six fonds en ligne, au choix par le bouton « Changer fond » : **OpenStreetMap**, **OpenTopoMap (relief)**, **IGN Plan**, **IGN SCAN 25 (topo)**, **IGN Photo aérienne**, **Esri (satellite)**, auxquels s'ajoutent les cartes **MBTiles** importées. Chaque carte affiche la source de son fond. Le dernier fond choisi est **mémorisé** et réappliqué à l'ouverture de n'importe quelle carte.

- **Maps Manager** : téléchargement d'une zone (jusqu'à 200 km², jusqu'au zoom 17) pour un usage sans réseau, pour chacun des fonds en ligne. Un téléchargement s'annule. Une zone téléchargée n'expire pas : seul « Vider le cache » la supprime. L'occupation s'affiche sur un budget de référence d'1 Go, sans purge automatique. Le Maps Manager peut cibler un protocole des Suivis, pour cadrer sur ses sites et les afficher.
- **Fonds hors-ligne importés** : un fichier **MBTiles** fourni par l'utilisateur s'importe et sert de fond sur toutes les cartes, sans aucun réseau.
- **Position** : affichée en direct sur les cartes de saisie (multi-taxons, mono-taxon, OccHab), l'Explorer, le Maps Manager et la carte des Suivis ; toutes, sauf la carte des Suivis, ont un bouton de recentrage. Sous Android, l'application lit le `LocationManager` (fournisseur GPS, sinon réseau) et non le fournisseur de Google Play, pour garder les positions des récepteurs RTK externes (relayés par SW Maps, par exemple) qui passent par les positions fictives.

## Paramètres

Écran ouvert par l'engrenage en haut à droite de l'accueil, et de lui-même au lancement tant que la configuration est incomplète.

| Paramètre | Description |
|-----------|-------------|
| Mode de saisie | Pro ou Simplifié |
| Serveur GeoNature | Choisi dans une liste de serveurs connus (`ServeursGeoNature.kt`), ou URL saisie |
| Identifiant / Mot de passe | Identifiants GeoNature ; le mot de passe est chiffré dans le Keystore d'Android, ou gardé dans le trousseau d'iOS |
| Jeu de données | Choisi dans la liste des jeux proposés (mode simplifié : les jeux personnels de l'utilisateur, s'il en a) |
| Listes | Liste de taxons par défaut (mode simplifié : la liste de la saisie d'OccTax) |
| Observateur par défaut | L'utilisateur connecté par défaut (mode simplifié : lui seul) |

Le bouton **Tester la connexion** vérifie les identifiants, affiche la version de l'instance (« Serveur GeoNature v… ») et révèle la section de chargement ; il ne télécharge rien. Le bouton **Charger les données** (« Recharger les données » une fois le cache rempli) télécharge les caches décrits plus haut : tout en mode pro, ce qui sert à la saisie multi-taxons en mode simplifié. Un appui long force le rechargement complet des taxons.

**Listes de taxons chargées.** Seules les listes utiles se téléchargent : la liste choisie, celles qu'imposent les jeux de données, celles des protocoles accessibles et celle de la configuration d'OccTax. Toutes les listes du serveur ne sont chargées que si un champ espèce d'un protocole n'a pas de liste. Choisir plus tard une liste non chargée la télécharge aussitôt.

**Chargement en arrière-plan.** Sous Android, le chargement s'exécute dans un service au premier plan (`foregroundServiceType="dataSync"`, notification de progression) : il continue si l'utilisateur quitte l'écran ou passe l'application en arrière-plan. En revenant sur Paramètres, on retrouve la progression ; le résultat, avec un éventuel récapitulatif des étapes en échec, s'affiche à la fin.

**Après une mise à jour de l'application.** Quand une version change le contenu des caches, un bandeau invite à recharger les données ; l'application reste utilisable d'ici là.

**Liste de taxons partiellement chargée.** Chaque page d'une liste est redemandée en cas d'échec. Si la pagination s'interrompt quand même, les taxons déjà reçus sont gardés, mais la liste est marquée **incomplète** : Paramètres le signale avec son numéro et la saisie attend un nouveau chargement. Sans cela, l'application refuserait des espèces pourtant valides.

**Garde de cohérence à l'accueil.** On ne peut démarrer une saisie que si la configuration est **complète et cohérente avec le serveur courant** : connexion renseignée **et** jeu de données, liste de taxons et observateur par défaut **réellement présents dans les caches** de ce serveur. Tant que ce n'est pas le cas, une pastille rouge s'affiche sur l'engrenage (un point vert une fois la configuration valide) et les boutons de saisie sont estompés.

Changer de serveur vide les caches et les sélections qui en dépendent ; si l'ancien serveur avait des données ou des envois en attente, l'application le demande d'abord (« Changer de serveur ? »). Le bouton **Vider le cache** purge les données chargées sans toucher aux saisies en attente.

À l'envoi, si le jeu de données d'un relevé est absent du serveur, l'erreur le dit (« … introuvable sur ce serveur ») au lieu d'une « erreur serveur ».

En mode pro, le panneau de chargement affiche six compteurs : **Taxons** (`cd_nom` distincts), **Protocoles**, **Nomenclatures**, **Listes**, **Observateurs** et **Stations OccHab** ; la ligne Protocoles est masquée sans protocole, la ligne Stations OccHab sans accès à OccHab. Pour les taxons, un bouton **Détails** liste le cache par liste ; toucher une liste montre ses taxons. Sous le champ **Listes**, une ligne indique le nombre de taxons de la liste choisie, ou avertit si elle est absente du cache ou vide.

Dans la saisie OccTax, le bouton **Détails** du relevé modifie le jeu de données et les observateurs de ce relevé (et ses champs additionnels) ; le bouton « i » de la carte, « Détails communs de la saisie », les règle pour les relevés suivants.

## Écran d'accueil

- **Mode pro** : **Saisie multi-taxons**, **Saisie mono-taxon**, **Monitoring** (s'il y a des protocoles accessibles), **OccHab** (si le module est installé et que l'utilisateur peut y créer des stations).
- **Mode simplifié** : un seul bouton, **Démarrer une saisie**, qui ouvre la saisie multi-taxons. Tant que la configuration est incomplète, une ligne dessous (« Pour commencer, renseignez la connexion dans les Paramètres ») ouvre les Paramètres.
- Numéro de version de l'application et lien vers la licence.
- Menu (pastille rouge dès qu'une saisie, une visite ou une station reste à envoyer) :
  - **Mes saisies** : saisies OccTax (À envoyer, Envoyées, Importées), export et import GPX ;
  - **Mes visites** : saisies des Suivis, si le module est là ou s'il reste quelque chose à envoyer ;
  - **Mes stations** : saisies OccHab, même règle ;
  - **Explorer** : observations récentes du serveur sur carte ;
  - **Maps Manager** : fonds de carte hors-ligne et cartes MBTiles.

  Chacun de ces écrans a un fil d'Ariane (maison › nom de l'écran) qui ramène à l'accueil.

## Limitations connues

- **Suivis** : pas de création de sites. Un widget que l'application ne prend pas en charge (`geometry`, `datetime`) est écarté et signalé dans le formulaire (« ⚠ N champ(s) non supporté(s) : … »). La *consultation* des géométries des sites existants est prise en charge (carte interactive, Maps Manager).
- **Expressions de formulaire hors grammaire** : les expressions `hidden`, `change` ou de validation que l'application ne sait pas lire sont ignorées (champ affiché et modifiable) plutôt que mal interprétées.
- **Médias OccHab** : une station ou un habitat OccHab ne porte ni photo ni média.
- **Géométries OccHab** : l'application ne dessine qu'un point ou un polygone. D'une station en plusieurs parties (MultiPolygon), seule la première est affichée, et la station n'est pas modifiable dans l'application (la renvoyer supprimerait les autres parties). Une station en ligne s'affiche comme un point. Les stations MultiPoint, MultiLineString ou GeometryCollection sont écartées au chargement, sans message.
- **Photo ajoutée après l'envoi** : une photo ajoutée à une espèce déjà transmise ne part pas (la mise à jour d'une occurrence ne porte pas de média) ; en mode simplifié, « Photo » et « Audio » sont grisés sur une observation déjà envoyée.
- **Dictée vocale** : sur le champ espèce d'OccTax seulement, en français, sans choix de langue.

## Routes serveur utilisées

Sauf mention `<taxhub>`, toutes les routes sont relatives à l'**URL du serveur GeoNature** configurée. `<taxhub>` désigne le TaxHub intégré, `<URL_GEONATURE>/api/taxhub` ; s'il ne répond pas, l'URL TaxHub publiée par le serveur (`sync.taxhub_url` des réglages mobiles d'OccTax).

**Authentification et configuration**

| Route | Usage |
|-------|-------|
| `POST /api/auth/login` | Authentification : jeton et cookies |
| `GET /api/gn_commons/config` | Version de GeoNature, formulaires OccTax et OccHab, durée d'historique |
| `GET /api/gn_commons/t_mobile_apps` | Réglages mobiles d'OccTax (URL TaxHub, taille de page) |
| `GET /api/gn_commons/modules` | Modules installés et droits (CRUVED) sur OccTax, OccHab et les Suivis |
| `GET /api/gn_commons/additional_fields?module_code=OCCTAX` | Champs additionnels d'OccTax |

**Taxons, nomenclatures, référentiels**

| Route | Usage |
|-------|-------|
| `GET <taxhub>/api/biblistes` | Listes de taxons |
| `GET <taxhub>/api/taxref?orderby=cd_nom&fields=listes&id_liste=<id>&limit=<n>&page=<p>` | Taxons d'une liste, page par page |
| `GET <taxhub>/api/taxref/version` | Version de TaxRef (évite un retéléchargement) |
| `GET <taxhub>/api/taxref/<cd_nom>` | `cd_ref` d'un taxon, pour les profils de taxons |
| `GET /api/nomenclatures/nomenclatures/taxonomy` | Nomenclatures et leurs filtres taxonomiques (replis `…/nomenclature/taxonomy`, `/api/nomenclatures/taxonomy`) |
| `GET /api/nomenclatures/nomenclature/<TYPE>?orderby=label_default` | Valeurs d'une nomenclature (champs additionnels) |
| `GET /api/nomenclatures/nomenclature?id_type=<id>&limit=200` | Repli à l'envoi si le cache des nomenclatures est vide (et variantes `code_nomenclature_type`, `/api/nomenclatures/nomenclatures`) |
| `GET /api/<module>/defaultNomenclatures` | Valeurs par défaut d'OccTax (deux casses essayées, `?organism=<id>`) et d'OccHab |
| `GET /api/habref/habitats/autocomplete?limit=200000[&id_list=<id>]` | Référentiel HABREF complet, mis en cache |
| `GET /api/meta/datasets?active=true&module_code=<MODULE>&fields=modules` · `POST /api/meta/datasets?active=true` | Jeux de données lisibles / où l'on peut créer |
| `GET /api/users/menu/<id>` | Observateurs d'une liste |

**Saisie OccTax**

| Route | Usage |
|-------|-------|
| `POST /api/occtax/OCCTAX/only/releve` | Création d'un relevé |
| `POST /api/occtax/OCCTAX/releve/<id>/occurrence` | Ajout d'une occurrence (taxon, dénombrements, médias) |
| `POST /api/occtax/OCCTAX/only/releve/<id>` | Mise à jour d'un relevé corrigé après un envoi partiel |
| `POST /api/occtax/OCCTAX/occurrence/<id>` | Mise à jour d'une occurrence corrigée après un envoi partiel |
| `GET /api/occtax/OCCTAX/releves?unique_id_sinp_grp=<uuid>&limit=1` | Anti-doublon : le relevé existe-t-il déjà ? |
| `GET /api/occtax/OCCTAX/releve/<id>` | Occurrences déjà présentes avant un nouvel essai |
| `DELETE /api/occtax/OCCTAX/releve/<id>` | Annulation d'un relevé sans occurrence aboutie |
| `POST /api/geo/altitude` | Altitudes d'un relevé, et d'une observation à vérifier |
| `POST /api/gn_profiles/check_observation` | Vérification « Donnée exceptionnelle » |

**Suivis**

| Route | Usage |
|-------|-------|
| `GET /api/monitorings/modules` | Protocoles et bloc CRUVED |
| `GET /api/monitorings/config/<module>` | Schéma d'un protocole |
| `GET /api/monitorings/object/<module>/module?depth=1` · `GET /api/monitorings/object/<module>/<type>/<id>?depth=1` | Enfants d'un protocole, fiche d'un objet et ses enfants |
| `GET /api/<api d'un champ>` (ou `<taxhub>/api/<api>`) | Options des listes déclarées par chaque protocole |
| `GET /api/media/monitorings/<module>/img.jpg` | Pictogramme d'un protocole (mis en cache) |
| `POST /api/monitorings/object/<module>/<type>` | Création d'une visite, d'une observation… |
| `PATCH /api/monitorings/object/<module>/<type>/<id>` | Correction d'un objet déjà créé |
| `GET /api/monitorings/list/<module>/<type>?<champ uuid>=<uuid>` | Anti-doublon : l'objet existe-t-il déjà ? |

**OccHab**

| Route | Usage |
|-------|-------|
| `GET /api/occhab/stations/?format=geojson&habitats=1&nomenclatures=1` | Stations du serveur (l'application garde celles de l'utilisateur) |
| `POST /api/occhab/stations/` | Création d'une station (géométrie, propriétés, habitats) |
| `GET /api/occhab/stations/<id>/` | Version du serveur d'une station avant sa mise à jour |
| `POST /api/occhab/stations/<id>/` | Mise à jour d'une station existante |
| `GET /api/occhab/stations/?format=json` | Anti-doublon d'une station |
| `POST /api/geo/info` | Altitudes min et max d'une station (MNT) |

**Médias et consultation**

| Route | Usage |
|-------|-------|
| `GET /api/gn_commons/get_id_table_location/<schema.table>` | Table de rattachement d'un média |
| `POST /api/gn_commons/media` · `DELETE /api/gn_commons/media/<id>` | Envoi d'un média, annulation |
| `GET /api/gn_commons/medias/<uuid_attached_row>` | Médias déjà reçus par le serveur (anti-doublon au renvoi) |
| `POST /api/synthese/for_web?limit=1000` | Observations récentes affichées dans l'Explorer |

## Compatibilité GeoNature

**Version minimale supportée : GeoNature 2.15** (TaxHub intégré, servi sous `<URL_GEONATURE>/api/taxhub`). La constante `VERSION_GEONATURE_MINIMALE` (`core/…/network/CompatibiliteServeur.kt`) porte ce seuil : si le test de connexion lit une version inférieure, il échoue et la connexion n'est plus considérée comme configurée. Une version non lue est tolérée (« Connexion réussie (version de GeoNature non lue) »), sauf si la dernière version lue était trop ancienne.

**Modules** : OccTax est le cœur de la saisie. Monitoring et OccHab sont facultatifs : absents ou sans droits, leurs boutons n'apparaissent pas.

Au-delà de ce seuil, l'API GeoNature n'étant pas versionnée par route, la compatibilité repose sur une **tolérance défensive** plutôt que sur une détection fine de version :

- **Replis de routes** : les routes qui varient entre versions sont essayées l'une après l'autre (nomenclatures, valeurs par défaut).
- **Replis de lecture** : les clés JSON connues sont toutes essayées (jeton de connexion, enveloppes de tableau, identifiant de relevé, version).
- **404 = fonctionnalité absente** : Monitoring non installé, `additional_fields`, `defaultNomenclatures`… se passent sans bloquer.
- **Nomenclature non résolue** : elle est omise de l'envoi (avec une ligne de journal) plutôt que de le bloquer.

Deux outils aident à qualifier un serveur avant un déploiement :

```bash
outils/verifier_serveur_geonature.py <url>             # verdict route par route ; identifiant et mot de passe demandés au clavier
outils/verifier_serveur_geonature.py <url> --public    # sans connexion : routes publiques seules
outils/lister_endpoints_geonature.py --full            # routes appelées par l'application
```

## Développement

GeoMys est le portage de GeoMys-Android (et des styles de GeoMys-IOS) en Kotlin Multiplatform, mené par lots d'après l'inventaire [`INVENTAIRE.md`](INVENTAIRE.md) (§ 10, ordre de portage). Tous les écrans de GeoMys-Android sont portés. Le bilan de chaque lot, avec son critère de sortie (la recette), est dans son fichier :

| Lot | Contenu | Bilan |
|---|---|---|
| 0 | Prototypes : carte MapLibre, mesure de TaxRef en mémoire | [`PROTOTYPE-CARTE.md`](PROTOTYPE-CARTE.md), [`MESURE-TAXREF.md`](MESURE-TAXREF.md) |
| 1 | Connexion et authentification GeoNature | [`LOT-1.md`](LOT-1.md) |
| 2 | Synchro des référentiels | [`LOT-2.md`](LOT-2.md) |
| 3 | Envois, avec leurs protections anti-doublon | [`LOT-3.md`](LOT-3.md) |
| 4 | Moteur de formulaires | [`LOT-4.md`](LOT-4.md) |
| 5 | Socle des écrans : Accueil, Paramètres | [`LOT-5.md`](LOT-5.md) |
| 6 | Écrans OccTax | [`LOT-6.md`](LOT-6.md) |
| 7 | Écrans des Suivis | [`LOT-7.md`](LOT-7.md) |
| 8 | Écrans OccHab | [`LOT-8.md`](LOT-8.md) |
| 9 | Explorer, Maps Manager et cartes MBTiles, puis les évolutions depuis | [`LOT-9.md`](LOT-9.md) |

Les défauts de GeoMys-Android que le portage reproduisait sont corrigés ([`LOT-8.md`](LOT-8.md) § 5, [`LOT-9.md`](LOT-9.md) § 5).

**Prototypes du lot 0.** Ils ne s'ouvrent que par paramètre de lancement :
- `ecran=prototype-carte` : l'écran d'essais MapLibre, avec ses fonds, le MBTiles, le hors-ligne, le cap et les sommets déplaçables. Outils dans `outils/prototype-carte/` ;
- `ecran=mesure-taxref` : ce que coûte un référentiel TaxRef chargé en mémoire comme dans GeoMys-Android.

**Paramètres de lancement des essais** (versions de debug et de mesure seulement) : `action=synchroniser` lance la synchro complète sans écran ; `action=monitoring-essai` et `action=occhab-essai` chargent des protocoles et des stations d'essai, sur l'émulateur et le simulateur seulement.

### Structure

| Dossier | Contenu |
|---|---|
| `core/` | Logique commune, sans Compose : modèles, réseau, stockage, ViewModels… Cibles Android et iOS. |
| `ui/` | Écrans Compose Multiplatform, thème, ressources (`composeResources`). Sur iOS, ce module produit le framework `GeoMysUi`. |
| `androidApp/` | Application Android : `MainActivity`, services au premier plan (suivi GPS, chargement des données), manifeste, icônes. |
| `iosApp/` | Projet Xcode de l'application iOS, qui affiche l'interface Compose. |
| `outils/` | Outils de développement : la vérification avant de pousser (`verifier.sh`), les attendus d'org.json tirés des sources d'Android (`reference-org-json/`), un GeoNature simulé pour l'authentification (`geonature-simule/`), le serveur de tuiles et le générateur de MBTiles de test (`prototype-carte/`), la liste des routes GeoNature appelées par l'appli (`lister_endpoints_geonature.py`) et la vérification d'un serveur GeoNature route par route (`verifier_serveur_geonature.py`). |
| `licences/` | Texte des licences des codes repris d'autres projets (Apache 2.0 pour org.json, les icônes de Material Components et `ic_menu_delete` d'AOSP). |
| `docs/` | Captures et documents annexes. |
| `INVENTAIRE.md`, `inventaire/` | Inventaire de ce qui est partageable dans GeoMys-Android, et suivi du portage. |
| `MODES-SAISIE.md`, `MODES-SAISIE.html` | Les différences entre mode pro et mode simplifié, à tenir à jour à chaque changement de l'un ou de l'autre mode. |

### Identité

| | Android | iOS |
|---|---|---|
| Identifiant | `fr.ariegenature.public.geomys` (release, celui de GeoMys-Android sur Google Play) ; `fr.ariegenature.geomys.common` (versions de test) | `fr.ariegenature.geomys.common` |
| Nom sous l'icône | `androidApp/src/main/res/values/strings.xml` | `APP_DISPLAY_NAME` dans `iosApp/Configuration/Config.xcconfig` |
| Version | `versionName` / `versionCode` dans `androidApp/build.gradle.kts` | `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` dans `Config.xcconfig` |

- **Nom affiché** : « GeoMys », sous l'icône comme dans l'application, où l'accueil reprend le nom de l'icône (`InfosApp.nom`).
- **Logo** : celui de GeoMys. Les icônes sont reprises de GeoMys-Android et de GeoMys-IOS.
- Sous iOS, GeoMys est indépendante de GeoMys-IOS : son propre identifiant, sans reprise des données.

### Prérequis

- **Android Studio 2025.3** (JDK 21 intégré) et le **SDK Android 37**.
- **Xcode 27** avec le SDK iOS 26. L'application vise iOS 17 et plus.
- Sur ce Mac, `xcode-select` pointe vers les outils en ligne de commande. Deux solutions :
  - préfixer les commandes Xcode par `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` ;
  - ou exécuter une fois `sudo xcode-select -s /Applications/Xcode.app`.

### Compiler, lancer, tester

**Android**
- `./gradlew :androidApp:assembleDebug` produit l'APK dans `androidApp/build/outputs/apk/debug/`.
- `./gradlew :androidApp:assembleRelease` produit la version à distribuer, dans `androidApp/build/outputs/apk/release/` : 14 Mo. Le code y est réduit par R8, sans renommage (les messages d'erreur citent des noms de classes). Seules les architectures des téléphones sont gardées (ARM 64 et 32 bits), et le moteur de MapLibre y est compressé. Elle est signée avec la clé de GeoMys-Android si `keystore.properties` est présent à la racine (gabarit : `storeFile`, `storePassword`, `keyAlias`, `keyPassword`), comme pour GeoMys-Android ; ce fichier et la clé ne vont jamais dans le dépôt (`.gitignore`). Une release ne s'installe pas par-dessus une version de debug : il faut désinstaller celle-ci, et ses données locales avec.
- `./gradlew :androidApp:assembleMesure` produit une release (réduite par R8) signée avec la clé de debug, pour mesurer durées et mémoire sans les ralentissements d'une appli débogable.
- Les paramètres de lancement des essais (`--es ecran …`, `--es action …`) ne sont lus que par les versions de debug et de mesure : une release les ignore, pour qu'aucune autre appli du téléphone ne puisse y écrire de données d'essai.
- Dans Android Studio, ouvrir le dossier racine et lancer la configuration `androidApp`.

**iOS**
- Dans Xcode, ouvrir `iosApp/iosApp.xcodeproj`, choisir le schéma `iosApp` et un simulateur, puis lancer (Run).
- La phase « Compile Kotlin Framework » compile le code Kotlin via `./gradlew :ui:embedAndSignAppleFrameworkForXcode`.
- En ligne de commande :

  ```
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
    -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
  ```

**Tests**
- `./gradlew :core:allTests` lance les tests communs sur la JVM et sur le simulateur iOS. `-Pgeomys.simulateur=<UDID>` impose le simulateur des tests iOS, pour laisser tranquille un simulateur déjà ouvert.
- `./gradlew :ui:testAndroidHostTest` lance les tests des écrans (ViewModels et état des champs), sur la JVM ; `./gradlew :ui:iosSimulatorArm64Test`, ceux de mise en page (Compose UI test, sans fenêtre), sur le simulateur iOS (même option `-Pgeomys.simulateur`). Il n'y a pas de test instrumenté sur appareil.
- `outils/verifier.sh` fait la vérification complète, à lancer avant de pousser : APK Android, tests communs et tests des écrans (JVM et simulateur iOS), build iOS pour le simulateur. Il tient lieu d'intégration continue. Avec `--sans-ios`, il se passe de Xcode ; avec `--simulateur=<UDID>`, il passe le simulateur aux tests iOS.
- `GEOMYS_ESSAI_URL=<url> GEOMYS_ESSAI_IDENTIFIANT=<compte> GEOMYS_ESSAI_MOT_DE_PASSE=<mot de passe> ./gradlew :core:testAndroidHostTest --tests '*EssaiServeurReelTest*' --rerun` fait tourner les vrais services de l'appli sur un serveur : connexion, chargement complet des données, formulaires de chaque protocole, Explorer, sans rien envoyer. Le mot de passe passant par l'environnement, à réserver à un compte d'essai, comme celui, public, du serveur de démonstration de GeoNature ; `GEOMYS_ESSAI_RAPPORT=<fichier>` garde le rapport.

### Versions

| Outil | Version |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| maplibre-compose (carte) | 0.20.0 |
| Navigation Compose (JetBrains) | 2.10.0-rc01 |
| Lifecycle ViewModel et Runtime Compose (JetBrains) | 2.11.0 |
| kotlinx-serialization | 1.11.0 |
| kotlinx-datetime | 0.8.0 |
| Ktor (client) | 3.6.0 |
| Kermit (journaux) | 2.2.0 |
| multiplatform-settings | 1.3.0 |
| atomicfu | 0.33.0 |
| AndroidX Core (Android, notification de synchro) | 1.18.0 |
| okio | 3.18.2 |
| AGP | 9.1.1 |
| Gradle | 9.3.1 |

- **Android** : minSdk 24 (Android 7.0), compileSdk 37, targetSdk 36 ; bytecode Java 11, avec désucrage des API Java récentes pour Android 7.

AGP et Gradle sont les mêmes que dans GeoMys-Android. Toutes les versions sont regroupées dans `gradle/libs.versions.toml`.

### Règles du portage

- **GeoMys-Android et GeoMys-IOS sont des sources en lecture seule.** On copie, on ne modifie jamais.
- **Les écrans reprennent ceux de GeoMys-Android** au plus près : interfaces, styles, placement des éléments et fonctionnalités. Sous iOS, ils prennent les styles de GeoMys-IOS ; les éléments, textes et comportements restent ceux d'Android (décision 15 de l'inventaire, [`LOT-5.md`](LOT-5.md) § 2).
- **Chaque fichier porté indique son commit d'origine**, dans un commentaire d'en-tête et dans `inventaire/index-fichiers.tsv` (colonnes `commit_reference` et `statut_portage`).

## Licence

Ce projet est distribué sous licence **GNU General Public License v3.0** (GPLv3), comme GeoMys-Android. Le texte complet de la licence est dans le fichier [`LICENSE`](LICENSE).

© ANA - CEN Ariège.
