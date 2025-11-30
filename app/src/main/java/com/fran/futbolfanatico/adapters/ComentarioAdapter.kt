package com.fran.futbolfanatico.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fran.futbolfanatico.databinding.ItemComentarioBinding
import com.fran.futbolfanatico.model.Comentario
import com.fran.futbolfanatico.model.Usuario
import com.fran.futbolfanatico.R
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore

class ComentarioAdapter(
    private val onAvatarClick:(String)-> Unit,
    private val onLongClick: ((Comentario) -> Unit)? = null
): RecyclerView.Adapter<ComentarioAdapter.ComentarioViewHolder>() {

    private val comentarios = mutableListOf<Comentario>()
    private val userCache = mutableMapOf<String, Usuario>()


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ComentarioViewHolder {
        val binding = ItemComentarioBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ComentarioViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ComentarioViewHolder,
        position: Int
    ) {
        holder.bind(comentarios[position],userCache,onAvatarClick, onLongClick)
    }

    override fun getItemCount(): Int =comentarios.size

    fun updateItems(nuevos: List<Comentario>){
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback(){
            override fun getOldListSize():Int=comentarios.size
            override fun getNewListSize(): Int = nuevos.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return comentarios[oldItemPosition].id_comentario == nuevos[newItemPosition].id_comentario
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return comentarios[oldItemPosition] == nuevos[newItemPosition]
            }
        })
        comentarios.clear()
        comentarios.addAll(nuevos)
        diff.dispatchUpdatesTo(this)
    }

    fun clearCache(){
        userCache.clear()
    }

    class ComentarioViewHolder(private val binding: ItemComentarioBinding):
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            comentario: Comentario,
            userCache: MutableMap<String, Usuario>,
            onAvatarClick: (String) -> Unit,
            onLongClick: ((Comentario) -> Unit)?
        ) {
            binding.tvTexto.text = comentario.texto

            //Fecha relativa
            binding.tvFecha.text = comentario.creado_en?.toDate()?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            }?.toString() ?: ""

            //Long click en comentario
            onLongClick?.let { callback ->
                binding.root.setOnLongClickListener {
                    callback(comentario)
                    true
                }
            }

                //Cargar usuario desde cache o Firestore
                val cached = userCache[comentario.id_usuario]
                if (cached != null) {
                    binding.tvUsername.text = cached.nombre_usuario
                    if (!cached.foto_url.isNullOrBlank()) {
                        binding.ivAvatar.load(cached.foto_url)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.usuario)
                    }
                    binding.ivAvatar.setOnClickListener { onAvatarClick(comentario.id_usuario) }
                } else {
                    FirebaseFirestore.getInstance()
                        .collection("usuarios")
                        .document(comentario.id_usuario)
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
                            userCache[comentario.id_usuario] = u
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                binding.tvUsername.text = u.nombre_usuario
                                if (!u.foto_url.isNullOrBlank()) {
                                    binding.ivAvatar.load(u.foto_url)
                                } else {
                                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                                }
                                binding.ivAvatar.setOnClickListener { onAvatarClick(comentario.id_usuario) }
                            }
                        }
                        .addOnFailureListener {
                            binding.ivAvatar.setImageResource(R.drawable.usuario)
                            binding.tvUsername.text = "usuario"
                        }
                }
            }

        }

}