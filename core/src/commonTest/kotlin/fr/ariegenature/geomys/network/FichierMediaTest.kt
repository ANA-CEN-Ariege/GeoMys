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

// Propre au portage : sous iOS, le dossier de l'appli change de chemin à chaque mise à jour.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.FileSystem
import okio.SYSTEM

/** [GeoNatureUpload.fichierMedia] : un média retrouvé par son nom quand son chemin a changé. */
class FichierMediaTest {

    private val systeme = FileSystem.SYSTEM
    private val racine = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val medias = racine / "conteneur-B" / "medias"
    private val ancienDossier = racine / "conteneur-A" / "medias"

    @AfterTest
    fun nettoyer() = systeme.deleteRecursively(racine)

    @Test
    fun un_chemin_d_un_ancien_conteneur_retombe_sur_le_dossier_des_medias() {
        systeme.createDirectories(medias)
        systeme.write(medias / "photo_1.jpg") { writeUtf8("jpeg") }
        val ancien = (ancienDossier / "photo_1.jpg").toString()
        assertEquals(medias / "photo_1.jpg", GeoNatureUpload.fichierMedia(systeme, ancien, medias))
        // Le chemin enregistré prime s'il existe toujours.
        systeme.createDirectories(ancienDossier)
        systeme.write(ancienDossier / "photo_1.jpg") { writeUtf8("jpeg") }
        assertEquals(ancienDossier / "photo_1.jpg", GeoNatureUpload.fichierMedia(systeme, ancien, medias))
        // Introuvable des deux côtés : le chemin enregistré, que l'envoi signalera.
        val absent = racine / "ailleurs" / "absent.jpg"
        assertEquals(absent, GeoNatureUpload.fichierMedia(systeme, absent.toString(), medias))
    }
}
