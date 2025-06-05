package game.bible.user.game

import game.bible.common.model.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "guess")
class Guess : BaseEntity()