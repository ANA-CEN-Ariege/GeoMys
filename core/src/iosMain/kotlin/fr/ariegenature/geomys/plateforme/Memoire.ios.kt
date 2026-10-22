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

package fr.ariegenature.geomys.plateforme

import fr.ariegenature.geomys.plateforme.proc.os_proc_available_memory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import platform.Foundation.NSProcessInfo
import platform.darwin.KERN_SUCCESS
import platform.darwin.TASK_VM_INFO
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.darwin.task_vm_info_data_t

@OptIn(ExperimentalForeignApi::class)
actual fun memoireUtilisee(): Long = memScoped {
    val info = alloc<task_vm_info_data_t>()
    val nombre = alloc<mach_msg_type_number_tVar>()
    // TASK_VM_INFO_COUNT : taille de la structure en mots de 32 bits.
    nombre.value = (sizeOf<task_vm_info_data_t>() / 4).toUInt()
    val resultat = task_info(mach_task_self_, TASK_VM_INFO.toUInt(), info.ptr.reinterpret(), nombre.ptr)
    if (resultat == KERN_SUCCESS) info.phys_footprint.toLong() else -1L
}

@OptIn(ExperimentalForeignApi::class)
actual fun memoireReference(): Long {
    // 0 hors limite d'empreinte, notamment sur le simulateur.
    val disponible = os_proc_available_memory().toLong()
    return if (disponible > 0) memoireUtilisee() + disponible else NSProcessInfo.processInfo.physicalMemory.toLong()
}

@OptIn(NativeRuntimeApi::class)
actual fun ramasserMiettes() {
    GC.collect()
}
