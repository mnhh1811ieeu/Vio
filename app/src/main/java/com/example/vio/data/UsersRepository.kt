package com.example.vio.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class UserSummary(
    val uid: String,
    val name: String,
    val photo: String?
)

/**
 * Repository with simple in-memory cache. Only fetches from Firebase once per process
 * unless refresh() is called.
 */
object UsersRepository {
    private var cache: Map<String, UserSummary>? = null
    private val db = FirebaseDatabase.getInstance().getReference("users")

    suspend fun getUsers(): Map<String, UserSummary> {
        cache?.let { return it }
        val data = fetchFromFirebase()
        cache = data
        return data
    }

    suspend fun refresh(): Map<String, UserSummary> {
        val data = fetchFromFirebase()
        cache = data
        return data
    }

    private suspend fun fetchFromFirebase(): Map<String, UserSummary> = suspendCancellableCoroutine { cont ->
        db.get()
            .addOnSuccessListener { snap ->
                val result = mutableMapOf<String, UserSummary>()
                for (child in snap.children) {
                    val uid = child.child("uid").getValue(String::class.java) ?: continue
                    val name = child.child("name").getValue(String::class.java).orEmpty()
                    val photo = child.child("photo").getValue(String::class.java)
                    result[uid] = UserSummary(uid, name, photo)
                }
                cont.resume(result)
            }
            .addOnFailureListener {
                cont.resume(emptyMap())
            }
    }
}
