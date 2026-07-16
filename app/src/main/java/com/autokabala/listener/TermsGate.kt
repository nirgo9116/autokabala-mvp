package com.autokabala.listener

import android.content.Context

/**
 * Single source of truth for "has the user accepted the current terms of service".
 * Checked at every entry point that can process a payment (notification listener,
 * share intent, foreground-app bubble) — not just [MainActivity]'s own UI — since
 * those can all fire before the user ever opens the app screen.
 */
object TermsGate {
    private const val PREFS_NAME = "autokabala_prefs"
    private const val KEY_TERMS_ACCEPTED_VERSION = "terms_accepted_version"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TERMS_ACCEPTED_VERSION, 0) >= TERMS_VERSION

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TERMS_ACCEPTED_VERSION, TERMS_VERSION).apply()
    }
}
