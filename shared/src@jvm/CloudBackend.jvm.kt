package kz.arctan.grepractice.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.data.readDataFile
import kz.arctan.grepractice.data.writeDataFile
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val SESSION_FILE = "session.json"
private const val AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts"
private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
private const val DB_PATH = "projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"
private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/$DB_PATH"

private val restJson = Json { ignoreUnknownKeys = true }

/** A single-document read found nothing (as opposed to the database itself missing). */
private class DocumentNotFound : Exception()

/** Signed-in session; only the refresh token and identity are persisted, ID tokens live in memory. */
@Serializable
private data class StoredSession(val uid: String, val email: String?, val refreshToken: String, val isAnonymous: Boolean = false)

/**
 * Desktop implementation using the Firebase Auth and Cloud Firestore REST APIs (there is no
 * official Firebase client SDK for the JVM desktop).
 */
private class RestCloudBackend : CloudBackend {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    private var session: StoredSession? = readDataFile(SESSION_FILE)
        ?.let { runCatching { restJson.decodeFromString(StoredSession.serializer(), it) }.getOrNull() }
    private var idToken: String? = null
    private var idTokenExpiresAt = 0L
    private var idTokenOwner: String? = null

    private val _user = MutableStateFlow(session?.let { CloudUser(it.uid, it.email, it.isAnonymous) })
    override val user: StateFlow<CloudUser?> = _user

    override suspend fun signIn(email: String, password: String) = authenticate("signInWithPassword", email, password)

    override suspend fun signUp(email: String, password: String) = authenticate("signUp", email, password)

    override suspend fun signInAnonymously() {
        // accounts:signUp without email/password creates an anonymous user.
        storeSession(authPost("$AUTH_URL:signUp", buildJsonObject { put("returnSecureToken", true) }), email = null, anonymous = true)
    }

    override val googleSignInAvailable: Boolean = FirebaseConfig.DESKTOP_OAUTH_CLIENT_ID.isNotBlank()

    override suspend fun signInWithGoogle() {
        if (!googleSignInAvailable) throw CloudException("Google sign-in on desktop needs a Desktop OAuth client ID in FirebaseConfig.")
        val googleIdToken = GoogleLoopbackSignIn(http).signIn() ?: return
        val body = authPost("$AUTH_URL:signInWithIdp", buildJsonObject {
            put("postBody", "id_token=$googleIdToken&providerId=google.com")
            put("requestUri", "http://localhost")
            put("returnSecureToken", true)
            put("returnIdpCredential", true)
        })
        storeSession(body, email = null, anonymous = false)
    }

    override suspend fun sendPasswordReset(email: String) {
        authPost("$AUTH_URL:sendOobCode", buildJsonObject {
            put("requestType", "PASSWORD_RESET")
            put("email", email)
        })
    }

    override suspend fun signOut() {
        session = null
        idToken = null
        writeDataFile(SESSION_FILE, "{}")
        _user.value = null
    }

