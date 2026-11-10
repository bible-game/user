package game.bible.user.clerk

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

class ClerkJwtDecoderTest {
    private lateinit var server: HttpServer
    private lateinit var key: RSAKey
    private lateinit var decoder: NimbusJwtDecoder
    private lateinit var issuer: String

    @BeforeEach
    fun setup() {
        key = RSAKeyGenerator(2048).keyID("first").generate()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        issuer = "http://127.0.0.1:${server.address.port}"
        server.createContext("/.well-known/jwks.json") { exchange ->
            val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        decoder = ClerkJwtDecoder.create(ClerkProperties().also {
            it.issuer = issuer
            it.authorizedParties = listOf("http://localhost:3000")
        })
    }

    @AfterEach
    fun cleanup() { server.stop(0) }

    @Test
    fun `accepts a correctly signed active session and refreshes JWKS on key rotation`() {
        assertEquals("user_abc", decoder.decode(token()).subject)
        key = RSAKeyGenerator(2048).keyID("rotated").generate()
        assertEquals("user_abc", decoder.decode(token()).subject)
    }

    @Test
    fun `rejects expired future wrong issuer and wrong origin tokens`() {
        for (claims in listOf(
            mapOf("exp" to Date.from(Instant.now().minusSeconds(120))),
            mapOf("nbf" to Date.from(Instant.now().plusSeconds(120))),
            mapOf("iss" to "https://other.clerk.accounts.dev"),
            mapOf("azp" to "https://attacker.example"),
            mapOf("azp" to null),
            mapOf("exp" to null),
            mapOf("nbf" to null),
            mapOf("sid" to null),
            mapOf("sts" to "pending"),
            mapOf("sub" to "123"))) {
            assertThrows(JwtException::class.java, { decoder.decode(token(claims)) }, claims.toString())
        }
    }

    @Test
    fun `rejects a forged signature`() {
        val other = RSAKeyGenerator(2048).keyID("first").generate()
        assertThrows(JwtException::class.java) { decoder.decode(token(signingKey = other)) }
    }

    private fun token(overrides: Map<String, Any?> = emptyMap(), signingKey: RSAKey = key): String {
        val now = Instant.now()
        val claims = linkedMapOf<String, Any?>(
            "iss" to issuer, "sub" to "user_abc", "sid" to "sess_abc", "azp" to "http://localhost:3000",
            "iat" to Date.from(now), "nbf" to Date.from(now.minusSeconds(1)), "exp" to Date.from(now.plusSeconds(60)))
        claims.putAll(overrides)
        val builder = JWTClaimsSet.Builder()
        claims.filterValues { it != null }.forEach { (name, value) -> builder.claim(name, value) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).type(JOSEObjectType.JWT).build(), builder.build())
            .also { it.sign(RSASSASigner(signingKey)) }.serialize()
    }
}
