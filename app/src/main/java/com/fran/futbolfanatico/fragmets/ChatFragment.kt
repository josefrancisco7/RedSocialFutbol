package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.MensajeAdapter
import com.fran.futbolfanatico.databinding.FragmentChatBinding
import com.fran.futbolfanatico.model.Mensaje
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query


class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = checkNotNull(_binding) {
        "No puede acceder al binding porque es nulo"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: MensajeAdapter
    private var listener: ListenerRegistration? = null

    private var conversacionId: String? = null
    private var otroUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversacionId = arguments?.getString("conversacion_id")
        otroUid = arguments?.getString("otro_uid")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val convId = conversacionId
        val otroUserId = otroUid

        if (convId == null || otroUserId == null){
            Toast.makeText(requireContext(), "Error al cargar chat", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        // Botón volver
        binding.btnVolver.setOnClickListener {
            findNavController().popBackStack()
        }

        // Botón enviar
        binding.btnEnviar.setOnClickListener {
            enviarMensaje(convId, otroUserId)
        }

        //RecyclerView
        adapter = MensajeAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true //Empezar desde abajo
        binding.rvMensajes.layoutManager = layoutManager
        binding.rvMensajes.adapter = adapter

        // Cargar datos del otro usuario
        cargarOtroUsuario(otroUserId)

        // Escuchar mensajes
        escucharMensajes(convId)

        // Marcar mensajes como leídos
        marcarComoLeido(convId)

     }


    // Carga los datos del otro usuario (nombre y foto) y configura el click en el avatar para ir a su perfil
    private fun cargarOtroUsuario(uid: String) {
        db.collection("usuarios").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val username = doc.getString("nombre_usuario") ?: "usuario"
                val fotoUrl = doc.getString("foto_url")

                binding.tvUsername.text = username
                if (!fotoUrl.isNullOrBlank()) {
                    binding.ivAvatar.load(fotoUrl)
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.usuario)
                }

                // Click en avatar para ir al perfil
                binding.ivAvatar.setOnClickListener {
                    val bundle = Bundle().apply { putString("uid", uid) }
                    findNavController().navigate(R.id.perfilOtrosUsuariosFragment, bundle)
                }
            }
    }

    // Escucha en tiempo real los mensajes de la conversación y actualiza el RecyclerView automáticamente
    private fun escucharMensajes(conversacionId: String) {
        listener?.remove()
        listener = db.collection("conversaciones")
            .document(conversacionId)
            .collection("mensajes")
            .orderBy("creado_en", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (_binding == null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener

                val lista = snap.documents.mapNotNull {
                    try {
                        Mensaje.desdeDoc(it)
                    } catch (e: Exception) {
                        null
                    }
                }

                adapter.updateItems(lista)

                // Scroll al último mensaje
                if (lista.isNotEmpty()) {
                    binding.rvMensajes.scrollToPosition(lista.size - 1)
                }
            }

    }

    // Envía un mensaje a la conversación y actualiza el contador de no leídos del otro usuario mediante transacción
    private fun enviarMensaje(conversacionId: String, otroUid: String) {
        val uid = auth.currentUser?.uid ?: return
        val texto = binding.etMensaje.text?.toString()?.trim()

        if (texto.isNullOrBlank()) return

        binding.btnEnviar.isEnabled = false

        val mensajeRef = db.collection("conversaciones")
            .document(conversacionId)
            .collection("mensajes")
            .document()

        val conversacionRef = db.collection("conversaciones").document(conversacionId)

        db.runTransaction { tx ->
            val convDoc = tx.get(conversacionRef)
            val noLeidosOtro = (convDoc.getLong("${otroUid}_no_leidos") ?: 0L).toInt()

            // Actualizar conversación
            tx.update(
                conversacionRef, mapOf(
                    "ultimo_mensaje" to texto,
                    "ultimo_mensaje_de" to uid,
                    "actualizado_en" to FieldValue.serverTimestamp(),
                    "${otroUid}_no_leidos" to noLeidosOtro + 1
                )
            )

            // Crear mensaje
            tx.set(
                mensajeRef, mapOf(
                    "de" to uid,
                    "texto" to texto,
                    "creado_en" to FieldValue.serverTimestamp(),
                    "leido" to false
                )
            )
        }.addOnSuccessListener {
            binding.etMensaje.text?.clear()
            binding.btnEnviar.isEnabled = true
        }.addOnFailureListener { e ->
            binding.btnEnviar.isEnabled = true
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Marca todos los mensajes de la conversación como leídos poniendo a 0 el contador de no leídos del usuario actual
    private fun marcarComoLeido(conversacionId: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("conversaciones").document(conversacionId)
            .update("${uid}_no_leidos", 0)
    }


    override fun onDestroyView() {
        listener?.remove()
        _binding = null
        super.onDestroyView()
    }


}