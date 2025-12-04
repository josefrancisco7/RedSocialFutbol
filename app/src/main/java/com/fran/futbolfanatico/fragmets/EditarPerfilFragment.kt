package com.fran.futbolfanatico.fragmets

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import coil.imageLoader
import coil.load
import com.fran.futbolfanatico.R
import com.fran.futbolfanatico.databinding.FragmentCrearPublicacionBinding
import com.fran.futbolfanatico.databinding.FragmentEditarPerfilBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.util.Locale


class EditarPerfilFragment : Fragment() {
    private var _binding: FragmentEditarPerfilBinding ?= null
    private val binding get() = checkNotNull(_binding){
        "No puede accedeer al binding porque es nulo. ¿Está visible la vista?"
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Variables para poder usar supabase
    private val SUPABASE_URL = ""
    private val SUPABASE_ANON_KEY = ""   // anon key (pública)
    private val BUCKET = ""


    private var nuevaFotoUri: Uri?= null
    private var fotoActualUrl: String? = null
    private val io= CoroutineScope(Dispatchers.IO)
    private val http = OkHttpClient()

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            lanzarCrop(uri)  //  Llamar al crop
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                nuevaFotoUri = resultUri
                binding.ivAvatar.load(resultUri)
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEditarPerfilBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvCambiarFoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnCancelar.setOnClickListener { findNavController().popBackStack() }
        binding.btnGuardar.setOnClickListener { guardarCambios() }
        binding.btnEliminarCuenta.setOnClickListener {
            mostrarDialogoEliminar()
        }

        cargarDatosActuales()
    }


    //Metodo para cargar los datos actuales y rellenar los campos
    private fun cargarDatosActuales(){
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener{d ->
                binding.etNombreMostrado.setText(d.getString("nombre_mostrado") ?: "")
                binding.etUsername.setText(d.getString("nombre_usuario") ?: "")
                binding.etBio.setText(d.getString("biografia") ?: "")
                fotoActualUrl = d.getString("foto_url")
                if (!fotoActualUrl.isNullOrBlank()) binding.ivAvatar.load(fotoActualUrl)
    }
    }

