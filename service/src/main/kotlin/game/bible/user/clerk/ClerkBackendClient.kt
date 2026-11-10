package game.bible.user.clerk

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

class ClerkBackendClient(properties: ClerkProperties) {
    private val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
        .also { it.setReadTimeout(Duration.ofSeconds(10)) }
    private val client = RestClient.builder().baseUrl("https://api.clerk.com/v1")
        .defaultHeader("Authorization", "Bearer ${properties.secretKey}")
        .requestFactory(factory).build()

    fun retrieve(userId: String): ClerkIdentity {
        val body = try {
            client.get().uri("/users/{id}", userId).retrieve().body(JsonNode::class.java)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 404) throw ClerkAccountException(HttpStatus.UNAUTHORIZED, "clerk_account_unavailable")
            // Never expose Clerk response bodies, API keys or user details in logs/errors.
            throw ClerkAccountException(HttpStatus.SERVICE_UNAVAILABLE, "identity_provider_unavailable")
        } catch (e: RestClientException) {
            throw ClerkAccountException(HttpStatus.SERVICE_UNAVAILABLE, "identity_provider_unavailable")
        }
        return ClerkIdentity.from(body ?: throw ClerkAccountException(HttpStatus.SERVICE_UNAVAILABLE, "identity_provider_unavailable"), userId)
    }
}
