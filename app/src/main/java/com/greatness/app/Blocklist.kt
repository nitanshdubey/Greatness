package com.greatness.app

import android.content.Context

object Blocklist {
    private val domains = HashSet<String>()
    private val keywords = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "hentai", "onlyfans",
        "redtube", "youporn", "brazzers", "chaturbate", "livejasmin",
        "camgirl", "sexcam", "nsfw"
    )

    fun load(ctx: Context) {
        if (domains.isNotEmpty()) return
        try {
            ctx.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { domains.add(it) }
            }
        } catch (_: Exception) { }
    }

    fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.')
        var part = d
        while (part.contains('.')) {
            if (domains.contains(part)) return true
            part = part.substringAfter('.')
        }
        return keywords.any { d.contains(it) }
    }
}
