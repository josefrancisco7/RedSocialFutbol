package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.UsuarioAdapter
import com.fran.futbolfanatico.databinding.FragmentListaUsuariosBinding
import com.fran.futbolfanatico.databinding.FragmentPerfilBinding
import com.fran.futbolfanatico.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class ListaUsuariosFragment : Fragment() {

    private var _binding: FragmentListaUsuariosBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var adapter: UsuarioAdapter



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentListaUsuariosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uid = arguments?.getString("uid") ?: return
        val tipo = arguments?.getString("tipo") ?: return // "seguidores" o "seguidos"

        binding.tvTitulo.text = if (tipo == "seguidores") "Seguidores" else "Seguidos"
        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }

        adapter = UsuarioAdapter { usuario ->  // ✅ Nombre más claro
            if (usuario.id == auth.currentUser?.uid) {  // ✅ Comparar String con String
                findNavController().navigate(R.id.perfilFragment)
            } else {
                val bundle = Bundle().apply { putString("uid", usuario.id) }
                findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
            }
        }

        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsuarios.adapter = adapter

        cargarUsuarios(uid, tipo)
    }

    // Carga la lista de seguidores o seguidos del usuario según el tipo especificado, manejando lotes mayores a 10 usuarios
    private fun cargarUsuarios(uid: String, tipo: String) {
        val campo = if (tipo == "seguidores") "seguido" else "seguidor"

        db.collection("seguimientos")
            .whereEqualTo(campo, uid)
            .get()
            .addOnSuccessListener { snap ->
                val uids = snap.documents.mapNotNull {
                    if (tipo == "seguidores") it.getString("seguidor") else it.getString("seguido")
                }

                if (uids.isEmpty()) {
                    binding.tvVacio.visibility = View.VISIBLE
                    binding.tvVacio.text = if (tipo == "seguidores") "No tiene seguidores" else "No sigue a nadie"
                    return@addOnSuccessListener
                }

                // Cargar usuarios
                if (uids.size <= 10) {
                    db.collection("usuarios")
                        .whereIn("__name__", uids)
                        .get()
                        .addOnSuccessListener { usuarios ->
                            val lista = usuarios.documents.mapNotNull { doc ->
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
                            adapter.updateItems(lista)

                            if (lista.isEmpty()) {
                                binding.tvVacio.visibility = View.VISIBLE
                            }
                        }
                } else {
                    // Si son más de 10, cargarlos de a uno
                    val usuarios = mutableListOf<Usuario>()
                    uids.forEach { usuarioId ->
                        db.collection("usuarios").document(usuarioId)
                            .get()
                            .addOnSuccessListener { doc ->
                                try {
                                    val usuario = Usuario(
                                        id = doc.id,
                                        nombre_usuario = doc.getString("nombre_usuario") ?: "",
                                        nombre_mostrado = doc.getString("nombre_mostrado") ?: "",
                                        foto_url = doc.getString("foto_url"),
                                        email = doc.getString("email") ?: "",
                                        nombre_usuario_lower = doc.getString("nombre_usuario_lower") ?: ""
                                    )
                                    usuarios.add(usuario)
                                    adapter.updateItems(usuarios)
                                } catch (e: Exception) {
                                    // Ignorar
                                }
                            }
                    }
                }
            }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }


}