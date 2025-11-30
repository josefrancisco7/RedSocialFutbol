package com.fran.futbolfanatico.fragmets

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.databinding.FragmentCrearPublicacionBinding
import com.fran.futbolfanatico.utils.HashtagUtils
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File

class CrearPublicacionFragment : Fragment() {

    private var _binding: FragmentCrearPublicacionBinding? = null
    private val binding get() = checkNotNull(_binding) {
        "No puede acceder al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val SUPABASE_URL = ""
    private val SUPABASE_ANON_KEY = ""
    private val BUCKET = ""

    private var imagenUri: Uri? = null
    private val io = CoroutineScope(Dispatchers.IO)
    private val http = OkHttpClient()
    private val PROGRESS_TAG = "progress_inline"

    // Launcher para seleccionar imagen
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            lanzarCrop(uri)
        }
    }

    // Launcher para resultado del crop
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                imagenUri = resultUri
                binding.ivPreview.visibility = View.VISIBLE
                binding.ivPreview.load(resultUri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrearPublicacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarBarra(binding.includeBottomBar.root)
        resaltarCrear(binding.includeBottomBar.root)

        binding.btnElegirImagen.setOnClickListener {
            pickMedia.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }

        binding.btnPublicar.setOnClickListener { publicar() }
    }

    // Inicia UCrop para recortar la imagen seleccionada con ratio 16:9 antes de publicarla
    private fun lanzarCrop(sourceUri: Uri) {
        val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))

        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)  //  Forzar ratio fijo
            setToolbarColor(resources.getColor(R.color.green_light, null))
            setToolbarWidgetColor(resources.getColor(R.color.black, null))
        }

        val displayMetrics = resources.displayMetrics
        val maxHeightPx = (220 * displayMetrics.density).toInt()

        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .withAspectRatio(16f, 9f)  //  Ratio 16:9 (horizontal, como Twitter/Instagram)
            .withMaxResultSize(displayMetrics.widthPixels, maxHeightPx)

        cropLauncher.launch(uCrop.getIntent(requireContext()))
    }

    // Valida los datos, extrae hashtags del texto, sube la imagen a Supabase y crea la publicación en Firestore
    private fun publicar() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val texto = binding.etTexto.text?.toString()?.trim().orEmpty()
        if (texto.isEmpty() && imagenUri == null) {
            Toast.makeText(requireContext(), "Escribe algo o elige una imagen", Toast.LENGTH_SHORT).show()
            return
        }
        barraProgreso(true)

        val postRef = db.collection("publicaciones").document()
        val postId = postRef.id

        // Extraer hashtags del texto
        val etiquetas = HashtagUtils.extraerHashtags(texto)

        io.launch {
            try {
                val publicUrl = imagenUri?.let { subirASupabase(it, uid, postId) }

                val data = mapOf(
                    "id_usuario" to uid,
                    "texto" to texto,
                    "tipo" to if (publicUrl == null) "texto" else "imagen",
                    "url_media" to (publicUrl ?: ""),
                    "etiquetas" to etiquetas ,
                    "cantidad_likes" to 0,
                    "cantidad_comentarios" to 0,
                    "creado_en" to FieldValue.serverTimestamp()
                )
                postRef.set(data).await()

                launch(Dispatchers.Main) {
                    barraProgreso(false)
                    Toast.makeText(requireContext(), "Publicado ✅", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.paginaInicioFragment)
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    barraProgreso(false)
                    Toast.makeText(
                        requireContext(),
                        e.localizedMessage ?: "Error al publicar",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Sube la imagen a Supabase Storage en la carpeta posts del usuario y retorna la URL pública
    private suspend fun subirASupabase(uri: Uri, uid: String, postId: String): String {
        val ext = obtenerExtensionDesdeMime(requireContext().contentResolver.getType(uri))
        val path = "posts/$uid/${postId}.$ext"

        val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }

        val mediaType = requireContext().contentResolver.getType(uri)?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, bytes)

        val putUrl = "$SUPABASE_URL/storage/v1/object/$BUCKET/$path"
        val req = Request.Builder()
            .url(putUrl)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .put(body)
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Falló subida (${resp.code}): ${resp.body?.string()}")
        }

        return "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$path"
    }

    // Convierte el tipo MIME de la imagen a su extensión correspondiente (jpg, png, webp)
    private fun obtenerExtensionDesdeMime(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    // Muestra u oculta la barra de progreso mientras se procesa la publicación
    private fun barraProgreso(loading: Boolean) {
        binding.btnPublicar.isEnabled = !loading
        binding.btnElegirImagen.isEnabled = !loading

        val existing = binding.root.findViewWithTag<LinearProgressIndicator?>(PROGRESS_TAG)
        if (loading && existing == null) {
            val p = LinearProgressIndicator(requireContext()).apply {
                id = View.generateViewId()
                tag = PROGRESS_TAG
                isIndeterminate = true
            }
            (binding.root as ViewGroup).addView(
                p,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6)
            )
        } else if (!loading && existing != null) {
            (binding.root as ViewGroup).removeView(existing)
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

    private fun resaltarCrear(bar: View) {
        val activos = setOf(R.id.btnCrear)
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
        _binding = null
        super.onDestroyView()
    }
}
