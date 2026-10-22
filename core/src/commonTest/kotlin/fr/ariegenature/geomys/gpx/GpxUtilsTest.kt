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

// Les cinq premiers cas sont portés de GeoMys-Android @ cdd5196
// (app/src/test/java/fr/ariegenature/geomys/GpxUtilsTest.kt), JUnit remplacé par kotlin.test. Les suivants,
// propres au portage, vérifient que la lecture XML commune lit et refuse ce que lisait et refusait le
// SAX d'Android.

package fr.ariegenature.geomys.gpx

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Export GPX + relecture. Garantit l'intégrité des données d'une sortie exportée puis réimportée
 *  (espèce, position, effectif, notes, parcours). */
class GpxUtilsTest {

    // Timestamp aligné sur la seconde (le format GPX tronque les millisecondes).
    private val t0 = 1_700_000_000_000L

    private fun obs(espece: String, lat: Double, lon: Double, nombre: Int = 1, notes: String = "") =
        Observation(espece = espece, latitude = lat, longitude = lon, nombre = nombre, notes = notes, date = t0)

    @Test
    fun genererGPX_produit_un_entete_et_les_waypoints() {
        val gpx = genererGPX(listOf(obs("Merle noir", 42.9, 1.4)), emptyList())
        assertTrue(gpx.contains("<?xml"))
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("""<wpt lat="42.9" lon="1.4">"""))
        assertTrue(gpx.contains("<name>Merle noir</name>"))
    }

    @Test
    fun genererGPX_echappe_les_caracteres_xml_du_nom() {
        val gpx = genererGPX(listOf(obs("Pie & <corbeau>", 43.0, 1.0)), emptyList())
        assertTrue(gpx.contains("Pie &amp; &lt;corbeau&gt;"))
        // Le nom brut non échappé ne doit pas apparaître.
        assertTrue(!gpx.contains("Pie & <corbeau>"))
    }

    @Test
    fun roundtrip_preserve_observations_et_parcours() {
        val observations = listOf(
            obs("Buse variable", 42.95, 1.43, nombre = 3, notes = "en vol"),
            obs("Rougegorge", 42.96, 1.45),
        )
        val parcours = listOf(PointTrace(42.95, 1.43), PointTrace(42.96, 1.45))

        val gpx = genererGPX(observations, parcours)
        val sortie = importerGPX(gpx.encodeToByteArray())!!

        assertEquals(2, sortie.observations.size)
        assertEquals(2, sortie.pointsParcours.size)

        val buse = sortie.observations.first { it.espece == "Buse variable" }
        assertEquals(42.95, buse.latitude, 1e-9)
        assertEquals(1.43, buse.longitude, 1e-9)
        assertEquals(3, buse.nombre)
        assertEquals("en vol", buse.notes)
        assertEquals(t0, buse.date)

        assertEquals(42.96, sortie.pointsParcours[1].latitude, 1e-9)
    }

    @Test
    fun importerGPX_donnees_invalides_renvoie_null() {
        assertNull(importerGPX("pas du gpx".encodeToByteArray()))
    }

    @Test
    fun importerGPX_gpx_vide_sans_point_renvoie_null() {
        val gpx = genererGPX(emptyList(), emptyList())
        assertNull(importerGPX(gpx.encodeToByteArray()))
    }

    @Test
    fun heure_et_date_ecrites_en_utc() {
        val gpx = genererGPX(listOf(obs("Merle noir", 42.9, 1.4)), emptyList())
        assertTrue(gpx.contains("<time>2023-11-14T22:13:20Z</time>"), gpx)
    }

    /** Un GPX d'un autre logiciel : préfixe d'espace de noms par défaut, altitude, heures de trace,
     *  entités, CDATA et commentaires. La distance est celle de Location.distanceBetween. */
    @Test
    fun gpx_d_un_autre_logiciel() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
            <!-- exporté par un autre logiciel -->
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="autre">
              <metadata><time>2020-01-01T00:00:00Z</time></metadata>
              <wpt lat='42.9' lon="1.4"><ele>500</ele><name>Pic &amp; mésange</name><desc><![CDATA[2 individus — <chant>]]></desc></wpt>
              <wpt lat="42.91" lon="1.41"><name></name><time>2026-05-21T10:00:00.123Z</time></wpt>
              <trk><trkseg>
                <trkpt lat="42.90" lon="1.40"><ele>500</ele><time>2026-05-21T09:00:00Z</time></trkpt>
                <trkpt lat="42.91" lon="1.40"><time>2026-05-21T09:10:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val sortie = importerGPX(gpx.encodeToByteArray())!!
        val premiere = 1_779_354_000_000L // 2026-05-21T09:00:00Z
        assertEquals(premiere, sortie.date, "date de la sortie = première heure de la trace")
        val pic = sortie.observations[0]
        assertEquals("Pic & mésange", pic.espece)
        assertEquals(2, pic.nombre)
        assertEquals("<chant>", pic.notes)
        assertEquals(premiere, pic.date, "sans heure : celle de la trace")
        val inconnue = sortie.observations[1]
        assertEquals("Espèce inconnue", inconnue.espece)
        assertEquals(premiere, inconnue.date, "heure avec millisecondes non lue, comme sous Android")
        // 0,01° de latitude à 42,9° N : l'arc de méridien de l'ellipsoïde WGS84.
        assertEquals(1110.91, sortie.distanceTotale, 0.01)
    }

    @Test
    fun heure_toleree_et_debordements_reportes() {
        val gpx = """<gpx><wpt lat="1" lon="2"><time>2026-2-30T25:00:00Zsuite</time></wpt></gpx>"""
        val date = importerGPX(gpx.encodeToByteArray())!!.observations.single().date
        assertEquals(1_772_499_600_000L, date, "30 février 25 h = 3 mars 1 h (UTC)")
    }

    @Test
    fun latin1_annonce_par_la_declaration() {
        val texte = """<?xml version="1.0" encoding="ISO-8859-1"?><gpx><wpt lat="1" lon="2"><name>Gélinotte</name></wpt></gpx>"""
        val octets = ByteArray(texte.length) { texte[it].code.toByte() }
        assertEquals("Gélinotte", importerGPX(octets)!!.observations.single().espece)
    }

    @Test
    fun refus_comme_l_analyseur_durci_d_android() {
        fun refuse(gpx: String) = assertNull(importerGPX(gpx.encodeToByteArray()), gpx)
        refuse("""<!DOCTYPE gpx [<!ENTITY a "x">]><gpx><wpt lat="1" lon="2"><name>&a;</name></wpt></gpx>""")
        refuse("""<gpx><wpt lat="1" lon="2"><name>&inconnue;</name></wpt></gpx>""")
        refuse("""<gpx><wpt lat="1" lon="2"><name>x</wpt></name></gpx>""")
        refuse("""<gpx><wpt lat="1" lon="2"></wpt>""")
        refuse("""<gpx><wpt lat="1" lon="2"/></gpx><gpx/>""")
        refuse("""<gpx><wpt lat=1 lon="2"/></gpx>""")
        val utf8Invalide = byteArrayOf(0x3C, 0x67, 0x3E, 0xC3.toByte(), 0x28, 0x3C, 0x2F, 0x67, 0x3E)
        assertNull(importerGPX(utf8Invalide))
    }
}
