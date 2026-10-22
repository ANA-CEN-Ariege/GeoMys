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

// Propre au portage : ces règles vivaient dans FicheObjetFragment (GeoMys-Android @ cdd5196), sans test. Les
// attendus sont ceux de son code.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.util.FormatDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** [FicheObjet] : les enfants d'une fiche et ce qu'ouvrent leurs boutons. */
class FicheObjetTest {

    private val site = MonitoringSchemaObjet(
        type = "site", label = "Site", labelList = "Sites", nameField = "base_site_name", parentType = "module",
        childrenTypes = listOf("visit"), geometryType = "Point",
    )
    private val visite = MonitoringSchemaObjet(
        type = "visit", label = "Visite", labelList = "Visites", nameField = null, parentType = "site",
        childrenTypes = listOf("observation"),
        properties = mapOf("visit_date_min" to MonitoringPropertySchema("visit_date_min", "date", "Date", false)),
        displayList = listOf("visit_date_min"),
    )
    private val schema = mapOf("site" to site, "visit" to visite)
    private val filSite = filRacineSuivis("STOM") + FilSegment("site", 501, "Col de Port")

    private fun objetSite(visites: List<MonitoringEnfant>?) = MonitoringObjet(
        type = "site", id = 501, moduleCode = "stom",
        proprietes = mapOf("base_site_name" to "Col de Port"),
        enfants = visites?.let { mapOf("visit" to it) } ?: emptyMap(),
        geometrie = null, geometrieGeoJson = null,
    )

    private fun saisie(uuid: String, date: Long, etat: SaisieEnAttente.Etat = SaisieEnAttente.Etat.PENDING, type: String = "visit") =
        SaisieEnAttente(
            uuid = uuid, moduleCode = "stom", objectType = type, parentObjectType = "site", parentIdServeur = 501,
            valeursJson = "{}", dateLocale = date, etat = etat,
        )

    private fun elements(objet: MonitoringObjet, saisies: List<SaisieEnAttente>) = FicheObjet.elements(
        objet, schema, LabelResolver(), saisies, filSite,
        labelTypeEnCache = { _, type -> if (type == "visit") "Visite" else null },
        libelleNouveau = { _, type -> "Nouvelle $type" },
    )

    @Test
    fun en_tete_puis_saisies_locales_puis_enfants_du_serveur() {
        val objet = objetSite(
            listOf(
                MonitoringEnfant(9002, "#9002", mapOf("visit_date_min" to "2025-06-18")),
                MonitoringEnfant(9001, "#9001", mapOf("visit_date_min" to "2026-06-12")),
            ),
        )
        val ancienne = saisie("a", 1_000L)
        val recente = saisie("b", 2_000L)
        val envoyee = saisie("c", 3_000L, SaisieEnAttente.Etat.SENT)
        val els = elements(objet, listOf(ancienne, recente, envoyee))

        val entete = els[0] as ElementFiche.Entete
        assertEquals("Visites (4)", entete.texte, "deux du serveur, deux locales ; l'envoyée ne compte pas")
        // Le « + » de l'en-tête crée une visite sœur, sous le site courant.
        assertEquals(NouvelleSaisie("site", 501, null, "Col de Port", "visit", "Nouvelle visit", filSite), entete.nouvelle)
        assertEquals(listOf("b", "a"), els.filterIsInstance<ElementFiche.Locale>().map { it.uuid }, "les plus récentes d'abord")
        val serveur = els.filterIsInstance<ElementFiche.Serveur>()
        assertEquals(listOf(9001, 9002), serveur.map { it.id }, "chronologie décroissante")
        assertEquals("12/06/2026", serveur[0].sousTitre)
        assertEquals(filSite + FilSegment("visit", 9001, "#9001"), serveur[0].fil)
        assertNull(serveur[0].nouvelle, "une visite historisée ne reçoit pas de sous-saisie")
        assertEquals(false, serveur[0].carte)
    }

    @Test
    fun une_saisie_locale_dit_son_etat_et_enchaine_par_son_uuid() {
        val erreur = saisie("e", 5_000L, SaisieEnAttente.Etat.ERROR)
        val locale = elements(objetSite(emptyList()), listOf(erreur)).filterIsInstance<ElementFiche.Locale>().single()
        val date = FormatDate.jourMoisHeure(5_000L)
        assertEquals("⚠ Visite (local)", locale.titre)
        assertEquals("$date · à envoyer", locale.sousTitre)
        assertEquals(
            NouvelleSaisie("visit", parentUuidLocal = "e", titreSite = "Visite (local) du $date", childObjectType = "observation",
                libelle = "Nouvelle observation", fil = filSite),
            locale.nouvelle,
        )
    }

    @Test
    fun un_type_de_saisie_sans_occurrence_garde_son_en_tete_et_son_plus() {
        // Le serveur déclare le type avec une liste vide, ou ne le déclare pas du tout.
        for (objet in listOf(objetSite(emptyList()), objetSite(null))) {
            val els = elements(objet, emptyList())
            val entete = els.single() as ElementFiche.Entete
            assertEquals("Visites (0)", entete.texte)
            assertEquals("visit", entete.nouvelle?.childObjectType)
        }
    }

    /** Propre au portage : une fiche reconstituée depuis son parent (visite passée, absente du cache)
     *  ne dit pas « (0) » : ses enfants du serveur ne sont pas connus. */
    @Test
    fun une_fiche_reconstituee_ne_compte_pas_ses_enfants() {
        val partielle = objetSite(null).copy(enfantsConnus = false)
        assertEquals(listOf("Visites"), elements(partielle, emptyList()).filterIsInstance<ElementFiche.Entete>().map { it.texte })
        assertEquals(listOf("Visites"), elements(partielle, listOf(saisie("p", 1L))).filterIsInstance<ElementFiche.Entete>().map { it.texte })
    }

    @Test
    fun les_saisies_locales_d_un_type_que_le_serveur_ignore_apparaissent() {
        val els = elements(objetSite(null), listOf(saisie("p", 1L)))
        assertEquals(listOf("Visites (1)"), els.filterIsInstance<ElementFiche.Entete>().map { it.texte })
        assertEquals(listOf("p"), els.filterIsInstance<ElementFiche.Locale>().map { it.uuid })
    }

    @Test
    fun la_signature_change_quand_une_saisie_part() {
        val objet = objetSite(emptyList())
        val avant = FicheObjet.signatureOutbox(objet, listOf(saisie("a", 1L)))
        val apres = FicheObjet.signatureOutbox(objet, listOf(saisie("a", 1L, SaisieEnAttente.Etat.SENT)))
        assertNotEquals(avant, apres)
        assertEquals("", FicheObjet.signatureOutbox(objet, listOf(saisie("a", 1L).copy(parentIdServeur = 7))))
    }
}
