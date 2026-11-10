package game.bible.user.clerk

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.net.http.HttpClient
import java.time.Duration

object ClerkJwtDecoder {
    fun create(properties: ClerkProperties): NimbusJwtDecoder {
        val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
        factory.setReadTimeout(Duration.ofSeconds(10))
        val decoder = NimbusJwtDecoder.withJwkSetUri(properties.issuer.trimEnd('/') + "/.well-known/jwks.json")
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(RestTemplate(factory))
            .build()
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(
            JwtValidators.createDefaultWithIssuer(properties.issuer),
            ClerkSessionValidator(properties.authorizedParties)
        ))
        return decoder
    }
}

/** Only active browser session tokens from the configured frontend origins are accepted. */
class ClerkSessionValidator(private val authorizedParties: List<String>) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val valid = token.subject?.matches(Regex("user_[A-Za-z0-9]+")) == true &&
            !token.getClaimAsString("sid").isNullOrBlank() &&
            token.expiresAt != null && token.notBefore != null && token.issuedAt != null &&
            token.getClaimAsString("azp") in authorizedParties &&
            (token.getClaimAsString("sts") == null || token.getClaimAsString("sts") == "active")
        return if (valid) OAuth2TokenValidatorResult.success()
        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "An active Clerk session from an allowed origin is required", null))
    }
}
