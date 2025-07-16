package com.lucbo.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.PackageManager
import android.util.Log

class VideoBackgroundService : Service() {
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var isMediaRecorderStarted = false
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var recordingSurface: android.view.Surface? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORD -> startCameraAndRecording()
            ACTION_STOP_RECORD -> stopRecording()
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
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Enregistrement vidéo")
            .setContentText("Enregistrement en cours...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notification)
    }

    private fun startCameraAndRecording() {
        setRecordingFlag(true)
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0] // caméra arrière
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                stopSelf()
                return
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    setupMediaRecorderAndSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }
            }, null)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun setupMediaRecorderAndSession() {
        mediaRecorder = MediaRecorder()
        try {
            // Utilise toujours le stockage privé, même sur Android 10+
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
            recordingSurface = mediaRecorder?.surface
            cameraDevice?.createCaptureSession(listOf(recordingSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraSession = session
                    try {
                        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        builder?.addTarget(recordingSurface!!)
                        builder?.build()?.let {
                            session.setRepeatingRequest(it, null, null)
                        }
                        try {
                            mediaRecorder?.start()
                            isMediaRecorderStarted = true
                            Log.d("VideoDebug", "MediaRecorder started OK (audio + vidéo)")
                        } catch (e: Exception) {
                            Log.e("VideoDebug", "Erreur lors du démarrage de MediaRecorder (audio)", e)
                        }
                    } catch (e: Exception) {
                        Log.e("VideoDebug", "Erreur configuration session caméra", e)
                        stopSelf()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("VideoDebug", "Erreur configuration session caméra (onConfigureFailed)")
                    stopSelf()
                }
            }, null)
        } catch (e: Exception) {
            Log.e("VideoDebug", "Erreur préparation MediaRecorder ou session caméra", e)
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            if (isMediaRecorderStarted) {
                mediaRecorder?.stop()
                isMediaRecorderStarted = false
            }
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            cameraSession?.close()
            cameraSession = null
            cameraDevice?.close()
            cameraDevice = null
            videoFile = null
            stopForeground(true)
            stopSelf()
        } finally {
            setRecordingFlag(false)
        }
    }

    private fun getOutputMediaFile(): File {
        val mediaStorageDir = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "VideoBackground"
        )
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(mediaStorageDir.path + File.separator + "VID_$timeStamp.mp4")
    }

    private fun setRecordingFlag(isRecording: Boolean) {
        val prefs = getSharedPreferences("video_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("isRecording", isRecording).apply()
    }

    companion object {
        const val ACTION_START_RECORD = "com.lucbo.phone.action.START_RECORD"
        const val ACTION_STOP_RECORD = "com.lucbo.phone.action.STOP_RECORD"
    }
} 