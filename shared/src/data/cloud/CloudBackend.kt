package kz.arctan.grepractice.data.cloud

import kotlinx.coroutines.flow.StateFlow

/**
 * Firebase project settings. The values live in the git-ignored [FirebaseSecrets]
 * (`FirebaseSecrets.kt`; copy `FirebaseSecrets.kt.example` to create it), mirrored from
 * `androidApp/google-services.json` because this build doesn't run the Google Services Gradle plugin.
 */
object FirebaseConfig {
    const val PROJECT_ID = FirebaseSecrets.PROJECT_ID
    const val API_KEY = FirebaseSecrets.API_KEY
    const val ANDROID_APP_ID = FirebaseSecrets.ANDROID_APP_ID
    const val STORAGE_BUCKET = FirebaseSecrets.STORAGE_BUCKET

    /** "Web client" OAuth ID (client_type 3 in google-services.json); Android's Google sign-in requests ID tokens for it. */
    const val WEB_CLIENT_ID = FirebaseSecrets.WEB_CLIENT_ID

    /** "Desktop app" OAuth client for Google sign-in on desktop; blank hides Google sign-in there. */
    const val DESKTOP_OAUTH_CLIENT_ID = FirebaseSecrets.DESKTOP_OAUTH_CLIENT_ID
    const val DESKTOP_OAUTH_CLIENT_SECRET = FirebaseSecrets.DESKTOP_OAUTH_CLIENT_SECRET
}

/** [isAnonymous]: a guest account that exists only on this device until the user signs in properly. */
data class CloudUser(val uid: String, val email: String?, val isAnonymous: Boolean = false)

/**
 * One synced record, stored in Firestore at `users/{uid}/{collection}/{id}` with the fields
 * `payload` (the record as JSON; empty when deleted), `updatedAt` (epoch millis) and `deleted`.
 */
data class CloudDoc(val id: String, val payload: String, val updatedAt: Long, val deleted: Boolean)

data class CloudWrite(val collection: String, val doc: CloudDoc)

/** A failure with a message that can be shown to the user as is. */
class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Firebase Auth + Firestore, implemented with the Android SDK on Android and the REST APIs on desktop. */
interface CloudBackend {
    val user: StateFlow<CloudUser?>

    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signInAnonymously()

    /** Whether Google sign-in is configured on this platform. */
    val googleSignInAvailable: Boolean

    /** Interactive Google sign-in. Returns without signing in if the user cancels. */
    suspend fun signInWithGoogle()
    suspend fun sendPasswordReset(email: String)
    suspend fun signOut()

    /** All documents (including tombstones) in the signed-in user's [collection]. */
    suspend fun list(collection: String): List<CloudDoc>

    /** Writes documents in the signed-in user's collections, replacing existing ones. */
    suspend fun write(writes: List<CloudWrite>)

    /** One document from the signed-in user's [collection], or null if it doesn't exist. */
    suspend fun get(collection: String, id: String): CloudDoc?
}

expect fun createCloudBackend(): CloudBackend

const val QUESTIONS_COLLECTION = "questions"
const val RESULTS_COLLECTION = "results"

/** Question figures: id = image file name, payload = base64 of the image bytes. */
const val IMAGES_COLLECTION = "images"

/** Firestore allows at most 500 writes per batch/commit. */
const val MAX_BATCH_WRITES = 400
