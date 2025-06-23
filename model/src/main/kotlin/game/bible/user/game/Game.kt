package game.bible.user.game

import game.bible.common.model.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

/**
 * Played Game Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "game")
class Game (
    val passageId: Long = 0,
    val playing: Boolean = true,
    val stars: Int = 0,

    @OneToMany(cascade = [CascadeType.ALL])
    val guesses: List<Guess> = emptyList()

) : BaseEntity()
