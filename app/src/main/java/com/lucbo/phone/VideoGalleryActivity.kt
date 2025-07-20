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
import android.widget.FrameLayout

class VideoGalleryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private var videos = listOf<File>()
    private var selectionMode = false
    private val selectedVideos = mutableSetOf<File>()
    private lateinit var trashButton: ImageButton

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
        
        // Bouton trash caché par défaut
        trashButton = ImageButton(this)
        trashButton.setImageResource(android.R.drawable.ic_menu_delete)
        trashButton.setBackgroundColor(Color.TRANSPARENT)
        trashButton.visibility = View.GONE
        val trashParams = LinearLayout.LayoutParams(48, 48)
        trashParams.gravity = Gravity.CENTER_VERTICAL
        trashButton.layoutParams = trashParams
        trashButton.setOnClickListener {
            deleteSelectedVideos()
        }
        actionBar.addView(trashButton)
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
        
        val adapter = VideoAdapter(videos,
            onClick = { file, position ->
                if (selectionMode) {
                    toggleSelection(file)
                    recyclerView.adapter?.notifyItemChanged(position)
                } else {
                    val intent = Intent(this, VideoPlayerActivity::class.java)
                    intent.putExtra("position", position)
                    startActivity(intent)
                }
            },
            onLongClick = { file, position ->
                if (!selectionMode) {
                    selectionMode = true
                    selectedVideos.clear()
                    toggleSelection(file)
                    trashButton.visibility = View.VISIBLE
                    recyclerView.adapter?.notifyDataSetChanged()
                }
            },
            isSelected = { file -> selectedVideos.contains(file) },
            selectionModeProvider = { selectionMode }
        )
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

    private fun toggleSelection(file: File) {
        if (selectedVideos.contains(file)) selectedVideos.remove(file) else selectedVideos.add(file)
        if (selectedVideos.isEmpty()) {
            selectionMode = false
            trashButton.visibility = View.GONE
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    private fun deleteSelectedVideos() {
        selectedVideos.forEach { it.delete() }
        videos = getAllVideos()
        selectedVideos.clear()
        selectionMode = false
        trashButton.visibility = View.GONE
        recyclerView.adapter = VideoAdapter(videos,
            onClick = { file, position ->
                if (selectionMode) {
                    toggleSelection(file)
                    recyclerView.adapter?.notifyItemChanged(position)
                } else {
                    val intent = Intent(this, VideoPlayerActivity::class.java)
                    intent.putExtra("position", position)
                    startActivity(intent)
                }
            },
            onLongClick = { file, position ->
                if (!selectionMode) {
                    selectionMode = true
                    selectedVideos.clear()
                    toggleSelection(file)
                    trashButton.visibility = View.VISIBLE
                    recyclerView.adapter?.notifyDataSetChanged()
                }
            },
            isSelected = { file -> selectedVideos.contains(file) },
            selectionModeProvider = { selectionMode }
        )
    }

    class VideoAdapter(
        private val videos: List<File>,
        private val onClick: (File, Int) -> Unit,
        private val onLongClick: (File, Int) -> Unit,
        private val isSelected: (File) -> Boolean,
        private val selectionModeProvider: () -> Boolean
    ) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
        
        class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.video_thumbnail)
            val duration: TextView = itemView.findViewById(R.id.video_duration)
            val date: TextView = itemView.findViewById(R.id.video_date)
            val selectedOverlay: ImageView = itemView.findViewById(R.id.video_selected_overlay)
            val selectedBg: View = itemView.findViewById(R.id.video_selected_bg)
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
            
            // Afficher la date en haut de la vignette
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            holder.date.text = file.nameWithoutExtension
            // Ajout d'une date en haut de la vignette
            val parent = holder.thumbnail.parent as android.widget.FrameLayout
            var dateOverlay: TextView? = parent.findViewWithTag("date_overlay") as? TextView
            if (dateOverlay == null) {
                dateOverlay = TextView(parent.context)
                dateOverlay.tag = "date_overlay"
                val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                params.setMargins(8, 8, 8, 8)
                dateOverlay.layoutParams = params
                dateOverlay.setBackgroundColor(android.graphics.Color.parseColor("#66000000"))
                dateOverlay.setTextColor(android.graphics.Color.WHITE)
                dateOverlay.setPadding(8, 4, 8, 4)
                dateOverlay.textSize = 12f
                parent.addView(dateOverlay)
            }
            dateOverlay.text = dateFormat.format(java.util.Date(file.lastModified()))
            
            // Sélection visuelle
            val selected = isSelected(file)
            if (selectionModeProvider()) {
                holder.selectedOverlay.visibility = View.VISIBLE
                holder.selectedOverlay.setImageResource(
                    if (selected) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
                )
            } else {
                holder.selectedOverlay.visibility = View.GONE
            }
            holder.selectedBg.visibility = View.GONE
            // Gestion des clics
            val itemView = holder.itemView
            itemView.setOnClickListener {
                if (selectionModeProvider()) {
                    onClick(file, position)
                } else {
                    onClick(file, position)
                }
            }
            itemView.setOnLongClickListener {
                onLongClick(file, position)
                true
            }
            holder.selectedOverlay.setOnClickListener {
                if (selectionModeProvider()) {
                    onClick(file, position)
                }
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