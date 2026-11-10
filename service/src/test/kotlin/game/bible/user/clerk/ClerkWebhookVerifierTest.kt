package game.bible.user.clerk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ClerkWebhookVerifierTest {
    private val key = ByteArray(32) { it.toByte() }
    private val secret = "whsec_" + Base64.getEncoder().encodeToString(key)
    private val clock = Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC)
    private val verifier = ClerkWebhookVerifier(secret, clock)
    private val body = "{\"name\":\"Élodie\"}".toByteArray()
    private fun sign(time: String = "1800000000", id: String = "msg_test"): String {
        val mac = Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(key, "HmacSHA256")) }
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal("$id.$time.".toByteArray() + body))
    }

    @Test
    fun `verifies original Unicode bytes and any full valid v1 rotation signature`() {
        verifier.verify(body, "msg_test", "1800000000", "v2,ignored v1,invalid ${sign()}")
    }

    @Test
    fun `rejects changed body message ID timestamp secret and truncated signature`() {
        for (action in listOf<() -> Unit>(
            { verifier.verify(body + 32.toByte(), "msg_test", "1800000000", sign()) },
            { verifier.verify(body, "other", "1800000000", sign()) },
            { verifier.verify(body, "msg_test", "1800000001", sign()) },
            { ClerkWebhookVerifier("whsec_" + Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), clock).verify(body, "msg_test", "1800000000", sign()) },
            { verifier.verify(body, "msg_test", "1800000000", "v1," + Base64.getEncoder().encodeToString(byteArrayOf(1))) },
            { verifier.verify(body, null, "1800000000", sign()) },
            { verifier.verify(body, "msg_test", null, sign()) },
            { verifier.verify(body, "msg_test", "1800000000", null) }
        )) assertEquals(400, assertThrows(ResponseStatusException::class.java, action).statusCode.value())
    }

    @Test
    fun `rejects valid signatures outside five minute past and future windows`() {
        for (time in listOf("1799999699", "1800000301", Long.MIN_VALUE.toString(), Long.MAX_VALUE.toString())) {
            assertEquals(400, assertThrows(ResponseStatusException::class.java) {
                verifier.verify(body, "msg_test", time, sign(time))
            }.statusCode.value())
        }
    }

    @Test
    fun `missing or malformed configuration fails closed`() {
        for (value in listOf("", "wrong", "whsec_?", "whsec_YQ==")) {
            assertEquals(503, assertThrows(ResponseStatusException::class.java) {
                ClerkWebhookVerifier(value, clock).verify(body, "msg_test", "1800000000", sign())
            }.statusCode.value())
        }
    }
}
