package game.bible.user.game

import game.bible.common.model.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Played Game Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "game")
class Game (
    val passageId: Long = 0,
    val guesses: List<Guess> = emptyList(),
) : BaseEntity()
