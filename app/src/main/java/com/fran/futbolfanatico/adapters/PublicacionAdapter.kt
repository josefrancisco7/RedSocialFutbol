package com.fran.futbolfanatico.adapters

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fran.futbolfanatico.databinding.ItemPublicacionBinding
import com.fran.futbolfanatico.model.Publicacion
import com.fran.futbolfanatico.model.Usuario
import com.google.firebase.firestore.FirebaseFirestore
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.utils.HashtagUtils
import com.google.firebase.auth.FirebaseAuth

class PublicacionAdapter(
    private val onLikeClick:(Publicacion)->Unit,
    private val onCommentClick:(Publicacion)-> Unit,
    private val onAvatarClick:(String)-> Unit,
    private val onLongClick: (Publicacion) -> Unit
) : RecyclerView.Adapter<PublicacionAdapter.PublicacionViewHolder>(){

    private val publicaciones = mutableListOf<Publicacion>()  // Lista de publicaciones a mostrar
    private val userCache = mutableMapOf<String, Usuario>() // Caché de usuarios para evitar múltiples llamadas a Firestore
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PublicacionViewHolder {
       val binding = ItemPublicacionBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return PublicacionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PublicacionViewHolder,
        position: Int
    ) {
       val publicacion= publicaciones[position]
        holder.bind(publicacion,userCache,onLikeClick,onCommentClick,onAvatarClick,onLongClick)
    }

    override fun getItemCount(): Int = publicaciones.size

    //Actualiza la lista con DiffUtil
    fun updateItems(nueva:List<Publicacion>){
        val diff= DiffUtil.calculateDiff(object : DiffUtil.Callback(){

            override fun getOldListSize(): Int = publicaciones.size
            override fun getNewListSize(): Int = nueva.size
            // Compara por ID
            override fun areItemsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int
            ): Boolean {
              return  publicaciones[oldItemPosition].id_publicacion == nueva[newItemPosition].id_publicacion
            }
            // Compara por contenido completo
            override fun areContentsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int
            ): Boolean {
               return publicaciones[oldItemPosition] == nueva[newItemPosition]
            }
        })
        publicaciones.clear()
        publicaciones.addAll(nueva)
        diff.dispatchUpdatesTo(this)
    }

    fun clearCache(){
        userCache.clear()
    }


    class PublicacionViewHolder(private val binding: ItemPublicacionBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            publicacion: Publicacion,
            userCache: MutableMap<String, Usuario>,
            onLikeClick: (Publicacion) -> Unit,
            onCommentClick: (Publicacion) -> Unit,
            onAvatarClick: (String) -> Unit,
            onLongClick: (Publicacion) -> Unit
        ) {

            //Asigan el texto a la publicacion
            binding.tvTexto.text = publicacion.texto

            //Muestra la cantidad de likes y comentarios
            binding.tvLikes.text = publicacion.cantidad_likes.toString()
            binding.tvComments.text = publicacion.cantidad_comentarios.toString()

            //Verificar si el usuario actual le dio like (SIN CACHE - SIMPLE)
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                FirebaseFirestore.getInstance()
                    .collection("likes")
                    .document("${publicacion.id_publicacion}_$uid")
                    .get()
                    .addOnSuccessListener { doc ->
                        binding.btnLike.setImageResource(
                            if (doc.exists()) R.drawable.likes else R.drawable.like
                        )
                    }
            } else {
                binding.btnLike.setImageResource(R.drawable.like)
            }

            //Muestra la fecha relativa(ej. "hace dos hora")
            binding.tvFecha.text = publicacion.creado_en?.toDate()?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            }?.toString() ?: ""

            //Muestra la imagen si existe, si no la oculta
            if (publicacion.url_media.isNullOrBlank()) {
                binding.ivImagen.visibility = View.GONE
            } else {
                binding.ivImagen.visibility = View.VISIBLE
                binding.ivImagen.load(publicacion.url_media)

                // DOBLE CLICK - Configurar AQUÍ cuando la imagen es visible
                val gestureDetector = GestureDetector(binding.root.context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        onLikeClick(publicacion)

                        // Animación de feedback
                        binding.ivImagen.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .withEndAction {
                                binding.ivImagen.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .start()
                            }
                            .start()
                        return true
                    }

                })

                binding.ivImagen.setOnTouchListener { v, event ->
                    gestureDetector.onTouchEvent(event)
                    true  //Cambiar a true para consumir el evento
                }
            }

            //Se intenta obtener el usuario desde cache
            val cached = userCache[publicacion.id_usuario]
            if (cached != null) {
                //Si esta en cache,muestra su nombre y foto (o avatar por defecto)
                binding.tvUsername.text = cached.nombre_usuario
                if (!cached.foto_url.isNullOrBlank()) {
                    binding.ivAvatar.load(cached.foto_url)
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                }
                } else {
                    //Si no esta en cache, se carga desde Firestore
                    FirebaseFirestore.getInstance()
                        .collection("usuarios")
                        .document(publicacion.id_usuario)
                        .get()
                        .addOnSuccessListener { d ->
                            val u = Usuario(
                                id = d.id,
                                nombre_usuario = d.getString("nombre_usuario") ?: "usuario",
                                nombre_mostrado = d.getString("nombre_mostrado") ?: "",
                                foto_url = d.getString("foto_url"),
                                email = d.getString("email") ?: "",
                                nombre_usuario_lower = d.getString("nombre_usuario_lower") ?: ""
                            )
                            userCache[publicacion.id_usuario] = u // Guarda en caché
                            val position = adapterPosition
                            if (position != RecyclerView.NO_POSITION) {
                                binding.tvUsername.text = u.nombre_usuario
                                if (!u.foto_url.isNullOrBlank()) {
                                    binding.ivAvatar.load(u.foto_url)
                                } else {
                                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                                }
                            }
                        }
                        .addOnFailureListener {
                            // Si falla la carga, muestra nombre genérico y avatar por defecto
                            binding.ivAvatar.setImageResource(R.drawable.usuario)
                            binding.tvUsername.text = "usuario"
                        }
                }

                // Asigna los listeners para los botones de like y comentar
                binding.ivAvatar.setOnClickListener { onAvatarClick(publicacion.id_usuario) }
                binding.btnLike.setOnClickListener { onLikeClick(publicacion) }
                binding.btnComentar.setOnClickListener { onCommentClick(publicacion) }

                // Mostrar texto con hashtags formateados
                if (publicacion.texto.isBlank()) {
                    binding.tvTexto.visibility = View.GONE
                } else {
                    binding.tvTexto.visibility = View.VISIBLE
                }

                    //  LONG CLICK en toda la publicación para reportar/eliminar
                    binding.root.setOnLongClickListener {
                        onLongClick(publicacion)
                        true
                    }

                    // Formatear hashtags como clickeables
                    val textoFormateado = HashtagUtils.formatearHashtags(publicacion.texto)
                    binding.tvTexto.text = textoFormateado
                    binding.tvTexto.movementMethod = LinkMovementMethod.getInstance()
                }

        }

    // Busca y actualiza una publicación específica en la lista por su ID y notifica el cambio al RecyclerView
    fun actualizarPublicacion(publicacionActualizada: Publicacion) {
        val index = publicaciones.indexOfFirst { it.id_publicacion == publicacionActualizada.id_publicacion }
        if (index != -1) {
            publicaciones[index] = publicacionActualizada
            notifyItemChanged(index)
        }
    }
    fun clearCaches() {
        userCache.clear()
    }
}