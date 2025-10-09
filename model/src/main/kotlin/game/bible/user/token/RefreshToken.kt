package game.bible.user.token

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID


/**
 * Model for Refresh Tokens (using JWT Format)
 *
 * @author Hayden Eastwell
 */
@Entity
@Table(name = "refresh_token")
data class RefreshToken(

    @Id
    var id: String = UUID.randomUUID().toString(),

    var userId: Long? = null,

    var signature: String? = null,

    var status: Status? = Status.ACTIVE,
) {

    enum class Status {
        EXPIRED,
        ACTIVE,
        ROTATED
    }
}