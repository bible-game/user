package game.bible.user.state.review

import com.fasterxml.jackson.annotation.JsonBackReference
import game.bible.common.model.BaseEntity
import game.bible.user.User
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.FetchType.LAZY
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * Review Model
 * @since 17th July 2025
 */
@Entity
@Table(name = "review")
class Review(
    val passageKey: String = "",
    val date: String = "",
    val stars: Int = 0,
    val summary: String = "",

    @ElementCollection(targetClass = String::class, fetch = EAGER)
    @CollectionTable(name = "answer", joinColumns = [JoinColumn(name = "review_id")])
    val answers: List<String> = mutableListOf(),

    @OneToOne(cascade = [ALL], orphanRemoval = true, fetch = LAZY)
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    val gradingResult: GradingResult = GradingResult(0, ""),

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference("app_user_review")
    var user: User? = null

) : BaseEntity()