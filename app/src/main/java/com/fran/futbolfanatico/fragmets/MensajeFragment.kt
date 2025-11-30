package com.fran.futbolfanatico.fragmets

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.adapters.ConversacionAdapter
import com.fran.futbolfanatico.databinding.FragmentMensajeBinding
import com.fran.futbolfanatico.model.Conversacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query


class MensajeFragment : Fragment() {
    private var _binding: FragmentMensajeBinding? = null
    private val binding get() = checkNotNull(_binding) {
        "No puede acceder al binding porque es nulo"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: ConversacionAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMensajeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.btnNuevoMensaje.setOnClickListener {
            findNavController().navigate(R.id.buscarUsuariosMensajesFragment)
        }

        adapter = ConversacionAdapter {conversacion, otroUid ->
            val bundle = Bundle().apply {
                putString("conversacion_id",conversacion.id)
                putString("otro_uid",otroUid)
            }
            findNavController().navigate(R.id.chatFragment, bundle)
        }
        binding.rvConversaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvConversaciones.adapter = adapter

        escucharConversaciones()


        configurarBarra(binding.includeBottomBar.root)
        resaltarMensajes(binding.includeBottomBar.root)
    }

    // Escucha en tiempo real las conversaciones del usuario actual ordenadas por fecha de actualización descendente
    private fun escucharConversaciones() {
        val uid = auth.currentUser?.uid ?: return

        listener?.remove()
        listener = db.collection("conversaciones")
            .whereArrayContains("participantes",uid)
            .orderBy("actualizado_en", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if(_binding == null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener

                val lista = snap.documents.mapNotNull {
                    try {
                        Conversacion.desdeDoc(it, uid)
                    } catch (e: Exception) {
                        null
                    }
                }
                adapter.updateItems(lista)

                if (lista.isEmpty()) {
                    binding.tvVacio.visibility = View.VISIBLE
                } else {
                    binding.tvVacio.visibility = View.GONE
                }
            }
    }


    private fun configurarBarra(bar: View) {
        val nav = findNavController()
        bar.findViewById<ImageButton>(R.id.btnInicio).setOnClickListener { nav.navigate(R.id.paginaInicioFragment) }
        bar.findViewById<ImageButton>(R.id.btnBuscar).setOnClickListener { nav.navigate(R.id.busquedaFragment) }
        bar.findViewById<ImageButton>(R.id.btnCrear).setOnClickListener { nav.navigate(R.id.crearPublicacionFragment)}
        bar.findViewById<ImageButton>(R.id.btnMensajes).setOnClickListener { nav.navigate(R.id.mensajeFragment)}
        bar.findViewById<ImageButton>(R.id.btnPerfil).setOnClickListener { nav.navigate(R.id.perfilFragment)}
    }

    private fun resaltarMensajes(bar: View) {
        val activos = setOf(R.id.btnMensajes)
        val todos = listOf(R.id.btnInicio, R.id.btnBuscar, R.id.btnCrear, R.id.btnMensajes, R.id.btnPerfil)

        todos.forEach { id ->
            val btn = bar.findViewById<ImageButton>(id)
            if (id in activos) {
                btn.scaleX = 1.1f
                btn.scaleY = 1.1f
            } else {
                btn.scaleX = 1f
                btn.scaleY = 1f
            }
        }
    }

    override fun onDestroyView() {
        listener?.remove()
        adapter.clearCache()
        _binding = null
        super.onDestroyView()
    }


}