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
    var email: String = "",
    val password: String = "",
    var firstname: String = "",
    var lastname: String = "",
    var church: String = "",

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

    @Column(name = "clerk_user_id", unique = true)
    var clerkUserId: String? = null

    /** Set only after the Backend API confirms ownership of the primary email. */
    @Column(name = "clerk_email_verified_at")
    var clerkEmailVerifiedAt: Timestamp? = null


    @Column(name = "profile_completed_at")
    var profileCompletedAt: Timestamp? = null

    /** When the password was last changed; tokens issued before then are no longer accepted */
    @Column(name = "password_changed_at")
    var passwordChangedAt: Timestamp? = null

}
