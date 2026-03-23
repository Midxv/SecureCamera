package com.midxv.secam

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.security.crypto.EncryptedFile
import java.io.EOFException
import java.io.InputStream

class EncryptedFileDataSource(
    private val encryptedFile: EncryptedFile
) : BaseDataSource(/* isNetwork = */ false) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            transferInitializing(dataSpec)

            val stream = encryptedFile.openFileInput()
            inputStream = stream

            // If ExoPlayer wants to seek to a specific part of the video, we must skip bytes.
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val skippedThisTime = stream.skip(dataSpec.position - skipped)
                if (skippedThisTime == 0L) {
                    if (stream.read() == -1) break else skipped++
                } else {
                    skipped += skippedThisTime
                }
            }

            if (skipped < dataSpec.position) throw EOFException("Reached end of file while seeking.")

            // FIX: Added .toLong() to C.LENGTH_UNSET to satisfy Kotlin's strict type checking
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                val available = stream.available().toLong()
                if (available == Int.MAX_VALUE.toLong()) C.LENGTH_UNSET.toLong() else available
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // FIX: Added .toLong() to C.LENGTH_UNSET
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: -1

        if (bytesRead == -1) {
            // FIX: Added .toLong() to C.LENGTH_UNSET
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) throw EOFException()
            return C.RESULT_END_OF_INPUT
        }

        // FIX: Added .toLong() to C.LENGTH_UNSET
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}