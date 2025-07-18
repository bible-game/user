package game.bible.user.state.game

import game.bible.common.model.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Guess Model
 * @since 26th June 2025
 */
@Entity
@Table(name = "guess")
class Guess(
    var distance: Int = 0,
    var percentage: Int = 0,
    var book: String = "",
    var chapter: String = ""

) : BaseEntity()