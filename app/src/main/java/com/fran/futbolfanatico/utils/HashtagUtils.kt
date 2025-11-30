package com.fran.futbolfanatico.utils

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

object HashtagUtils {

    // Extrae hashtags del texto (palabras que empiezan con #)
    fun extraerHashtags(texto: String): List<String> {
        if (texto.isBlank()) return emptyList()

        val regex = "#\\w+".toRegex()
        return regex.findAll(texto)
            .map { it.value.lowercase() }  // Convertir a minúsculas para búsqueda
            .distinct()  // Sin duplicados
            .toList()
    }

    // Convierte el texto con hashtags clickeables (para mostrar en UI)
    fun formatearHashtags(texto: String): SpannableString {
        val spannable = SpannableString(texto)
        val regex = "#\\w+".toRegex()

        regex.findAll(texto).forEach { match ->
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    // Este callback se maneja desde el adapter
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.parseColor("#90EE90")  // Verde claro
                    ds.isUnderlineText = false
                }
            }

            spannable.setSpan(
                clickableSpan,
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }
}