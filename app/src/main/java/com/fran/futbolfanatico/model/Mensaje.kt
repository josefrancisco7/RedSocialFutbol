package com.fran.futbolfanatico.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Mensaje(
    val id: String,
    val de: String,
    val texto: String,
    val creadoEn: Timestamp?,
    val leido: Boolean = false
) {
    companion object {
        // Convierte un DocumentSnapshot de Firestore en un objeto Mensaje
        fun desdeDoc(doc: DocumentSnapshot): Mensaje {
            return Mensaje(
                id = doc.id,
                de = doc.getString("de") ?: "",
                texto = doc.getString("texto") ?: "",
                creadoEn = doc.getTimestamp("creado_en")
                    ?: Timestamp(System.currentTimeMillis() / 1000, 0),
                leido = doc.getBoolean("leido") ?: false
            )
        }
    }
}