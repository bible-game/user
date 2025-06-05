package game.bible.user

import game.bible.common.model.BaseEntity
import game.bible.user.game.Game
import game.bible.user.read.Read
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * User Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "app_user")
data class User(
    val email: String = "",
    val password: String = "",
    val games: List<Game> = emptyList(),
    val reads: List<Read> = emptyList()
) : BaseEntity()
