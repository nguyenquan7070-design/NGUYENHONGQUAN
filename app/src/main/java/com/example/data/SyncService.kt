package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MeetingParticipant(
    val userId: String,
    val name: String,
    val position: String,
    val isCameraOn: Boolean,
    val isMicOn: Boolean,
    val lastHeartbeat: Long
)

object SyncService {
    private const val TAG = "SyncService"
    private const val BASE_URL = "https://kvdb.io"
    
    // Choose a unique/customizable bucket suffix to prevent collision with other applications
    var groupId: String = "ca_daklak_hcqt_v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun getUrl(key: String): String {
        return "$BASE_URL/$groupId/$key"
    }

    /**
     * Helper to PUT text data to kvdb.io
     */
    private fun writeData(key: String, data: String): Boolean {
        try {
            val url = getUrl(key)
            val requestBody = data.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return true
                } else {
                    Log.e(TAG, "Failed to write data: Code ${response.code} for key $key")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key $key to cloud", e)
        }
        return false
    }

    /**
     * Helper to GET text data from kvdb.io
     */
    private fun readData(key: String): String? {
        try {
            val url = getUrl(key)
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.string()
                } else if (response.code == 404) {
                    // Resource doesn't exist yet, which is fine
                    return null
                } else {
                    Log.e(TAG, "Failed to read data: Code ${response.code} for key $key")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading key $key from cloud", e)
        }
        return null
    }

    // --- TASK SYNC LOGIC ---

    fun uploadTasks(tasks: List<Task>): Boolean {
        try {
            val array = JSONArray()
            for (task in tasks) {
                val obj = JSONObject().apply {
                    put("title", task.title)
                    put("content", task.content)
                    put("assignedTo", task.assignedTo)
                    put("dateAssigned", task.dateAssigned)
                    put("status", task.status)
                    put("progress", task.progress)
                    put("score", task.score)
                    put("attachedImageUrl", task.attachedImageUrl ?: "")
                    put("dateCompleted", task.dateCompleted ?: -1L)
                    put("evaluation", task.evaluation ?: "")
                    put("ratingStars", task.ratingStars)
                    put("dueDate", task.dueDate ?: "")
                }
                array.put(obj)
            }
            return writeData("tasks", array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing tasks", e)
        }
        return false
    }

    fun downloadTasks(): List<Task> {
        val list = mutableListOf<Task>()
        try {
            val jsonStr = readData("tasks") ?: return emptyList()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = Task(
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    assignedTo = obj.getString("assignedTo"),
                    dateAssigned = obj.getLong("dateAssigned"),
                    status = obj.getString("status"),
                    progress = obj.getInt("progress"),
                    score = obj.getInt("score"),
                    attachedImageUrl = obj.getString("attachedImageUrl").ifBlank { null },
                    dateCompleted = obj.getLong("dateCompleted").let { if (it == -1L) null else it },
                    evaluation = obj.getString("evaluation").ifBlank { null },
                    ratingStars = obj.getInt("ratingStars"),
                    dueDate = obj.getString("dueDate").ifBlank { null }
                )
                list.add(task)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing tasks", e)
        }
        return list
    }

    // --- MESSAGE SYNC LOGIC ---

    fun uploadMessages(channelId: String, messages: List<Message>): Boolean {
        try {
            val array = JSONArray()
            // Only upload the latest 100 messages to save bandwidth
            val recentMessages = if (messages.size > 100) messages.takeLast(100) else messages
            for (msg in recentMessages) {
                val obj = JSONObject().apply {
                    put("channelId", msg.channelId)
                    put("senderId", msg.senderId)
                    put("senderName", msg.senderName)
                    put("senderPosition", msg.senderPosition)
                    put("content", msg.content)
                    put("attachmentPath", msg.attachmentPath ?: "")
                    put("isPinned", msg.isPinned)
                    put("timestamp", msg.timestamp)
                }
                array.put(obj)
            }
            return writeData("chat_$channelId", array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing messages for $channelId", e)
        }
        return false
    }

    fun downloadMessages(channelId: String): List<Message> {
        val list = mutableListOf<Message>()
        try {
            val jsonStr = readData("chat_$channelId") ?: return emptyList()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val msg = Message(
                    channelId = obj.getString("channelId"),
                    senderId = obj.getString("senderId"),
                    senderName = obj.getString("senderName"),
                    senderPosition = obj.getString("senderPosition"),
                    content = obj.getString("content"),
                    attachmentPath = obj.getString("attachmentPath").ifBlank { null },
                    isPinned = obj.getBoolean("isPinned"),
                    timestamp = obj.getLong("timestamp")
                )
                list.add(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing messages of $channelId", e)
        }
        return list
    }

    // --- MEETING PRESENCE ---

    fun registerMeetingPresence(participant: MeetingParticipant): Boolean {
        try {
            val currentList = downloadMeetingPresence().toMutableList()
            // Filter out old heartbeats of the same user
            currentList.removeAll { it.userId == participant.userId }
            // Add current state
            currentList.add(participant)
            
            // Upload current list
            val array = JSONArray()
            for (p in currentList) {
                val obj = JSONObject().apply {
                    put("userId", p.userId)
                    put("name", p.name)
                    put("position", p.position)
                    put("isCameraOn", p.isCameraOn)
                    put("isMicOn", p.isMicOn)
                    put("lastHeartbeat", p.lastHeartbeat)
                }
                array.put(obj)
            }
            return writeData("meeting_presence", array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating meeting presence", e)
        }
        return false
    }

    fun downloadMeetingPresence(): List<MeetingParticipant> {
        val list = mutableListOf<MeetingParticipant>()
        try {
            val jsonStr = readData("meeting_presence") ?: return emptyList()
            val array = JSONArray(jsonStr)
            val now = System.currentTimeMillis()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val lastHeartbeat = obj.getLong("lastHeartbeat")
                // Keep only participants with heartbeats in the last 15 seconds
                if (now - lastHeartbeat < 15000) {
                    val p = MeetingParticipant(
                        userId = obj.getString("userId"),
                        name = obj.getString("name"),
                        position = obj.getString("position"),
                        isCameraOn = obj.getBoolean("isCameraOn"),
                        isMicOn = obj.getBoolean("isMicOn"),
                        lastHeartbeat = lastHeartbeat
                    )
                    list.add(p)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing meeting presence", e)
        }
        return list
    }

    fun removeMeetingPresence(userId: String) {
        try {
            val currentList = downloadMeetingPresence().toMutableList()
            currentList.removeAll { it.userId == userId }
            
            val array = JSONArray()
            for (p in currentList) {
                val obj = JSONObject().apply {
                    put("userId", p.userId)
                    put("name", p.name)
                    put("position", p.position)
                    put("isCameraOn", p.isCameraOn)
                    put("isMicOn", p.isMicOn)
                    put("lastHeartbeat", p.lastHeartbeat)
                }
                array.put(obj)
            }
            writeData("meeting_presence", array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error removing meeting presence", e)
        }
    }
}
