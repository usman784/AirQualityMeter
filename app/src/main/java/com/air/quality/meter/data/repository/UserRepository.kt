package com.air.quality.meter.data.repository

import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.data.model.FeedbackModel
import com.air.quality.meter.data.model.HealthRecommendation
import com.air.quality.meter.data.model.UserModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Repository for user profiles, recommendations, feedback, and activity logs.
 * All operations are Firestore-backed (no local Room cache needed for this data).
 */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun DocumentSnapshot.toUserModelOrNull(): UserModel? {
        val mapped = toObject(UserModel::class.java) ?: return null
        val resolvedUid = mapped.uid.ifBlank { id }

        val rawActive = get("isActive") ?: get("active")
        val resolvedActive = when (rawActive) {
            is Boolean -> rawActive
            is Number -> rawActive.toInt() != 0
            is String -> rawActive.equals("true", ignoreCase = true) || rawActive == "1"
            else -> mapped.isActive
        }

        return mapped.copy(
            uid = resolvedUid,
            isActive = resolvedActive
        )
    }

    // ─── User Profile ─────────────────────────────────────────────────────────

    suspend fun getUser(uid: String): Result<UserModel> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = db.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).await()
            doc.toUserModelOrNull() ?: UserModel(uid = doc.id)
        }
    }

    suspend fun saveUser(user: UserModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("users").document(user.uid).set(user).await()
            Unit
        }
    }

    /** Fetch a citizen profile for admin management screen. */
    suspend fun getCitizenByUid(uid: String): Result<UserModel> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = db.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).await()
            val merged = doc.toUserModelOrNull() ?: UserModel(uid = uid)
            if (merged.createdAt <= 0L) merged.copy(createdAt = System.currentTimeMillis()) else merged
        }
    }

    /**
     * Admin-managed update (Optimized):
     *  - Email is immutable in this flow.
     *  - Role change migrates record between /users and /admins collections.
     *  - Deactivation state is stored in isActive.
     *  - Simplified: directly update target collection without searching for duplicates.
     */
    suspend fun adminUpdateUser(
        uid: String,
        name: String,
        age: String,
        gender: String,
        cellNumber: String,
        countryCode: String,
        role: String,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Fetch current user to preserve email and createdAt
            val currentUserDoc = try {
                db.collection("users").document(uid).get().await()
            } catch (e: Exception) {
                null
            }

            val currentUser = currentUserDoc?.toUserModelOrNull()
            val currentEmail = currentUser?.email ?: ""
            val createdAt = currentUser?.createdAt?.takeIf { it > 0L } ?: System.currentTimeMillis()

            val safeRole = normalizeRole(role)
            val safeCountryCode = countryCode.trim().ifBlank { "+92" }
            val safeCell = cellNumber.trim()
            val fullPhone = if (safeCell.isBlank()) "" else "$safeCountryCode$safeCell"
            val now = System.currentTimeMillis()

            val payload = mapOf(
                "uid" to uid,
                "name" to name.trim(),
                "email" to currentEmail,
                "role" to safeRole,
                "isActive" to isActive,
                "age" to age.trim(),
                "gender" to gender.trim(),
                "cellNumber" to safeCell,
                "countryCode" to safeCountryCode,
                "fullPhone" to fullPhone,
                "createdAt" to createdAt,
                "updatedAt" to now
            )

            // Simple direct update: set in target collection
            val targetRef = if (safeRole == "admin") {
                db.collection("admins").document(uid)
            } else {
                db.collection("users").document(uid)
            }

            targetRef.set(payload, SetOptions.merge()).await()
            Unit
        }
    }

    suspend fun setCitizenActive(uid: String, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            db.collection("users").document(uid)
                .set(mapOf("uid" to uid, "isActive" to active, "updatedAt" to now), SetOptions.merge())
                .await()
            Unit
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("users").document(uid).delete().await()
            Unit
        }
    }

    /** Fetch all citizen accounts (for admin user management) - Always fresh from server */
    suspend fun getAllCitizens(): Result<List<UserModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("users")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toUserModelOrNull()
            }.filter { user ->
                user.role.trim().lowercase() != "admin"
            }
        }
    }

    /** Real-time listener for citizens - updates whenever data changes */
    fun listenerAllCitizens(
        onUpdate: (List<UserModel>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val citizens = snapshot.documents.mapNotNull { doc ->
                        doc.toUserModelOrNull()
                    }.filter { user ->
                        user.role.trim().lowercase() != "admin"
                    }
                    onUpdate(citizens)
                }
            }
    }

    /** Debug: Fetch all users excluding admins to check visibility - Always fresh */
    suspend fun getAllUsersDebug(): Result<List<UserModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("users")
                .whereNotEqualTo("role", "admin")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toUserModelOrNull()
            }
        }
    }

    /** Collect admin UIDs from both legacy collections: /admin and /admins - Always fresh */
    suspend fun getAdminUids(): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            suspend fun fetchCollection(collection: String): QuerySnapshot {
                return db.collection(collection)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
            }

            val adminSnapshot = fetchCollection("admin")
            val adminsSnapshot = fetchCollection("admins")

            val allDocs = adminSnapshot.documents + adminsSnapshot.documents
            val adminUids = mutableSetOf<String>()
            val adminEmails = mutableSetOf<String>()

            allDocs.forEach { doc ->
                doc.getString("uid")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { adminUids += it }

                doc.id.trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { adminUids += it }

                doc.getString("email")
                    ?.trim()
                    ?.lowercase(Locale.US)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { adminEmails += it }
            }

            // If admin docs store email but not uid, resolve matching user uid(s) by email.
            if (adminEmails.isNotEmpty()) {
                adminEmails.chunked(10).forEach { chunk ->
                    val userSnapshot: QuerySnapshot = db.collection("users")
                        .whereIn("email", chunk)
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .await()

                    userSnapshot.documents.forEach { userDoc ->
                        userDoc.id.takeIf { it.isNotBlank() }?.let { adminUids += it }
                        userDoc.getString("uid")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { adminUids += it }
                    }
                }
            }

            adminUids
        }
    }

    // ─── Health Recommendations ───────────────────────────────────────────────

    suspend fun getRecommendations(): Result<List<HealthRecommendation>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("recommendations")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            snapshot.toObjects(HealthRecommendation::class.java)
        }
    }

    suspend fun saveRecommendation(rec: HealthRecommendation): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val docRef = if (rec.id.isBlank()) db.collection("recommendations").document()
                         else db.collection("recommendations").document(rec.id)
            docRef.set(rec).await()
            Unit
        }
    }

    suspend fun deleteRecommendation(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("recommendations").document(id).delete().await()
            Unit
        }
    }

    // ─── Feedback ─────────────────────────────────────────────────────────────

    suspend fun submitFeedback(feedback: FeedbackModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("feedback").document().set(feedback).await()
            Unit
        }
    }

    suspend fun getAllFeedback(): Result<List<FeedbackModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("feedback")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            snapshot.toObjects(FeedbackModel::class.java)
        }
    }

    suspend fun updateFeedbackStatus(id: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("feedback").document(id).update("status", status).await()
            Unit
        }
    }

    // ─── Activity Logs ────────────────────────────────────────────────────────

    suspend fun logActivity(log: ActivityLog): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("activity_logs").document().set(log).await()
            Unit
        }
    }

    suspend fun getRecentLogs(limit: Long = 50): Result<List<ActivityLog>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            snapshot.toObjects(ActivityLog::class.java)
        }
    }

    private fun normalizeRole(role: String): String {
        return if (role.trim().lowercase(Locale.US) == "admin") "admin" else "citizen"
    }

    private suspend fun findUserIdentityDocs(
        collection: String,
        uid: String,
        email: String?
    ): List<DocumentSnapshot> {
        val docsByPath = linkedMapOf<String, DocumentSnapshot>()

        fun addIfExists(doc: DocumentSnapshot?) {
            if (doc != null && doc.exists()) docsByPath[doc.reference.path] = doc
        }

        addIfExists(
            runCatching {
                db.collection(collection).document(uid).get().await()
            }.getOrNull()
        )

        runCatching {
            db.collection(collection)
                .whereEqualTo("uid", uid)
                .get()
                .await()
        }.getOrNull()?.documents?.forEach(::addIfExists)

        val rawEmail = email?.trim().orEmpty()
        if (rawEmail.isNotBlank()) {
            val emailVariants = linkedSetOf(rawEmail, rawEmail.lowercase(Locale.US))
            emailVariants.forEach { emailValue ->
                runCatching {
                    db.collection(collection)
                        .whereEqualTo("email", emailValue)
                        .get()
                        .await()
                }.getOrNull()?.documents?.forEach(::addIfExists)
            }
        }

        return docsByPath.values.toList()
    }

    private fun deleteCandidateDocs(
        batch: com.google.firebase.firestore.WriteBatch,
        docs: List<DocumentSnapshot>,
        keepPath: String?
    ) {
        docs.forEach { doc ->
            if (!doc.exists()) return@forEach
            if (keepPath != null && doc.reference.path == keepPath) return@forEach
            batch.delete(doc.reference)
        }
    }

}
