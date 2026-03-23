package com.midxv.secam

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.concurrent.Executors

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingLayout: LinearLayout
    private var exoPlayer: ExoPlayer? = null
    private var tempDecryptedFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the player secure!
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerView)
        loadingLayout = findViewById(R.id.loadingLayout)

        val videoFilePath = intent.getStringExtra("VIDEO_PATH")
        if (videoFilePath == null) {
            finish()
            return
        }

        decryptAndPlay(File(videoFilePath))
    }

    private fun decryptAndPlay(encryptedVideo: File) {
        executor.execute {
            try {
                // 1. Create a hidden temporary file in the app's secure cache
                tempDecryptedFile = File(cacheDir, "temp_playback_${System.currentTimeMillis()}.mp4")

                val masterKey = MasterKey.Builder(applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val encryptedFile = EncryptedFile.Builder(
                    applicationContext, encryptedVideo, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                // 2. Decrypt the file directly into the sandbox
                encryptedFile.openFileInput().use { input ->
                    tempDecryptedFile!!.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. Switch to Main Thread to start flawless playback
                runOnUiThread {
                    loadingLayout.visibility = View.GONE
                    playerView.visibility = View.VISIBLE

                    exoPlayer = ExoPlayer.Builder(this@VideoPlayerActivity).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(tempDecryptedFile!!)))
                        prepare()
                        playWhenReady = true // Auto-play
                    }
                    playerView.player = exoPlayer
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@VideoPlayerActivity, "Decryption Failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down the player
        exoPlayer?.release()
        exoPlayer = null
        executor.shutdown()

        // CRITICAL: Securely wipe the unencrypted video from memory immediately when closed
        tempDecryptedFile?.let {
            if (it.exists()) it.delete()
        }
    }
}