    //Metodo que hace as comprobaciones de los datos insertados y si es correct hace los cambios en la base datos
    private fun guardarCambios(){
        val uid = auth.currentUser?.uid ?: return

        val nombreMostrado = binding.etNombreMostrado.text?.toString()?.trim().orEmpty()
        val nuevoUsername = binding.etUsername.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val bio = binding.etBio.text?.toString()?.trim().orEmpty()


        setLoading(true)

        io.launch {
            try {
                //Subir la foto de perfil si cambio
                val urlFoto= if(nuevaFotoUri!=null){
                    subirASupabase(nuevaFotoUri!!,uid)
                }else fotoActualUrl

                //Transaccion Firestore: username unico + resto de campos

                db.runTransaction { tx->
                    val userRef = db.collection("usuarios").document(uid)
                    val userDoc= tx.get(userRef)
                    val usernameActual = userDoc.getString("nombre_usuario") ?: ""

                    if(nuevoUsername != usernameActual){
                        val nuevoRef = db.collection("nombres_usuarios").document(nuevoUsername)
                        if(tx.get(nuevoRef).exists()){
                            throw IllegalStateException("USERNAME_TAKEN")
                        }
                        tx.set(nuevoRef,mapOf("uid" to uid))
                        if(usernameActual.isNotBlank()){
                            val viejoRef= db.collection("nombres_usuarios").document(usernameActual)
                            tx.delete(viejoRef)
                        }
                    }
                        tx.update(userRef,mapOf(
                            "nombre_usuario" to nuevoUsername,
                            "nombre_usuario_lower" to nuevoUsername,
                            "nombre_mostrado" to nombreMostrado,
                            "biografia" to bio,
                            "foto_url" to (urlFoto ?: "")
                        ))
                }.await()

                withContext(Dispatchers.Main){
                    setLoading(false)

                    // Limpiar caché y recargar imagen
                    if (urlFoto != null) {
                        requireContext().imageLoader.diskCache?.remove(urlFoto)
                        requireContext().imageLoader.memoryCache?.remove(
                            coil.memory.MemoryCache.Key(urlFoto)
                        )
                    }

                    toast("Perfil actualizado")
                    findNavController().popBackStack()
                }
            }catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    when (e) {
                        is IllegalStateException ->
                            when (e.message) {
                                "USERNAME_TAKEN" -> toast("Ese nombre de usuario ya está en uso")
                                "WRONG_PASSWORD" -> toast("Contraseña incorrecta. Inténtalo de nuevo")
                                "Operación cancelada" -> toast("Operación cancelada")
                                "Debes ingresar tu contraseña" -> toast("Debes ingresar tu contraseña")
                                "EMAIL_VERIFICATION_SENT" -> {
                                }
                                else -> toast(e.localizedMessage ?: "Error")
                            }
                        is FirebaseAuthUserCollisionException ->
                            toast("Ese email ya está en uso")

                        else -> toast(e.localizedMessage ?: "Error al guardar cambios")
                    }
                }
            }
        }
    }

    // Muestra un diálogo de confirmación antes de proceder con la eliminación de la cuenta
    private fun mostrarDialogoEliminar() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar cuenta")
            .setMessage("¿Estás seguro? Esta acción es permanente y eliminará todas tus publicaciones, comentarios y datos.")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarCuenta()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Elimina todos los datos del usuario (publicaciones, comentarios, likes, seguimientos) y finalmente la cuenta de Auth
    private fun eliminarCuenta() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Eliminar publicaciones
        db.collection("publicaciones")
            .whereEqualTo("id_usuario", uid)
            .get()
            .addOnSuccessListener { pubs ->
                pubs.documents.forEach { it.reference.delete() }

                // 2. Eliminar comentarios
                db.collection("comentarios")
                    .whereEqualTo("id_usuario", uid)
                    .get()
                    .addOnSuccessListener { comments ->
                        comments.documents.forEach { it.reference.delete() }

                        // 3. Eliminar likes
                        db.collection("likes")
                            .whereEqualTo("id_usuario", uid)
                            .get()
                            .addOnSuccessListener { likes ->
                                likes.documents.forEach { it.reference.delete() }

                                // 4. Eliminar seguimientos
                                db.collection("seguimientos")
                                    .whereEqualTo("seguidor", uid)
                                    .get()
                                    .addOnSuccessListener { seg1 ->
                                        seg1.documents.forEach { it.reference.delete() }

                                        db.collection("seguimientos")
                                            .whereEqualTo("seguido", uid)
                                            .get()
                                            .addOnSuccessListener { seg2 ->
                                                seg2.documents.forEach { it.reference.delete() }

                                                // 5. Eliminar usuario de Firestore
                                                db.collection("usuarios").document(uid)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        // 6. Eliminar cuenta de Authentication
                                                        auth.currentUser?.delete()
                                                            ?.addOnSuccessListener {
                                                                Toast.makeText(requireContext(), "Cuenta eliminada", Toast.LENGTH_SHORT).show()
                                                                findNavController().navigate(R.id.loginFragment)
                                                            }
                                                            ?.addOnFailureListener { e ->
                                                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                    }
                                            }
                                    }
                            }
                    }
            }
    }

    // Metodo que sube la imagen a  Supabase Storage y devuelve la URL pública
    private suspend fun subirASupabase(uri: Uri, uid: String): String {
        // ruta dentro del bucket (carpeta opcional "posts/")
        val ext = obtenerExtensionDesdeMime(requireContext().contentResolver.getType(uri))
        val path = "avatars/$uid.$ext"

        // Intentar borrar el archivo anterior
        if (!fotoActualUrl.isNullOrBlank() && !fotoActualUrl!!.contains("avatars/usuario.png")) {
            try {
                val deleteUrl = "$SUPABASE_URL/storage/v1/object/$BUCKET/$path"
                val deleteReq = Request.Builder()
                    .url(deleteUrl)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .delete()
                    .build()
                http.newCall(deleteReq).execute().close()
            } catch (_: Exception) { }
        }
        // lee bytes
        val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val mediaType = requireContext().contentResolver.getType(uri)
            ?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()

        // IMPORTANTE: usamos el bucket codificado en la URL
        val putUrl = "$SUPABASE_URL/storage/v1/object/$BUCKET/$path?upsert=true"
        val req = Request.Builder()
            .url(putUrl)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .post(RequestBody.create(mediaType,bytes))
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Falló subida (${resp.code}): ${resp.body?.string()}")
        }

        // URL pública
        return "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$path"
    }

    // Inicia UCrop para recortar la imagen seleccionada con aspect ratio 16:9
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

    //Este metodo convierte un tipo MIME (como "image/png") en una extensión de archivo ("png")
    private fun obtenerExtensionDesdeMime(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    // Habilita o deshabilita los botones mientras se procesa una operación
    private fun setLoading(loading: Boolean) {
        binding.btnGuardar.isEnabled = !loading
        binding.btnCancelar.isEnabled = !loading
    }

    // Muestra un mensaje Toast al usuario
    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }


}
