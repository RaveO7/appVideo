package com.lucbo.phone

import android.app.*
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.PackageManager

class VideoRecordService : Service() {

    companion object {
        @Volatile
        var isRecording: Boolean = false
        const val ACTION_START_RECORDING = "com.lucbo.phone.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.lucbo.phone.action.STOP_RECORDING"
    }

    private var cameraDevice: CameraDevice? = null
    private var mediaRecorder: MediaRecorder? = null
    private var cameraSession: CameraCaptureSession? = null
    private var surface: Surface? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasAllPermissions()) {
            showPermissionErrorNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                if (!isRecording) {
                    startForegroundNotification()
                    startRecording()
                }
            }
            ACTION_STOP_RECORDING -> {
                if (isRecording) {
                    stopRecording()
                    stopForeground(true)
                    stopSelf()
                }
            }
            else -> {
                if (!isRecording) {
                    startForegroundNotification()
                    startRecording()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "video_record_channel"
        val channelName = "Video Record Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Enregistrement vidéo")
            .setContentText("Enregistrement en cours...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notification)
    }

    private fun startRecording() {
        isRecording = true
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0] // Utilise la caméra arrière
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val videoSizes = map!!.getOutputSizes(MediaRecorder::class.java)
            val videoSize = videoSizes.find { it.width == 1920 && it.height == 1080 }
                ?: videoSizes.find { it.width == 1280 && it.height == 720 }
                ?: videoSizes[0]

            // Utiliser le dossier privé comme dans VideoBackgroundService
            val file = getOutputMediaFile()
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(file.absolutePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
            lastOutputFile = file

            val surfaceTexture = SurfaceTexture(10)
            surfaceTexture.setDefaultBufferSize(videoSize.width, videoSize.height)
            surface = Surface(surfaceTexture)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaces = listOf(mediaRecorder!!.surface, surface!!)
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraSession = session
                            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            builder.addTarget(mediaRecorder!!.surface)
                            builder.addTarget(surface!!)
                            session.setRepeatingRequest(builder.build(), null, null)
                            mediaRecorder?.start()
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            stopSelf()
                        }
                    }, null)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }
            }, null)
        } catch (e: Exception) {
            Log.e("VideoRecordService", "Erreur lors du démarrage de l'enregistrement", e)
            android.os.Handler(mainLooper).post {
                android.widget.Toast.makeText(this, "Erreur: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            }
            stopSelf()
        }
    }

    private fun stopRecording() {
        isRecording = false
        var videoFile: File? = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            videoFile = lastOutputFile
        } catch (e: Exception) {
            Log.e("VideoRecordService", "Erreur lors de l'arrêt de l'enregistrement", e)
        }
        cameraSession?.close()
        cameraSession = null
        cameraDevice?.close()
        cameraDevice = null
        surface?.release()
        surface = null
        // Pas d'ajout à la galerie, tout reste dans le dossier privé
    }

    private var lastOutputFile: File? = null

    private fun getOutputMediaFile(): File {
        val mediaStorageDir = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "VideoBackground"
        )
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(mediaStorageDir.path + File.separator + "VID_$timeStamp.mp4")
        lastOutputFile = file
        return file
    }

    private fun addVideoToGallery(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return // rien à faire, déjà dans MediaStore
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.TITLE, file.name)
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.DATA, file.absolutePath)
            put(android.provider.MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(android.provider.MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun hasAllPermissions(): Boolean {
        val camera = checkSelfPermission(android.Manifest.permission.CAMERA)
        val audio = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        val storage = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        return camera == PackageManager.PERMISSION_GRANTED &&
                audio == PackageManager.PERMISSION_GRANTED &&
                storage == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionErrorNotification() {
        val channelId = "video_record_channel"
        val channelName = "Video Record Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Erreur d'enregistrement vidéo")
            .setContentText("Permissions manquantes : caméra, micro ou stockage")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, notification)
    }
} 