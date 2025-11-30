package com.fran.futbolfanatico.utils

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

object FirebaseAuthHelper {

     // Traduce excepciones de Firebase Auth a mensajes en español
    fun obtenerMensajeError(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> {
                when (exception.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "El formato del email es inválido"
                    "ERROR_WRONG_PASSWORD" -> "La contraseña es incorrecta"
                    "ERROR_INVALID_CREDENTIAL" -> "Las credenciales son inválidas"
                    else -> "Email o contraseña incorrectos"
                }
            }
            is FirebaseAuthInvalidUserException -> {
                when (exception.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "No existe una cuenta con este email"
                    "ERROR_USER_DISABLED" -> "Esta cuenta ha sido deshabilitada"
                    else -> "Usuario no encontrado"
                }
            }
            is FirebaseAuthWeakPasswordException ->
                "La contraseña es demasiado débil. Debe tener al menos 6 caracteres"

            is FirebaseAuthUserCollisionException ->
                "Ya existe una cuenta con este email"

            is FirebaseNetworkException ->
                "Error de conexión. Verifica tu internet"

            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "Este email ya está registrado"
                    "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Intenta más tarde"
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Operación no permitida"
                    else -> exception.localizedMessage ?: "Error de autenticación"
                }
            }
            else -> exception.localizedMessage ?: "Error desconocido"
        }
    }
}