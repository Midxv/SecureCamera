package com.midxv.secam

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    // Use a thread pool to decrypt multiple thumbnails simultaneously without freezing the UI
    private val executor = Executors.newFixedThreadPool(4)

    companion object {
        private const val TAG = "SeCamGallery"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. SECURE THE GALLERY (Prevent screenshots of the grid)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. SETUP RECYCLER VIEW PROGRAMMATICALLY
        recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            layoutManager = GridLayoutManager(this@GalleryActivity, 4) // 4 Columns like iOS
        }
        setContentView(recyclerView)

        loadEncryptedMedia()
    }

    private fun loadEncryptedMedia() {
        // Fetch all files, but filter out the "_thumb.jpg" files so they don't show up
        // as independent pictures in the gallery grid.
        val mainFiles = filesDir.listFiles { _, name ->
            name.startsWith("secam_") && !name.endsWith("_thumb.jpg")
        }?.sortedByDescending { it.lastModified() } ?: return

        recyclerView.adapter = SecureMediaAdapter(mainFiles)
    }

    // --- SECURE ADAPTER FOR GRID ---
    inner class SecureMediaAdapter(private val files: List<File>) : RecyclerView.Adapter<SecureMediaAdapter.MediaViewHolder>() {

        inner class MediaViewHolder(
            val container: FrameLayout,
            val imageView: ImageView,
            val playIcon: ImageView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            // Container for the image and the play button overlay
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    300 // Fixed height for square-ish grid cells
                )
                setPadding(2, 2, 2, 2) // Small black border between cells
            }

            // The actual thumbnail / photo
            val imageView = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY) // Loading state color
            }

            // The Play Button Overlay (Centered)
            val playIcon = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(80, 80, Gravity.CENTER)
                setImageResource(android.R.drawable.ic_media_play)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                visibility = View.GONE // Hidden by default
            }

            container.addView(imageView)
            container.addView(playIcon)
            return MediaViewHolder(container, imageView, playIcon)
        }

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val file = files[position]

            // Reset state for recycled views
            holder.imageView.setImageDrawable(null)

            var targetFileToDecrypt = file

            // Check if this item is a video
            if (file.name.endsWith(".mp4")) {
                holder.playIcon.visibility = View.VISIBLE

                // For videos, target the generated JPG thumbnail instead of the massive MP4
                val thumbName = file.name.replace(".mp4", "_thumb.jpg")
                val thumbFile = File(filesDir, thumbName)

                if (thumbFile.exists()) {
                    targetFileToDecrypt = thumbFile
                }
            } else {
                // It's a photo. Hide the play icon.
                holder.playIcon.visibility = View.GONE
            }

            // --- BACKGROUND DECRYPTION ---
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

                    // Decrypt stream directly into memory (RAM) as a Bitmap
                    val bitmap = encryptedFile.openFileInput().use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }

                    // Push image to the UI thread
                    runOnUiThread { holder.imageView.setImageBitmap(bitmap) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt thumbnail for ${file.name}", e)
                }
            }

            // --- CLICK LISTENER TO OPEN MEDIA ---
            holder.container.setOnClickListener {
                if (file.name.endsWith(".mp4")) {
                    // Launch the Custom Secure ExoPlayer
                    val intent = Intent(this@GalleryActivity, VideoPlayerActivity::class.java)
                    intent.putExtra("VIDEO_PATH", file.absolutePath)
                    startActivity(intent)
                } else {
                    // (Optional) For later: Launch a Fullscreen Image Viewer here for .heic photos
                    Toast.makeText(
                        this@GalleryActivity,
                        "Photo Viewer Coming Soon!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun getItemCount() = files.size
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up threads to prevent memory leaks
        executor.shutdown()
    }
}