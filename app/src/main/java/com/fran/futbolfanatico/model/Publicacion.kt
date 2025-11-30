package com.fran.futbolfanatico.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp

data class Publicacion(
    val id_publicacion: String,
    val id_usuario: String,
    val texto: String,
    val tipo: String,                  // "texto" | "imagen"
    val url_media: String?,
    val etiquetas: List<String> = emptyList<String>(),
    val cantidad_likes: Int,
    val cantidad_comentarios: Int,
    val creado_en: Timestamp?
){
    // Crea una copia de la publicación con una nueva cantidad de likes manteniendo el resto de datos
    fun conNuevosLikes(nuevaCantidad: Int): Publicacion {
        return Publicacion(
            id_publicacion = this.id_publicacion,
            id_usuario = this.id_usuario,
            texto = this.texto,
            tipo = this.tipo,
            url_media = this.url_media,
            etiquetas = this.etiquetas,
            cantidad_likes = nuevaCantidad,
            cantidad_comentarios = this.cantidad_comentarios,
            creado_en = this.creado_en
        )
    }

    companion object{
        // Convierte un DocumentSnapshot de Firestore en un objeto Publicacion extrayendo todos los campos incluyendo etiquetas
        fun desdeDoc(doc: DocumentSnapshot): Publicacion{
            val d = doc.data ?: emptyMap<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            return Publicacion(
                id_publicacion = doc.id,
                id_usuario = d["id_usuario"] as? String ?: "",
                texto = d["texto"] as? String ?: "",
                tipo = d["tipo"] as? String ?: "texto",
                url_media = d["url_media"] as? String,
                etiquetas = (d["etiquetas"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                cantidad_likes = (d["cantidad_likes"] as? Number)?.toInt() ?: 0,
                cantidad_comentarios = (d["cantidad_comentarios"] as? Number)?.toInt() ?: 0,
                creado_en = d["creado_en"] as? Timestamp)
        }
    }
}