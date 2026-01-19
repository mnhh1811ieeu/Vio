package com.example.vio.data

/**
 * Simple in-memory cache for user display data (name, photo).
 * Process lifetime only; avoids re-fetching names/photos repeatedly from Firebase.
 */
object UserCache {
    private val nameMap: MutableMap<String, String> = mutableMapOf()
    private val photoMap: MutableMap<String, String> = mutableMapOf()

    fun put(uid: String, name: String?, photo: String?) {
        if (!name.isNullOrBlank()) nameMap[uid] = name
        if (!photo.isNullOrBlank()) photoMap[uid] = photo
    }

    fun putAll(entries: Map<String, Pair<String?, String?>>) {
        for ((uid, pair) in entries) {
            val (name, photo) = pair
            put(uid, name, photo)
        }
    }

    fun getName(uid: String): String? = nameMap[uid]

    fun getPhoto(uid: String): String? = photoMap[uid]

    fun namesSnapshot(): Map<String, String> = nameMap.toMap()
}
