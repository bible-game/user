package game.bible.user.clerk

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

/** Ordering/deletion guard only: no profile, email or webhook payload is retained. */
@Entity
@Table(name = "clerk_webhook_state")
class ClerkWebhookState(
    @Id @Column(name = "clerk_user_id", length = 255) var clerkUserId: String = "",
    @Column(name = "updated_at_ms", nullable = false) var updatedAtMs: Long = 0,
    @Column(nullable = false) var deleted: Boolean = false
)

interface ClerkWebhookStateRepository : JpaRepository<ClerkWebhookState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClerkWebhookState s WHERE s.clerkUserId = :id")
    fun lockById(id: String): Optional<ClerkWebhookState>
}