    override suspend fun list(collection: String): List<CloudDoc> {
        val docs = mutableListOf<CloudDoc>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$FIRESTORE_URL/users/${uid()}/$collection?pageSize=300")
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, Charsets.UTF_8)) }
            }
            val body = firestore(HttpRequest.newBuilder(URI.create(url)).GET())
            body["documents"]?.jsonArray?.forEach { docs += parseDoc(it.jsonObject) }
            pageToken = body["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        return docs
    }

    override suspend fun get(collection: String, id: String): CloudDoc? {
        val url = "$FIRESTORE_URL/users/${uid()}/$collection/${URLEncoder.encode(id, Charsets.UTF_8)}"
        return try {
            parseDoc(firestore(HttpRequest.newBuilder(URI.create(url)).GET()))
        } catch (e: DocumentNotFound) {
            null
        }
    }

    private fun parseDoc(doc: JsonObject): CloudDoc {
        val fields = doc["fields"]?.jsonObject ?: JsonObject(emptyMap())
        return CloudDoc(
            id = doc["name"]!!.jsonPrimitive.content.substringAfterLast('/'),
            payload = fields["payload"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content.orEmpty(),
            // Firestore's REST API encodes 64-bit integers as strings.
            updatedAt = fields["updatedAt"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            deleted = fields["deleted"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.boolean ?: false,
        )
    }

    override suspend fun write(writes: List<CloudWrite>) {
        val uid = uid()
        writes.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val body = buildJsonObject {
                put("writes", buildJsonArray {
                    chunk.forEach { w ->
                        add(buildJsonObject {
                            putJsonObject("update") {
                                put("name", "$DB_PATH/users/$uid/${w.collection}/${w.doc.id}")
                                putJsonObject("fields") {
                                    putJsonObject("payload") { put("stringValue", w.doc.payload) }
                                    putJsonObject("updatedAt") { put("integerValue", w.doc.updatedAt.toString()) }
                                    putJsonObject("deleted") { put("booleanValue", w.doc.deleted) }
                                }
                            }
                        })
                    }
                })
            }
            firestore(
                HttpRequest.newBuilder(URI.create("$FIRESTORE_URL:commit"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())),
            )
        }
    }

    // ---- Auth ----

    private suspend fun authenticate(endpoint: String, email: String, password: String) {
        val body = authPost("$AUTH_URL:$endpoint", buildJsonObject {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        })
        storeSession(body, email, anonymous = false)
    }

    private fun storeSession(body: JsonObject, email: String?, anonymous: Boolean) {
        val s = StoredSession(
            uid = body["localId"]!!.jsonPrimitive.content,
            email = body["email"]?.jsonPrimitive?.content ?: email,
            refreshToken = body["refreshToken"]!!.jsonPrimitive.content,
            isAnonymous = anonymous,
        )
        setToken(body["idToken"]!!.jsonPrimitive.content, body["expiresIn"]?.jsonPrimitive?.content)
        session = s
        idTokenOwner = s.uid
        writeDataFile(SESSION_FILE, restJson.encodeToString(StoredSession.serializer(), s))
        _user.value = CloudUser(s.uid, s.email, s.isAnonymous)
    }

    private fun uid(): String = session?.uid ?: throw CloudException("Sign in to sync.")

    /** A valid ID token, refreshed with the stored refresh token when it's about to expire. */
    private suspend fun token(forceRefresh: Boolean = false): String {
        val current = idToken
        if (!forceRefresh && current != null && idTokenOwner == session?.uid && nowMillis() < idTokenExpiresAt - 60_000) return current
        val s = session ?: throw CloudException("Sign in to sync.")
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, Charsets.UTF_8)
        val response = send(
            HttpRequest.newBuilder(URI.create("$TOKEN_URL?key=${FirebaseConfig.API_KEY}"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)),
        )
        val body = parse(response.body())
        if (response.statusCode() != 200) {
            val code = authErrorCode(body)
            if (code in setOf("TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN")) {
                signOut()
                throw CloudException("Your session expired. Please sign in again.")
            }
            throw CloudException(authMessage(code))
        }
        val refreshed = body["refresh_token"]?.jsonPrimitive?.content ?: s.refreshToken
        if (refreshed != s.refreshToken) {
            session = s.copy(refreshToken = refreshed)
            writeDataFile(SESSION_FILE, restJson.encodeToString(StoredSession.serializer(), session!!))
        }
        setToken(body["id_token"]!!.jsonPrimitive.content, body["expires_in"]?.jsonPrimitive?.content)
        idTokenOwner = s.uid
        return idToken!!
    }

    private fun setToken(token: String, expiresInSeconds: String?) {
        idToken = token
        idTokenExpiresAt = nowMillis() + (expiresInSeconds?.toLongOrNull() ?: 3600L) * 1000
    }

    private suspend fun authPost(url: String, body: JsonObject): JsonObject {
        val response = send(
            HttpRequest.newBuilder(URI.create("$url?key=${FirebaseConfig.API_KEY}"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())),
        )
        val parsed = parse(response.body())
        if (response.statusCode() != 200) throw CloudException(authMessage(authErrorCode(parsed)))
        return parsed
    }

    // ---- Firestore ----

    /** Sends an authorized Firestore request, retrying once with a fresh token on 401. */
    private suspend fun firestore(request: HttpRequest.Builder): JsonObject {
        var response = send(request.copy().header("Authorization", "Bearer ${token()}"))
        if (response.statusCode() == 401) response = send(request.copy().header("Authorization", "Bearer ${token(forceRefresh = true)}"))
        val body = parse(response.body())
        if (response.statusCode() == 404 && "database" !in body.toString()) throw DocumentNotFound()
        if (response.statusCode() !in 200..299) throw CloudException(firestoreMessage(response.statusCode(), body))
        return body
    }

    private suspend fun send(request: HttpRequest.Builder): HttpResponse<String> = withContext(Dispatchers.IO) {
        try {
            http.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw CloudException("No connection to Firebase. Check your internet connection.", e)
        }
    }

    private fun parse(body: String): JsonObject =
        runCatching { restJson.parseToJsonElement(body).jsonObject }.getOrDefault(JsonObject(emptyMap()))
}

/** e.g. "WEAK_PASSWORD : Password should be at least 6 characters" → "WEAK_PASSWORD". */
private fun authErrorCode(body: JsonObject): String =
    (body["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content?.substringBefore(" ")
        ?: (body["error"] as? JsonPrimitive)?.content?.uppercase()
        ?: "UNKNOWN"

private fun authMessage(code: String): String = when (code) {
    "EMAIL_EXISTS" -> "An account with this email already exists. Sign in instead."
    "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" -> "Wrong email or password."
    "INVALID_EMAIL" -> "That email address isn't valid."
    "WEAK_PASSWORD" -> "Password should be at least 6 characters."
    "MISSING_PASSWORD" -> "Enter a password."
    "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Try again later."
    "USER_DISABLED" -> "This account has been disabled."
    "OPERATION_NOT_ALLOWED" -> "Email/password sign-in isn't enabled in the Firebase console."
    "CONFIGURATION_NOT_FOUND" -> "Firebase Authentication isn't set up for this project yet."
    "ADMIN_ONLY_OPERATION" -> "Guest (anonymous) sign-in isn't enabled in the Firebase console."
    "INVALID_IDP_RESPONSE" -> "Firebase rejected the Google sign-in. Check that the Desktop OAuth client belongs to this project."
    else -> "Sign-in failed ($code)."
}

private fun firestoreMessage(status: Int, body: JsonObject): String {
    val error = body["error"] as? JsonObject
    val message = error?.get("message")?.jsonPrimitive?.content.orEmpty()
    val reason = error?.get("status")?.jsonPrimitive?.content
    return when {
        "SERVICE_DISABLED" in body.toString() || "does not exist" in message ->
            "The Firestore database isn't set up for this project yet."
        reason == "PERMISSION_DENIED" -> "Firestore denied access. Are the security rules from firestore.rules published?"
        reason == "UNAVAILABLE" -> "Firestore is unreachable right now; try again later."
        else -> "Firestore error $status: ${message.ifBlank { reason ?: "unknown" }}"
    }
}

actual fun createCloudBackend(): CloudBackend = RestCloudBackend()
