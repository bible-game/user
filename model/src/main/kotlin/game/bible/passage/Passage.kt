package game.bible.user

import game.bible.common.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.Date

/**
 * user Model
 *
 * @author J. R. Smith
 * @since 7th December 2024
 */
@Entity
@Table(name = "user")
data class user(
    val date: Date = Date(),
    val book: String = "",
    val chapter: String = "",
    val title: String = "",
    val summary: String = "",
    val verses: Int = 0,

    @Column(columnDefinition="TEXT")
    val text: String = ""
) : BaseEntity()
