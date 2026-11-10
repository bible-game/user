package game.bible.user.clerk

import com.fasterxml.jackson.databind.JsonNode
import game.bible.user.UserRepository
import game.bible.user.auth.repository.PasswordResetTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp

@Service
class ClerkWebhookService(
    private val states: ClerkWebhookStateRepository,
    private val users: UserRepository,
    private val resetTokens: PasswordResetTokenRepository
) {
    @Transactional
    fun apply(event: JsonNode) {
        val type = event.path("type").asText()
        if (type != "user.updated" && type != "user.deleted") return
        val data = event.path("data")
        val id = data.path("id").asText()
        if (!id.matches(Regex("user_[A-Za-z0-9]+")) || id.length > 255) invalid()
        val version = if (type == "user.deleted") event.path("timestamp") else data.path("updated_at")
        if (!version.isIntegralNumber || !version.canConvertToLong() || version.asLong() <= 0) invalid()
        // Shared with synchronous provisioning. Concurrent first inserts may fail and are retried by Svix.
        val state = states.lockById(id).orElseGet { states.saveAndFlush(ClerkWebhookState(id)) }
        if (state.deleted) return
        val linked = users.findByClerkUserId(id).orElse(null)
        val user = linked?.let { users.findForClerkMigration(it.id!!).orElse(null) }
        if (type == "user.deleted") {
            // Deletion always wins, including an unexpectedly old/replayed deletion event.
            state.deleted = true
            state.updatedAtMs = maxOf(state.updatedAtMs, version.asLong())
            if (user != null) {
                resetTokens.deleteForUser(user.id!!)
                // The bulk token delete clears the persistence context so its legacy cascade cannot resurrect the user.
                users.deleteById(user.id!!) // cascades games/guesses, reads, reviews/answers/grades
                users.flush()
            }
            return
        }
        // Never provision or link from a webhook (and never match accounts by email/external_id).
        if (user == null || version.asLong() <= state.updatedAtMs) return
        fun name(field: String): String {
            val value = data.path(field)
            if (!value.isNull && !value.isTextual) invalid()
            return value.takeUnless { it.isNull }?.asText()?.trim().orEmpty().also { if (it.length > 255) invalid() }
        }
        val first = name("first_name")
        val last = name("last_name")
        val primaryId = data.path("primary_email_address_id").asText()
        val primary = data.path("email_addresses").firstOrNull { it.path("id").asText() == primaryId }
        val verified = primary != null && primary.path("verification").path("status").asText() == "verified" &&
            primary.path("email_address").isTextual && primary.path("email_address").asText().isNotBlank()
        if (verified) {
            val email = primary!!.path("email_address").asText()
            if (email.length > 255) invalid()
            // Do not introduce an ambiguous email mapping while legacy users are still being migrated.
            val collision = users.findByEmailIgnoreCase(email).orElse(null)
            if (collision != null && collision.id != user.id) throw ResponseStatusException(HttpStatus.CONFLICT, "Email migration conflict")
            user.email = email
        }
        user.firstname = first
        user.lastname = last
        user.clerkEmailVerifiedAt = if (verified && !data.path("banned").asBoolean() && !data.path("locked").asBoolean())
            Timestamp(System.currentTimeMillis()) else null
        if (first.isBlank() || last.isBlank()) user.profileCompletedAt = null
        state.updatedAtMs = version.asLong()
        users.saveAndFlush(user)
    }

    private fun invalid(): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user event")
}
