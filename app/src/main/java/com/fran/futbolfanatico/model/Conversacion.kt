package com.fran.futbolfanatico.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Conversacion(
    val id: String,
    val participantes: List<String>,
    val ultimoMensaje: String,
    val ultimoMensajeDe: String,
    val actualizadoEn: Timestamp?,
    val noLeidos: Int = 0  // Calculado según el usuario actual
) {
    companion object {
        // Convierte un DocumentSnapshot de Firestore en un objeto Conversacion calculando los mensajes no leídos del usuario actual
        fun desdeDoc(doc: DocumentSnapshot, uidActual: String): Conversacion {
            val participantes = doc.get("participantes") as? List<String> ?: emptyList()
            val otroUid = participantes.firstOrNull { it != uidActual } ?: ""
            val noLeidos = (doc.getLong("${uidActual}_no_leidos") ?: 0L).toInt()

            return Conversacion(
                id = doc.id,
                participantes = participantes,
                ultimoMensaje = doc.getString("ultimo_mensaje") ?: "",
                ultimoMensajeDe = doc.getString("ultimo_mensaje_de") ?: "",
                actualizadoEn = doc.getTimestamp("actualizado_en"),
                noLeidos = noLeidos
            )
        }

        // Genera un ID único para la conversación ordenando los UIDs alfabéticamente para mantener consistencia
        fun generarId(uid1: String, uid2: String): String {
            return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
        }
    }
}