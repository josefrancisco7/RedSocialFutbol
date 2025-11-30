package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.PublicacionAdapter
import com.fran.futbolfanatico.adapters.UsuarioAdapter
import com.fran.futbolfanatico.databinding.FragmentBusquedaBinding
import com.fran.futbolfanatico.databinding.FragmentEditarPerfilBinding
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.model.Usuario
import com.fran.futbolfanatico.utils.ReporteHelper
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class BusquedaFragment : Fragment() {

    private var _binding: FragmentBusquedaBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var usuarioAdapter: UsuarioAdapter
    private lateinit var publicacionAdapter: PublicacionAdapter

    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBusquedaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarBarra(binding.includeBottomBar.root)
        resaltarLupa(binding.includeBottomBar.root)

        // Configurar adapter de usuarios
        usuarioAdapter = UsuarioAdapter { usuario ->
            if (usuario.id == auth.currentUser?.uid) {
                findNavController().navigate(R.id.perfilFragment)
            } else {
                val bundle = Bundle().apply { putString("uid", usuario.id) }
                findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
            }
        }

        // Configurar adapter de publicaciones
        publicacionAdapter = PublicacionAdapter(
            onLikeClick = { p -> alternarMeGustas(p) },
            onCommentClick = { p ->
                val bundle = Bundle().apply { putString("id_publicacion", p.id_publicacion) }
                findNavController().navigate(R.id.comentarioFragment, bundle)
            },
            onAvatarClick = { uid ->
                if (uid == auth.currentUser?.uid) {
                    findNavController().navigate(R.id.perfilFragment)
                } else {
                    val bundle = Bundle().apply { putString("uid", uid) }
                    findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
                }
            },
            onLongClick = { p -> ReporteHelper.mostrarDialogoPublicacion(this, p) }
        )

        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsuarios.adapter = usuarioAdapter

        binding.rvPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPublicaciones.adapter = publicacionAdapter


        // Cambiar entre tabs
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // Tab Usuarios
                        binding.rvUsuarios.visibility = View.VISIBLE
                        binding.rvPublicaciones.visibility = View.GONE
                        binding.tvVacio.visibility = View.GONE
                        val query = binding.etBuscar.text?.toString()?.trim() ?: ""
                        if (query.length >= 2) buscarUsuarios(query)
                    }
                    1 -> {
                        // Tab Etiquetas
                        binding.rvUsuarios.visibility = View.GONE
                        binding.rvPublicaciones.visibility = View.VISIBLE
                        binding.tvVacio.visibility = View.GONE
                        val query = binding.etBuscar.text?.toString()?.trim() ?: ""
                        if (query.isNotEmpty()) buscarPorEtiqueta(query)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Búsqueda en tiempo real
        binding.etBuscar.addTextChangedListener { texto ->

            searchJob?.cancel()
            searchJob = lifecycleScope.launch { delay(500)
                val query = texto?.toString()?.trim() ?: ""

                when (binding.tabLayout.selectedTabPosition) {
                    0 -> {
                        // Buscar usuarios
                        if (query.length >= 2) {
                            buscarUsuarios(query.lowercase())
                        } else {
                            usuarioAdapter.updateItems(emptyList())
                            binding.tvVacio.visibility = View.GONE
                        }
                    }
                    1 -> {
                        // Buscar por etiqueta
                        if (query.isNotEmpty()) {
                            val etiqueta = if (query.startsWith("#")) query else "#$query"
                            buscarPorEtiqueta(etiqueta.lowercase())
                        } else {
                            publicacionAdapter.updateItems(emptyList())
                            binding.tvVacio.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    // Busca usuarios en Firestore filtrando por nombre de usuario que coincida con la query ingresada
    private fun buscarUsuarios(query: String) {
        db.collection("usuarios")
            .orderBy("nombre_usuario_lower")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { docs ->
                if (_binding == null) return@addOnSuccessListener

                val usuarios = docs.documents.mapNotNull { doc ->
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

                usuarioAdapter.updateItems(usuarios)
                binding.tvVacio.visibility = if (usuarios.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Busca publicaciones en Firestore que contengan la etiqueta especificada en su array de etiquetas
    private fun buscarPorEtiqueta(etiqueta: String) {
        val etiquetaLimpia = etiqueta.lowercase()

        db.collection("publicaciones")
            .whereArrayContains("etiquetas", etiquetaLimpia)
            .limit(50)
            .get()
            .addOnSuccessListener { docs ->
                if (_binding == null) return@addOnSuccessListener

                val publicaciones = docs.documents.mapNotNull { doc ->
                    try {
                        Publicacion.desdeDoc(doc)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.creado_en?.toDate()?.time }

                publicacionAdapter.updateItems(publicaciones)
                binding.tvVacio.visibility = if (publicaciones.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Alterna el estado de me gusta en una publicación usando transacciones para mantener consistencia
    private fun alternarMeGustas(publicacion: Publicacion){
        val uid = auth.currentUser?.uid ?: return
        val idLike = "${publicacion.id_publicacion}_$uid"
        val likeRef = db.collection("likes").document(idLike)
        val pubRef= db.collection("publicaciones").document(publicacion.id_publicacion)

        db.runTransaction { tx->
            val likeDoc = tx.get(likeRef)
            val pDoc = tx.get(pubRef)
            val actuales = (pDoc.getLong("cantidad_likes") ?: 0L).toInt()

            if(likeDoc.exists()){
                tx.delete(likeRef)
                tx.update(pubRef, "cantidad_likes", max(0,actuales-1))
                false
            }else{
                tx.set(likeRef,mapOf(
                    "id_publicacion" to publicacion.id_publicacion,
                    "id_usuario" to uid,
                    "creado_en" to FieldValue.serverTimestamp()
                ))
                tx.update(pubRef,"cantidad_likes",actuales+1)
                true
            }
        }.addOnSuccessListener { dioLike ->
            // Usar función helper
            val nuevaCantidad = if (dioLike) {
                publicacion.cantidad_likes + 1
            } else {
                max(0, publicacion.cantidad_likes - 1)
            }

            val publicacionActualizada = publicacion.conNuevosLikes(nuevaCantidad)
            publicacionAdapter.actualizarPublicacion(publicacionActualizada)
        }
    }

    //Metodo para cofigurar la barra y asignarle una navigation a cada boton de la barra
    private fun configurarBarra(bar: View) {
        val nav = findNavController()
        bar.findViewById<ImageButton>(R.id.btnInicio).setOnClickListener { nav.navigate(R.id.paginaInicioFragment) }
        bar.findViewById<ImageButton>(R.id.btnBuscar).setOnClickListener { nav.navigate(R.id.busquedaFragment) }
        bar.findViewById<ImageButton>(R.id.btnCrear).setOnClickListener { nav.navigate(R.id.crearPublicacionFragment)}
        bar.findViewById<ImageButton>(R.id.btnMensajes).setOnClickListener { nav.navigate(R.id.mensajeFragment)}
        bar.findViewById<ImageButton>(R.id.btnPerfil).setOnClickListener { nav.navigate(R.id.perfilFragment)}
    }
    //Metodo para aumentar un poco el tamaño del icono donde nos situamos actualmente
    private fun resaltarLupa(bar: View) {
        val activos = setOf(R.id.btnBuscar)
        val todos = listOf(R.id.btnInicio, R.id.btnBuscar, R.id.btnCrear, R.id.btnMensajes, R.id.btnPerfil)

        todos.forEach { id ->
            val btn = bar.findViewById<ImageButton>(id)
            if (id in activos) {
                btn.scaleX = 1.1f
                btn.scaleY = 1.1f
            } else {
                btn.scaleX = 1f
                btn.scaleY = 1f
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}