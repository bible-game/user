package game.bible.user.read

import game.bible.common.model.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Ticked Read Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "read")
class Read (
    val passageKey: String = ""
) : BaseEntity()