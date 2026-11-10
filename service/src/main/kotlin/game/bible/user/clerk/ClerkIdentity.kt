package game.bible.user.clerk

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus

/** Data obtained from Clerk's authenticated Backend API, never from a browser-supplied profile. */
data class ClerkIdentity(
    val id: String,
    val email: String,
    val firstname: String,
    val lastname: String,
    val externalId: Long?,
    val updatedAtMs: Long = 0
) {
    companion object {
        fun from(data: JsonNode, expectedId: String): ClerkIdentity {
            if (data.path("id").asText() != expectedId || data.path("deleted").asBoolean() ||
                data.path("banned").asBoolean() || data.path("locked").asBoolean()) {
                throw ClerkAccountException(HttpStatus.UNAUTHORIZED, "clerk_account_unavailable")
            }
            val primaryId = data.path("primary_email_address_id").asText()
            val primary = data.path("email_addresses").firstOrNull { it.path("id").asText() == primaryId }
            if (primary == null || primary.path("verification").path("status").asText() != "verified" ||
                primary.path("email_address").asText().isBlank()) {
                throw ClerkAccountException(HttpStatus.FORBIDDEN, "email_verification_required")
            }
            val external = data.path("external_id").takeUnless { it.isNull || it.isMissingNode }?.asText()
            val externalId = external?.toLongOrNull()
            if (external != null && (externalId == null || externalId <= 0)) {
                throw ClerkAccountException(HttpStatus.CONFLICT, "invalid_migration_id")
            }
            return ClerkIdentity(expectedId, primary.path("email_address").asText(),
                data.path("first_name").takeUnless { it.isNull }?.asText() ?: "",
                data.path("last_name").takeUnless { it.isNull }?.asText() ?: "", externalId, data.path("updated_at").asLong(0))
        }
    }
}

class ClerkAccountException(val status: HttpStatus, val code: String) : RuntimeException(code)
