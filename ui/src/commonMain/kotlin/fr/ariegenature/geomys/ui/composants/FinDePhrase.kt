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

// Propre au portage (constat du 23 octobre 2026 sur l'iPhone : « rouge-gorge » restait dans le champ) : la
// reconnaissance d'iOS ne dit pas d'elle-même qu'une phrase est finie, elle écoute jusqu'à ce qu'on l'arrête.
// GeoMys-IOS n'en tenait pas compte non plus.

package fr.ariegenature.geomys.ui.composants

/** Silence, après le dernier mot entendu, au bout duquel la phrase dictée est tenue pour finie (ms) :
 *  de l'ordre de celui de la reconnaissance d'Android. */
internal const val SILENCE_FIN_DE_PHRASE = 1500L

/**
 * La fin d'une phrase dictée, quand la reconnaissance ne la signale pas : [delai] ms sans nouveau texte.
 * [programmer] lance une action après un délai (la file principale sous iOS) ; [onFin] clôt l'écoute.
 */
internal class FinDePhrase(
    private val delai: Long,
    private val programmer: (Long, () -> Unit) -> Unit,
    private val onFin: () -> Unit,
) {
    private var attente = 0

    /** Du texte vient d'être entendu : l'attente du silence repart de zéro. */
    fun entendu() {
        val jeton = ++attente
        programmer(delai) { if (jeton == attente) onFin() }
    }

    /** L'écoute est close ou relancée : l'attente en cours ne vaut plus. */
    fun oublier() {
        attente++
    }
}
