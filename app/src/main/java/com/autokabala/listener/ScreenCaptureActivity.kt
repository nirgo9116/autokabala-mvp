package com.autokabala.listener

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Transparent activity that shows the system MediaProjection consent dialog,
 * then passes the result to BubbleService which handles the actual capture.
 * BubbleService must already be running (started via ACTION_SHOW).
 */
class ScreenCaptureActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            // Pass result to BubbleService — it will upgrade its FGS type and capture
            startService(Intent(this, BubbleService::class.java).apply {
                action = BubbleService.ACTION_START_CAPTURE
                putExtra(BubbleService.EXTRA_RESULT_CODE, resultCode)
                putExtra(BubbleService.EXTRA_DATA, data)
            })
        }
        finish()
    }
}
