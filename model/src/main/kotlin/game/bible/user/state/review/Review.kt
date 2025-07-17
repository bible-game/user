package game.bible.user.state.review

import com.fasterxml.jackson.annotation.JsonBackReference
import game.bible.common.model.BaseEntity
import game.bible.user.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

import game.bible.user.state.ReviewData

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

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference("app_user_review")
    var user: User? = null

) : BaseEntity() {
    constructor(data: ReviewData, user: User) : this(
        data.passageKey,
        data.date,
        data.stars,
        data.summary,
        data.answers,
        user
    )
}