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
    val guesses: MutableList<Guess> = mutableListOf(),

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference("app_user_games")
    val user: User = User()

) : BaseEntity()
