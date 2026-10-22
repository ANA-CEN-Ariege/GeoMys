/*
 * GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
 * Copyright (C) 2026 ANA - CEN Ariège
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Gardes du lot 8 sur ce que OccHabDetailsDialog, OccHabHabitatFragment, OccHabStationFragment et
// OccHabStationsFragment de GeoMys-Android @ cdd5196 faisaient sans test : jeux de données proposés,
// champs requis, application des détails, mémoire du relevé précédent, règles de l'habitat, section
// ANA-EVAL, textes de l'écran d'une station et de « Mes stations ».

package fr.ariegenature.geomys.occhab

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.io.listeVersJson
import fr.ariegenature.geomys.util.AnaEval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormulairesOccHabTest {

    private fun config() = GeoNatureConfig(MapSettings(), CoffreEnMemoire())

    private fun saisies(
        idDataset: Int? = 12,
        observateurs: List<Pair<Int, String>> = listOf(1 to "MOI"),
        natureVisible: Boolean = true,
        nature: Int? = 6,
    ) = SaisiesDetailsStation(
        idDataset = idDataset,
        nomDataset = "Prairies (12)",
        observateurs = observateurs,
        dateMin = 2_000L,
        dateMax = 1_000L,
        natureVisible = natureVisible,
        nature = nature,
    )

    // ─── Détails de la station ───

    @Test
    fun jeux_de_donnees_occhab_actifs_et_creables() {
        val config = config().apply {
            datasetsOcchabCacheJson = listeVersJson(
                GeoNatureDataset.serializer(),
                listOf(
                    GeoNatureDataset(12, "Prairies", moduleCodes = listOf("OCCHAB")),
                    GeoNatureDataset(13, "Oiseaux", moduleCodes = listOf("OCCTAX")),
                    GeoNatureDataset(14, "Archives", actif = false),
                    GeoNatureDataset(15, "Tourbières"),
                ),
            )
        }
        // Comme le web : actifs, rattachés à OCCHAB et créables, nommés sans leur numéro ; sans liste
        // des créables, aucun.
        assertEquals(emptyList(), DetailsStation.datasets(config))
        config.datasetsCreablesOcchab = setOf(12, 13, 14, 15)
        assertEquals(listOf(12 to "Prairies"), DetailsStation.datasets(config))
        assertEquals("Archives", DetailsStation.nomJeuInactif(config, 14), "jeu inactif d'une station : lecture seule")
        assertNull(DetailsStation.nomJeuInactif(config, 12))
    }

    @Test
    fun champs_requis_dans_l_ordre_du_web() {
        val c = config()
        assertEquals("Choisissez un jeu de données", DetailsStation.champManquant(saisies(idDataset = null), c))
        assertEquals("Choisissez un observateur", DetailsStation.champManquant(saisies(observateurs = emptyList()), c))
        assertEquals("Choisissez la nature de l'objet géographique", DetailsStation.champManquant(saisies(nature = null), c))
        // Masquée sans valeur (pas de défaut du serveur) : requise quand même, comme sur le web.
        assertEquals("Choisissez la nature de l'objet géographique", DetailsStation.champManquant(saisies(natureVisible = false, nature = null), c))
        assertEquals("Choisissez une date de début", DetailsStation.champManquant(saisies().copy(dateMin = null), c), "date masquée : sans date du jour")
        assertNull(DetailsStation.champManquant(saisies(), c))
        // Observateurs en texte libre (`OCCHAB.OBSERVER_AS_TXT`) : le texte est requis, pas la liste.
        assertEquals("Saisissez les observateurs", DetailsStation.champManquant(saisies(observateurs = emptyList()).copy(observateursTexte = "  "), c))
        assertNull(DetailsStation.champManquant(saisies(observateurs = emptyList()).copy(observateursTexte = "Alice"), c))
        assertEquals("L'altitude max ne peut être inférieure à l'altitude min", DetailsStation.altitudesIncoherentes("900", " 800 "))
        assertNull(DetailsStation.altitudesIncoherentes("800", ""))
        assertNull(DetailsStation.altitudesIncoherentes("800", "0"), "0 ne compte pas, comme dans le minMaxValidator du web")
        assertEquals("L'altitude max ne peut être inférieure à l'altitude min", DetailsStation.altitudesIncoherentes("-5", "-10"))
        // dateValidator du web : la fin ne précède pas le début.
        assertEquals("La date de début ne peut pas être postérieure à la date de fin", DetailsStation.datesIncoherentes(2_000L, 1_000L))
        assertNull(DetailsStation.datesIncoherentes(1_000L, 1_000L))
        assertNull(DetailsStation.datesIncoherentes(null, 1_000L))
    }

    @Test
    fun demarrage_les_valeurs_deviennent_les_defauts_et_la_fin_ne_precede_jamais_le_debut() {
        val session = SessionOccHab().apply { demarrerSession(OccHabDetailsSession(idNomObjetGeographique = 4)) }
        DetailsStation.appliquer(session, saisies(natureVisible = false, nature = null), demarrage = true)
        val d = session.defautsSession
        assertEquals(12, d.idDataset)
        assertEquals("Prairies (12)", d.nomDataset)
        assertEquals(listOf("MOI"), d.observateursNoms)
        assertEquals(2_000L, d.dateMax, "date_max ≥ date_min (garde serveur)")
        assertEquals(4, d.idNomObjetGeographique, "nature masquée : valeur préservée")
        assertTrue(session.jddDefini)
    }

    @Test
    fun details_complets_derivees_par_station_et_champs_masques_preserves() {
        val session = SessionOccHab().apply {
            demarrerSession(OccHabDetailsSession(idNomCalculSurface = 5, comment = "avant"))
            definirSurface(1200L)
            definirAltitudes(800, 850)
        }
        DetailsStation.appliquer(
            session,
            saisies().copy(
                altitudeMin = "810", altitudeMax = null, surface = "", commentaire = "  revu  ", anaEval = { "{\"enjeu\":\"fort\"}" },
                profondeurMin = "2", profondeurMax = null, expositionVisible = true, exposition = 31, typeSolVisible = false, typeSol = null,
            ),
            demarrage = false,
        )
        assertEquals(31, session.details.idNomExposition)
        assertEquals(31, session.stationAEnregistrer().idNomExposition, "part avec la station")
        assertEquals(2, session.details.profondeurMin)
        assertEquals(2, session.stationAEnregistrer().profondeurMin, "part avec la station")
        assertNull(session.details.profondeurMax, "masquée : la valeur (vide) de la station")
        assertEquals(810, session.station.altitudeMin)
        assertEquals(850, session.station.altitudeMax, "champ masqué : valeur de la station")
        assertNull(session.station.surface, "champ vidé : effacé")
        assertEquals(5, session.details.idNomCalculSurface, "méthode masquée : préservée")
        assertEquals("revu", session.details.comment)
        assertEquals("{\"enjeu\":\"fort\"}", session.station.anaEvalJson)
        assertEquals(12, session.details.idDataset, "le « i » vise la station")
        assertNull(session.defautsSession.idDataset, "les défauts de session ne bougent pas")
    }

    @Test
    fun le_releve_precedent_reprend_tout_sauf_les_dates_et_le_commentaire() {
        val config = config().apply { idRoleUtilisateur = 3 }
        val vierge = detailsSessionParDefaut(config, aujourdhui = 86_400_000L)
        assertEquals(emptyList(), vierge.observateursIds, "le web ne pose aucun observateur")
        assertEquals(86_400_000L, vierge.dateMin)
        config.datasetsCreablesOcchab = setOf(12)
        config.datasetsOcchabCacheJson = listeVersJson(GeoNatureDataset.serializer(), listOf(GeoNatureDataset(12, "Prairies", moduleCodes = listOf("OCCHAB"))))
        DetailsStation.memoriser(
            config,
            OccHabDetailsSession(idDataset = 12, comment = "propre au relevé", dateMin = 5L, idNomTypeSol = 40, idNomExposition = 31, profondeurMin = 2),
        )
        val relu = detailsSessionParDefaut(config, aujourdhui = 172_800_000L)
        assertEquals(12, relu.idDataset)
        assertNull(relu.comment)
        assertEquals(172_800_000L, relu.dateMin)
        assertEquals(172_800_000L, relu.dateMax)
        assertEquals(40, relu.idNomTypeSol)
        assertNull(relu.idNomExposition, "propre à une station")
        assertNull(relu.profondeurMin, "propre à une station")
        // Un jeu qui n'est plus proposé (archivé, plus créable) n'est pas repris.
        config.datasetsCreablesOcchab = setOf(15)
        assertNull(detailsSessionParDefaut(config, aujourdhui = 172_800_000L).idDataset)
    }

    @Test
    fun defauts_du_serveur_et_observateurs_en_texte() {
        val config = config().apply {
            idRoleUtilisateur = 3
            occhabDefautsNomencJson = """{"TYPE_SOL":40,"DETERMINATION_TYP_HAB":50,"TECHNIQUE_COLLECT_HAB":60,"HAB_INTERET_COM":70,"ABONDANCE_HAB":80}"""
        }
        assertEquals(40, detailsSessionParDefaut(config).idNomTypeSol)
        // Habitat : le web ne pré-remplit que le type de détermination et la technique de collecte.
        assertEquals(50, HabitatOccHab.valeurInitiale(null, HabitatOccHab.TYPE_DETERMINATION.type, nouveau = true, config))
        assertEquals(60, HabitatOccHab.valeurInitiale(null, HabitatOccHab.TECHNIQUE.type, nouveau = true, config))
        assertNull(HabitatOccHab.valeurInitiale(null, HabitatOccHab.INTERET.type, nouveau = true, config))
        assertNull(HabitatOccHab.valeurInitiale(null, HabitatOccHab.ABONDANCE.type, nouveau = true, config))
        // Une date masquée n'a pas de date du jour.
        config.occhabFormConfigJson = """{"date_max": false}"""
        assertNull(detailsSessionParDefaut(config).dateMax)
    }

    @Test
    fun une_valeur_absente_de_la_liste_en_cache_est_gardee_tant_qu_on_n_y_touche_pas() {
        // Une liste avec l'entrée vide du web (intérêt communautaire, abondance), libellés label_fr.
        val liste = ListeNomenclatureOccHab(listOf(NomValeur(31, "Nord", labelFr = "Nord (fr)"), NomValeur(32, "Sud")), avecEntreeVide = true)
        assertEquals(listOf("", "Nord (fr)", "Sud"), liste.libelles)
        // 99 n'est pas dans la liste en cache : affichée vide, elle reste.
        assertEquals(0, liste.position(99))
        assertEquals(99, liste.retenue(99, 0))
        // Choisie par l'utilisateur : la valeur de la liste, ou vide.
        assertEquals(32, liste.retenue(99, 2))
        assertNull(liste.retenue(31, 0), "entrée vide choisie : vidée")
        assertEquals(31, liste.retenue(31, liste.position(31)))
        // Sans entrée vide (les autres listes du web) : rien d'affiché pour une valeur vide.
        val sans = ListeNomenclatureOccHab(listOf(NomValeur(31, "Nord"), NomValeur(32, "Sud")))
        assertEquals(listOf("Nord", "Sud"), sans.libelles)
        assertEquals(-1, sans.position(null))
        assertNull(sans.retenue(null, -1))
        assertEquals(32, sans.retenue(null, 1))
    }

    // ─── Habitat ───

    @Test
    fun valider_l_habitat_requis_et_autre_preciser() {
        val techniques = listOf(NomValeur(1, "In situ", cd = "1"), NomValeur(10, "Autre, préciser", cd = "10"))
        assertTrue(HabitatOccHab.techniqueEstAutre(10, techniques))
        assertFalse(HabitatOccHab.techniqueEstAutre(1, techniques))
        assertFalse(HabitatOccHab.techniqueEstAutre(10, listOf(NomValeur(10, "Autre, préciser"))), "cd inconnu : règle inactive")
        assertFalse(HabitatOccHab.peutValider(null, technique = 1, precisionRequise = false, precision = null))
        // Technique requise, affichée ou masquée (masquée, elle garde son défaut ; sans lui, bloquée).
        assertFalse(HabitatOccHab.peutValider(7, technique = null, precisionRequise = false, precision = null))
        assertFalse(HabitatOccHab.peutValider(7, technique = 10, precisionRequise = true, precision = "  "))
        assertFalse(HabitatOccHab.peutValider(7, technique = 10, precisionRequise = true, precision = null), "précision masquée : bloquée")
        assertTrue(HabitatOccHab.peutValider(7, technique = 10, precisionRequise = true, precision = "drone"))
        assertEquals("Choisissez une technique de collecte", HabitatOccHab.manque(null, null, false, null))
        assertEquals("Choisissez un habitat dans la liste HABREF", HabitatOccHab.manque(0, 1, false, null))
        assertEquals("Veuillez fournir des précisions concernant la technique de collecte", HabitatOccHab.manque(7, 10, true, ""))
    }

    @Test
    fun habitat_retenu_nom_cite_conserve_et_champs_masques_preserves() {
        val existant = OccHabHabitat(cdHab = 7, habitatLabel = "Pelouses", nomCite = "Pelouse sèche (web)", determiner = "Moi", recouvrement = 40.0)
        fun s(cd: Int, determinateur: String?, recouvrement: String?, choisi: Boolean = true) =
            SaisiesHabitat(cd, " Pelouses ", determinateur, null, recouvrement, null, null, 1, null, choisiDansLaListe = choisi)
        val intact = HabitatOccHab.habitat(existant, s(7, determinateur = "Moi", recouvrement = "40", choisi = false))
        assertEquals("Pelouse sèche (web)", intact.nomCite, "habitat non rechoisi : nom cité conservé")
        assertEquals("Pelouses", intact.habitatLabel)
        val meme = HabitatOccHab.habitat(existant, s(7, determinateur = "", recouvrement = "12,5"))
        assertEquals("Pelouses", meme.nomCite, "rechoisi dans la liste : le nom cité devient son libellé, comme le web")
        assertNull(meme.determiner, "champ visible vidé : effacé")
        assertEquals(12.5, meme.recouvrement, "virgule décimale acceptée")
        val change = HabitatOccHab.habitat(existant, s(8, determinateur = null, recouvrement = null))
        assertEquals("Pelouses", change.nomCite, "habitat changé : libellé HABREF")
        assertEquals("Moi", change.determiner, "champ masqué : préservé")
        assertEquals(40.0, change.recouvrement)
        assertEquals("12", HabitatOccHab.texteRecouvrement(12.0))
        assertEquals("12.5", HabitatOccHab.texteRecouvrement(12.5))
    }

    // ─── Section ANA-EVAL ───

    @Test
    fun section_ana_eval_fusion_non_destructive() {
        val bloc = """{"enjeu":"fort","statut":"brouillon","echelle":5000,"determination":{"nom":"Prairie humide","ancre":"EUNIS"}}"""
        val e = EditionAnaEval.station(bloc)!!
        val enjeu = AnaEval.CHAMPS_STATION.first { it.cle == "enjeu" }
        val echelle = AnaEval.CHAMPS_STATION.first { it.cle == "echelle" }
        assertEquals(2, e.positionInitiale(enjeu), "« Fort », après « Non renseigné » et « Très fort »")
        assertEquals("5000", e.texteInitial(echelle))
        assertEquals("Prairie humide — ancre EUNIS", e.determination())
        assertEquals("Échelle de numérisation : entre 1 et 1000000", e.erreurEntier(echelle, "0"))
        assertEquals("Échelle de numérisation : nombre entier attendu", e.erreurEntier(echelle, "1/25000"))
        assertNull(e.erreurEntier(echelle, ""))
        // Enjeu vidé, échelle changée ; « statut » et « determination » traversent tels quels.
        val json = e.fusionner(mapOf("enjeu" to 0, "statut" to 2), mapOf("echelle" to "25000"), emptyList())!!
        val relu = AnaEval.depuisJson(json)
        assertNull(relu["enjeu"])
        assertEquals("valide", relu["statut"])
        assertEquals(25000, (relu["echelle"] as Number).toInt())
        assertTrue(relu.containsKey("determination"))
        assertNull(EditionAnaEval.station(null), "station sans bloc : pas de section")
        val h = EditionAnaEval.habitat("""{"pee":["Buddleja davidii"]}""")!!
        assertEquals(listOf("Buddleja davidii", "", ""), h.peeInitiaux())
        assertNull(h.fusionner(emptyMap(), emptyMap(), listOf("", " ", "")), "plus rien : le bloc disparaît")
    }

    // ─── Station, Mes stations ───

    @Test
    fun ecran_de_la_station() {
        // Défaut n° 16 de l'inventaire corrigé : « Nouvelle station » pour une géométrie dessinée de
        // zéro, jusqu'à la station suivante ; « Modifier la station » pour une station reprise.
        val session = SessionOccHab().apply { demarrerSession(OccHabDetailsSession()) }
        assertEquals("Modifier la station", StationOccHab.titre(session.stationNouvelle))
        session.marquerStationNouvelle()
        assertEquals("Nouvelle station", StationOccHab.titre(session.stationNouvelle))
        session.nouvelleStation()
        assertFalse(session.stationNouvelle, "la station suivante n'est pas encore dessinée")
        session.marquerStationNouvelle()
        session.reprendreStation(OccHabStation(idStationServeur = 4))
        assertFalse(session.stationNouvelle, "une station reprise se modifie")
        session.marquerStationNouvelle()
        session.reprendreSaisie(OccHabSaisie(stations = listOf(OccHabStation())))
        assertFalse(session.stationNouvelle)
        assertEquals("Géométrie : point (42,93000, 1,40000)", StationOccHab.resumeGeometrie(OccHabStation(latitude = 42.93, longitude = 1.4)))
        assertEquals(
            "Géométrie : polygone (3 sommets, 2 trous conservés)",
            StationOccHab.resumeGeometrie(
                OccHabStation(
                    geometryType = "Polygon",
                    geometryCoordsJson = "[[0,0],[1,0],[1,1]]",
                    geometryTrousJson = "[[[0.1,0.1],[0.2,0.1],[0.2,0.2]],[[0.5,0.5],[0.6,0.5],[0.6,0.6]]]",
                ),
            ),
        )
        assertEquals("Jeu de données : (à choisir)", StationOccHab.jeuDeDonnees(OccHabDetailsSession(), emptyList()))
        assertEquals("Jeu de données : Prairies (12)", StationOccHab.jeuDeDonnees(OccHabDetailsSession(idDataset = 12), listOf(12 to "Prairies (12)")))
        assertEquals("Habitat 7", StationOccHab.libelleHabitat(OccHabHabitat(cdHab = 7)))
    }

    @Test
    fun mes_stations_onglets_envoi_et_suppression() {
        val ancienne = OccHabSaisie(id = "a", date = 1_000L, stations = listOf(OccHabStation()))
        val recente = OccHabSaisie(id = "b", date = 2_000L, stations = listOf(OccHabStation(), OccHabStation()))
        val vide = OccHabSaisie(id = "c", date = 3_000L)
        val envoyee = OccHabSaisie(id = "d", date = 4_000L, stations = listOf(OccHabStation()), envoyeGeoNature = true)
        val toutes = listOf(ancienne, recente, vide, envoyee)
        assertEquals(listOf("c", "b", "a"), MesStations.filtrer(toutes, OngletMesStations.A_ENVOYER).map { it.id })
        assertEquals("À envoyer (3)", MesStations.titreOnglet(toutes, OngletMesStations.A_ENVOYER))
        assertEquals("Envoyées (1)", MesStations.titreOnglet(toutes, OngletMesStations.ENVOYEES))
        assertEquals(listOf("a", "b"), MesStations.envoyables(toutes).map { it.id }, "au moins une station")
        assertFalse(MesStations.peutEnvoyer(vide, envoiAutorise = true))
        assertFalse(MesStations.peutEnvoyer(ancienne, envoiAutorise = false), "sans droit de création")
        assertFalse(MesStations.peutEditer(envoyee))
        assertEquals("2 stations", MesStations.nbStations(recente))
        assertTrue(MesStations.messageSuppression(ancienne).endsWith("(1 station) ? Cette action est définitive."))
    }
}
