package game.bible.user.clerk

import game.bible.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException

class ClerkAccountResolver(
    private val repository: UserRepository,
    private val client: ClerkBackendClient,
    private val store: ClerkAccountStore
) {
    fun resolve(clerkId: String): Long {
        verifiedUserId(clerkId)?.let { return it }
        val identity = client.retrieve(clerkId)
        return try {
            store.provision(identity)
        } catch (e: DataIntegrityViolationException) {
            // A simultaneous request may have committed the same Clerk user. Its transaction has ended here.
            verifiedUserId(clerkId) ?: throw e
        }
    }

    private fun verifiedUserId(clerkId: String): Long? = repository.findByClerkUserId(clerkId)
        .filter { it.clerkEmailVerifiedAt != null }.map { it.id!! }.orElse(null)
}
