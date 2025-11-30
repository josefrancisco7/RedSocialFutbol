package com.fran.futbolfanatico.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fran.futbolfanatico.databinding.ItemMensajeBinding
import com.fran.futbolfanatico.model.Mensaje
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.*
import com.fran.futbolfanatico.R
import java.util.concurrent.TimeUnit

class MensajeAdapter : RecyclerView.Adapter<MensajeAdapter.MensajeViewHolder>() {

    private val mensajes = mutableListOf<Mensaje>()
    private val uidActual = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MensajeViewHolder {
        val binding = ItemMensajeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MensajeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MensajeViewHolder,
        position: Int
    ) {
        holder.bind(mensajes[position], uidActual)
    }

    override fun getItemCount(): Int = mensajes.size

    fun updateItems(nuevos: List<Mensaje>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = mensajes.size
            override fun getNewListSize(): Int = nuevos.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return mensajes[oldItemPosition].id == nuevos[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return mensajes[oldItemPosition] == nuevos[newItemPosition]
            }
        })
        mensajes.clear()
        mensajes.addAll(nuevos)
        diff.dispatchUpdatesTo(this)
    }

    // Función para formatear fecha/hora
    private fun formatearTiempo(timestamp: Date?): String {
        if (timestamp == null) return ""

        val ahora = System.currentTimeMillis()
        val mensajeTime = timestamp.time
        val diferencia = ahora - mensajeTime

        // Si es menos de 24 horas, mostrar solo hora
        if (diferencia < TimeUnit.DAYS.toMillis(1)) {
            val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatoHora.format(timestamp)
        }

        // Si es de ayer, mostrar "Ayer"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = ahora
        val hoyDia = calendar.get(Calendar.DAY_OF_YEAR)
        val hoyAno = calendar.get(Calendar.YEAR)

        calendar.timeInMillis = mensajeTime
        val mensajeDia = calendar.get(Calendar.DAY_OF_YEAR)
        val mensajeAno = calendar.get(Calendar.YEAR)

        if (hoyAno == mensajeAno && hoyDia - mensajeDia == 1) {
            return "Ayer"
        }

        // Si es más de 1 día, mostrar fecha
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatoFecha.format(timestamp)
    }



    inner class MensajeViewHolder(private val binding: ItemMensajeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mensaje: Mensaje, uidActual: String) {
            val esMio = mensaje.de == uidActual

            binding.tvTexto.text = mensaje.texto

            // Fecha
            binding.tvHora.text = formatearTiempo(mensaje.creadoEn?.toDate())

            // Alineación y color
            val params = binding.mensajeContainer.layoutParams as ViewGroup.MarginLayoutParams
            val layout = binding.root as? LinearLayout
            if (esMio) {
                // Mensaje enviado (derecha, verde)
                //(binding.root as ViewGroup).gravity = Gravity.END
                layout?.gravity = Gravity.END
                params.marginStart = 64
                params.marginEnd = 8
                binding.mensajeContainer.setBackgroundResource(R.drawable.mensaje_enviado)
                binding.tvTexto.setTextColor(ContextCompat.getColor(binding.root.context, R.color.black))
                binding.tvHora.setTextColor(ContextCompat.getColor(binding.root.context, R.color.black))
            } else {
                // Mensaje recibido (izquierda, gris)
                layout?.gravity = Gravity.START
                params.marginStart = 8
                params.marginEnd = 64
                binding.mensajeContainer.setBackgroundResource(R.drawable.mensaje_recibido)
                binding.tvTexto.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                binding.tvHora.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
            }
            binding.mensajeContainer.layoutParams = params
        }
        }
}