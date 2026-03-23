package com.midxv.secam

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.util.concurrent.Executors

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private val executor = Executors.newFixedThreadPool(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Keep the fullscreen viewer secure!
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // 2. Build the ViewPager2 entirely in Kotlin (No XML needed!)
        viewPager = ViewPager2(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(viewPager)

        val initialPhotoPath = intent.getStringExtra("PHOTO_PATH")
        loadEncryptedPhotos(initialPhotoPath)
    }

    private fun loadEncryptedPhotos(initialPhotoPath: String?) {
        // Find only HEIC photos (exclude MP4s and _thumb.jpgs)
        val photoFiles = filesDir.listFiles { _, name ->
            name.startsWith("secam_") && name.endsWith(".heic")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (photoFiles.isEmpty()) return

        // Set up the swipe adapter
        val adapter = FullscreenPhotoAdapter(photoFiles)
        viewPager.adapter = adapter

        // Jump directly to the photo the user tapped in the grid
        val startIndex = photoFiles.indexOfFirst { it.absolutePath == initialPhotoPath }
        if (startIndex != -1) {
            viewPager.setCurrentItem(startIndex, false)
        }
    }

    // --- VIEW PAGER ADAPTER ---
    inner class FullscreenPhotoAdapter(private val files: List<File>) :
        RecyclerView.Adapter<FullscreenPhotoAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            // 3. Build the PhotoView entirely in Kotlin (Fixes the 'item_photo_page' and 'findViewById' errors!)
            val photoView = PhotoView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }
            return PhotoViewHolder(photoView)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val file = files[position]

            // Decrypt the image in the background
            executor.execute {
                try {
                    val masterKey = MasterKey.Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val encryptedFile = EncryptedFile.Builder(
                        applicationContext, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    val bitmap = encryptedFile.openFileInput().use { BitmapFactory.decodeStream(it) }

                    // Use Coil to load the Bitmap with a smooth crossfade
                    runOnUiThread {
                        holder.photoView.load(bitmap) {
                            crossfade(true)
                            crossfade(300)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun getItemCount(): Int = files.size
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}