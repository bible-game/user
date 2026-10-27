package game.bible.user

import com.fasterxml.jackson.annotation.JsonManagedReference
import game.bible.common.model.BaseEntity
import game.bible.user.state.game.Game
import game.bible.user.state.read.Read
import game.bible.user.state.review.Review
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.sql.Timestamp

/**
 * User Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "app_user")
data class User(
    val email: String = "",
    val password: String = "",
    val firstname: String = "",
    val lastname: String = "",
    val church: String = "",

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    @JsonManagedReference("app_user_game")
    val games: MutableList<Game> = mutableListOf(),
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    @JsonManagedReference("app_user_read")
    val reads: MutableList<Read> = mutableListOf(),
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    @JsonManagedReference("app_user_review")
    val reviews: MutableList<Review> = mutableListOf()

) : BaseEntity() {

    /** When the password was last changed; tokens issued before then are no longer accepted */
    @Column(name = "password_changed_at")
    var passwordChangedAt: Timestamp? = null

}
