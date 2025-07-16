package com.lucbo.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.graphics.Color
import android.view.Gravity
import android.view.TextureView
import android.view.Surface
import android.media.MediaPlayer
import android.view.View
import android.widget.SeekBar
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.RelativeLayout
import java.io.File
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.widget.ImageView

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private var videos = listOf<File>()
    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Masquer la barre d'état et la barre de navigation pour une expérience plein écran
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        supportActionBar?.hide()
        
        // Récupérer la position de la vidéo cliquée
        currentPosition = intent.getIntExtra("position", 0)
        videos = getAllVideos()
        
        if (videos.isEmpty()) {
            Toast.makeText(this, "Aucune vidéo trouvée", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Layout principal style lecteur natif
        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.setBackgroundColor(Color.BLACK)
        
        // ViewPager2 pour le swipe
        viewPager = ViewPager2(this)
        viewPager.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        rootLayout.addView(viewPager)
        setContentView(rootLayout)
        
        // Adapter pour les vidéos
        val adapter = VideoPagerAdapter(videos) { file ->
            // Optionnel : action lors du clic sur la vidéo
        }
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentPosition, false)
    }

    private fun getAllVideos(): List<File> {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoBackground")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.extension == "mp4" || file.extension == "3gp" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    class VideoPagerAdapter(
        private val videos: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<VideoPagerAdapter.VideoViewHolder>() {
        
        // --- Ajout : fonction pour ajuster la taille de la TextureView selon le ratio vidéo ---
        private fun adjustTextureViewSize(textureView: TextureView, videoWidth: Int, videoHeight: Int, rotation: Int) {
            val parent = textureView.parent as? View ?: return
            val viewWidth = parent.width
            val viewHeight = parent.height

            // Si la vidéo est en portrait (rotation 90 ou 270), on inverse largeur/hauteur
            val (videoW, videoH) = if (rotation == 90 || rotation == 270) {
                videoHeight to videoWidth
            } else {
                videoWidth to videoHeight
            }

            if (videoW == 0 || videoH == 0 || viewWidth == 0 || viewHeight == 0) return

            val scaleX = viewWidth.toFloat() / videoW
            val scaleY = viewHeight.toFloat() / videoH
            val scale = minOf(scaleX, scaleY)

            val scaledWidth = (videoW * scale).toInt()
            val scaledHeight = (videoH * scale).toInt()

            val layoutParams = textureView.layoutParams as FrameLayout.LayoutParams
            layoutParams.width = scaledWidth
            layoutParams.height = scaledHeight
            layoutParams.gravity = Gravity.CENTER
            textureView.layoutParams = layoutParams
        }
        
        class VideoViewHolder(val container: FrameLayout, val textureView: TextureView) : RecyclerView.ViewHolder(container) {
            var mediaPlayer: MediaPlayer? = null
            var mediaController: MediaController? = null
            var customControls: CustomControls? = null
            var updateHandler: Handler? = null
            var updateRunnable: Runnable? = null
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            // Conteneur principal
            val container = FrameLayout(parent.context)
            container.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            container.setBackgroundColor(Color.BLACK)

            // TextureView pour l'affichage vidéo
            val textureView = TextureView(parent.context)
            textureView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            container.addView(textureView)

            // ImageView pour la preview (miniature)
            val previewImage = ImageView(parent.context)
            previewImage.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            previewImage.scaleType = ImageView.ScaleType.CENTER_CROP
            container.addView(previewImage)

            // --- BARRE DE TITRE EN HAUT ---
            val titleLayout = LinearLayout(parent.context)
            titleLayout.orientation = LinearLayout.HORIZONTAL
            titleLayout.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            titleLayout.setPadding(24, 48, 24, 24) // padding haut pour ne pas coller au bord
            titleLayout.setBackgroundColor(0x66000000.toInt()) // fond semi-transparent

            // Ajout du bouton retour en haut à gauche
            val btnBackTop = ImageButton(parent.context)
            btnBackTop.setImageResource(android.R.drawable.ic_menu_revert)
            btnBackTop.setBackgroundColor(Color.TRANSPARENT)
            btnBackTop.setOnClickListener {
                (parent.context as? AppCompatActivity)?.finish()
            }
            titleLayout.addView(btnBackTop)

            val titleText = TextView(parent.context)
            titleText.text = ""
            titleText.setTextColor(Color.WHITE)
            titleText.textSize = 18f
            titleText.gravity = Gravity.CENTER
            titleText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            titleLayout.addView(titleText)
            container.addView(titleLayout)

            // --- BARRE DE CONTRÔLE EN BAS ---
            val controlsLayout = LinearLayout(parent.context)
            controlsLayout.orientation = LinearLayout.HORIZONTAL
            controlsLayout.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            controlsLayout.setPadding(24, 12, 24, 24)
            controlsLayout.setBackgroundColor(0x88000000.toInt()) // fond semi-transparent

            // Bouton lecture/pause
            val btnPlayPause = ImageButton(parent.context)
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            btnPlayPause.setBackgroundColor(Color.TRANSPARENT)
            controlsLayout.addView(btnPlayPause)

            // SeekBar
            val seekBar = SeekBar(parent.context)
            seekBar.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            controlsLayout.addView(seekBar)

            // Temps écoulé
            val timeText = TextView(parent.context)
            timeText.text = "00:00 / 00:00"
            timeText.setTextColor(Color.WHITE)
            timeText.textSize = 12f
            timeText.setPadding(16, 0, 0, 0)
            controlsLayout.addView(timeText)

            // Bouton pour forcer le mode paysage
            val btnLandscape = ImageButton(parent.context)
            btnLandscape.setImageResource(android.R.drawable.ic_menu_rotate)
            btnLandscape.setBackgroundColor(Color.TRANSPARENT)
            btnLandscape.setOnClickListener {
                val activity = parent.context as? AppCompatActivity
                val orientation = activity?.resources?.configuration?.orientation
                if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    // Changer l'icône si possible
                    btnLandscape.setImageResource(android.R.drawable.ic_menu_rotate) // Remplacer par ic_menu_crop_portrait si dispo
                } else {
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    btnLandscape.setImageResource(android.R.drawable.ic_menu_rotate)
                }
            }
            controlsLayout.addView(btnLandscape)

            // Ajout de la barre de contrôle en surimpression
            container.addView(controlsLayout)

            // Stocker les vues dans le ViewHolder pour accès ultérieur
            val holder = VideoViewHolder(container, textureView)
            holder.customControls = CustomControls(
                controlsLayout, btnPlayPause, seekBar, timeText, titleLayout, titleText, btnLandscape, previewImage, btnBackTop
            )
            return holder
        }

        data class CustomControls(
            val controlsLayout: LinearLayout,
            val btnPlayPause: ImageButton,
            val seekBar: SeekBar,
            val timeText: TextView,
            val titleLayout: LinearLayout,
            val titleText: TextView,
            val btnLandscape: ImageButton,
            val previewImage: ImageView,
            val btnBackTop: ImageButton // nouveau bouton retour en haut
        )
        
        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val file = videos[position]

            // Nettoyer le MediaPlayer précédent
            holder.mediaPlayer?.release()
            holder.mediaPlayer = null

            val controls = holder.customControls
            controls?.titleText?.text = file.name
            controls?.btnPlayPause?.setImageResource(android.R.drawable.ic_media_play)
            controls?.seekBar?.progress = 0
            controls?.seekBar?.max = 1000
            controls?.timeText?.text = "00:00 / 00:00"
            controls?.controlsLayout?.visibility = View.VISIBLE
            // Afficher la preview de la vidéo
            controls?.previewImage?.visibility = View.VISIBLE
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val bitmap: Bitmap? = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                controls?.previewImage?.setImageBitmap(bitmap)
                // --- Ajout : appliquer la rotation de la vidéo à la TextureView ---
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                holder.textureView.rotation = rotation.toFloat()
                // --- Ajout : ajuster la taille de la TextureView selon le ratio vidéo ---
                val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                holder.textureView.post {
                    adjustTextureViewSize(holder.textureView, videoWidth, videoHeight, rotation)
                }
                retriever.release()
            } catch (e: Exception) {
                controls?.previewImage?.setImageDrawable(null)
            }

            var isUserSeeking = false
            var duration = 0

            try {
                val uri = FileProvider.getUriForFile(holder.itemView.context, "com.lucbo.phone.fileprovider", file)
                android.util.Log.d("VideoPlayer", "URI créé: $uri pour le fichier: ${file.absolutePath}")

                // Créer un nouveau MediaPlayer
                holder.mediaPlayer = MediaPlayer().apply {
                    setDataSource(holder.itemView.context, uri)
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        controls?.seekBar?.max = duration
                        controls?.timeText?.text = "00:00 / " + formatTime(duration)
                        controls?.btnPlayPause?.setImageResource(android.R.drawable.ic_media_play)
                        // Attacher la surface vidéo
                        if (holder.textureView.isAvailable) {
                            mp.setSurface(android.view.Surface(holder.textureView.surfaceTexture))
                        } else {
                            holder.textureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                    // Vérifie que le MediaPlayer est toujours le bon et pas relâché
                                    if (holder.mediaPlayer != null) {
                                        try {
                                            holder.mediaPlayer?.setSurface(android.view.Surface(surface))
                                        } catch (e: IllegalStateException) {
                                            // Ignore ou log l’erreur
                                        }
                                    }
                                }
                                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                            }
                        }
                        // NE PAS démarrer la vidéo automatiquement
                        // Mise à jour de la SeekBar et du temps uniquement si la vidéo est en lecture
                        holder.updateHandler = Handler(Looper.getMainLooper())
                        holder.updateRunnable = object : Runnable {
                            override fun run() {
                                if (!isUserSeeking && mp.isPlaying) {
                                    val pos = mp.currentPosition
                                    controls?.seekBar?.progress = pos
                                    controls?.timeText?.text = formatTime(pos) + " / " + formatTime(duration)
                                }
                                if (mp.isPlaying) {
                                    holder.updateHandler?.postDelayed(this, 500)
                                }
                            }
                        }
                    }
                    setOnErrorListener { mp, what, extra ->
                        android.util.Log.e("VideoPlayer", "Erreur de lecture: what=$what, extra=$extra pour ${file.name}")
                        Toast.makeText(holder.itemView.context, "Erreur de lecture vidéo: $what", Toast.LENGTH_SHORT).show()
                        true
                    }
                    setOnCompletionListener { mp ->
                        controls?.btnPlayPause?.setImageResource(android.R.drawable.ic_media_play)
                        controls?.seekBar?.progress = duration
                    }
                }

                // Contrôles lecture/pause
                controls?.btnPlayPause?.setOnClickListener {
                    val mp = holder.mediaPlayer
                    if (mp != null) {
                        if (mp.isPlaying) {
                            mp.pause()
                            controls.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        } else {
                            // Cacher la preview au démarrage
                            controls.previewImage.visibility = View.GONE
                            mp.start()
                            controls.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                            // Démarrer la mise à jour de la SeekBar et du temps
                            holder.updateHandler?.removeCallbacksAndMessages(null)
                            holder.updateHandler?.post(holder.updateRunnable!!)
                        }
                    }
                }

                // SeekBar interaction
                controls?.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            controls.timeText.text = formatTime(progress) + " / " + formatTime(duration)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = true
                    }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = false
                        holder.mediaPlayer?.seekTo(seekBar?.progress ?: 0)
                    }
                })

                // Masquage/affichage automatique des contrôles
                fun showControls() {
                    controls?.controlsLayout?.visibility = View.VISIBLE
                    controls?.controlsLayout?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    controls?.titleLayout?.visibility = View.VISIBLE
                    controls?.titleLayout?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    holder.updateHandler?.removeCallbacksAndMessages(null)
                    holder.updateHandler?.postDelayed({
                        controls?.controlsLayout?.animate()?.alpha(0f)?.setDuration(400)?.withEndAction {
                            controls?.controlsLayout?.visibility = View.GONE
                        }?.start()
                        controls?.titleLayout?.animate()?.alpha(0f)?.setDuration(400)?.withEndAction {
                            controls?.titleLayout?.visibility = View.GONE
                        }?.start()
                    }, 3000)
                }
                holder.container.setOnClickListener {
                    if (controls?.controlsLayout?.visibility == View.VISIBLE) {
                        controls.controlsLayout.animate().alpha(0f).setDuration(400).withEndAction {
                            controls.controlsLayout.visibility = View.GONE
                        }.start()
                        controls.titleLayout.animate().alpha(0f).setDuration(400).withEndAction {
                            controls.titleLayout.visibility = View.GONE
                        }.start()
                    } else {
                        showControls()
                    }
                }
                // Afficher les contrôles au démarrage
                showControls()

                // Bouton retour
                controls?.btnBackTop?.setOnClickListener {
                    (holder.itemView.context as? AppCompatActivity)?.finish()
                }

                // Préparer la vidéo
                holder.mediaPlayer?.prepareAsync()

            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Erreur lors de la création de l'URI pour ${file.name}", e)
                Toast.makeText(holder.itemView.context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Formatage du temps mm:ss
        private fun formatTime(ms: Int): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
        
        override fun onViewRecycled(holder: VideoViewHolder) {
            super.onViewRecycled(holder)
            // Nettoyer le MediaPlayer
            holder.mediaPlayer?.release()
            holder.mediaPlayer = null
            // Nettoyer le handler
            holder.customControls?.let {
                it.seekBar.setOnSeekBarChangeListener(null)
                it.previewImage.setImageDrawable(null)
            }
            holder.updateHandler?.removeCallbacksAndMessages(null)
            holder.updateHandler = null
            holder.updateRunnable = null
            // Nettoyer le listener de la TextureView
            holder.textureView.surfaceTextureListener = null
        }
        
        override fun getItemCount() = videos.size
    }
} 