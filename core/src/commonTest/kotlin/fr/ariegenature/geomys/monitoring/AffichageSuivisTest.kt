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

// Propre au portage : ces règles vivaient dans SuiviDetailFragment et FicheObjetFragment (GeoMys-Android
// @ cdd5196), sans test. Les attendus sont ceux de leur code.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringObjets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [AffichageSuivis] : libellés, types montrés, sous-titres, tris et propriétés d'une fiche. */
class AffichageSuivisTest {

    private fun prop(nom: String, widget: String, label: String = nom, typeUtil: String? = null) =
        MonitoringPropertySchema(nom = nom, typeWidget = widget, label = label, obligatoire = false, typeUtil = typeUtil)

    private val site = MonitoringSchemaObjet(
        type = "site", label = "Site", labelList = "Sites", nameField = "base_site_name", parentType = "module",
        childrenTypes = listOf("visit"),
        properties = mapOf(
            "base_site_name" to prop("base_site_name", "text", "Nom"),
            "observateur" to prop("observateur", "observers", "Observateur", typeUtil = "user"),
        ),
        displayProperties = listOf("base_site_name", "altitude", "observateur", "absent"),
        displayList = listOf("base_site_name", "date_pose", "observateur", "vide"),
    )
    private val visite = MonitoringSchemaObjet(
        type = "visit", label = "Visite", labelList = null, nameField = null, parentType = "site",
        childrenTypes = listOf("observation"),
        properties = mapOf(
            "visit_date_min" to prop("visit_date_min", "date"),
            "heure" to prop("heure", "time"),
        ),
    )
    private val schema = mapOf("site" to site, "visit" to visite)
    private val resolveur = LabelResolver(users = mapOf("11" to "DURAND Émile"))

    private fun enfant(id: Int, nom: String, vararg p: Pair<String, String>) = MonitoringEnfant(id, nom, mapOf(*p))

    @Test
    fun libelles_du_schema_puis_repli_generique() {
        assertEquals("Sites", AffichageSuivis.libelleListe("site", schema))
        assertEquals("Visites", AffichageSuivis.libelleListe("visit", schema), "sans label_list, le label et un « s », comme le web")
        assertEquals("Sites group", AffichageSuivis.libelleListe("sites_group", schema))
        assertEquals("Site", AffichageSuivis.libelleType("site", site), "la fiche prend le label, au singulier")
        assertEquals("Sites : 3  ·  Visites : 0", AffichageSuivis.resume(listOf("site", "visit"), schema, mapOf("site" to 3)))
    }

    @Test
    fun seuls_les_types_macro_sont_listes() {
        assertEquals(listOf("site"), AffichageSuivis.typesAfficherEnListe(listOf("site", "visit"), schema))
        // Aucun type rattaché au module : les types dont le parent est présent disparaissent.
        val plat = mapOf("visit" to visite, "observation" to visite.copy(type = "observation", parentType = "visit"))
        assertEquals(listOf("visit"), AffichageSuivis.typesAfficherEnListe(listOf("visit", "observation"), plat))
        assertEquals(listOf("a", "b"), AffichageSuivis.typesAfficherEnListe(listOf("a", "b"), null), "sans schéma, tout")
    }

    @Test
    fun chaque_type_enfant_du_module_a_sa_liste_comme_le_web() {
        // STOM et les RHOMEO : tree.module porte les groupes de sites ET les sites, dont le premier
        // parent est le groupe ; le web montre les deux listes (sites-groups.resolver.ts).
        val module = MonitoringSchemaObjet(
            type = "module", label = "Module", labelList = null, nameField = null, parentType = null,
            childrenTypes = listOf("sites_group", "site"),
        )
        val groupe = site.copy(type = "sites_group", label = "Groupe de sites", labelList = "Groupes de sites")
        val aGroupes = mapOf("module" to module, "sites_group" to groupe, "site" to site.copy(parentType = "sites_group"))
        assertEquals(listOf("sites_group", "site"), AffichageSuivis.typesAfficherEnListe(listOf("sites_group", "site"), aGroupes))
        // POPAmphibien : seuls les groupes au niveau du module, les sites dans leur fiche.
        val groupesSeuls = aGroupes + ("module" to module.copy(childrenTypes = listOf("sites_group")))
        assertEquals(listOf("sites_group"), AffichageSuivis.typesAfficherEnListe(listOf("sites_group", "site"), groupesSeuls))
    }

