package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * KeymapperAccessibilityService
 * Real Android Accessibility Service that injects actual screen taps using dispatchGesture
 * at the targeted pixel coordinates (X, Y).
 */
class KeymapperAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeymapperA11yService"
        var instance: KeymapperAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        /**
         * Dispatch a synthetic tap at given screen pixel coordinates
         */
        fun injectTap(x: Float, y: Float, durationMs: Long = 50L, onComplete: (() -> Unit)? = null) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot inject tap: Accessibility Service not active")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val clickPath = Path().apply {
                    moveTo(x, y)
                }
                val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()

                val dispatched = service.dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            Log.d(TAG, "Tap executed successfully at ($x, $y)")
                            onComplete?.invoke()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            Log.w(TAG, "Tap was cancelled at ($x, $y)")
                        }
                    },
                    null
                )
                if (!dispatched) {
                    Log.e(TAG, "dispatchGesture returned false")
                }
            } else {
                Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Keymapper Accessibility Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op - we only use this service for gesture injection
    }

    override fun onInterrupt() {
        Log.w(TAG, "Keymapper Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Keymapper Accessibility Service Destroyed")
    }
}
