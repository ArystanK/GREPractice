package kz.arctan.grepractice.data.cloud

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
private const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val SIGN_IN_TIMEOUT_MS = 5 * 60_000L
private val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")

private val DONE_PAGE = """
    <!doctype html><html><head><meta charset="utf-8"><title>GRE Math Practice</title></head>
    <body style="font-family:sans-serif;text-align:center;padding-top:80px">
    <h2>%s</h2><p>You can close this tab and return to GRE Math Practice.</p></body></html>
""".trimIndent()

/**
 * Google sign-in for desktop apps: the OAuth 2.0 authorization-code flow with PKCE and a loopback
 * redirect (https://developers.google.com/identity/protocols/oauth2/native-app). Opens the system
 * browser, receives the code on a one-shot local HTTP server, and exchanges it for a Google ID token.
 */
internal class GoogleLoopbackSignIn(private val http: HttpClient) {
    private val random = SecureRandom()

    /** Returns a Google ID token, or null if the user cancelled in the browser. */
    suspend fun signIn(): String? {
        val verifier = randomToken(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomToken(16)
        val callback = CompletableDeferred<Map<String, String>>()

        val server = withContext(Dispatchers.IO) {
            HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0)
        }
        server.createContext("/") { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery)
            val ok = params["state"] == state && params["code"] != null
            val page = DONE_PAGE.format(if (ok) "Signed in" else "Sign-in was not completed").toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
            if (params.containsKey("code") || params.containsKey("error")) callback.complete(params)
        }
        server.start()
        try {
            val redirectUri = "http://127.0.0.1:${server.address.port}"
            val url = AUTHORIZE_URL + "?" + formEncode(
                "client_id" to FirebaseConfig.DESKTOP_OAUTH_CLIENT_ID,
                "redirect_uri" to redirectUri,
                "response_type" to "code",
                "scope" to "openid email profile",
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "state" to state,
                "prompt" to "select_account",
            )
            openBrowser(url)

            val params = try {
                withTimeout(SIGN_IN_TIMEOUT_MS) { callback.await() }
            } catch (e: TimeoutCancellationException) {
                throw CloudException("Google sign-in timed out. Try again.", e)
            }
            when {
                params["error"] == "access_denied" -> return null
                params["error"] != null -> throw CloudException("Google sign-in failed: ${params["error"]}")
                params["state"] != state -> throw CloudException("Google sign-in failed: the response didn't match the request.")
            }
            return exchangeCode(params.getValue("code"), verifier, redirectUri)
        } finally {
            server.stop(0)
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String, redirectUri: String): String = withContext(Dispatchers.IO) {
        val form = formEncode(
            "code" to code,
            "client_id" to FirebaseConfig.DESKTOP_OAUTH_CLIENT_ID,
            "client_secret" to FirebaseConfig.DESKTOP_OAUTH_CLIENT_SECRET,
            "redirect_uri" to redirectUri,
            "grant_type" to "authorization_code",
            "code_verifier" to verifier,
        )
        val response = try {
            http.send(
                HttpRequest.newBuilder(URI.create(GOOGLE_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (e: IOException) {
            throw CloudException("No connection to Google. Check your internet connection.", e)
        }
        val body = runCatching { Json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
        body?.get("id_token")?.jsonPrimitive?.content
            ?: throw CloudException("Google sign-in failed: ${body?.get("error_description")?.jsonPrimitive?.content ?: "HTTP ${response.statusCode()}"}")
    }

    private fun openBrowser(url: String) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop == null || !desktop.isSupported(Desktop.Action.BROWSE)) {
            throw CloudException("Couldn't open a web browser for Google sign-in.")
        }
        desktop.browse(URI.create(url))
    }

    private fun randomToken(bytes: Int): String = base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
}

private fun formEncode(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) -> URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8) }

private fun parseQuery(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }
