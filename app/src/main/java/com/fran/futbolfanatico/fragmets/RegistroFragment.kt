package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.databinding.FragmentIniciosesionBinding
import com.fran.futbolfanatico.databinding.FragmentRegistroBinding
import com.fran.futbolfanatico.utils.FirebaseAuthHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.toString


class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding= FragmentRegistroBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnRegistrarte.setOnClickListener { registrarse() }
    }

    // Valida los campos del formulario, crea la cuenta en Firebase Auth y registra el usuario en Firestore con username único mediante transacción
    private fun registrarse() {
        //  Leer inputs
        val correo = binding.etCorreoElectronico.text?.toString()?.trim().orEmpty()
        val nombreCompleto = binding.etNombreCompleto.text?.toString()?.trim().orEmpty()
        val nombreUsuario = binding.etNombreUsuario.text?.toString()?.trim().orEmpty()
        val contrasena = binding.etContrasena.text?.toString()?.trim().orEmpty()
        val contrasena2 = binding.etConfirmarContrasena.text?.toString()?.trim().orEmpty()
        val nombreUsuarioMinus = nombreUsuario.lowercase()

        //  Limpiar errores previos
        binding.ilCorreoTelefono.error = null
        binding.ilNombreCompleto.error = null
        binding.ilNombreUsuario.error = null
        binding.ilContrasena.error = null
        binding.ilConfirmarContrasena.error = null

        //  Validaciones locales -> errores en el campo
        var hayError = false
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.ilCorreoTelefono.error = "Email inválido"
            hayError = true
        }
        if (nombreCompleto.length < 2) {
            binding.ilNombreCompleto.error = "Nombre demasiado corto"
            hayError = true
        }
        if (nombreUsuario.length < 3) {
            binding.ilNombreUsuario.error = "El usuario debe tener 3+ caracteres"
            hayError = true
        }
        if (contrasena.length < 6) {
            binding.ilContrasena.error = "Contraseña mínima de 6 caracteres"
            hayError = true
        }

        if (contrasena2.isEmpty()) {
            binding.ilConfirmarContrasena.error = "Repite la contraseña"
            hayError = true
        } else if (contrasena != contrasena2) {
            binding.ilConfirmarContrasena.error = "Las contraseñas no coinciden"
            hayError = true
        }

        if (hayError) return

        //  Crear cuenta + reservar username (transacción)
        barraProgreso(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 4.1 Crear usuario en Firebase Auth
                val credenciales = auth.createUserWithEmailAndPassword(correo, contrasena).await()
                val idUsuario = credenciales.user!!.uid

                val refUsuario = db.collection("usuarios").document(idUsuario)
                val refNombreUsuario = db.collection("nombres_usuarios").document(nombreUsuarioMinus)

                try {
                    // 4.2 Transacción: asegurar username único y crear perfil
                    db.runTransaction { transaccion ->
                        val docNombreUsuario = transaccion.get(refNombreUsuario)
                        if (docNombreUsuario.exists()) {
                            throw IllegalStateException("USERNAME_TAKEN")
                        }
                        // Reservar username
                        transaccion.set(refNombreUsuario, mapOf(
                            "uid" to idUsuario,
                            "creado_en" to FieldValue.serverTimestamp()
                        ))

                        transaccion.set(refUsuario, mapOf(
                            "nombre_mostrado" to nombreCompleto,
                            "nombre_usuario" to nombreUsuario,
                            "nombre_usuario_lower" to nombreUsuarioMinus,
                            "foto_url" to "***/storage/v1/object/public/***/avatars/usuario.png",
                            "biografia" to "",
                            "email" to correo,
                            "seguidores" to 0,
                            "siguiendo" to 0,
                            "fecha_registro" to FieldValue.serverTimestamp()
                        ))
                        null
                    }.await()

                    //  OK -> navegar y avisar
                    mensaje("Cuenta creada. Inicia sesión con tu email y contraseña.")
                    findNavController().navigate(R.id.cuentaCreada)

                } catch (e: Exception) {
                    // Username ya en uso -> borrar el usuario recién creado en Auth y marcar error en el campo
                    if (e is IllegalStateException && e.message == "USERNAME_TAKEN") {
                        try { auth.currentUser?.delete()?.await() } catch (_: Exception) {}
                        binding.ilNombreUsuario.error = "Ese nombre de usuario ya está en uso"
                    } else {
                        // Otros errores de Firestore
                        mensaje(e.localizedMessage ?: "No se pudo crear tu perfil. Inténtalo de nuevo.")
                    }
                }

            } catch (e: Exception) {
                mensaje(FirebaseAuthHelper.obtenerMensajeError(e))
            } finally {
                barraProgreso(false)
            }
        }
    }

    // Muestra un mensaje Toast al usuario
    private fun mensaje(texto: String) {
        Toast.makeText(requireContext(),texto, Toast.LENGTH_LONG).show()
    }

    // Muestra u oculta la barra de progreso y habilita/deshabilita el botón durante la operación
    private fun barraProgreso(mostrar: Boolean) {
        binding.progress.isVisible = mostrar
        binding.btnRegistrarte.isEnabled= !mostrar
    }


    override fun onDestroyView() {
        _binding= null
        super.onDestroyView()
    }

}

