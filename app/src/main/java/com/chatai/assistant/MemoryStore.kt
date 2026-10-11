package com.chatai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("chat_ai_memory", Context.MODE_PRIVATE)

    @Synchronized fun add(kind: String, text: String) {
        val key = when (kind) { "important" -> "important"; "project" -> "projects"; "crash" -> "crashes"; else -> "recent" }
        val items = JSONArray(prefs.getString(key, "[]") ?: "[]")
        items.put(JSONObject().put("time", System.currentTimeMillis()).put("text", text.take(3000)))
        val trimmed = JSONArray()
        for (i in (items.length() - 100).coerceAtLeast(0) until items.length()) trimmed.put(items.getJSONObject(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    fun readAll(): String = listOf("important", "projects", "crashes", "recent").joinToString("\n") { key ->
        "$key: ${prefs.getString(key, "[]") ?: "[]"}"
    }.take(12000)

    fun saveUserNotes(notes: String) { prefs.edit().putString("user_notes", notes.take(12000)).apply() }
    fun userNotes(): String = prefs.getString("user_notes", "") ?: ""
}
