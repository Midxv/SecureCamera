package com.midxv.secam

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.viewpager2.widget.ViewPager2
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.util.concurrent.Executors

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: FullscreenPhotoAdapter
    private val executor = Executors.newFixedThreadPool(4)
    private var photoFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Secure the viewer
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_photo_viewer)

        viewPager = findViewById(R.id.viewPager)

        val initialPhotoPath = intent.getStringExtra("PHOTO_PATH")
        loadEncryptedPhotos(initialPhotoPath)

        // setup click listeners for the pill buttons
        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener { handleRotation() }
        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener { handleDeleteConfirm() }
        findViewById<ImageButton>(R.id.btnEdit).setOnClickListener {
            Toast.makeText(this, "Edit Feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEncryptedPhotos(initialPhotoPath: String?) {
        // Query only jpg photos (ignore thumbs)
        val files = filesDir.listFiles { _, name ->
            name.startsWith("secam_") && name.endsWith(".jpg") && !name.endsWith("_thumb.jpg")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (files.isEmpty()) { finish(); return }

        // Convert to MutableList so we can delete items
        photoFiles = files.toMutableList()

        // Set up the adapter
        adapter = FullscreenPhotoAdapter(photoFiles)
        viewPager.adapter = adapter

        // Sync with clicked photo
        val startIndex = photoFiles.indexOfFirst { it.absolutePath == initialPhotoPath }
        if (startIndex != -1) {
            viewPager.setCurrentItem(startIndex, false)
        }
    }

    // --- SMART ROTATE LOGIC ---
    private fun handleRotation() {
        val currentFile = getCurrentFile() ?: return

        // Fetch the existing rotation stored for this file (defaulting to 0)
        val currentDeg = adapter.rotationMap[currentFile] ?: 0f
        val nextDeg = (currentDeg + 90f) % 360f // Add 90 and wrap around 360

        // 1. Update the memory map (Smart persistent logic)
        adapter.rotationMap[currentFile] = nextDeg

        // 2. Visually update the current View immediately for smooth performance
        val currentViewHolder = (viewPager.getChildAt(0) as RecyclerView).findViewHolderForAdapterPosition(viewPager.currentItem) as? FullscreenPhotoAdapter.PhotoViewHolder
        currentViewHolder?.photoView?.setRotationBy(90f) // Visually spins the view
    }

    // --- SECURE DELETE LOGIC ---
    private fun handleDeleteConfirm() {
        val currentFile = getCurrentFile() ?: return

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Secure Wipe")
            .setMessage("Are you sure you want to securely delete this image from your vault?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Wipe It!") { _, _ -> secureDelete(currentFile) }
            .setNegativeButton("Keep It", null)
            .show()
    }

    private fun secureDelete(fileToDelete: File) {
        // 1. Permanently wipe the encrypted file from storage
        val success = if (fileToDelete.exists()) fileToDelete.delete() else false

        if (!success) {
            Toast.makeText(this, "Error securely wiping file", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Image Securely Wiped!", Toast.LENGTH_SHORT).show()

        // 2. Update the Adapter Instantly
        val positionToRemoving = photoFiles.indexOf(fileToDelete)
        if (positionToRemoving == -1) return // Should not happen

        photoFiles.removeAt(positionToRemoving)
        adapter.notifyItemRemoved(positionToRemoving)

        // 3. Close viewer if vault is now empty
        if (photoFiles.isEmpty()) { finish() }
    }

    private fun getCurrentFile(): File? {
        if (photoFiles.isEmpty()) return null
        return photoFiles[viewPager.currentItem]
    }

    // --- ADAPTER ---
    inner class FullscreenPhotoAdapter(private val files: List<File>) :
        RecyclerView.Adapter<FullscreenPhotoAdapter.PhotoViewHolder>() {

        // Memory Map to keep track of visual rotation for each photo separately
        val rotationMap = mutableMapOf<File, Float>()

        inner class PhotoViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
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
            holder.photoView.setImageDrawable(null)

            // Apply the stored persistent rotation for this file! (Important for logic)
            val storedRotation = rotationMap[file] ?: 0f
            holder.photoView.rotation = storedRotation

            executor.execute {
                try {
                    val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                    val encryptedFile = EncryptedFile.Builder(applicationContext, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
                    val bitmap = encryptedFile.openFileInput().use { BitmapFactory.decodeStream(it) }

                    runOnUiThread {
                        holder.photoView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) { Log.e("SeCamViewer", "Decryption fail", e) }
            }
        }

        override fun getItemCount(): Int = files.size
    }

    override fun onDestroy() { super.onDestroy(); executor.shutdown() }
}