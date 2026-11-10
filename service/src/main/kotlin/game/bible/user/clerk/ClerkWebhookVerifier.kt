package game.bible.user.clerk

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Svix's documented HMAC-SHA256 protocol, over the original bytes, before JSON parsing. */
class ClerkWebhookVerifier(private val secret: String, private val clock: Clock = Clock.systemUTC()) {
    fun verify(body: ByteArray, id: String?, timestamp: String?, signatures: String?) {
        if (secret.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook signing is not configured")
        val key = try {
            require(secret.startsWith("whsec_"))
            Base64.getDecoder().decode(secret.removePrefix("whsec_")).also { require(it.size >= 16) }
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook signing is not configured")
        }
        val seconds = timestamp?.toLongOrNull()
        val now = clock.instant().epochSecond
        if (id.isNullOrBlank() || id.length > 255 || timestamp == null || timestamp.length > 20 ||
            seconds == null || seconds < now - 300 || seconds > now + 300 || signatures.isNullOrBlank()) reject()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update("$id.$timestamp.".toByteArray(Charsets.UTF_8))
        val expected = mac.doFinal(body)
        val valid = signatures!!.split(' ').any { signature ->
            if (!signature.startsWith("v1,")) false else try {
                MessageDigest.isEqual(expected, Base64.getDecoder().decode(signature.substring(3)))
            } catch (_: IllegalArgumentException) { false }
        }
        if (!valid) reject()
    }

    private fun reject(): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature")
}
