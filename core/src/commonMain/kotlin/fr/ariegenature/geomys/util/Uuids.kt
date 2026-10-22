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

package fr.ariegenature.geomys.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import okio.ByteString.Companion.toByteString

/** Un UUID aléatoire (version 4) en texte, minuscules et tirets : `java.util.UUID.randomUUID()
 *  .toString()` dans GeoMys-Android. */
@OptIn(ExperimentalUuidApi::class)
fun uuidAleatoire(): String = Uuid.random().toString()

/** L'UUID de version 3 (MD5) de [octets], identique à `java.util.UUID.nameUUIDFromBytes` : comme
 *  lui, sans espace de noms. Sert aux identités qui doivent rester les mêmes d'un envoi à l'autre
 *  (l'uuid d'un relevé, GeoNatureUpload.kt:646), dont dépend l'anti-doublon côté serveur. */
@OptIn(ExperimentalUuidApi::class)
fun uuidDepuisOctets(octets: ByteArray): String {
    val empreinte = octets.toByteString().md5().toByteArray()
    empreinte[6] = ((empreinte[6].toInt() and 0x0f) or 0x30).toByte() // version 3
    empreinte[8] = ((empreinte[8].toInt() and 0x3f) or 0x80).toByte() // variante IETF
    return Uuid.fromByteArray(empreinte).toString()
}
