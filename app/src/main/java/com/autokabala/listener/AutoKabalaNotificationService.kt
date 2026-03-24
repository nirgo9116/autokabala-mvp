package com.autokabala.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AutoKabalaNotificationService : NotificationListenerService() {

    private val client = OkHttpClient()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Filter only Bit and PayBox notifications
        if (packageName != "com.bnhp.payments.paymentsapp" && packageName != "com.paybox.android") {
            return
        }

        val extras = sbn.notification.extras
        val text = extras.getString("android.text") ?: return

        sendToBackend(text)
    }

    private fun sendToBackend(notificationText: String) {
        val mediaType = "application/json".toMediaType()
        val json = "{\"text\":\"$notificationText\"}"
        val requestBody = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}/extract-payment")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotificationService", "Failed to send to backend", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.close()
            }
        })
    }
}
