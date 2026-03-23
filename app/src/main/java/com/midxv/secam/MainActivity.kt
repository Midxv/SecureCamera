package com.midxv.secam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var textVideoMode: TextView
    private lateinit var textPhotoMode: TextView
    private lateinit var timerPill: Chronometer
    private lateinit var transitionOverlay: View
    private lateinit var btnGallery: ShapeableImageView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isVideoMode = false

    companion object {
        private const val TAG = "SeCam"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        btnCapture = findViewById(R.id.btnCapture)
        textVideoMode = findViewById(R.id.textVideoMode)
        textPhotoMode = findViewById(R.id.textPhotoMode)
        timerPill = findViewById(R.id.timerPill)
        transitionOverlay = findViewById(R.id.transitionOverlay)
        btnGallery = findViewById(R.id.btnGallery)
        val btnFlipCamera = findViewById<ImageButton>(R.id.btnFlipCamera)

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)

        cameraExecutor = Executors.newSingleThreadExecutor()

        textPhotoMode.setOnClickListener { if (!isVideoMode || recording != null) return@setOnClickListener; isVideoMode = false; triggerModeTransition() }
        textVideoMode.setOnClickListener { if (isVideoMode) return@setOnClickListener; isVideoMode = true; triggerModeTransition() }
        btnFlipCamera.setOnClickListener { if (recording != null) return@setOnClickListener; lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT; triggerModeTransition(); startCamera() }
        btnCapture.setOnClickListener { if (isVideoMode) captureVideo() else takePhoto() }
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        loadLastThumbnail() // Load the latest photo/video into the gallery icon on launch
    }

    private fun triggerModeTransition() {
        transitionOverlay.animate().alpha(1f).setDuration(150).withEndAction {
            updateUIMode()
            transitionOverlay.animate().alpha(0f).setDuration(150).start()
        }.start()
    }

    private fun updateUIMode() {
        if (isVideoMode) {
            textVideoMode.setTextColor(Color.parseColor("#F3C623"))
            textPhotoMode.setTextColor(Color.parseColor("#888888"))
            btnCapture.setColorFilter(Color.parseColor("#E53935")) // Red for video
        } else {
            textPhotoMode.setTextColor(Color.parseColor("#F3C623"))
            textVideoMode.setTextColor(Color.parseColor("#888888"))
            btnCapture.clearColorFilter()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD)).build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageCapture, videoCapture)
            } catch (exc: Exception) { Log.e(TAG, "Binding failed", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val tempFile = File(cacheDir, "temp_img_${System.currentTimeMillis()}.heic")
        imageCapture?.takePicture(ImageCapture.OutputFileOptions.Builder(tempFile).build(), ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {}
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                cameraExecutor.execute {
                    encryptAndSaveFile(tempFile, false)
                    runOnUiThread { loadLastThumbnail() } // Update icon
                }
            }
        })
        transitionOverlay.alpha = 0.5f
        transitionOverlay.animate().alpha(0f).setDuration(100).start()
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        if (recording != null) {
            // STOP RECORDING (Animate button back to normal)
            btnCapture.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            recording?.stop()
            recording = null
            timerPill.stop()
            timerPill.visibility = View.GONE
            return
        }

        // START RECORDING (Animate button smaller like iOS)
        btnCapture.animate().scaleX(0.7f).scaleY(0.7f).setDuration(200).start()

        val tempFile = File(cacheDir, "temp_vid_${System.currentTimeMillis()}.mp4")
        recording = videoCapture.output.prepareRecording(this, FileOutputOptions.Builder(tempFile).build())
            .apply { if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Start) {
                    timerPill.base = SystemClock.elapsedRealtime()
                    timerPill.visibility = View.VISIBLE
                    timerPill.start()
                } else if (recordEvent is VideoRecordEvent.Finalize) {
                    if (!recordEvent.hasError()) {
                        cameraExecutor.execute {
                            // 1. Generate thumbnail before encryption
                            val thumbFile = extractVideoThumbnail(tempFile)
                            val baseName = "secam_${System.currentTimeMillis()}"

                            // 2. Encrypt Video
                            encryptSecurely(tempFile, File(filesDir, "$baseName.mp4"))

                            // 3. Encrypt Thumbnail
                            if (thumbFile != null) encryptSecurely(thumbFile, File(filesDir, "${baseName}_thumb.jpg"))

                            runOnUiThread { loadLastThumbnail() }
                        }
                    }
                }
            }
    }

    // Extractor Helper
    private fun extractVideoThumbnail(videoFile: File): File? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1000000) // 1 second in
            retriever.release()

            if (bitmap != null) {
                val thumbFile = File(cacheDir, "temp_thumb.jpg")
                thumbFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                thumbFile
            } else null
        } catch (e: Exception) { null }
    }

    // Unified Encryption Method
    private fun encryptAndSaveFile(tempFile: File, isVideo: Boolean) {
        val extension = if (isVideo) ".mp4" else ".heic"
        val dest = File(filesDir, "secam_${System.currentTimeMillis()}$extension")
        encryptSecurely(tempFile, dest)
    }

    private fun encryptSecurely(source: File, dest: File) {
        try {
            val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(applicationContext, dest, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            source.inputStream().use { input -> encryptedFile.openFileOutput().use { output -> input.copyTo(output) } }
        } finally { if (source.exists()) source.delete() } // Always wipe unencrypted file
    }

    // LOAD LAST IMAGE INTO GALLERY ICON
    private fun loadLastThumbnail() {
        executor.execute {
            // Find the most recent file. If it's a video, look for its thumbnail.
            val files = filesDir.listFiles { _, name -> name.startsWith("secam_") }?.sortedByDescending { it.lastModified() } ?: return@execute
            if (files.isEmpty()) return@execute

            var targetFile = files.first()
            if (targetFile.name.endsWith(".mp4")) {
                val thumbName = targetFile.name.replace(".mp4", "_thumb.jpg")
                targetFile = File(filesDir, thumbName)
            }

            if (!targetFile.exists()) return@execute

            try {
                val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val encryptedFile = EncryptedFile.Builder(applicationContext, targetFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
                val bitmap = encryptedFile.openFileInput().use { BitmapFactory.decodeStream(it) }
                runOnUiThread { btnGallery.setImageBitmap(bitmap) }
            } catch (e: Exception) { Log.e(TAG, "Failed to load icon", e) }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }
    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown(); executor.shutdown() }
}