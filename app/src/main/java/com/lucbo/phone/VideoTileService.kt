package com.lucbo.phone

import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VideoTileService : TileService() {
    private var isLocallyRecording = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocallyRecording) {
            stopRecording(this)
            isLocallyRecording = false
        } else {
            startRecording(this)
            isLocallyRecording = true
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            if (isLocallyRecording) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Arrêter Capture"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Démarrer Capture"
            }
            tile.updateTile()
        }
    }

    private fun startRecording(context: Context) {
        val intent = Intent(context, VideoRecordService::class.java)
        intent.action = VideoRecordService.ACTION_START_RECORDING
        context.startService(intent)
    }

    private fun stopRecording(context: Context) {
        val intent = Intent(context, VideoRecordService::class.java)
        intent.action = VideoRecordService.ACTION_STOP_RECORDING
        context.startService(intent)
    }
} 