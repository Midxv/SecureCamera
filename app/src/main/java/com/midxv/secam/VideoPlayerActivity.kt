package com.midxv.secam

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private var videoFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the player secure too!
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerView)
        videoFilePath = intent.getStringExtra("VIDEO_PATH")

        if (videoFilePath == null) {
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializePlayer()
    }

    private fun initializePlayer() {
        val file = File(videoFilePath!!)

        try {
            val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(
                applicationContext, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            // Build our custom DataSource Factory
            val dataSourceFactory = DataSource.Factory { EncryptedFileDataSource(encryptedFile) }

            // Create the MediaSource using the secure factory
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))

            exoPlayer = ExoPlayer.Builder(this).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@VideoPlayerActivity, "Decryption Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }

            playerView.player = exoPlayer

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load secure video.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}