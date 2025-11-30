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
import coil.imageLoader
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.databinding.FragmentIniciosesionBinding
import com.fran.futbolfanatico.utils.FirebaseAuthHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class InicioSesionFragment : Fragment() {

    private var _binding: FragmentIniciosesionBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentIniciosesionBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        auth.currentUser?.let {
            findNavController().navigate(R.id.iniciarSesion)
            return
        }

        binding.btnIniciarSesion.setOnClickListener { logearse() }
        binding.btnCrearUsuario.setOnClickListener {
            findNavController().navigate(R.id.registrarse)
        }
        binding.tvOlvidarPassword.setOnClickListener { resetPassword() }

    }

    // Valida los campos de email y contraseña, autentica al usuario con Firebase y limpia la caché de imágenes al iniciar sesión
    private fun logearse() {
        val email= binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etContrasena.text?.toString()?.trim().orEmpty()

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            mensaje("Email invalido"); return
        }
        if(password.length < 6){
            mensaje("La contraseña debe tener al menos 6 caracteres");return
        }
        barraProgreso(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email,password).await()
                // Limpiar caché de imágenes al iniciar sesión
                requireContext().imageLoader.diskCache?.clear()
                requireContext().imageLoader.memoryCache?.clear()
                findNavController().navigate(R.id.iniciarSesion)
            }catch (e: Exception){
//                mensaje(e.localizedMessage?: "Error al iniciar sesion")
                mensaje(FirebaseAuthHelper.obtenerMensajeError(e))
            }
            finally {
                barraProgreso(false)
            }
        }
    }


    // Envía un correo de recuperación de contraseña al email ingresado usando Firebase Auth
    private fun resetPassword() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mensaje("Escribe tu email para enviarle el enlace de recuperación")
            return
        }
        barraProgreso(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                mensaje("Se envio un correo para restablecer la contraseña")
            }catch (e: Exception) {
//            mensaje(e.localizedMessage ?: "No se pudo enviar el correo")
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

    // Muestra u oculta la barra de progreso y habilita/deshabilita los botones durante la operación
    private fun barraProgreso(mostrar: Boolean) {
        binding.progress.isVisible = mostrar
        binding.btnIniciarSesion.isEnabled = !mostrar
        binding.btnCrearUsuario.isEnabled = !mostrar
        binding.tvOlvidarPassword.isEnabled = !mostrar
    }



    override fun onDestroyView() {
        _binding= null
        super.onDestroyView()
    }
}