    @Test
    fun le_sous_titre_suit_display_list_sans_le_nom() {
        val e = enfant(5, "Col de Port", "base_site_name" to "Col de Port", "date_pose" to "2026-06-12T08:00:00", "observateur" to "11", "vide" to "null")
        assertEquals("12/06/2026 · DURAND Émile", AffichageSuivis.sousTitrePourEnfant(e, site, resolveur))
        assertEquals("", AffichageSuivis.sousTitrePourEnfant(e, visite, resolveur), "sans display_list, rien")
    }

    @Test
    fun les_valeurs_multiples_sont_resolues_et_jointes_comme_le_web() {
        // Les observateurs d'une visite : une liste d'id_role, que le web résout un à un et joint par « , ».
        val e = MonitoringEnfant(5, "Col de Port", mapOf("base_site_name" to "Col de Port"), valeursMultiples = mapOf("observateur" to listOf("11", "12")))
        val deux = resolveur.copy(users = resolveur.users + ("12" to "MARTIN Paul"))
        assertEquals("DURAND Émile, MARTIN Paul", AffichageSuivis.sousTitrePourEnfant(e, site, deux))
        val objet = MonitoringObjet(
            type = "site", id = 5, moduleCode = "m", proprietes = mapOf("base_site_name" to "Col de Port"),
            enfants = emptyMap(), geometrie = null, geometrieGeoJson = null, valeursMultiples = mapOf("observateur" to listOf("12", "99")),
        )
        assertEquals(listOf("Observateur" to "MARTIN Paul, 99"), AffichageSuivis.proprietesAffichees(objet, site, deux), "un id sans libellé reste tel quel")
    }

    @Test
    fun les_tableaux_du_serveur_sont_gardes_a_part() {
        val enfants = MonitoringObjets.enfantsDe(
            JSONObject(
                """{"children":{"visit":[{"id":9,"properties":{"visit_date_min":"2026-06-01",
                    "observers":[3,4],"medias":[{"id_media":1}],"vide":[],"modules":["a"]}}]}}""",
            ),
        )
        val v = enfants.getValue("visit").single()
        assertEquals(mapOf("observers" to listOf("3", "4"), "modules" to listOf("a")), v.valeursMultiples, "les tableaux d'objets et vides sont écartés")
        assertEquals(mapOf("visit_date_min" to "2026-06-01"), v.proprietes)
    }

    @Test
    fun le_detail_renomme_par_name_field_et_trie_par_nom() {
        val enfants = mapOf(
            "site" to listOf(
                enfant(2, "#2", "base_site_name" to "Étang de Lers"),
                enfant(1, "#1", "base_site_name" to "Col de Port"),
                enfant(3, "Plateau"),
            ),
        )
        val noms = AffichageSuivis.enfantsDuProtocole(enfants, schema).getValue("site").map { it.nom }
        assertEquals(listOf("Col de Port", "Étang de Lers", "Plateau"), noms, "sans `sorts`, par nom, à la française")
    }

    @Test
    fun le_detail_trie_sur_le_premier_critere_du_schema_comme_le_serveur() {
        // Décision du 21 octobre : « comme le web ». `last_visit desc` : un site jamais visité en tête
        // (NULLS FIRST de PostgreSQL), puis du plus récent au plus ancien.
        val trie = site.copy(sorts = listOf("last_visit" to "desc", "base_site_name" to "asc"))
        val enfants = mapOf(
            "site" to listOf(
                enfant(1, "#1", "base_site_name" to "Col de Port", "last_visit" to "2025-06-18"),
                enfant(2, "#2", "base_site_name" to "Étang de Lers"),
                enfant(3, "#3", "base_site_name" to "Plateau", "last_visit" to "2026-05-02"),
            ),
        )
        val ordre = { s: MonitoringSchemaObjet -> AffichageSuivis.enfantsDuProtocole(enfants, mapOf("site" to s)).getValue("site").map { it.id } }
        assertEquals(listOf(2, 3, 1), ordre(trie))
        // Croissant : l'absent en fin.
        assertEquals(listOf(1, 3, 2), ordre(site.copy(sorts = listOf("last_visit" to "asc"))))
        // Les nombres par valeur.
        val codes = listOf(enfant(1, "a", "code" to "10"), enfant(2, "b", "code" to "9"))
        assertEquals(listOf(2, 1), AffichageSuivis.trierCommeLeServeur(codes, "code" to "asc").map { it.id })
        // Propriété absente des lignes (hors display_list) : par nom.
        assertEquals(listOf(1, 2, 3), ordre(site.copy(sorts = listOf("nb_visits" to "desc"))))
    }

