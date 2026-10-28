package com.encryptedjournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class PageStyle(
    val fontSize: Int = 16,
    val textColor: Int = -16777216
)

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pages: List<String>, 
    val images: List<String> = emptyList(), // List of internal file paths or URIs
    val timestamp: Long = System.currentTimeMillis(),
    val mood: String? = null,
    val tags: List<String> = emptyList(),
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val templateId: String? = null,
    val voiceNotes: List<String> = emptyList(), // List of recorded audio files
    val videos: List<String> = emptyList(), // List of internal file paths for videos
    val reminderTimestamp: Long? = null,
    val alarmTimestamp: Long? = null,
    val fontSize: Int = 16, // Default font size
    val textColor: Int = -16777216, // Default Black
    val repeatType: String = "ONCE", // "ONCE" or "WEEKLY"
    val repeatDays: String = "", // Comma-separated day names
    val location: String? = null, // Story location
    val checklistJson: String = "", // JSON string representing per-page Map<Int, List<ChecklistItem>> or List<ChecklistItem>
    val pageStylesJson: String = "" // JSON string representing per-page Map<Int, PageStyle>
) {
    val content: String get() = pages.firstOrNull() ?: ""
    
    val wordCount: Int get() = pages.sumOf { it.split("\\s+".toRegex()).filter { word -> word.isNotEmpty() }.size }
    val charCount: Int get() = pages.sumOf { it.length }

    val pageStyles: Map<Int, PageStyle> get() {
        if (pageStylesJson.isBlank()) return emptyMap()
        val result = mutableMapOf<Int, PageStyle>()
        try {
            val root = JSONObject(pageStylesJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pageIdx = key.toIntOrNull() ?: continue
                val obj = root.getJSONObject(key)
                result[pageIdx] = PageStyle(
                    fontSize = obj.optInt("fontSize", fontSize),
                    textColor = obj.optInt("textColor", textColor)
                )
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return result
    }

    fun getFontSizeForPage(pageIdx: Int): Int {
        return pageStyles[pageIdx]?.fontSize ?: fontSize
    }

    fun getTextColorForPage(pageIdx: Int): Int {
        return pageStyles[pageIdx]?.textColor ?: textColor
    }

    val pageChecklists: Map<Int, List<ChecklistItem>> get() {
        if (checklistJson.isBlank()) return emptyMap()
        val result = mutableMapOf<Int, List<ChecklistItem>>()
        try {
            if (checklistJson.trim().startsWith("{")) {
                val root = JSONObject(checklistJson)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val pageIdx = key.toIntOrNull() ?: continue
                    val array = root.getJSONArray(key)
                    val list = mutableListOf<ChecklistItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            ChecklistItem(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                text = obj.optString("text", ""),
                                isChecked = obj.optBoolean("isChecked", false)
                            )
                        )
                    }
                    result[pageIdx] = list
                }
            } else if (checklistJson.trim().startsWith("[")) {
                val array = JSONArray(checklistJson)
                val list = mutableListOf<ChecklistItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ChecklistItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text", ""),
                            isChecked = obj.optBoolean("isChecked", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    result[0] = list
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return result
    }

    val checklist: List<ChecklistItem> get() = pageChecklists[0] ?: emptyList()

    companion object {
        fun encodePageStyles(map: Map<Int, PageStyle>): String {
            if (map.isEmpty()) return ""
            val root = JSONObject()
            map.forEach { (pageIdx, style) ->
                val obj = JSONObject()
                obj.put("fontSize", style.fontSize)
                obj.put("textColor", style.textColor)
                root.put(pageIdx.toString(), obj)
            }
            return root.toString()
        }

        fun encodePageChecklists(map: Map<Int, List<ChecklistItem>>): String {
            if (map.isEmpty()) return ""
            val root = JSONObject()
            map.forEach { (pageIdx, items) ->
                if (items.isNotEmpty()) {
                    val array = JSONArray()
                    items.forEach { item ->
                        val obj = JSONObject()
                        obj.put("id", item.id)
                        obj.put("text", item.text)
                        obj.put("isChecked", item.isChecked)
                        array.put(obj)
                    }
                    root.put(pageIdx.toString(), array)
                }
            }
            return root.toString()
        }

        fun encodeChecklist(items: List<ChecklistItem>): String {
            return encodePageChecklists(mapOf(0 to items))
        }
    }
}
