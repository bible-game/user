package game.bible.user.clerk

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@ConditionalOnProperty(prefix = "clerk", name = ["enabled"], havingValue = "true")
class ClerkWebhookController(
    private val properties: ClerkProperties,
    private val mapper: ObjectMapper,
    private val service: ClerkWebhookService
) {
    @PostMapping("/webhooks/clerk")
    fun receive(request: HttpServletRequest): ResponseEntity<Void> {
        val body = request.inputStream.readNBytes(262145)
        if (body.size > 262144) throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE)
        fun singleHeader(name: String): String? {
            val values = request.getHeaders(name).toList()
            if (values.size != 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or repeated signature header")
            return values.single()
        }
        ClerkWebhookVerifier(properties.webhookSigningSecret).verify(body,
            singleHeader("svix-id"), singleHeader("svix-timestamp"), singleHeader("svix-signature"))
        if (properties.instanceId.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook instance is not configured")
        val event = try { mapper.readTree(body) } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook envelope")
        }
        if (event == null || event.path("object").asText() != "event" ||
            event.path("instance_id").asText() != properties.instanceId || !event.path("type").isTextual) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook envelope")
        }
        service.apply(event)
        return ResponseEntity.noContent().build()
    }
}
