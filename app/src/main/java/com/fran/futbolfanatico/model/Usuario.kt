package com.fran.futbolfanatico.model

import java.sql.Timestamp

data class Usuario(
    val id: String,
    val nombre_usuario: String,
    val nombre_mostrado: String,
    val foto_url: String?,
    val email: String,
    val biografia: String? = "",
    val fecha_registro: Timestamp? = null,
    val seguidores: Int = 0,
    val siguiendo: Int = 0,
    val nombre_usuario_lower: String
)
