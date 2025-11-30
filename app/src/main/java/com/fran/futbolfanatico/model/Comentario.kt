package com.fran.futbolfanatico.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Comentario (
    val id_comentario: String,
    val id_publicacion: String,
    val id_usuario: String,
    val texto:String,
    val creado_en: Timestamp?
){
    companion object {
        // Convierte un DocumentSnapshot de Firestore en un objeto Comentario
    fun desdeDoc(doc: DocumentSnapshot): Comentario {
        val d = doc.data ?: emptyMap<String, Any?>()
        return Comentario(
            id_comentario = doc.id,
            id_publicacion = d["id_publicacion"] as? String ?: "",
            id_usuario = d["id_usuario"] as? String ?: "",
            texto = d["texto"] as? String ?: "",
            creado_en = d["creado_en"] as? Timestamp
        )
    }
}
}
