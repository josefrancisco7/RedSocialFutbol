package com.fran.futbolfanatico.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fran.futbolfanatico.databinding.ItemConversacionBinding
import com.fran.futbolfanatico.model.Conversacion
import com.fran.futbolfanatico.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.fran.futbolfanatico.R
import com.google.firebase.firestore.FirebaseFirestore

class ConversacionAdapter(
    private val onClick:(Conversacion,String)-> Unit //conversacion, otroUid
) : RecyclerView.Adapter<ConversacionAdapter.ConversacionViewHolder>(){

    private val conversaciones = mutableListOf<Conversacion>()
    private val userCache = mutableMapOf<String, Usuario>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConversacionViewHolder {
        val binding = ItemConversacionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,false
        )
        return ConversacionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ConversacionViewHolder,
        position: Int
    ) {
       holder.bind(conversaciones[position],userCache,onClick)
    }

    override fun getItemCount(): Int = conversaciones.size

    fun updateItems(nuevas: List<Conversacion>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = conversaciones.size
            override fun getNewListSize(): Int = nuevas.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return conversaciones[oldItemPosition].id == nuevas[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return conversaciones[oldItemPosition] == nuevas[newItemPosition]
            }
        })
        conversaciones.clear()
        conversaciones.addAll(nuevas)
        diff.dispatchUpdatesTo(this)
    }

    fun clearCache() {
        userCache.clear()
    }

    class ConversacionViewHolder(private val binding: ItemConversacionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            conversacion: Conversacion,
            userCache: MutableMap<String, Usuario>,
            onClick: (Conversacion, String) -> Unit
        ) {
            //Obtener UID del otro usuario
            val otroUid = conversacion.participantes.firstOrNull {
                it != FirebaseAuth.getInstance().currentUser?.uid
            } ?: ""

            //Ultimo mensaje
            binding.tvUltimoMensaje.text = conversacion.ultimoMensaje

            // Fecha relativa
            binding.tvFecha.text = conversacion.actualizadoEn?.toDate()?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            }?.toString() ?: ""

            //Badge de no leidos
            if (conversacion.noLeidos > 0) {
                binding.tvNoLeidos.visibility = View.VISIBLE
                binding.tvNoLeidos.text = conversacion.noLeidos.toString()
            } else {
                binding.tvNoLeidos.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onClick(conversacion, otroUid)
            }

            //Cargar usuario desde cache o Firestore
            val cached = userCache[otroUid]
            if (cached != null) {
                binding.tvUsername.text = cached.nombre_usuario
                if (!cached.foto_url.isNullOrBlank()) {
                    binding.ivAvatar.load(cached.foto_url)
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                }
            } else {
                FirebaseFirestore.getInstance()
                    .collection("usuarios")
                    .document(otroUid)
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
                        userCache[otroUid] = u
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            binding.tvUsername.text = u.nombre_usuario
                            if (!u.foto_url.isNullOrBlank()) {
                                binding.ivAvatar.load(u.foto_url)
                            } else {
                                binding.ivAvatar.setImageResource(R.drawable.usuario)
                            }
                        }
                    }.addOnFailureListener {
                        binding.ivAvatar.setImageResource(R.drawable.usuario)
                        binding.tvUsername.text = "usuario"
                    }


            }
        }
    }
}