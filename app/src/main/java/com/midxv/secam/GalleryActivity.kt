package com.midxv.secam

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import coil.load
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val executor = Executors.newFixedThreadPool(4)

    companion object {
        private const val TAG = "SeCamGallery"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Secure the gallery against screenshots
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            layoutManager = GridLayoutManager(this@GalleryActivity, 4) // 4x4 Grid
        }
        setContentView(recyclerView)

        loadEncryptedMedia()
    }

    private fun loadEncryptedMedia() {
        // SMART FILTERING:
        // This grabs your .mp4 videos AND your new .jpg photos,
        // but safely ignores the _thumb.jpg files so they don't duplicate in the grid.
        val mainFiles = filesDir.listFiles { _, name ->
            name.startsWith("secam_") && !name.endsWith("_thumb.jpg")
        }?.sortedByDescending { it.lastModified() } ?: return

        recyclerView.adapter = SecureMediaAdapter(mainFiles)
    }

    inner class SecureMediaAdapter(private val files: List<File>) : RecyclerView.Adapter<SecureMediaAdapter.MediaViewHolder>() {

        inner class MediaViewHolder(
            val container: FrameLayout,
            val imageView: ImageView,
            val playIcon: ImageView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300)
                setPadding(2, 2, 2, 2)
            }
            val imageView = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }
            val playIcon = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(80, 80, Gravity.CENTER)
                setImageResource(android.R.drawable.ic_media_play)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                visibility = View.GONE
            }

            container.addView(imageView)
            container.addView(playIcon)
            return MediaViewHolder(container, imageView, playIcon)
        }

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val file = files[position]

            // Reset ImageView to prevent flickering while recycling views
            holder.imageView.setImageDrawable(null)

            var targetFileToDecrypt = file

            if (file.name.endsWith(".mp4")) {
                holder.playIcon.visibility = View.VISIBLE
                // Swap the target to the video's thumbnail for grid display
                val thumbFile = File(filesDir, file.name.replace(".mp4", "_thumb.jpg"))
                if (thumbFile.exists()) targetFileToDecrypt = thumbFile
            } else {
                holder.playIcon.visibility = View.GONE
            }

            // Decrypt safely in the background
            executor.execute {
                try {
                    if (!targetFileToDecrypt.exists()) return@execute

                    val masterKey = MasterKey.Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val encryptedFile = EncryptedFile.Builder(
                        applicationContext,
                        targetFileToDecrypt,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    // Extract raw bytes so Coil can natively read EXIF orientation data
                    val bytes = encryptedFile.openFileInput().readBytes()

                    runOnUiThread {
                        holder.imageView.load(bytes) {
                            crossfade(true)
                            crossfade(200)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt thumbnail", e)
                }
            }

            // Launch the correct viewer based on file type
            holder.container.setOnClickListener {
                if (file.name.endsWith(".mp4")) {
                    val intent = Intent(this@GalleryActivity, VideoPlayerActivity::class.java)
                    intent.putExtra("VIDEO_PATH", file.absolutePath)
                    startActivity(intent)
                } else {
                    // This now safely handles the new .jpg photos
                    val intent = Intent(this@GalleryActivity, PhotoViewerActivity::class.java)
                    intent.putExtra("PHOTO_PATH", file.absolutePath)
                    startActivity(intent)
                }
            }
        }

        override fun getItemCount() = files.size
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}