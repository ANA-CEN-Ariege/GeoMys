/*
 * GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
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
        assertEquals(listOf(12 to "Prairies (12)", 15 to "Tourbières (15)"), DetailsStation.datasets(config))
        config.datasetsCreablesOcchab = setOf(15)
        assertEquals(listOf(15 to "Tourbières (15)"), DetailsStation.datasets(config))
    }

    @Test
    fun champs_requis_dans_l_ordre_du_web() {
        val c = config()
        assertEquals("Choisissez un jeu de données", DetailsStation.champManquant(saisies(idDataset = null), c))
        assertEquals("Choisissez au moins un observateur", DetailsStation.champManquant(saisies(observateurs = emptyList()), c))
        assertEquals("Choisissez la nature de l'objet géographique", DetailsStation.champManquant(saisies(nature = null), c))
        assertNull(DetailsStation.champManquant(saisies(natureVisible = false, nature = null), c), "champ masqué : pas bloquant")
        assertNull(DetailsStation.champManquant(saisies(), c))
        assertEquals("Altitude min supérieure à l'altitude max", DetailsStation.altitudesIncoherentes("900", " 800 "))
        assertNull(DetailsStation.altitudesIncoherentes("800", ""))
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
            saisies().copy(altitudeMin = "810", altitudeMax = null, surface = "", commentaire = "  revu  ", anaEval = { "{\"enjeu\":\"fort\"}" }),
            demarrage = false,
        )
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
        val config = config().apply {
            observateurDefautId = "3"
            observateurDefautNom = "DUPONT Alice"
        }
        val vierge = detailsSessionParDefaut(config, aujourdhui = 86_400_000L)
        assertEquals(listOf(3), vierge.observateursIds)
        assertEquals(listOf("DUPONT Alice"), vierge.observateursNoms)
        assertEquals(86_400_000L, vierge.dateMin)
        DetailsStation.memoriser(config, OccHabDetailsSession(idDataset = 12, comment = "propre au relevé", dateMin = 5L))
        val relu = detailsSessionParDefaut(config, aujourdhui = 172_800_000L)
        assertEquals(12, relu.idDataset)
        assertNull(relu.comment)
        assertEquals(172_800_000L, relu.dateMin)
        assertEquals(172_800_000L, relu.dateMax)
    }

    // ─── Habitat ───

    @Test
    fun valider_l_habitat_requis_et_autre_preciser() {
        val techniques = listOf(NomValeur(1, "In situ", cd = "1"), NomValeur(10, "Autre, préciser", cd = "10"))
        assertTrue(HabitatOccHab.techniqueEstAutre(10, techniques))
        assertFalse(HabitatOccHab.techniqueEstAutre(1, techniques))
        assertFalse(HabitatOccHab.techniqueEstAutre(10, listOf(NomValeur(10, "Autre, préciser"))), "cd inconnu : règle inactive")
        assertFalse(HabitatOccHab.peutValider(null, techniqueVisible = false, technique = null, precisionRequise = false, precision = null))
        assertFalse(HabitatOccHab.peutValider(7, techniqueVisible = true, technique = null, precisionRequise = false, precision = null))
        assertFalse(HabitatOccHab.peutValider(7, techniqueVisible = true, technique = 10, precisionRequise = true, precision = "  "))
        assertTrue(HabitatOccHab.peutValider(7, techniqueVisible = true, technique = 10, precisionRequise = true, precision = "drone"))
        assertEquals("Choisissez une technique de collecte", HabitatOccHab.manque(null, true, null, false, null))
        assertEquals("Choisissez un habitat dans la liste HABREF", HabitatOccHab.manque(0, false, null, false, null))
        assertEquals("Précision technique *", HabitatOccHab.libellePrecision(true))
    }

    @Test
    fun habitat_retenu_nom_cite_conserve_et_champs_masques_preserves() {
        val existant = OccHabHabitat(cdHab = 7, habitatLabel = "Pelouses", nomCite = "Pelouse sèche (web)", determiner = "Moi", recouvrement = 40.0)
        fun s(cd: Int, determinateur: String?, recouvrement: String?) = SaisiesHabitat(cd, " Pelouses ", determinateur, null, recouvrement, null, null, 1, null)
        val meme = HabitatOccHab.habitat(existant, s(7, determinateur = "", recouvrement = "12,5"))
        assertEquals("Pelouse sèche (web)", meme.nomCite, "même habitat HABREF : nom cité conservé")
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
