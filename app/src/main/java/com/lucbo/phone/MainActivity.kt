package com.lucbo.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues
import android.provider.MediaStore
import android.view.Surface
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore.Video.Thumbnails
import android.widget.ImageButton
import androidx.core.content.FileProvider
import android.widget.FrameLayout
import android.view.View

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 1001
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var isRecording = false
    private lateinit var statusText: TextView
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var recordingSurface: Surface? = null
    private var isMediaRecorderStarted = false
    private var videoUri: Uri? = null
    private var videoPfd: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Masquer la barre d'état et la barre de navigation pour une expérience plein écran
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        // Test Toast à chaque ouverture
        Toast.makeText(this, "Test Toast ouverture activité", Toast.LENGTH_SHORT).show()
        // Demande les permissions dès l'ouverture
        if (!checkPermissions()) {
            requestPermissions()
        }

        val btnStartStop = findViewById<ImageButton>(R.id.btnStartStop)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val statusText = findViewById<TextView>(R.id.statusText)
        val cameraPreview = findViewById<SurfaceView>(R.id.cameraPreview)
        // Suppression de la galerie vidéo : plus d'initialisation de RecyclerView ni d'adapter
        val btnFolder = findViewById<ImageButton>(R.id.btnFolder)

        this.statusText = statusText // pour garder la référence dans la classe
        surfaceHolder = cameraPreview.holder
        surfaceHolder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (checkPermissions() && shouldShowPreview()) {
                    openCamera()
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                closeCamera()
            }
        })

        btnStartStop.setOnClickListener {
            if (shouldShowPreview()) {
                // Démarrer l'enregistrement
                if (checkPermissions()) {
                    closeCamera()
                    val intent = Intent(this, VideoBackgroundService::class.java)
                    intent.action = VideoBackgroundService.ACTION_START_RECORD
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    statusText.text = "Statut : Enregistrement en arrière-plan..."
                    btnStartStop.setImageResource(R.drawable.ic_stop)
                } else {
                    requestPermissions()
                }
            } else {
                // Arrêter l'enregistrement
                val intent = Intent(this, VideoBackgroundService::class.java)
                intent.action = VideoBackgroundService.ACTION_STOP_RECORD
                startService(intent)
                statusText.text = "Statut : Arrêt demandé, vidéo en cours de sauvegarde..."
                btnStartStop.setImageResource(R.drawable.ic_record)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (checkPermissions() && shouldShowPreview()) openCamera()
                    statusText.text = "Statut : Prêt"
                }, 1500)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnFolder.setOnClickListener {
            val intent = Intent(this, VideoGalleryActivity::class.java)
            startActivity(intent)
        }

        // Suppression de la galerie vidéo : plus d'initialisation de RecyclerView ni d'adapter
    }

    private fun checkPermissions(): Boolean {
        val camera = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            camera == PackageManager.PERMISSION_GRANTED &&
            audio == PackageManager.PERMISSION_GRANTED
        } else {
            val storage = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            camera == PackageManager.PERMISSION_GRANTED &&
            audio == PackageManager.PERMISSION_GRANTED &&
            storage == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // Ajout pour lecture galerie
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                openCamera()
            } else {
                statusText.text = "Statut : Permissions requises"
            }
        }
    }

    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCameraPreview()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    statusText.text = "Statut : Erreur caméra"
                }
            }, null)
        } catch (e: Exception) {
            statusText.text = "Statut : Erreur ouverture caméra"
        }
    }

    private fun startCameraPreview() {
        try {
            val surface = surfaceHolder?.surface ?: return
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) {
                        // La caméra a été fermée, on ne fait rien
                        return
                    }
                    cameraSession = session
                    previewRequestBuilder?.build()?.let {
                        session.setRepeatingRequest(it, null, null)
                    }
                    statusText.text = "Statut : Aperçu caméra OK"
                    
                    // Ajuster la taille de l'aperçu pour les bonnes proportions
                    adjustPreviewSize()
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    statusText.text = "Statut : Erreur aperçu caméra"
                }
            }, null)
        } catch (e: Exception) {
            statusText.text = "Statut : Erreur démarrage aperçu"
        }
    }
    
    private fun adjustPreviewSize() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = map?.getOutputSizes(SurfaceHolder::class.java)

            val surfaceView = findViewById<SurfaceView>(R.id.cameraPreview)
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isLandscape = screenWidth > screenHeight

            if (isLandscape && previewSizes != null && previewSizes.isNotEmpty()) {
                // Choisir la taille la plus proche du ratio 16:9 ou 4:3
                val targetRatio = 16.0 / 9.0
                var bestSize = previewSizes[0]
                var minDiff = Double.MAX_VALUE
                for (size in previewSizes) {
                    val ratio = size.width.toDouble() / size.height.toDouble()
                    val diff = Math.abs(ratio - targetRatio)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestSize = size
                    }
                }
                // En paysage : hauteur = match_parent, largeur ajustée pour ratio, centré horizontalement
                val previewRatio = bestSize.width.toFloat() / bestSize.height.toFloat()
                val finalHeight = screenHeight
                val finalWidth = (screenHeight * previewRatio).toInt()
                val layoutParams = surfaceView.layoutParams as FrameLayout.LayoutParams
                layoutParams.width = finalWidth
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                layoutParams.gravity = android.view.Gravity.CENTER
                surfaceView.layoutParams = layoutParams
                Log.d("Camera", "Aperçu paysage hauteur max, largeur ajustée et centré : ${finalWidth}x${finalHeight}")
            } else if (previewSizes != null && previewSizes.isNotEmpty()) {
                // En portrait : format 3:4, centré horizontalement, hauteur max
                val targetRatio = 3.0 / 4.0
                var bestSize = previewSizes[0]
                var minDiff = Double.MAX_VALUE
                for (size in previewSizes) {
                    val ratio = size.width.toDouble() / size.height.toDouble()
                    val diff = Math.abs(ratio - targetRatio)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestSize = size
                    }
                }
                val previewRatio = bestSize.width.toFloat() / bestSize.height.toFloat()
                val finalHeight = screenHeight - 100 * displayMetrics.density // 100dp pour la barre boutons
                val finalWidth = (finalHeight * previewRatio).toInt()
                val layoutParams = surfaceView.layoutParams as FrameLayout.LayoutParams
                layoutParams.width = finalWidth
                layoutParams.height = finalHeight.toInt()
                layoutParams.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                surfaceView.layoutParams = layoutParams
                Log.d("Camera", "Aperçu portrait 3:4 centré, taille : ${finalWidth}x${finalHeight}")
            } else {
                // fallback
                val layoutParams = surfaceView.layoutParams
                layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                surfaceView.layoutParams = layoutParams
                Log.d("Camera", "Aperçu fallback : match_parent")
            }
        } catch (e: Exception) {
            Log.e("Camera", "Erreur lors de l'ajustement de la taille", e)
        }
    }

    private fun startRecordingVideo() {
        if (isRecording) {
            statusText.text = "Statut : Déjà en enregistrement"
            return
        }
        try {
            closeCameraSession()
            setupMediaRecorder()
            val surface = surfaceHolder?.surface ?: return
            val surfaces = mutableListOf<Surface>()
            recordingSurface = mediaRecorder?.surface
            if (recordingSurface != null) surfaces.add(recordingSurface!!)
            surfaces.add(surface)
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraSession = session
                    try {
                        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        if (recordingSurface != null) builder?.addTarget(recordingSurface!!)
                        builder?.addTarget(surface)
                        builder?.build()?.let {
                            session.setRepeatingRequest(it, null, null)
                        }
                        mediaRecorder?.start()
                        isRecording = true
                        isMediaRecorderStarted = true
                        statusText.text = "Statut : Enregistrement démarré"
                        Log.d("VideoDebug", "MediaRecorder started OK")
                    } catch (e: Exception) {
                        statusText.text = "Statut : Erreur démarrage enregistrement : ${e.message}"
                        Log.e("VideoDebug", "Erreur démarrage enregistrement", e)
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    statusText.text = "Statut : Erreur session enregistrement"
                }
            }, null)
        } catch (e: Exception) {
            statusText.text = "Statut : Erreur préparation enregistrement : ${e.message}"
            Log.e("VideoDebug", "Erreur préparation enregistrement", e)
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : enregistrer directement dans MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoBackground")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Video.Media.DISPLAY_NAME, "VID_${System.currentTimeMillis()}.mp4")
            }
            val resolver = contentResolver
            videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            videoPfd = videoUri?.let { resolver.openFileDescriptor(it, "w") }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoPfd?.fileDescriptor)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(1280, 720)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
        } else {
            // Android 9 et moins : fichier classique
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                videoFile = getOutputMediaFile()
                setOutputFile(videoFile?.absolutePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(1280, 720)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
        }
    }

    private fun getOutputMediaFile(): File {
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VideoBackground"
        )
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(mediaStorageDir.path + File.separator + "VID_$timeStamp.mp4")
    }

    private fun addVideoToGallery(file: File?) {
        if (file == null) return
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, file.name)
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun closeCameraSession() {
        cameraSession?.close()
        cameraSession = null
    }

    private fun closeCamera() {
        closeCameraSession()
        cameraDevice?.close()
        cameraDevice = null
        mediaRecorder?.release()
        mediaRecorder = null
        videoPfd?.close()
        videoPfd = null
        videoUri = null
    }

    private fun stopRecordingVideo() {
        if (!isRecording) {
            statusText.text = "Statut : Pas d'enregistrement en cours"
            return
        }
        try {
            if (isMediaRecorderStarted) {
                mediaRecorder?.stop()
                isMediaRecorderStarted = false
            }
            mediaRecorder?.reset()
            isRecording = false
            statusText.text = "Statut : Traitement de la vidéo..."
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ : rien à faire, la vidéo est déjà dans la galerie
                    videoPfd?.close()
                    videoPfd = null
                    statusText.text = "Statut : Enregistrement arrêté (vidéo sauvegardée dans la galerie)"
                } else {
                    addVideoToGallery(videoFile)
                    statusText.text = "Statut : Enregistrement arrêté (vidéo sauvegardée dans la galerie)"
                }
                startCameraPreview()
            }, 500)
        } catch (e: Exception) {
            statusText.text = "Statut : Erreur arrêt enregistrement : "+e.message
            Log.e("VideoDebug", "Erreur arrêt enregistrement", e)
        }
    }

    private fun shouldShowPreview(): Boolean {
        val prefs = getSharedPreferences("video_prefs", MODE_PRIVATE)
        return !prefs.getBoolean("isRecording", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
    }

    // Suppression de getAllVideos() et VideoGalleryAdapter
    // Récupère la liste des fichiers vidéo enregistrés
    private fun getAllVideos(): List<File> {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoBackground")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.extension == "mp4" || file.extension == "3gp" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
} 