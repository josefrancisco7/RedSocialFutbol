package com.fran.futbolfanatico.fragmets

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.PublicacionAdapter
import com.fran.futbolfanatico.databinding.FragmentPaginaInicioBinding
import com.fran.futbolfanatico.databinding.FragmentRegistroBinding
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.utils.ReporteHelper
import com.google.android.material.tabs.TabLayout
import com.google.android.recaptcha.internal.zztx
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.max


class PaginaInicioFragment : Fragment() {

    private var _binding: FragmentPaginaInicioBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: PublicacionAdapter
    private var listener: ListenerRegistration?=null

    private var uidsSeguidos = listOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPaginaInicioBinding.inflate(inflater,container,false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //RecyclerView
        adapter = PublicacionAdapter(
            onLikeClick = { p -> alternarMeGustas(p)},
            onCommentClick = { p ->
                val bundle = Bundle().apply { putString("id_publicacion", p.id_publicacion) }
                findNavController().navigate(R.id.comentarioFragment, bundle)
            },
            onAvatarClick = { uid->
                if (uid == auth.currentUser?.uid) {
                    Toast.makeText(requireContext(), "Este es tu perfil", Toast.LENGTH_SHORT).show()
                } else {
                    val bundle = Bundle().apply { putString("uid", uid) }
                    findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
            }
            }, onLongClick = { p -> ReporteHelper.mostrarDialogoPublicacion(this, p) }
        )

        binding.rvPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPublicaciones.adapter = adapter


        //Barra inferior
        configurarBarra(binding.includeBottomBar.root)
        resaltarCasita(binding.includeBottomBar.root)


        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when(tab?.position){
                    0 -> {cargarTodasLasPublicaciones()}  // Todos
                    1 -> cargarPublicacionesSiguiendo()  // Siguiendo
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        cargarSeguidos()

        cargarTodasLasPublicaciones()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }
    }

    // Obtiene los UIDs de los usuarios que sigue el usuario actual desde la colección seguimientos
    private fun cargarSeguidos(){
        val uid = auth.currentUser?.uid ?: return

        db.collection("seguimientos")
            .whereEqualTo("seguidor", uid)
            .get()
            .addOnSuccessListener { snap ->
                uidsSeguidos = snap.documents.mapNotNull {
                    it.getString("seguido")
                }
            }
    }

    // Carga todas las publicaciones de Firestore ordenadas por fecha descendente con listener en tiempo real
    private fun cargarTodasLasPublicaciones() {
        listener?.remove()
        listener = db.collection("publicaciones")
            .orderBy("creado_en", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (_binding == null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener
                val lista = snap?.documents?.mapNotNull {
                    try {
                        Publicacion.desdeDoc(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                adapter.updateItems(lista)
               // binding.rvPublicaciones.scrollToPosition(0)
            }
    }

    // Carga solo las publicaciones de los usuarios seguidos, manejando lotes mayores a 10 con filtrado manual
    private fun cargarPublicacionesSiguiendo() {
        listener?.remove()
        binding.tvVacio.visibility = View.GONE
        binding.tvVacio.text = ""

        if (uidsSeguidos.isEmpty()) {
            // No sigue a nadie
            adapter.updateItems(emptyList())
            binding.tvVacio.visibility = View.VISIBLE
            binding.tvVacio.text = "No hay publicaciones para mostrar"
            return
        }

        // Si sigues a 10 o menos, usar whereIn directamente
        if (uidsSeguidos.size <= 10) {
            listener = db.collection("publicaciones")
                .whereIn("id_usuario", uidsSeguidos)
                .orderBy("creado_en", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snap, _ ->
                    if (_binding == null) return@addSnapshotListener
                    if (snap == null) return@addSnapshotListener
                    val lista = snap?.documents?.mapNotNull {
                        try {
                            Publicacion.desdeDoc(it)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    adapter.updateItems(lista)
                    //binding.rvPublicaciones.scrollToPosition(0)
                }
        } else {
            // Si sigues a más de 10, traer todas y filtrar en código
            listener = db.collection("publicaciones")
                .orderBy("creado_en", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snap, _ ->
                    if (_binding == null) return@addSnapshotListener
                    if (snap == null) return@addSnapshotListener
                    val lista = snap?.documents?.mapNotNull {
                        try {
                            Publicacion.desdeDoc(it)
                        } catch (e: Exception) {
                            null
                        }
                    }?.filter { pub ->
                        // Filtrar solo publicaciones de usuarios seguidos
                        pub.id_usuario in uidsSeguidos
                    } ?: emptyList()

                    adapter.updateItems(lista)
                    //binding.rvPublicaciones.scrollToPosition(0)
                }
        }
    }


    //Metodo para sumar en uno el like si se da click, y si ya le habiamos dado le quita el like
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
            }else{
                tx.set(likeRef,mapOf(
                    "id_publicacion" to publicacion.id_publicacion,
                    "id_usuario" to uid,
                    "creado_en" to FieldValue.serverTimestamp()
                ))
                tx.update(pubRef,"cantidad_likes",actuales+1)
            }
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
    private fun resaltarCasita(bar: View) {
        val activos = setOf(R.id.btnInicio)
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
        listener?.remove()
        adapter.clearCache()
        _binding= null
        super.onDestroyView()
    }
}


