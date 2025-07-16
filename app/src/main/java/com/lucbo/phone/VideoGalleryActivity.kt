package com.lucbo.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoGalleryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private var videos = listOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        videos = getAllVideos()
        
        if (videos.isEmpty()) {
            Toast.makeText(this, "Aucune vidéo trouvée", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Layout principal style natif
        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.setBackgroundColor(Color.WHITE)
        
        // Barre d'action style natif
        val actionBar = LinearLayout(this)
        actionBar.orientation = LinearLayout.HORIZONTAL
        actionBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        actionBar.setPadding(16, 16, 16, 16)
        actionBar.setBackgroundColor(Color.parseColor("#F5F5F5"))
        
        // Titre style natif
        val titleText = TextView(this)
        titleText.text = "Albums (${videos.size})"
        titleText.textSize = 20f
        titleText.setTextColor(Color.BLACK)
        titleText.gravity = Gravity.CENTER_VERTICAL
        titleText.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        titleText.setPadding(16, 0, 0, 0)
        actionBar.addView(titleText)
        
        rootLayout.addView(actionBar)
        
        // RecyclerView pour la grille
        recyclerView = RecyclerView(this)
        recyclerView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setBackgroundColor(Color.WHITE)
        
        val adapter = VideoAdapter(videos) { file, position ->
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("position", position)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        
        rootLayout.addView(recyclerView)

        // Bouton retour en bas
        val btnBackBottom = android.widget.Button(this)
        btnBackBottom.text = "Retour caméra"
        btnBackBottom.textSize = 18f
        btnBackBottom.setTextColor(Color.WHITE)
        btnBackBottom.setBackgroundColor(Color.parseColor("#2196F3"))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER_HORIZONTAL
        params.setMargins(0, 16, 0, 32)
        btnBackBottom.layoutParams = params
        btnBackBottom.setOnClickListener { finish() }
        rootLayout.addView(btnBackBottom)
        setContentView(rootLayout)
    }

    private fun getAllVideos(): List<File> {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoBackground")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.extension == "mp4" || file.extension == "3gp" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    class VideoAdapter(
        private val videos: List<File>,
        private val onClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
        
        class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.video_thumbnail)
            val duration: TextView = itemView.findViewById(R.id.video_duration)
            val date: TextView = itemView.findViewById(R.id.video_date)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.video_item, parent, false)
            return VideoViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val file = videos[position]
            
            // Générer la vignette
            generateThumbnail(file, holder.thumbnail)
            
            // Afficher la durée
            val duration = getVideoDuration(file)
            holder.duration.text = formatDuration(duration)
            
            // Afficher la date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            holder.date.text = dateFormat.format(Date(file.lastModified()))
            
            // Clic sur l'item
            val itemView = holder.itemView
            itemView.setOnClickListener {
                onClick(file, position)
            }
        }
        
        override fun getItemCount() = videos.size
        
        private fun generateThumbnail(file: File, imageView: ImageView) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val bitmap = retriever.frameAtTime
                retriever.release()
                
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(android.R.drawable.ic_media_play)
                }
            } catch (e: Exception) {
                imageView.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        
        private fun getVideoDuration(file: File): Long {
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                duration?.toLong() ?: 0
            } catch (e: Exception) {
                0
            }
        }
        
        private fun formatDuration(durationMs: Long): String {
            val minutes = durationMs / 60000
            val seconds = (durationMs % 60000) / 1000
            return String.format("%d:%02d", minutes, seconds)
        }
    }
} 