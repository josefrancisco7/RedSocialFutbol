package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.PublicacionAdapter
import com.fran.futbolfanatico.databinding.FragmentCrearPublicacionBinding
import com.fran.futbolfanatico.databinding.FragmentPerfilBinding
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.utils.ReporteHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.max


class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: PublicacionAdapter
    private var postsListener: ListenerRegistration?=null
    private var userListener: ListenerRegistration?=null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarBarra(binding.includeBottomBar.root)
        resaltarPerfil(binding.includeBottomBar.root)

        //Logout
        binding.btnCerrarSesion.setOnClickListener { confirmarCierreSesion() }

        //Editar perfil
        binding.btnEditarPerfil.setOnClickListener {
            findNavController().navigate(R.id.editarPerfilFragment)
        }

        //Recycler
        adapter = PublicacionAdapter(
            onLikeClick = { p-> alternarMeGustas(p)},
            onCommentClick = { p ->
                val bundle = Bundle().apply { putString("id_publicacion", p.id_publicacion) }
                findNavController().navigate(R.id.comentarioFragment, bundle)
            },
            onAvatarClick = {},
            onLongClick = { p -> ReporteHelper.mostrarDialogoPublicacion(this, p) }
        )

        binding.rvMisPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMisPublicaciones.adapter=adapter

        cargarUsuariosYStats()
        escucharMisPost()

        //Ver los seguidores y seguidos
        binding.layoutSeguidores.setOnClickListener {
            val bundle = Bundle().apply {
                putString("uid", auth.currentUser?.uid)
                putString("tipo", "seguidores")
            }
            findNavController().navigate(R.id.listaUsuariosFragment, bundle)
        }
        binding.layoutSeguidos.setOnClickListener {
            val bundle = Bundle().apply {
                putString("uid", auth.currentUser?.uid)
                putString("tipo", "seguidos")
            }
            findNavController().navigate(R.id.listaUsuariosFragment, bundle)
        }

    }

    //Metodo que carga todos lo datos relacionados al usuario actual para mostrar en su perfil
    private fun cargarUsuariosYStats(){
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("usuarios").document(uid)

        userListener?.remove()
        userListener = userRef.addSnapshotListener { snap, _ ->
            val d = snap ?: return@addSnapshotListener
            val nombreMostrado = d.getString("nombre_mostrado") ?: ""
            val username = d.getString("nombre_usuario") ?: ""
            val bio = d.getString("biografia") ?: ""
            val foto = d.getString("foto_url")
            val seguidores = (d.getLong("seguidores") ?: 0L).toInt()
            val seguidos = (d.getLong("seguidos") ?: 0L).toInt()

            binding.tvNombreMostrado.text= nombreMostrado.ifBlank { nombreMostrado }
            binding.tvUsername.text="@$username"
            binding.tvBio.text = if(bio.isBlank()) "-" else bio
            binding.tvCountSeguidores.text=seguidores.toString()
            binding.tvCountSeguidos.text=seguidos.toString()
            if(!foto.isNullOrBlank()) binding.ivAvatar.load(foto) else binding.ivAvatar.setImageResource(R.drawable.usuario)

        }
    }

    //Metodo que mantiene actualizada  las publicaciones del perfil
    private fun escucharMisPost(){
        val uid = auth.currentUser?.uid ?: return
        postsListener?.remove()
        postsListener= db.collection("publicaciones")
            .whereEqualTo("id_usuario",uid)
            .orderBy("creado_en", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (_binding == null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener
                val lista = snap?.documents?.map{ Publicacion.desdeDoc(it)  } ?: emptyList()
                adapter.updateItems(lista)
                binding.tvCountPosts.text = lista.size.toString()
                }
    }


    //Metodo para sumar en uno el like si se da click, y si ya le habiamos dado le quita el like
    private fun alternarMeGustas(publicacion: Publicacion){
        val uid = auth.currentUser?.uid
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

            }else{
                tx.set(likeRef,mapOf(
                    "id_publicacion" to publicacion.id_publicacion,
                    "id_usuario" to uid,
                    "creado_en" to FieldValue.serverTimestamp()
                ))
                tx.update(pubRef,"cantidad_likes",actuales+1)
            }
        }.addOnSuccessListener {
        //  Recargar la publicación actualizada
        db.collection("publicaciones").document(publicacion.id_publicacion)
            .get()
            .addOnSuccessListener { doc ->
                val publicacionActualizada = Publicacion.desdeDoc(doc)
                adapter.actualizarPublicacion(publicacionActualizada)
            }
    }
    }

    // Muestra un diálogo de confirmación antes de cerrar la sesión del usuario
    private fun confirmarCierreSesion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, cerrar") { _, _ ->
                cerrarSesionYVolverALogin()
            }
            .show()
    }

    // Cierra la sesión de Firebase y navega al login limpiando el back stack completo
    private fun cerrarSesionYVolverALogin() {
        try {
            auth.signOut()
        } catch (_: Exception) { /* ignora */ }

        // Navegar a Login y limpiar el back stack
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, true)
            .build()
        findNavController().navigate(R.id.loginFragment, null, navOptions)
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
    private fun resaltarPerfil(bar: View) {
        val activos = setOf(R.id.btnPerfil)
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
        postsListener?.remove()
        userListener?.remove()
        adapter.clearCache()
        _binding = null
        super.onDestroyView()
    }

}