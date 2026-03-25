package com.autokabala.listener

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class BubbleService : Service() {

    companion object {
        const val ACTION_SHOW           = "com.autokabala.listener.BUBBLE_SHOW"
        const val ACTION_HIDE           = "com.autokabala.listener.BUBBLE_HIDE"
        const val ACTION_START_CAPTURE  = "com.autokabala.listener.BUBBLE_START_CAPTURE"
        const val EXTRA_RESULT_CODE     = "result_code"
        const val EXTRA_DATA            = "data"
        private const val NOTIF_ID  = 9001
        const val CHANNEL_ID        = "bubble_channel"

        fun show(context: Context) =
            context.startForegroundService(Intent(context, BubbleService::class.java).apply { action = ACTION_SHOW })

        fun hide(context: Context) =
            context.startService(Intent(context, BubbleService::class.java).apply { action = ACTION_HIDE })
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private var bubbleView: ImageButton? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startForegroundCompat(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                showBubble()
            }
            ACTION_HIDE -> {
                removeBubble()
                stopSelf()
            }
            ACTION_START_CAPTURE -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (code == Activity.RESULT_OK && data != null) {
                    // Upgrade FGS type to mediaProjection — required on Android 14+
                    startForegroundCompat(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    captureScreen(code, data)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), type)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val dp = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (72 * dp).toInt(), (72 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - (72 * dp).toInt() - (16 * dp).toInt()
            y = (200 * dp).toInt()
        }
        val btn = ImageButton(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(8, 8, 8, 8)
            elevation = 12f * dp

            var downRawX = 0f; var downRawY = 0f
            var downX = 0; var downY = 0
            var dragged = false
            val dragThreshold = (8 * dp)

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX; downRawY = event.rawY
                        downX = params.x; downY = params.y
                        dragged = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragged && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                            dragged = true
                        }
                        if (dragged) {
                            params.x = (downX + dx).toInt()
                            params.y = (downY + dy).toInt()
                            windowManager.updateViewLayout(this, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) onBubbleTapped()
                        true
                    }
                    else -> false
                }
            }
        }
        windowManager.addView(btn, params)
        bubbleView = btn
        Log.d("BubbleService", "Bubble shown")
    }

    fun removeBubble() {
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
    }

    private fun onBubbleTapped() {
        Log.d("BubbleService", "Bubble tapped — hiding and launching capture")
        removeBubble()
        // Brief delay so bubble is fully gone before capture
        handler.postDelayed({
            startActivity(Intent(this, ScreenCaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }, 250)
    }

    private fun captureScreen(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data)

        val dm = resources.displayMetrics
        val imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)

        // Android 14+ requires registering a callback before createVirtualDisplay()
        mp.registerCallback(object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {
                // Projection was stopped externally; clean up if needed
            }
        }, handler)

        val vd = mp.createVirtualDisplay(
            "BubbleCapture", dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        handler.postDelayed({
            val image = imageReader.acquireLatestImage()
            val bitmap: Bitmap? = image?.let {
                val plane = it.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * it.width
                val raw = Bitmap.createBitmap(it.width + rowPadding / plane.pixelStride, it.height, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(plane.buffer)
                Bitmap.createBitmap(raw, 0, 0, it.width, it.height).also { _ -> raw.recycle() }
            }
            image?.close()
            vd.release()
            mp.stop()
            imageReader.close()

            bitmap?.let { saveAndEmit(it) } ?: run {
                Log.e("BubbleService", "Failed to capture frame")
                stopSelf()
            }
        }, 600)
    }

    private fun saveAndEmit(bitmap: Bitmap) {
        try {
            val dir = File(cacheDir, "bubble_capture").also { it.mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            Log.d("BubbleService", "Captured: $uri")
            (application as AutoKabalaApplication).pendingCaptureFlow.tryEmit(uri)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e("BubbleService", "Error saving capture", e)
        } finally {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "בועת אוטוקבלה", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "מאפשר צילום מסך לפענוח תשלומים"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BubbleService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("אוטוקבלה פעילה")
            .setContentText("לחץ על הבועה לאחר אישור התשלום")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "סגור", stopIntent)
            .build()
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
