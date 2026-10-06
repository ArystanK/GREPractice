package kz.arctan.grepractice.data.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference

/** The Activity hosting the UI; Credential Manager needs it to show the Google account picker. */
private var currentActivity: WeakReference<Activity>? = null

/** Must be called from the Activity's onCreate, before the UI is shown. */
fun initFirebase(activity: Activity) {
    currentActivity = WeakReference(activity)
    val context: Context = activity.applicationContext
    if (FirebaseApp.getApps(context).isNotEmpty()) return
    FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
            .setApiKey(FirebaseConfig.API_KEY)
            .setApplicationId(FirebaseConfig.ANDROID_APP_ID)
            .setProjectId(FirebaseConfig.PROJECT_ID)
            .setStorageBucket(FirebaseConfig.STORAGE_BUCKET)
            .build(),
    )
}

/** Writes are queued in Firestore's local cache; don't block a sync for longer than this waiting for the server. */
private const val WRITE_ACK_TIMEOUT_MS = 15_000L

private class FirebaseCloudBackend : CloudBackend {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val _user = MutableStateFlow(auth.currentUser?.toCloudUser())
    override val user: StateFlow<CloudUser?> = _user

    init {
        auth.addAuthStateListener { _user.value = it.currentUser?.toCloudUser() }
    }

    override suspend fun signIn(email: String, password: String) = guard {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun signUp(email: String, password: String) = guard {
        auth.createUserWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun signInAnonymously() = guard {
        auth.signInAnonymously().await()
        Unit
    }

    override val googleSignInAvailable: Boolean = true

    override suspend fun signInWithGoogle() = guard {
        val activity = currentActivity?.get() ?: throw CloudException("Google sign-in isn't available right now.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(FirebaseConfig.WEB_CLIENT_ID).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            return@guard
        } catch (e: NoCredentialException) {
            throw CloudException("No Google account is available on this device.", e)
        } catch (e: GetCredentialException) {
            throw CloudException("Google sign-in failed: ${e.message ?: e.type}", e)
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw CloudException("Google sign-in returned an unexpected credential.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String) = guard {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun list(collection: String): List<CloudDoc> = guard {
        userCollection(collection).get().await().documents.map { d ->
            CloudDoc(
                id = d.id,
                payload = d.getString("payload").orEmpty(),
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deleted = d.getBoolean("deleted") ?: false,
            )
        }
    }

    override suspend fun write(writes: List<CloudWrite>) = guard {
        writes.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { w ->
                batch.set(
                    userCollection(w.collection).document(w.doc.id),
                    mapOf("payload" to w.doc.payload, "updatedAt" to w.doc.updatedAt, "deleted" to w.doc.deleted),
                )
            }
            // Offline, the commit stays queued and uploads later; don't hang the sync on it.
            withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { batch.commit().await() }
        }
    }

    private fun userCollection(collection: String) =
        db.collection("users").document(auth.currentUser?.uid ?: throw CloudException("Sign in to sync.")).collection(collection)

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: CloudException) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw CloudException("No connection to Firebase. Check your internet connection.", e)
    } catch (e: FirebaseAuthException) {
        throw CloudException(e.message ?: "Sign-in failed.", e)
    } catch (e: FirebaseFirestoreException) {
        throw CloudException(
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore denied access. Are the security rules from firestore.rules published?"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "Firestore is unreachable right now; changes will sync later."
                FirebaseFirestoreException.Code.NOT_FOUND, FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    "The Firestore database isn't set up for this project yet."
                else -> e.message ?: "Firestore error: ${e.code}"
            },
            e,
        )
    }
}

private fun FirebaseUser.toCloudUser() = CloudUser(uid, email, isAnonymous)

actual fun createCloudBackend(): CloudBackend = FirebaseCloudBackend()
