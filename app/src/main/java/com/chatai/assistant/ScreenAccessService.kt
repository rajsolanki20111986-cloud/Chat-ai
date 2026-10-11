package com.chatai.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenAccessService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { refreshScreenText() }
    override fun onInterrupt() {}
    override fun onServiceConnected() { instance = this; refreshScreenText() }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun refreshScreenText(): String {
        val root = rootInActiveWindow ?: return "No accessible screen content. Enable Chat AI in Android Accessibility settings."
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30 || lines.size >= 250) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val label = listOf(text, desc).filter { it.isNotBlank() }.distinct().joinToString(" — ")
            if (label.isNotBlank()) lines.add(label.take(300))
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        latestScreenText = lines.joinToString("\n").take(16000)
        return latestScreenText.ifBlank { "The active app exposes no readable text. Images and video require a separate vision model." }
    }

    fun clickLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        fun search(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val matches = node.text?.toString()?.equals(label, true) == true || node.contentDescription?.toString()?.equals(label, true) == true
            if (matches && node.isEnabled && node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            for (i in 0 until node.childCount) if (search(node.getChild(i))) return true
            return false
        }
        return search(root)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile var instance: ScreenAccessService? = null
        @Volatile var latestScreenText: String = ""
    }
}
