package com.autokabala.listener

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * Transparent, no-history activity that receives an image share from Bit/Paybox.
 *
 * The content:// URI permission granted to this Activity may be revoked once finish()
 * is called, so we copy the bytes to a private cache file first. BubbleService then
 * reads the stable file:// path with no permission issues, including on repeat shares.
 */
class ShareHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TermsGate.isAccepted(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
            return
        }
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val srcUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (srcUri != null) {
                val fileUri = copyToCache(srcUri)
                BubbleService.processShare(this, fileUri ?: srcUri, srcUri)
            }
        }
        finish()
    }

    private fun copyToCache(src: Uri): Uri? {
        return try {
            // Fixed filename — overwritten each share, so old files don't accumulate.
            val dest = File(cacheDir, "pending_share.jpg")
            contentResolver.openInputStream(src)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(dest)
        } catch (e: Exception) {
            Log.w("ShareHandlerActivity", "Cache copy failed, falling back to original URI", e)
            null
        }
    }
}
