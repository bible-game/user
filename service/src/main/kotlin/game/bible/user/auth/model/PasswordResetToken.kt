package game.bible.user.auth.model

import game.bible.user.User
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Models the token used when a User is resetting a forgotten password
 *
 * @author Hayden Eastwell (haydende)
 */
@Entity
@Table(name = "password_reset_token")
class PasswordResetToken(

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var token: String? = null,

    @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    val user: User,

    @Column(nullable = false)
    var state: ResetTokenState = ResetTokenState.ACTIVE,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

) {

    enum class ResetTokenState(string: String) {
        ACTIVE("ACTIVE"),
        USED("USED"),
        EXPIRED("EXPIRED"),
        REPLACED("REPLACED"),
    }
}