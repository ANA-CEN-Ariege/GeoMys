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

package fr.ariegenature.geomys.store

import cnames.structs.__CFData
import fr.ariegenature.geomys.journal.Journal
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSLock
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Le mot de passe GeoNature dans le trousseau iOS (élément « mot de passe générique »). Il est
 *  accessible après le premier déverrouillage, sur cet appareil seulement : ni sauvegardé ni migré
 *  vers un autre appareil, comme le blob chiffré de GeoMys-Android, exclu des sauvegardes. Les
 *  lectures sont mises en cache, comme dans MdpChiffre : le trousseau passe par un service du
 *  système. Jamais d'exception : un échec rend null ou false, et le statut part au journal. */
@OptIn(ExperimentalForeignApi::class)
class CoffreKeychain(
    private val service: String = "fr.ariegenature.geomys.common",
    private val compte: String = "gn_password",
) : CoffreMotDePasse {

    private val verrou = NSLock()
    private var cache: String? = null
    private var cacheValide = false

    override fun lire(): String? = avecVerrou {
        if (cacheValide) {
            cache
        } else {
            avecRequete(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { requete ->
                memScoped {
                    val resultat = alloc<CFTypeRefVar>()
                    when (val statut = SecItemCopyMatching(requete, resultat.ptr)) {
                        errSecSuccess -> try {
                            octets(resultat.value?.reinterpret<__CFData>()).decodeToString()
                                .also { cache = it; cacheValide = true }
                        } finally {
                            resultat.value?.let { CFRelease(it) }
                        }
                        errSecItemNotFound -> {
                            cache = null
                            cacheValide = true
                            null
                        }
                        else -> {
                            Journal.w(TAG, "Trousseau illisible (statut $statut) — repli mémoire")
                            null
                        }
                    }
                }
            }
        }
    }

    override fun ecrire(mdp: String): Boolean = avecVerrou {
        supprimer()
        cache = null
        cacheValide = true
        val octets = mdp.encodeToByteArray()
        val donnees = octets.usePinned {
            CFDataCreate(null, if (octets.isEmpty()) null else it.addressOf(0).reinterpret(), octets.size.convert())
        }
        try {
            avecRequete(kSecValueData to donnees, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) { requete ->
                val statut = SecItemAdd(requete, null)
                if (statut == errSecSuccess) {
                    cache = mdp
                    true
                } else {
                    Journal.w(TAG, "Écriture dans le trousseau impossible (statut $statut) — repli mémoire")
                    false
                }
            }
        } finally {
            donnees?.let { CFRelease(it) }
        }
    }

    override fun effacer() {
        avecVerrou {
            supprimer()
            cache = null
            cacheValide = true
        }
    }

    override fun disponible(): Boolean = true

    private fun supprimer() {
        avecRequete { requete ->
            val statut = SecItemDelete(requete)
            if (statut != errSecSuccess && statut != errSecItemNotFound) {
                Journal.w(TAG, "Effacement dans le trousseau impossible (statut $statut)")
            }
        }
    }

    private inline fun <R> avecVerrou(bloc: () -> R): R {
        verrou.lock()
        try {
            return bloc()
        } finally {
            verrou.unlock()
        }
    }

    /** La requête de base (classe, service, compte) complétée de [extras]. */
    private inline fun <R> avecRequete(vararg extras: Pair<CFStringRef?, CFTypeRef?>, bloc: (CFMutableDictionaryRef?) -> R): R {
        val cfService = CFStringCreateWithCString(null, service, kCFStringEncodingUTF8)
        val cfCompte = CFStringCreateWithCString(null, compte, kCFStringEncodingUTF8)
        val requete = CFDictionaryCreateMutable(null, 0.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(requete, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(requete, kSecAttrService, cfService)
            CFDictionaryAddValue(requete, kSecAttrAccount, cfCompte)
            for ((cle, valeur) in extras) {
                CFDictionaryAddValue(requete, cle, valeur)
            }
            return bloc(requete)
        } finally {
            requete?.let { CFRelease(it) }
            cfService?.let { CFRelease(it) }
            cfCompte?.let { CFRelease(it) }
        }
    }

    private fun octets(donnees: CFDataRef?): ByteArray {
        if (donnees == null) return ByteArray(0)
        val debut = CFDataGetBytePtr(donnees) ?: return ByteArray(0)
        return debut.reinterpret<ByteVar>().readBytes(CFDataGetLength(donnees).toInt())
    }

    private companion object {
        const val TAG = "CoffreKeychain"
    }
}
