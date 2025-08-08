package game.bible.user.state.review

import game.bible.common.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Grading Result Model
 * @since 18th July 2025
 */
@Entity
@Table(name = "grading_result")
class GradingResult(
    val score: Int = 0,

    @Column(columnDefinition="TEXT")
    val message: String = "",

) : BaseEntity()