package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.PublicacionAdapter
import com.fran.futbolfanatico.databinding.FragmentEditarPerfilBinding
import com.fran.futbolfanatico.databinding.FragmentPerfilOtrosUsuariosBinding
import com.fran.futbolfanatico.model.Conversacion
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.utils.ReporteHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.max


class PerfilOtrosUsuariosFragment : Fragment() {

    private var _binding: FragmentPerfilOtrosUsuariosBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: PublicacionAdapter
    private var postsListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private var seguimientoListener: ListenerRegistration? = null

    private var uidUsuario: String? = null
    private var estaSiguiendo = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uidUsuario = arguments?.getString("uid")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPerfilOtrosUsuariosBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarBarra(binding.includeBottomBar.root)

        val uid = uidUsuario
        if(uid == null){
            Toast.makeText(requireContext(), "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        //Boton seguir/dejar de seguir
        binding.btnSeguir.setOnClickListener {
            alternarSeguimiento(uid)
        }

        // Botón enviar mensaje
        binding.btnEnviarMensaje.setOnClickListener {
            val uidOtroUsuario = uidUsuario ?: return@setOnClickListener
            iniciarChat(uidOtroUsuario)
        }

        //Recycler
        adapter = PublicacionAdapter(
            onLikeClick = { p -> alternarMeGustas(p) },
            onCommentClick = { p ->
                val bundle = Bundle().apply { putString("id_publicacion", p.id_publicacion) }
                findNavController().navigate(R.id.comentarioFragment, bundle)
            },
            onAvatarClick = {},
            onLongClick = { p -> ReporteHelper.mostrarDialogoPublicacion(this, p) }
        )
        binding.rvPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPublicaciones.adapter= adapter
        
        cargarDatosUsuario(uid)
        escucharPublicaciones(uid)
        verificarSiSigue(uid)

        //Ver los seguidores y seguidos
        binding.layoutSeguidores.setOnClickListener {
            val bundle = Bundle().apply {
                putString("uid", uid)
                putString("tipo", "seguidores")
            }
            findNavController().navigate(R.id.listaUsuariosFragment, bundle)
        }
        binding.layoutSeguidos.setOnClickListener {
            val bundle = Bundle().apply {
                putString("uid", uid)
                putString("tipo", "seguidos")
            }
            findNavController().navigate(R.id.listaUsuariosFragment, bundle)
        }
        
    }

    // Carga y actualiza en tiempo real los datos del usuario visitado incluyendo estadísticas de seguidores y seguidos
    private fun cargarDatosUsuario(uid: String) {
        userListener?.remove()
        userListener= db.collection("usuarios").document(uid)
            .addSnapshotListener { snap, _ ->
                val d= snap ?: return@addSnapshotListener
                val nombreMostrado = d.getString("nombre_mostrado") ?: ""
                val username = d.getString("nombre_usuario") ?: ""
                val bio = d.getString("biografia") ?: ""
                val foto = d.getString("foto_url")
                val seguidores = (d.getLong("seguidores") ?: 0L).toInt()
                val seguidos = (d.getLong("seguidos") ?: 0L).toInt()

                binding.tvNombreMostrado.text=nombreMostrado.ifBlank { username }
                binding.tvUsername.text = "@$username"
                binding.tvBio.text = if(bio.isBlank()) "-" else bio
                binding.tvCountSeguidores.text = seguidores.toString()
                binding.tvCountSeguidos.text = seguidos.toString()
                if (!foto.isNullOrBlank()){
                    binding.ivAvatar.load(foto)
                }else{
                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                }
            }
    }

    // Escucha en tiempo real las publicaciones del usuario visitado ordenadas por fecha descendente
    private fun escucharPublicaciones(uid: String){
        postsListener?.remove()
        postsListener = db.collection("publicaciones")
            .whereEqualTo("id_usuario",uid)
            .orderBy("creado_en", Query.Direction.DESCENDING)
            .addSnapshotListener { snap,  _ ->
                if(_binding == null) return@addSnapshotListener
                if(snap == null) return@addSnapshotListener
                val lista = snap.documents.mapNotNull {
                    try {
                        Publicacion.desdeDoc(it)
                    }catch (e: Exception){
                        null
                    }}
                    adapter.updateItems(lista)
                    binding.tvCountPosts.text=lista.size.toString()

            }
    }

    // Verifica en tiempo real si el usuario actual está siguiendo al usuario visitado
    private fun verificarSiSigue(uidSeguido: String){
        val uidActual = auth.currentUser?.uid ?: return
        val idSeguimiento = "${uidActual}_$uidSeguido"

        seguimientoListener?.remove()
        seguimientoListener = db.collection("seguimientos").document(idSeguimiento)
            .addSnapshotListener { snap, _ ->
                estaSiguiendo = snap?.exists() == true
                actualizarBotonSeguir()
            }
    }

    // Actualiza el texto del botón seguir según el estado actual de seguimiento
    private fun actualizarBotonSeguir(){
        if(estaSiguiendo){
            binding.btnSeguir.text ="Siguiendo ✔"
        }else{
            binding.btnSeguir.text ="Seguir"
        }
    }

    // Alterna el estado de seguimiento usando transacciones para actualizar contadores de ambos usuarios
    private fun alternarSeguimiento(uidSeguido: String){
        val uidActual = auth.currentUser?.uid ?: return
        val idSeguimiento = "${uidActual}_${uidSeguido}"
        val segRef = db.collection("seguimientos").document(idSeguimiento)
        val usuarioActualRef = db.collection("usuarios").document(uidActual)
        val usuarioSeguidoRef = db.collection("usuarios").document(uidSeguido)

        db.runTransaction { tx ->
            val segDoc = tx.get(segRef)
            val actualDoc= tx.get(usuarioActualRef)
            val seguidoDoc= tx.get(usuarioSeguidoRef)

            val seguidosActual = (actualDoc.getLong("seguidos") ?: 0L).toInt()
            val seguidoresSeguido = (seguidoDoc.getLong("seguidores") ?: 0L).toInt()

            if(segDoc.exists()){
                //Dejar de seguir
                tx.delete(segRef)
                tx.update(usuarioActualRef,"seguidos", max(0,seguidosActual-1))
                tx.update(usuarioSeguidoRef,"seguidores", max(0,seguidoresSeguido-1))
            }else{
                tx.set(segRef,mapOf(
                    "seguidor" to uidActual,
                    "seguido" to uidSeguido,
                    "creado_en" to FieldValue.serverTimestamp()
                ))
                tx.update(usuarioActualRef, "seguidos", seguidosActual + 1)
                tx.update(usuarioSeguidoRef, "seguidores", seguidoresSeguido + 1)
            }
        }.addOnSuccessListener {
            Toast.makeText(requireContext(),
                if (estaSiguiendo)"Dejaste de seguir" else "Ahora sigues a este usuario",
            Toast.LENGTH_SHORT
            ).show()
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Verificar y crear conversación si no existe
    private fun iniciarChat(uidOtroUsuario: String) {
        val uidActual = auth.currentUser?.uid ?: return
        val conversacionId = Conversacion.generarId(uidActual, uidOtroUsuario)

        // Verificar si la conversación ya existe
        db.collection("conversaciones").document(conversacionId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Ya existe - navegar directamente
                    navegarAlChat(conversacionId, uidOtroUsuario)
                } else {
                    // No existe - crear primero
                    crearConversacionYNavegar(conversacionId, uidActual, uidOtroUsuario)
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


    //Metodo para sumar en uno el like si se da click, y si ya le habiamos dado le quita el like
    private fun alternarMeGustas(publicacion: Publicacion){
        val uid = auth.currentUser?.uid ?:return
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
            // Recargar la publicación actualizada
            db.collection("publicaciones").document(publicacion.id_publicacion)
                .get()
                .addOnSuccessListener { doc ->
                    val publicacionActualizada = Publicacion.desdeDoc(doc)
                    adapter.actualizarPublicacion(publicacionActualizada)
                }
        }
    }



    //Metodo para cofigurar la barra y asignarle una navigation a cada boton de la barra
    private fun configurarBarra(bar: View) {
        val nav = findNavController()
        bar.findViewById<ImageButton>(R.id.btnInicio).setOnClickListener { nav.navigate(R.id.paginaInicioFragment) }
        bar.findViewById<ImageButton>(R.id.btnBuscar).setOnClickListener { }
        bar.findViewById<ImageButton>(R.id.btnCrear).setOnClickListener { nav.navigate(R.id.crearPublicacionFragment) }
        bar.findViewById<ImageButton>(R.id.btnMensajes).setOnClickListener {nav.navigate(R.id.mensajeFragment) }
        bar.findViewById<ImageButton>(R.id.btnPerfil).setOnClickListener { nav.navigate(R.id.perfilFragment)}
    }


    override fun onDestroyView() {
        postsListener?.remove()
        userListener?.remove()
        seguimientoListener?.remove()
        adapter.clearCache()
        _binding = null
        super.onDestroyView()
    }
}