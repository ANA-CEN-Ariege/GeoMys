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

// Propre au portage : la saisie multi-taxons simplifiée (14 novembre 2026) répartit autrement des champs
// des écrans Dénombrement et Caractérisation ; l'observation doit être la même qu'en mode pro.

package fr.ariegenature.geomys.occtax

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.PreferencesGeoMys
import fr.ariegenature.geomys.store.TaxrefRestriction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaisieSimplifieeTest {

    private val t = ServicesDeTest()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Une espèce déjà renseignée en mode pro : deux dénombrements, une caractérisation, un média. */
    private val espece = EspeceReleve(
        Taxon.OISEAU, "Merle noir", 4117,
        nombre = 2, nombreMax = 5, sexe = "s1", stadeVie = "v1", objDenbr = "o1", typDenbr = "t1",
        denombrementsAdditionnels = listOf(Denombrement(id = "d2", nombreMin = 7, nombreMax = 7, sexe = "s2")),
        techniqueObs = "m1", statutBio = "b1", etaBio = "e1", comportement = "c1", naturalite = "n1", statutObs = "so1",
        determinateur = "Alice", notes = "note",
        mediaUrisCounting0 = listOf("file:///photo1.jpg"),
        additionalFieldsOccurrence = mapOf("a" to "1"),
        additionalFieldsCounting0 = mapOf("b" to "2"),
        champsOccExtra = mapOf("blurring" to "7"),
    )

    @Test
    fun details_ecrit_nombre_stade_sexe_et_etat_biologique_et_rien_d_autre() {
        val apres = SaisieSimplifiee.appliquerDetails(
            espece, "3", mapOf("STADE_VIE" to "v9", "SEXE" to "s9", "ETA_BIO" to "e9"),
        )
        assertEquals(3, apres.nombre)
        assertNull(apres.nombreMax, "nombre changé : minimum et maximum valent le nombre")
        assertEquals("v9", apres.stadeVie)
        assertEquals("s9", apres.sexe)
        assertEquals("e9", apres.etaBio)
        // Le reste de l'espèce ne bouge pas.
        assertEquals(espece.copy(nombre = 3, nombreMax = null, stadeVie = "v9", sexe = "s9", etaBio = "e9"), apres)
    }

    @Test
    fun un_nombre_inchange_garde_le_maximum_saisi_en_mode_pro() {
        val apres = SaisieSimplifiee.appliquerDetails(espece, "2", mapOf("SEXE" to "s9"))
        assertEquals(2, apres.nombre)
        assertEquals(5, apres.nombreMax)
        assertEquals("v1", apres.stadeVie, "champ masqué par le serveur : sa valeur reste")
        assertEquals("e1", apres.etaBio)
    }

    @Test
    fun les_champs_additionnels_obligatoires_s_ajoutent_a_ceux_deja_portes() {
        val apres = SaisieSimplifiee.appliquerDetails(espece, "2", emptyMap(), mapOf("requis_occ" to "x"), mapOf("requis_cpt" to "y"))
        assertEquals(mapOf("a" to "1", "requis_occ" to "x"), apres.additionalFieldsOccurrence)
        assertEquals(mapOf("b" to "2", "requis_cpt" to "y"), apres.additionalFieldsCounting0)
        assertEquals(espece.champsOccExtra, apres.champsOccExtra)
    }

    /** Détails, puis Comportement, puis une photo : l'observation enregistrée est celle qu'auraient
     *  donnée les écrans Dénombrement et Caractérisation. */
    @Test
    fun l_observation_est_reconstituee_comme_en_mode_pro() {
        val neuve = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117, determinateur = "Alice")
        var e = SaisieSimplifiee.appliquerDetails(neuve, "4", mapOf("STADE_VIE" to "2", "SEXE" to "3", "ETA_BIO" to "2"))
        // « Comportement » : la caractérisation réduite au comportement.
        e = FormulairesEspece.appliquerCaracterisation(e, mapOf("OCC_COMPORTEMENT" to "12"), null, null, emptyMap(), null)
        e = SaisieSimplifiee.ajouterMedias(e, listOf("file:///chant.m4a"))

        val obs = e.surObservation(Observation(id = "o", espece = "Merle noir", latitude = 0.0, longitude = 0.0))
        assertEquals(4, obs.nombre)
        assertNull(obs.nombreMax)
        assertEquals("2", obs.stadeVie)
        assertEquals("3", obs.sexe)
        assertEquals("2", obs.etaBio)
        assertEquals("12", obs.comportement)
        assertEquals(listOf("file:///chant.m4a"), obs.mediaUrisCounting0)
        // Jamais saisis : vides, le serveur y met ses valeurs par défaut, comme pour une espèce dont on
        // n'a pas ouvert la caractérisation.
        assertNull(obs.techniqueObs)
        assertNull(obs.statutBio)
        assertNull(obs.objDenbr)
        assertNull(obs.typDenbr)
        assertEquals(emptyList(), obs.denombrementsAdditionnels)
    }

    @Test
    fun le_commentaire_va_a_l_occurrence_et_rien_d_autre() {
        val apres = SaisieSimplifiee.appliquerCommentaire(espece, "  Chante au lever du jour \n")
        assertEquals(espece.copy(notes = "Chante au lever du jour"), apres)
        val obs = apres.surObservation(Observation(id = "o", espece = "Merle noir", latitude = 0.0, longitude = 0.0))
        assertEquals("Chante au lever du jour", obs.notes, "le `comment` de l'occurrence à l'envoi")
        assertEquals("", SaisieSimplifiee.appliquerCommentaire(espece, "   ").notes)
    }

    /** 14 novembre 2026 : « le champ sexe n'existe pas pour la flore ». */
    @Test
    fun les_champs_suivent_le_groupe_taxonomique() {
        val nomenclatures = t.services.nomenclatures
        val animaux = listOf(TaxrefRestriction("Animalia", "all"))
        val plantes = listOf(TaxrefRestriction("Plantae", "all"))
        nomenclatures.setAll(
            mapOf(
                "SEXE" to listOf(NomValeur(3, "Mâle", animaux)),
                "STADE_VIE" to listOf(NomValeur(2, "Adulte", animaux), NomValeur(20, "Floraison", plantes)),
                "ETA_BIO" to listOf(NomValeur(4, "Vivant", animaux + plantes)),
                "OCC_COMPORTEMENT" to listOf(NomValeur(12, "Chant", animaux)),
                // Une valeur pour les seuls animaux : rien pour une plante.
                "TYP_DENBR" to listOf(NomValeur(7, "Compté", animaux)),
            ),
        )
        val plante = GroupeTaxonomique("Plantae", "Angiospermes", null)
        val insecte = GroupeTaxonomique("Animalia", "Insectes", null)
        fun pertinent(code: String, groupe: GroupeTaxonomique, filtres: Boolean = true) = SaisieSimplifiee.pertinent(
            FormulairesEspece.champ(OcctaxFieldsConfig.parCode.getValue(code), nomenclatures, groupe, filtres), groupe, nomenclatures,
        )
        for (code in listOf("SEXE", "STADE_VIE", "ETA_BIO", "OCC_COMPORTEMENT")) assertTrue(pertinent(code, insecte), code)
        assertFalse(pertinent("SEXE", plante))
        assertFalse(pertinent("OCC_COMPORTEMENT", plante))
        assertTrue(pertinent("STADE_VIE", plante), "le stade phénologique d'une plante")
        assertTrue(pertinent("ETA_BIO", plante))
        // Même si le serveur ne filtre pas les valeurs par taxon : une plante n'a pas de sexe.
        assertFalse(pertinent("SEXE", plante, filtres = false))
        // Aucune valeur proposée pour une plante : le champ n'a pas de sens pour elle.
        assertFalse(pertinent("TYP_DENBR", plante))
        // Rien en cache : le champ reste, pour dire de recharger les données.
        assertTrue(pertinent("OBJ_DENBR", plante))
    }

    @Test
    fun les_nombres_min_et_max_sont_des_entiers_requis() {
        assertEquals("Renseignez le nombre min", SaisieSimplifiee.refusEffectifs("", "3"))
        assertEquals("Renseignez le nombre max", SaisieSimplifiee.refusEffectifs("2", ""))
        assertEquals("Nombre entier attendu", SaisieSimplifiee.refusEffectifs("2,5", "3"))
        assertNull(SaisieSimplifiee.refusEffectifs("0", "2147483647"))
        assertEquals("Nombre trop grand", SaisieSimplifiee.refusEffectifs("99999999999", "99999999999"), "il était enregistré comme 1")
        assertEquals("Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum", SaisieSimplifiee.refusEffectifs("5", "3"))
        // Un seul des deux nombres montré par le serveur : il se contrôle seul.
        assertNull(SaisieSimplifiee.refusEffectifs("4", null))
        assertEquals("Renseignez le nombre min", SaisieSimplifiee.refusEffectifs("", null))
        assertEquals("Renseignez le nombre max", SaisieSimplifiee.refusEffectifs(null, ""))
        assertNull(SaisieSimplifiee.refusEffectifs(null, null))
    }

    /** 16 novembre 2026 : la fiche porte le nombre min et le nombre max, et le comportement. */
    @Test
    fun la_fiche_ecrit_le_minimum_le_maximum_et_le_comportement() {
        val apres = SaisieSimplifiee.appliquerDetails(espece, "3", mapOf("OCC_COMPORTEMENT" to "c9"), max = "8")
        assertEquals(3, apres.nombre)
        assertEquals(8, apres.nombreMax)
        assertEquals("c9", apres.comportement)
        // Un maximum sous le minimum est relevé au minimum, comme à l'écran Dénombrement.
        assertNull(SaisieSimplifiee.appliquerDetails(espece, "6", emptyMap(), max = "4").nombreMax, "max = min")
    }

    @Test
    fun photo_et_audio_vont_au_premier_denombrement() {
        val avec = SaisieSimplifiee.ajouterMedias(espece, listOf("file:///photo1.jpg", "file:///chant.m4a"))
        assertEquals(listOf("file:///photo1.jpg", "file:///chant.m4a"), avec.mediaUrisCounting0, "pas de doublon")
        assertEquals(listOf("file:///chant.m4a"), SaisieSimplifiee.retirerMedia(avec, "file:///photo1.jpg").mediaUrisCounting0)
        assertEquals(espece.denombrementsAdditionnels, avec.denombrementsAdditionnels)
    }

    @Test
    fun le_mode_pro_par_defaut_et_le_mode_choisi_retenu() {
        val reglages = MapSettings()
        assertEquals(ModeSaisie.PRO, PreferencesGeoMys(reglages).modeSaisie, "rien de choisi : rien ne change")
        PreferencesGeoMys(reglages).modeSaisie = ModeSaisie.SIMPLIFIE
        assertEquals(ModeSaisie.SIMPLIFIE, PreferencesGeoMys(reglages).modeSaisie)
        reglages.putString("mode_saisie", "INCONNU")
        assertEquals(ModeSaisie.PRO, PreferencesGeoMys(reglages).modeSaisie)
    }

    /** 20 novembre 2026 : au démarrage, une nouvelle installation passe en mode simplifié ; une appli
     *  qui a déjà un serveur (mise à jour, ou GeoMys-Android relu sur place) garde le mode pro ; un
     *  mode enregistré ne bouge plus. */
    @Test
    fun au_demarrage_une_nouvelle_installation_passe_en_mode_simplifie() {
        t.services.fixerModeSaisieInitial()
        assertEquals(ModeSaisie.SIMPLIFIE, t.services.preferences.modeSaisie, "nouvelle installation")
        t.services.config.urlServeur = "https://geonature.exemple.org"
        t.redemarrer()
        t.services.fixerModeSaisieInitial()
        assertEquals(ModeSaisie.SIMPLIFIE, t.services.preferences.modeSaisie, "enregistré une fois pour toutes")

        val miseAJour = ServicesDeTest()
        try {
            miseAJour.services.config.urlServeur = "https://geonature.exemple.org"
            miseAJour.redemarrer()
            miseAJour.services.fixerModeSaisieInitial()
            assertEquals(ModeSaisie.PRO, miseAJour.services.preferences.modeSaisie, "une mise à jour ne change rien")
        } finally {
            miseAJour.nettoyer()
        }

        val choisi = MapSettings().apply { putString("mode_saisie", "PRO") }
        PreferencesGeoMys(choisi).fixerModeInitial(dejaUtilisee = false)
        assertEquals(ModeSaisie.PRO, PreferencesGeoMys(choisi).modeSaisie, "un mode choisi reste")
    }

    /** 15 novembre 2026 : le bouton « Chant » des oiseaux. */
    @Test
    fun le_chant_est_reconnu_par_son_code_et_bascule_le_comportement() {
        val valeurs = listOf(NomValeur(40, "Repos", cd = "17"), NomValeur(41, "Chanteur", cd = "18"), NomValeur(42, "Chant", cd = "99"))
        assertEquals(41, SaisieSimplifiee.valeurChant(valeurs)?.id, "le code 18, quel que soit le libellé")
        assertEquals(7, SaisieSimplifiee.valeurChant(listOf(NomValeur(6, "Repos"), NomValeur(7, "Chant")))?.id, "cache sans codes : le libellé")
        assertNull(SaisieSimplifiee.valeurChant(listOf(NomValeur(40, "Repos", cd = "17"))))
        assertTrue(SaisieSimplifiee.estOiseau(GroupeTaxonomique("Animalia", "Oiseaux", null)))
        assertFalse(SaisieSimplifiee.estOiseau(GroupeTaxonomique("Animalia", "Insectes", null)))

        val espece = EspeceReleve(taxon = Taxon.OISEAU, espece = "Merle noir", cdNom = null, comportement = "40", notes = "posé sur un fil")
        assertEquals("41", SaisieSimplifiee.comportementApresChant(espece.comportement, "41"), "un autre comportement est remplacé")
        assertEquals("", SaisieSimplifiee.comportementApresChant("41", "41"))
        assertEquals("40", SaisieSimplifiee.comportementApresChant("41", "41", sansChant = "40"), "le repli : le défaut du serveur")

        // Codes atlas : l'indice « Mâle chanteur » en période de nidification seulement.
        val atlas = valeurs + NomValeur(50, "2 - Mâle chanteur", cd = "CA2")
        assertEquals(50, SaisieSimplifiee.valeurMaleChanteur(atlas)?.id)
        assertNull(SaisieSimplifiee.valeurMaleChanteur(valeurs))
        assertEquals("50", SaisieSimplifiee.idChantAPoser("41", "50", nicheur = true))
        assertEquals("41", SaisieSimplifiee.idChantAPoser("41", "50", nicheur = false))
        assertEquals("41", SaisieSimplifiee.idChantAPoser("41", null, nicheur = true))
        assertNull(SaisieSimplifiee.idChantAPoser(null, "50", nicheur = false))
        // L'indice posé au printemps s'enlève même si le bouton poserait aujourd'hui le chant simple.
        assertEquals("", SaisieSimplifiee.comportementApresChant("50", "41", setOf("41", "50")))
    }
}
