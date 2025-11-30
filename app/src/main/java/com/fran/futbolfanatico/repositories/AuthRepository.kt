package com.fran.futbolfanatico.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class AuthRepository (
    private val auth : FirebaseAuth = FirebaseAuth.getInstance(),
    private val db : FirebaseFirestore = FirebaseFirestore.getInstance()
){
    // Inicia sesión con email y contraseña
    suspend fun iniciarSesion(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    // Registra un nuevo usuario y guarda su perfil en Firestore
    suspend fun register(email: String, pass: String, displayName: String) {
        val cred = auth.createUserWithEmailAndPassword(email, pass).await()
        val uid = cred.user!!.uid

        // Construimos el objeto Usuario con valores por defecto
        val nuevoUsuario = mapOf(
            "id" to uid,
            "nombre_usuario" to displayName,
            "nombre_mostrado" to displayName,
            "foto_url" to "",
            "email" to email,
            "biografia" to "",
            "fecha_registro" to FieldValue.serverTimestamp(),
            "seguidores" to 0,
            "siguiendo" to 0,
            "nombre_usuario_lower" to displayName.lowercase()
        )

        // Guardamos el perfil en Firestore
        db.collection("users").document(uid).set(nuevoUsuario).await()
    }

    // Verifica si hay un usuario logueado
    fun isLogged() = auth.currentUser != null

    // Cierra la sesión actual
    fun logout() = auth.signOut()

    // Devuelve el email del usuario actual
    fun currentEmail() = auth.currentUser?.email ?: ""
}
