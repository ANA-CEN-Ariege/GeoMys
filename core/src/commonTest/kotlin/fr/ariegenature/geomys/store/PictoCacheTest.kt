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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/PictoCacheTest.kt) :
// MockWebServer remplacé par ServeurSimule, TemporaryFolder et java.io par okio, l'objet par une
// instance. Les deux derniers cas de téléchargement sont propres au portage : le contrôle d'image par
// signature y rend testable le cas nominal, impossible sous Android (BitmapFactory du stub JVM). Le
// cache négatif d'un 404 et ses trois cas ont disparu le 20 octobre : seule la synchro télécharge les
// pictos, l'écran Suivis ne lit que le cache.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.network.ServeurSimule.Companion.octets
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Cache disque des pictogrammes de protocole monitoring ([PictoCache]) : résolution d'URL
 * ([PictoCache.urlPicto]), présence locale ([PictoCache.fichierLocal]), téléchargement sur un
 * serveur simulé ([PictoCache.telecharger]) et vidage ([PictoCache.vider]).
 */
class PictoCacheTest {

    private val systeme = FileSystem.SYSTEM
    private val racine = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val dossier = racine / "pictos"
    private val serveur = ServeurSimule()
    private val pictos = PictoCache(dossier, serveur.clients)

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(racine)
    }

    private fun ecrire(nom: String, contenu: ByteArray) {
        systeme.write(dossier / nom) { write(contenu) }
    }

    private fun fichiers(): List<Path> = systeme.list(dossier)

    // ------------------------------------------------------------------ urlPicto

    @Test
    fun url_picto_module_picto_url_absolue_renvoyee_telle_quelle() {
        assertEquals(
            "https://cdn.exemple.org/pictos/stom.png",
            pictos.urlPicto("https://srv.fr/geonature", "STOM", "https://cdn.exemple.org/pictos/stom.png"),
        )
        // Insensible à la casse du schéma.
        assertEquals(
            "HTTP://cdn/x.jpg",
            pictos.urlPicto("https://srv.fr", "STOM", "HTTP://cdn/x.jpg"),
        )
    }

    @Test
    fun url_picto_chemin_absolu_serveur_prefixe_par_la_base() {
        assertEquals(
            "https://srv.fr/media/pictos/a.png",
            pictos.urlPicto("https://srv.fr/", "STOM", "/media/pictos/a.png"),
        )
    }

    @Test
    fun url_picto_nom_de_fichier_image_relatif_prefixe_par_la_base() {
        assertEquals(
            "https://srv.fr/logo.WEBP", // extension insensible à la casse
            pictos.urlPicto("https://srv.fr", "STOM", "logo.WEBP"),
        )
    }

    @Test
    fun url_picto_code_fontawesome_ou_vide_retombe_sur_la_convention_img_jpg() {
        val attendu = "https://srv.fr/api/media/monitorings/STOM/img.jpg"
        // Défaut serveur fréquent : « fa-puzzle-piece » n'est PAS une image.
        assertEquals(attendu, pictos.urlPicto("https://srv.fr", "STOM", "fa-puzzle-piece"))
        assertEquals(attendu, pictos.urlPicto("https://srv.fr", "STOM", null))
        assertEquals(attendu, pictos.urlPicto("https://srv.fr", "STOM", "  "))
        // Base nettoyée (espaces + « / » final).
        assertEquals(attendu, pictos.urlPicto(" https://srv.fr/ ", "STOM", ""))
    }

    // ------------------------------------------------------------------ fichierLocal

    @Test
    fun fichier_local_absent_renvoie_null() {
        assertNull(pictos.fichierLocal("STOM"))
    }

    @Test
    fun fichier_local_present_et_non_vide_renvoie_le_fichier() {
        ecrire("STOM.jpg", byteArrayOf(1, 2, 3))
        assertEquals(dossier / "STOM.jpg", pictos.fichierLocal("STOM"))
    }

    @Test
    fun fichier_local_fichier_vide_traite_comme_absent() {
        ecrire("STOM.jpg", ByteArray(0))
        assertNull(pictos.fichierLocal("STOM"))
    }

    @Test
    fun fichier_local_slash_du_module_code_assaini_en_underscore() {
        // Un module_code contenant « / » ne doit pas sortir du dossier de cache.
        ecrire("A_B.jpg", byteArrayOf(1))
        assertEquals(dossier / "A_B.jpg", pictos.fichierLocal("A/B"))
    }

    // ------------------------------------------------------------------ telecharger

    @Test
    fun telecharger_404_renvoie_null_et_n_ecrit_rien() = runTest {
        // Pas d'image.
        serveur.repondre(404)
        assertNull(pictos.telecharger("STOM", "${serveur.base}/api/media/monitorings/STOM/img.jpg"))
        assertEquals(0, fichiers().size)
    }

    @Test
    fun telecharger_erreur_serveur_renvoie_null_et_n_ecrit_rien() = runTest {
        serveur.repondre(500)
        assertNull(pictos.telecharger("STOM", "${serveur.base}/img.jpg"))
        assertEquals(0, fichiers().size)
    }

    @Test
    fun telecharger_sans_token_ni_cookies_pas_d_en_tetes_d_auth() = runTest {
        serveur.repondre(404)
        pictos.telecharger("STOM", "${serveur.base}/img.jpg")
        val req = serveur.requetes.single()
        assertNull(req.headers["Authorization"])
        assertNull(req.headers["Cookie"])
    }

    @Test
    fun telecharger_token_et_cookies_fournis_poses_en_authorization_bearer_et_cookie() = runTest {
        // Session transmise SEULEMENT vers l'origine (hôte + schéma) de l'instance GeoNature
        // (audit sécurité 2026-08-27) : ici le picto est servi par le serveur lui-même.
        serveur.repondre(404)
        pictos.telecharger("STOM", "${serveur.base}/img.jpg", token = "tok123", cookies = "session=abc",
            base = "${serveur.base}/geonature")
        val req = serveur.requetes.single()
        assertEquals("Bearer tok123", req.headers["Authorization"])
        assertEquals("session=abc", req.headers["Cookie"])
    }

    @Test
    fun telecharger_picto_hors_de_l_origine_du_serveur_jamais_de_session() = runTest {
        // module_picto absolu vers un autre hôte (CDN, ancien serveur) : ni Bearer ni Cookie.
        serveur.repondre(404)
        pictos.telecharger("STOM", "${serveur.base}/img.jpg", token = "tok123", cookies = "session=abc",
            base = "https://autre-serveur.example.org/geonature")
        val req = serveur.requetes.single()
        assertNull(req.headers["Authorization"])
        assertNull(req.headers["Cookie"])
    }

    @Test
    fun telecharger_contenu_non_image_portail_captif_renvoie_null_et_n_ecrit_rien() = runTest {
        // Règle estImageValide : un 200 au contenu non décodable en image (portail captif,
        // proxy, page d'erreur) ne doit JAMAIS être persisté — il serait resservi hors-ligne
        // indéfiniment (audit 2026-08-23). Ni fichier final, ni .tmp orphelin.
        serveur.repondre(200, "<html><body>Connectez-vous au wifi de la gare</body></html>", "Content-Type" to "text/html")
        assertNull(pictos.telecharger("STOM", "${serveur.base}/img.jpg"))
        assertEquals(0, fichiers().size, "ni image ni .tmp ne doivent rester")
    }

    @Test
    fun telecharger_corps_vide_renvoie_null_et_n_ecrit_rien() = runTest {
        serveur.repondre(200, "")
        assertNull(pictos.telecharger("STOM", "${serveur.base}/img.jpg"))
        assertEquals(0, fichiers().size)
    }

    @Test
    fun telecharger_erreur_reseau_renvoie_null_sans_lever() = runTest {
        // Serveur fermé, connexion refusée.
        serveur.couperLeReseau()
        assertNull(pictos.telecharger("STOM", "${serveur.base}/img.jpg"))
        assertEquals(0, fichiers().size)
    }

    @Test
    fun telecharger_vraie_image_persistee() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        serveur.repondre(octets(jpeg, "image/jpeg"))
        assertEquals(dossier / "STOM.jpg", pictos.telecharger("STOM", "${serveur.base}/img.jpg"))
        assertTrue(jpeg.contentEquals(systeme.read(dossier / "STOM.jpg") { readByteArray() }))
        assertEquals(listOf(dossier / "STOM.jpg"), fichiers(), "aucun .tmp ne doit rester")
    }

    @Test
    fun telecharger_image_d_un_autre_format_reconnue_png() = runTest {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)
        serveur.repondre(octets(png, "image/png"))
        assertEquals(dossier / "STOM.jpg", pictos.telecharger("STOM", "${serveur.base}/img.png"))
    }

    // ------------------------------------------------------------------ vider

    @Test
    fun vider_supprime_tous_les_pictos_en_cache_et_les_marqueurs_negatifs() {
        ecrire("STOM.jpg", byteArrayOf(1, 2))
        ecrire("STERF.jpg", byteArrayOf(3))
        ecrire("STERF.absent", ByteArray(0))
        pictos.vider()
        assertEquals(0, fichiers().size)
        assertNull(pictos.fichierLocal("STOM"))
        assertNull(pictos.fichierLocal("STERF"))
    }

    @Test
    fun vider_sans_rien_en_cache_ne_leve_pas() {
        pictos.vider()
        assertTrue(systeme.metadata(dossier).isDirectory)
    }
}
