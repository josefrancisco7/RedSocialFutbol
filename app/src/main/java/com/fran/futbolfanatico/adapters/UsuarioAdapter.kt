package com.fran.futbolfanatico.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fran.futbolfanatico.databinding.ItemBusquedaUsuarioMensajeBinding
import com.fran.futbolfanatico.model.Usuario
import com.fran.futbolfanatico.R

class UsuarioAdapter(
    private val onClick: (Usuario) -> Unit
) : RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder>() {

    private val usuarios = mutableListOf<Usuario>()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): UsuarioViewHolder {
        val binding = ItemBusquedaUsuarioMensajeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UsuarioViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: UsuarioViewHolder,
        position: Int
    ) {
        holder.bind(usuarios[position], onClick)
    }

    override fun getItemCount(): Int = usuarios.size

    fun updateItems(nuevos: List<Usuario>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = usuarios.size
            override fun getNewListSize(): Int = nuevos.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return usuarios[oldItemPosition].id == nuevos[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return usuarios[oldItemPosition] == nuevos[newItemPosition]
            }
        })
        usuarios.clear()
        usuarios.addAll(nuevos)
        diff.dispatchUpdatesTo(this)
    }


    class UsuarioViewHolder(private val binding: ItemBusquedaUsuarioMensajeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(usuario : Usuario, onClick: (Usuario) -> Unit){
            binding.tvUsername.text = usuario.nombre_usuario
            binding.tvNombreMostrado.text = usuario.nombre_mostrado.ifBlank { usuario.nombre_usuario }
            if (!usuario.foto_url.isNullOrBlank()) {
                binding.ivAvatar.load(usuario.foto_url)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.usuario)
            }

            binding.root.setOnClickListener {
                onClick(usuario)
            }
        }
        }
}