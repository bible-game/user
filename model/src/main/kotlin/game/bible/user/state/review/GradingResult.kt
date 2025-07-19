package game.bible.user.state.review

import com.fasterxml.jackson.annotation.JsonBackReference
import game.bible.common.model.BaseEntity
import game.bible.user.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

/**
 * Grading Result Model
 * @since 18th July 2025
 */
@Entity
@Table(name = "grading_result")
class GradingResult(
    val score: Int = 0,
    val message: String = "",

) : BaseEntity()