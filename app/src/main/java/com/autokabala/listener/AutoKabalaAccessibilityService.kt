package com.autokabala.listener

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

// Stub — accessibility service is no longer used.
// Share sheet → BubbleService overlay is the capture mechanism now.
class AutoKabalaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
