package com.air.quality.meter.data.repository

import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.data.model.FeedbackModel
import com.air.quality.meter.data.model.HealthRecommendation
import com.air.quality.meter.data.model.UserModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for user profiles, recommendations, feedback, and activity logs.
 * All operations are Firestore-backed (no local Room cache needed for this data).
 */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ─── User Profile ─────────────────────────────────────────────────────────

    suspend fun getUser(uid: String): Result<UserModel> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = db.collection("users").document(uid).get().await()
            val model = doc.toObject(UserModel::class.java) ?: UserModel()
            if (model.uid.isBlank()) model.copy(uid = doc.id) else model
        }
    }

    suspend fun saveUser(user: UserModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("users").document(user.uid).set(user).await()
            Unit
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("users").document(uid).delete().await()
            Unit
        }
    }

    /** Fetch all citizen accounts (for admin user management) */
    suspend fun getAllCitizens(): Result<List<UserModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("users")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val m = doc.toObject(UserModel::class.java)
                if (m == null) null else if (m.uid.isBlank()) m.copy(uid = doc.id) else m
            }
        }
    }
    /** Debug: Fetch all users excluding admins to check visibility */
    suspend fun getAllUsersDebug(): Result<List<UserModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("users")
                .whereNotEqualTo("role", "admin")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val m = doc.toObject(UserModel::class.java)
                if (m == null) null else if (m.uid.isBlank()) m.copy(uid = doc.id) else m
            }
        }
    }

    // ─── Health Recommendations ───────────────────────────────────────────────

    suspend fun getRecommendations(): Result<List<HealthRecommendation>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("recommendations").get().await()
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
                .get()
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
                .get()
                .await()
            snapshot.toObjects(ActivityLog::class.java)
        }
    }
}
