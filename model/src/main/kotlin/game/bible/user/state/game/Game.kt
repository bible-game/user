package game.bible.user.state.game

import com.fasterxml.jackson.annotation.JsonBackReference
import game.bible.common.model.BaseEntity
import game.bible.user.User
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

import game.bible.user.state.GameData

/**
 * Played Game Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "game")
class Game(
    var passageId: Long = 0,
    var playing: Boolean = true,
    var stars: Int = 0,

    @OneToMany(cascade = [CascadeType.ALL])
    var guesses: MutableList<Guess> = mutableListOf(),

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference("app_user_game")
    var user: User? = null

) : BaseEntity() {
    constructor(data: GameData, user: User) : this(
        data.passageId,
        data.playing,
        data.stars,
        data.guesses.map { Guess(it) }.toMutableList(),
        user
    )
}
