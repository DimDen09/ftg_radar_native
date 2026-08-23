package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import org.json.JSONArray

internal class RadarEventStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        RadarLocationService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun append(event: RadarQueuedEvent) = synchronized(LOCK) {
        val queue = readQueue().toMutableList().apply { add(event) }
        writeQueue(queue)
        RadarLog.info("event_persisted type=${event.type} queue_depth=${queue.size}")
    }

    fun peek(): RadarQueuedEvent? = synchronized(LOCK) { readQueue().firstOrNull() }

    fun acknowledge(id: String) = synchronized(LOCK) {
        val queue = readQueue().filterNot { it.id == id }
        writeQueue(queue)
        RadarLog.info("event_acknowledged queue_depth=${queue.size}")
    }

    fun markAttempt(id: String) = synchronized(LOCK) {
        writeQueue(readQueue().map { if (it.id == id) it.withAttempt() else it })
    }

    fun size(): Int = synchronized(LOCK) { readQueue().size }

    fun clear() = synchronized(LOCK) { prefs.edit().remove(KEY_QUEUE).commit() }

    private fun readQueue(): List<RadarQueuedEvent> = runCatching {
        val array = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
        List(array.length()) { index -> RadarQueuedEvent.fromJson(array.getJSONObject(index)) }
    }.getOrElse { emptyList() }

    private fun writeQueue(events: List<RadarQueuedEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_QUEUE, array.toString()).commit()
    }

    companion object {
        private const val KEY_QUEUE = "geofence_event_queue_v1"
        private val LOCK = Any()
    }
}
