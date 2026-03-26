package com.autokabala.listener

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent trampoline Activity that deletes only images previously shared with
 * this app (tracked in SharedPreferences under KEY_SHARED_URIS).
 * On Android 11+ uses MediaStore.createDeleteRequest for user-confirmed deletion.
 */
class GalleryCleanupActivity : ComponentActivity() {

    private val deleteRequest = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        clearStoredUris()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { performCleanup() }
    }

    private suspend fun performCleanup() {
        val uris = withContext(Dispatchers.IO) { loadStoredUris() }
        if (uris.isEmpty()) {
            Toast.makeText(this, "לא נמצאו תמונות למחיקה", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(contentResolver, uris)
            deleteRequest.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            val deleted = withContext(Dispatchers.IO) {
                uris.count { uri ->
                    try { contentResolver.delete(uri, null, null) > 0 } catch (_: Exception) { false }
                }
            }
            clearStoredUris()
            Toast.makeText(this, "נמחקו $deleted תמונות", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadStoredUris(): List<Uri> {
        val prefs = getSharedPreferences(BubbleService.PREFS_NAME, MODE_PRIVATE)
        return (prefs.getStringSet(BubbleService.KEY_SHARED_URIS, emptySet()) ?: emptySet())
            .mapNotNull { uriStr ->
                try { Uri.parse(uriStr) } catch (_: Exception) { null }
            }
    }

    private fun clearStoredUris() {
        getSharedPreferences(BubbleService.PREFS_NAME, MODE_PRIVATE)
            .edit().remove(BubbleService.KEY_SHARED_URIS).apply()
    }
}
