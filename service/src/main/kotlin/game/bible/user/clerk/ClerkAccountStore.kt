package game.bible.user.clerk

import game.bible.user.User
import game.bible.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Service
class ClerkAccountStore(private val repository: UserRepository, private val states: ClerkWebhookStateRepository) {
    /** A database constraint handles concurrent first requests; callers retry after rollback. */
    @Transactional
    fun provision(identity: ClerkIdentity): Long {
        val state = states.lockById(identity.id).orElseGet { states.saveAndFlush(ClerkWebhookState(identity.id)) }
        if (state.deleted) throw ClerkAccountException(HttpStatus.UNAUTHORIZED, "clerk_account_unavailable")
        val linked = repository.findByClerkUserId(identity.id).orElse(null)
        val user = if (identity.externalId != null) {
            val imported = repository.findForClerkMigration(identity.externalId).orElseThrow {
                ClerkAccountException(HttpStatus.CONFLICT, "migration_user_missing")
            }
            if ((linked != null && linked.id != imported.id) ||
                (imported.clerkUserId != null && imported.clerkUserId != identity.id)) {
                throw ClerkAccountException(HttpStatus.CONFLICT, "migration_identity_conflict")
            }
            imported
        } else linked ?: run {
            // Email equality is not proof of ownership of a legacy app account.
            if (repository.existsByEmailIgnoreCase(identity.email)) {
                throw ClerkAccountException(HttpStatus.CONFLICT, "account_migration_required")
            }
            User()
        }
        // A newer signed event must not be overwritten by an earlier in-flight Backend API response.
        if (linked != null && state.updatedAtMs > identity.updatedAtMs) {
            if (linked.clerkEmailVerifiedAt == null) throw ClerkAccountException(HttpStatus.FORBIDDEN, "identity_refresh_required")
            return linked.id!!
        }
        state.updatedAtMs = maxOf(state.updatedAtMs, identity.updatedAtMs)
        user.clerkUserId = identity.id
        user.email = identity.email
        user.firstname = identity.firstname
        user.lastname = identity.lastname
        user.clerkEmailVerifiedAt = Timestamp(System.currentTimeMillis())
        return repository.saveAndFlush(user).id!!
    }
}