    @Test
    fun le_plus_d_une_ligne_cree_sa_saisie_sauf_sur_une_saisie_historique() {
        assertEquals("visit", AffichageSuivis.typeSaisieEnfantServeur("site", site))
        assertNull(AffichageSuivis.typeSaisieEnfantServeur("visit", visite), "une visite ancienne ne reçoit pas d'observation")
        assertEquals("observation", AffichageSuivis.premierTypeSaisie(visite))
    }

    @Test
    fun les_visites_sont_triees_de_la_plus_recente_a_la_plus_ancienne() {
        val items = listOf(
            enfant(1, "a", "visit_date_min" to "2025-06-18", "heure" to "06:40"),
            enfant(2, "b"),
            enfant(3, "c", "visit_date_min" to "2026-06-12", "heure" to "06:15"),
            enfant(4, "d", "visit_date_min" to "2026-06-12", "heure" to "09:00"),
        )
        assertEquals(listOf(4, 3, 1, 2), AffichageSuivis.trierChronologique(items, visite).map { it.id })
        // Sans champ date : tri par nom.
        assertEquals(listOf(3, 1), AffichageSuivis.trierChronologique(listOf(enfant(3, "a"), enfant(1, "b")), site).map { it.id })
    }

    @Test
    fun la_fiche_montre_display_properties_dans_l_ordre() {
        val objet = MonitoringObjet(
            type = "site", id = 5, moduleCode = "m",
            proprietes = mapOf("base_site_name" to "Col de Port", "observateur" to "11", "altitude" to "1249", "id_base_site" to "5"),
            enfants = emptyMap(), geometrie = null, geometrieGeoJson = null,
        )
        assertEquals("Col de Port", AffichageSuivis.nomObjet(objet, site))
        val visiteDatee = visite.copy(nameField = "visit_date_min")
        val uneVisite = objet.copy(type = "visit", proprietes = mapOf("visit_date_min" to "2026-06-12"))
        assertEquals("12/06/2026", AffichageSuivis.nomObjet(uneVisite, visiteDatee), "le titre d'une visite : sa date, comme le web")
        assertEquals(
            listOf("Altitude" to "1249", "Observateur" to "DURAND Émile"),
            AffichageSuivis.proprietesAffichees(objet, site, resolveur),
        )
        // Sans display_properties : tout, moins les champs techniques.
        assertEquals(
            listOf("Observateur" to "11", "Altitude" to "1249"),
            AffichageSuivis.proprietesAffichees(objet, site.copy(displayProperties = emptyList()), LabelResolver()),
        )
    }

    @Test
    fun valeurs_et_champs_mis_en_forme() {
        assertEquals("oui", AffichageSuivis.formatValeur("true"))
        assertEquals("12/06/2026", AffichageSuivis.formatValeur("2026-06-12T08:00:00"), "jj/mm/aaaa, comme le web")
        assertEquals("12/06/2026", AffichageSuivis.formatValeur("2026-06-12"))
        assertEquals("2024-01-01 transect", AffichageSuivis.formatValeur("2024-01-01 transect"), "un nom n'est pas une date")
        assertEquals("Date de visite", AffichageSuivis.labelChamp("visit_date_min"))
        assertEquals("Nb individus", AffichageSuivis.labelChamp("nb_individus"))
    }

    @Test
    fun les_proprietes_specifiques_suivent_l_ordre_des_noms() {
        // Le pipe keyvalue du web les range par nom ; une valeur absente est omise.
        val objet = MonitoringObjet(
            type = "site", id = 5, moduleCode = "m",
            proprietes = mapOf("hauteur" to "12", "essence" to "Chêne"),
            enfants = emptyMap(), geometrie = null, geometrieGeoJson = null,
        )
        val champs = linkedMapOf(
            "hauteur" to prop("hauteur", "number", "Hauteur (m)"),
            "essence" to prop("essence", "text", "Essence"),
            "cavites" to prop("cavites", "number", "Cavités"),
        )
        assertEquals(listOf("Essence" to "Chêne", "Hauteur (m)" to "12"), AffichageSuivis.proprietesSpecifiques(objet, champs, LabelResolver()))
    }
}
