package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import android.text.format.DateUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.ComentarioAdapter
import com.fran.futbolfanatico.adapters.PublicacionAdapter
import com.fran.futbolfanatico.databinding.FragmentComentarioBinding
import com.fran.futbolfanatico.databinding.FragmentPerfilBinding
import com.fran.futbolfanatico.databinding.ItemPublicacionBinding
import com.fran.futbolfanatico.model.Comentario
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.model.Usuario
import com.fran.futbolfanatico.utils.ReporteHelper
import com.google.android.recaptcha.internal.zztx
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

class ComentarioFragment : Fragment() {
    private var _binding: FragmentComentarioBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: ComentarioAdapter
    private var listener: ListenerRegistration? = null
    private var idPublicacion: String? = null
    private val userCache = mutableMapOf<String, Usuario>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idPublicacion = arguments?.getString("id_publicacion")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentComentarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val idPub = idPublicacion
        if(idPub == null){
            Toast.makeText(requireContext(), "Error: Publicación no encontrada", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        binding.btnVolver.setOnClickListener {
            findNavController().popBackStack()
        }

        // Botón enviar comentario
        binding.btnEnviar.setOnClickListener {
            enviarComentario(idPub)
        }

        //RecyclerView
        adapter = ComentarioAdapter(
            onAvatarClick = { uid ->
                if(uid == auth.currentUser?.uid){
                    findNavController().navigate(R.id.perfilFragment)
                }
                else{
                    val bundle = Bundle().apply { putString("uid",uid) }
                    findNavController().navigate(R.id.perfilOtrosUsuariosFragment,bundle)
                }
            },
            onLongClick = { comentario ->
                ReporteHelper.mostrarDialogoComentario(
                    fragment = this,
                    idComentario = comentario.id_comentario,
                    idUsuarioComentario = comentario.id_usuario
                )}
        )
        binding.rvComentarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComentarios.adapter = adapter

        //Cargar la Publicacion
        cargarPublicacion(idPub)

        // Escuchar comentarios en tiempo real
        escucharComentarios(idPub)

    }

    // Escucha en tiempo real los comentarios de la publicación y los ordena por fecha descendente
    private fun escucharComentarios(idPub: String) {
        listener?.remove()
        listener = db.collection("comentarios")
            .whereEqualTo("id_publicacion",idPub)
            //.orderBy("creado_en", Query.Direction.DESCENDING)
            .addSnapshotListener { snap,  _ ->
            if(_binding == null) return@addSnapshotListener
            if(snap == null)return@addSnapshotListener
            val lista = snap.documents.mapNotNull {
                try {
                    Comentario.desdeDoc(it)
                }catch (e: Exception){
                    null
                }
            }
                val listaOrdenada = lista.sortedByDescending {
                    it.creado_en?.toDate()?.time ?: 0
                }
                adapter.updateItems(listaOrdenada)
                binding.tvCantidadComentarios.text = "${lista.size} comentarios"
            }
    }

    // Carga la publicación desde Firestore y la muestra en la parte superior del fragment
    private fun cargarPublicacion(idPub: String) {
        db.collection("publicaciones").document(idPub)
            .get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener

                try {
                    val publicacion = Publicacion.desdeDoc(doc)
                    db.collection("usuarios").document(publicacion.id_usuario)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            userCache[publicacion.id_usuario] = Usuario(
                                id = userDoc.id,
                                nombre_usuario = userDoc.getString("nombre_usuario") ?: "usuario",
                                nombre_mostrado = userDoc.getString("nombre_mostrado") ?: "",
                                foto_url = userDoc.getString("foto_url"),
                                email = userDoc.getString("email") ?: "",
                                nombre_usuario_lower = userDoc.getString("nombre_usuario_lower") ?: ""
                            )
                            mostrarPublicacion(publicacion)
                        }
                        .addOnFailureListener { mostrarPublicacion(publicacion) }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error al cargar publicación", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Renderiza la publicación usando el ViewHolder de PublicacionAdapter y deshabilita el botón de comentar
    private fun mostrarPublicacion(publicacion: Publicacion) {

        val publicacionBinding = ItemPublicacionBinding.bind(binding.includePublicacion.root)

        val viewHolder = PublicacionAdapter.PublicacionViewHolder(publicacionBinding)

        //
        viewHolder.bind(
            publicacion = publicacion,
            userCache = userCache, // Cache vacío (no importa para 1 sola publicación)
            onLikeClick = { alternarMeGustas(it) },
            onCommentClick = { /* Ya estamos en comentarios, deshabilitar */ },
            onAvatarClick = { uid ->
                if (uid == auth.currentUser?.uid) {
                    findNavController().navigate(R.id.perfilFragment)
                } else {
                    val bundle = Bundle().apply { putString("uid", uid) }
                    findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
                }
            },
            onLongClick = { pub ->
                ReporteHelper.mostrarDialogoPublicacion(
                    fragment = this,
                    publicacion = pub
                )
            }
        )

        // Deshabilitar botón de comentarios
        publicacionBinding.btnComentar.isEnabled = false
        publicacionBinding.btnComentar.alpha = 0.5f
    }

    //Metodo para dar/quitar like desde esta pantalla
    private fun alternarMeGustas(publicacion: Publicacion) {
        val uid = auth.currentUser?.uid ?: return
        val idLike = "${publicacion.id_publicacion}_$uid"
        val likeRef = db.collection("likes").document(idLike)
        val pubRef = db.collection("publicaciones").document(publicacion.id_publicacion)

        db.runTransaction { tx ->
            val likeDoc = tx.get(likeRef)
            val pDoc = tx.get(pubRef)
            val actuales = (pDoc.getLong("cantidad_likes") ?: 0L).toInt()

            if (likeDoc.exists()) {
                tx.delete(likeRef)
                tx.update(pubRef, "cantidad_likes", kotlin.math.max(0, actuales - 1))
            } else {
                tx.set(
                    likeRef, mapOf(
                        "id_publicacion" to publicacion.id_publicacion,
                        "id_usuario" to uid,
                        "creado_en" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(pubRef, "cantidad_likes", actuales + 1)
            }
        }.addOnSuccessListener {
            // Recargar la publicación para actualizar el contador
            cargarPublicacion(publicacion.id_publicacion)
        }
    }

    // Crea un nuevo comentario en Firestore y actualiza el contador de comentarios de la publicación mediante transacción
    private fun enviarComentario(idPublicacion: String) {
        val uid = auth.currentUser?.uid ?: return
        val texto = binding.etComentario.text?.toString()?.trim()

        if(texto.isNullOrBlank()){
            Toast.makeText(requireContext(), "Escribe un comentario", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnEnviar.isEnabled = false

        val comentarioRef = db.collection("comentarios").document()
        val publicacionRef = db.collection("publicaciones").document(idPublicacion)

        db.runTransaction { tx ->
            val pubDoc = tx.get(publicacionRef)
            val cantidadActual = (pubDoc.getLong("cantidad_comentarios") ?: 0L).toInt()

            tx.set(comentarioRef, mapOf(
                "id_publicacion" to idPublicacion,
                "id_usuario" to uid,
                "texto" to texto,
                "creado_en" to FieldValue.serverTimestamp()
            ))

            tx.update(publicacionRef,"cantidad_comentarios",cantidadActual+1)
        }.addOnSuccessListener {
            binding.etComentario.text?.clear()
            binding.btnEnviar.isEnabled = true
            cargarPublicacion(idPublicacion)
            binding.scrollView.post {
                binding.scrollView.fullScroll(View.FOCUS_UP)
            }
            Toast.makeText(requireContext(), "Comentario publicado", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e->
            binding.btnEnviar.isEnabled = true
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        listener?.remove()
        adapter.clearCache()
        userCache.clear()
        _binding = null
        super.onDestroyView()
    }
}