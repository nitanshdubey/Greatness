package com.greatness.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuardService : AccessibilityService() {

    private val browsers = setOf(
        "com.android.chrome", "com.google.android.googlequicksearchbox",
        "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
        "com.opera.browser", "com.sec.android.app.sbrowser",
        "com.kiwibrowser.browser", "com.duckduckgo.mobile.android"
    )

    // matched anywhere in the text
    private val partial = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "hentai", "onlyfans",
        "redtube", "youporn", "brazzers", "chaturbate", "livejasmin", "camgirl"
    )
    // matched only as whole words (avoids blocking words like "Essex")
    private val whole = Regex("\\b(sex|nude|nudes|nsfw|naked|erotic|boobs)\\b")

    private var lastAction = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in browsers) return
        val root = rootInActiveWindow ?: return
        if (scan(root, 0)) {
            val now = System.currentTimeMillis()
            if (now - lastAction > 1500) {
                lastAction = now
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun scan(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 25) return false
        val id = node.viewIdResourceName ?: ""
        val relevant = node.isEditable ||
            id.contains("url", true) || id.contains("search", true) ||
            id.contains("omnibox", true) || id.contains("location", true)
        if (relevant) {
            val t = node.text?.toString()?.lowercase() ?: ""
            if (t.isNotEmpty() && (partial.any { t.contains(it) } || whole.containsMatchIn(t))) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (scan(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
