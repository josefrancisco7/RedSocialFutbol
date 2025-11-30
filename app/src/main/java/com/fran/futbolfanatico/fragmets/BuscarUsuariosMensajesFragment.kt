package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.UsuarioAdapter
import com.fran.futbolfanatico.databinding.FragmentBuscarUsuariosMensajesBinding
import com.fran.futbolfanatico.model.Conversacion
import com.fran.futbolfanatico.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.widget.addTextChangedListener

class BuscarUsuariosMensajesFragment : Fragment() {

    private var _binding: FragmentBuscarUsuariosMensajesBinding? = null
    private val binding get() = checkNotNull(_binding) {
        "No puede acceder al binding porque es nulo"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: UsuarioAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuscarUsuariosMensajesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Botón volver
        binding.btnVolver.setOnClickListener {
            findNavController().popBackStack()
        }

        //RecyclerView
        adapter = UsuarioAdapter { usuario ->
            // Verificar/crear conversación antes de navegar
            iniciarChat(usuario)
        }
        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsuarios.adapter = adapter

        // Cargar usuarios seguidos
        cargarSeguidos()

        // Búsqueda
        binding.etBuscar.addTextChangedListener { text ->
            filtrarUsuarios(text.toString())
        }
    }

    private var usuarioSeguidos = listOf<Usuario>()

    // Carga la lista de usuarios que sigue el usuario actual desde Firestore y los muestra en el RecyclerView
    private fun cargarSeguidos() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("seguimientos")
            .whereEqualTo("seguidor", uid)
            .get()
            .addOnSuccessListener { snap ->
                val uidsSeguidos = snap.documents.mapNotNull {
                    it.getString("seguido")
                }
                if (uidsSeguidos.isEmpty()) {
                    binding.tvVacio.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                //Cargar datos de usuarios
                db.collection("usuarios")
                    .whereIn("__name__", uidsSeguidos)
                    .get()
                    .addOnSuccessListener { docs ->
                        usuarioSeguidos = docs.documents.mapNotNull { doc ->
                            try {
                                Usuario(
                                    id = doc.id,
                                    nombre_usuario = doc.getString("nombre_usuario") ?: "",
                                    nombre_mostrado = doc.getString("nombre_mostrado") ?: "",
                                    foto_url = doc.getString("foto_url"),
                                    email = doc.getString("email") ?: "",
                                    nombre_usuario_lower = doc.getString("nombre_usuario_lower") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        adapter.updateItems(usuarioSeguidos)

                        if (usuarioSeguidos.isEmpty()) {
                            binding.tvVacio.visibility = View.VISIBLE
                        } else {
                            binding.tvVacio.visibility = View.GONE
                        }
                    }
            }
    }

    // Filtra la lista de usuarios seguidos según el texto de búsqueda ingresado
    private fun filtrarUsuarios(query: String) {
        if (query.isBlank()) {
            adapter.updateItems(usuarioSeguidos)
            return
        }
        val filtrados = usuarioSeguidos.filter {
            it.nombre_usuario.contains(query, ignoreCase = true) ||
                    it.nombre_mostrado.contains(query, ignoreCase = true)
        }
        adapter.updateItems(filtrados)
    }

    // Verifica si existe una conversación con el usuario seleccionado, si no existe la crea antes de navegar
    private fun iniciarChat(usuario: Usuario) {
        val uidActual = auth.currentUser?.uid ?: return
        val conversacionId = Conversacion.generarId(uidActual, usuario.id)

        // Verificar si la conversación ya existe
        db.collection("conversaciones").document(conversacionId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Ya existe - navegar directamente
                    navegarAlChat(conversacionId, usuario.id)
                } else {
                    // No existe - crear primero
                    crearConversacionYNavegar(conversacionId, uidActual, usuario.id)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Crea una nueva conversación en Firestore con los datos iniciales y navega al chat
    private fun crearConversacionYNavegar(conversacionId: String, uidActual: String, otroUid: String) {
        val nuevaConv = hashMapOf(
            "participantes" to listOf(uidActual, otroUid),
            "ultimo_mensaje" to "",
            "ultimo_mensaje_de" to "",
            "actualizado_en" to FieldValue.serverTimestamp(),
            "creado_en" to FieldValue.serverTimestamp(),
            "${uidActual}_no_leidos" to 0,
            "${otroUid}_no_leidos" to 0
        )

        db.collection("conversaciones").document(conversacionId)
            .set(nuevaConv)
            .addOnSuccessListener {
                // Conversación creada - navegar
                navegarAlChat(conversacionId, otroUid)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al crear chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Navega al fragment del chat pasando el ID de la conversación y el UID del otro usuario
    private fun navegarAlChat(conversacionId: String, otroUid: String) {
        val bundle = Bundle().apply {
            putString("conversacion_id", conversacionId)
            putString("otro_uid", otroUid)
        }
        findNavController().navigate(R.id.chatFragment, bundle)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}