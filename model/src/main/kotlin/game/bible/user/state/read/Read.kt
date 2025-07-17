package game.bible.user.state.read

import com.fasterxml.jackson.annotation.JsonBackReference
import game.bible.common.model.BaseEntity
import game.bible.user.User
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

import game.bible.user.state.ReadData

/**
 * Ticked Read Model
 * @since 5th June 2025
 */
@Entity
@Table(name = "read")
class Read(
    val passageKey: String = "",

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference("app_user_read")
    var user: User? = null

) : BaseEntity() {
    constructor(data: ReadData, user: User) : this(
        data.passageKey,
        user
    )
}