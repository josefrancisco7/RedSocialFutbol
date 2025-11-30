package com.fran.futbolfanatico.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.model.Publicacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object ReporteHelper {

    //Muestra diálogo para reportar o eliminar publicación
    fun mostrarDialogoPublicacion(
        fragment: Fragment,
        publicacion: Publicacion,
        onPublicacionEliminada: (() -> Unit)? = null
    ) {
        val context = fragment.requireContext()
        val auth = FirebaseAuth.getInstance()

        if (publicacion.id_usuario == auth.currentUser?.uid) {
            // Es tu propia publicación - opción de eliminar
            android.app.AlertDialog.Builder(context)
                .setTitle("Eliminar publicación")
                .setMessage("¿Deseas eliminar esta publicación?")
                .setPositiveButton("Eliminar") { _, _ ->
                    eliminarPublicacion(context, publicacion.id_publicacion, onPublicacionEliminada)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            // Es de otro usuario - opción de reportar
            android.app.AlertDialog.Builder(context)
                .setTitle("Reportar publicación")
                .setMessage("¿Deseas reportar esta publicación por contenido inapropiado?")
                .setPositiveButton("Reportar") { _, _ ->
                    mostrarDialogoMotivo(fragment, "publicacion", publicacion.id_publicacion)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

     //Muestra diálogo de selección de motivo de reporte
    private fun mostrarDialogoMotivo(
        fragment: Fragment,
        tipo: String,
        idItem: String
    ) {
        val context = fragment.requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reportar, null)
        val rgMotivos = dialogView.findViewById<RadioGroup>(R.id.rgMotivos)
        val etDetalle = dialogView.findViewById<EditText>(R.id.etDetalle)

        android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Motivo del reporte")
            .setPositiveButton("Enviar") { _, _ ->
                val motivoId = rgMotivos.checkedRadioButtonId
                if (motivoId == -1) {
                    Toast.makeText(context, "Selecciona un motivo", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val motivo = when (motivoId) {
                    R.id.rbSpam -> "Spam o contenido engañoso"
                    R.id.rbOfensivo -> "Contenido ofensivo o violento"
                    R.id.rbAcoso -> "Acoso o bullying"
                    R.id.rbInapropiado -> "Contenido inapropiado"
                    else -> "Otro motivo"
                }

                val detalle = etDetalle.text?.toString() ?: ""
                enviarReporte(context, tipo, idItem, motivo, detalle)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    //Envía el reporte a Firestore
    private fun enviarReporte(
        context: Context,
        tipo: String,
        idItem: String,
        motivo: String,
        detalle: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val reporte = hashMapOf(
            "tipo" to tipo,
            "id_item" to idItem,
            "id_reportante" to uid,
            "motivo" to motivo,
            "detalle" to detalle,
            "estado" to "pendiente",
            "creado_en" to FieldValue.serverTimestamp()
        )

        db.collection("reportes")
            .add(reporte)
            .addOnSuccessListener {
                Toast.makeText(context, "Reporte enviado. Gracias.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


     //Elimina una publicación de Firestore
    private fun eliminarPublicacion(
        context: Context,
        idPublicacion: String,
        onEliminada: (() -> Unit)?
    ) {
        val db = FirebaseFirestore.getInstance()

        db.collection("publicaciones").document(idPublicacion)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Publicación eliminada", Toast.LENGTH_SHORT).show()
                onEliminada?.invoke()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    //Muestra diálogo para reportar o eliminar comentario
    fun mostrarDialogoComentario(
        fragment: Fragment,
        idComentario: String,
        idUsuarioComentario: String,
        onComentarioEliminado: (() -> Unit)? = null
    ) {
        val context = fragment.requireContext()
        val auth = FirebaseAuth.getInstance()

        if (idUsuarioComentario == auth.currentUser?.uid) {
            // Es tu propio comentario - opción de eliminar
            android.app.AlertDialog.Builder(context)
                .setTitle("Eliminar comentario")
                .setMessage("¿Deseas eliminar este comentario?")
                .setPositiveButton("Eliminar") { _, _ ->
                    eliminarComentario(context, idComentario, onComentarioEliminado)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            // Es de otro usuario - opción de reportar
            android.app.AlertDialog.Builder(context)
                .setTitle("Reportar comentario")
                .setMessage("¿Deseas reportar este comentario por contenido inapropiado?")
                .setPositiveButton("Reportar") { _, _ ->
                    mostrarDialogoMotivo(fragment, "comentario", idComentario)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }


     //Elimina un comentario de Firestore
    private fun eliminarComentario(
        context: Context,
        idComentario: String,
        onEliminado: (() -> Unit)?
    ) {
        val db = FirebaseFirestore.getInstance()

        db.collection("comentarios").document(idComentario)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Comentario eliminado", Toast.LENGTH_SHORT).show()
                onEliminado?.invoke()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}