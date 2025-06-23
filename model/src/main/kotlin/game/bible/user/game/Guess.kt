package game.bible.user.game

import game.bible.common.model.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "guess")
class Guess (
    val book: String = "",
    val chapter: String = "",
    val distance: Int = 0,
    val percentage: Int = 0
) : BaseEntity()