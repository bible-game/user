package game.bible.user.state.game

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * User Data Access
 * @since 5th June 2024
 */
@Repository
interface GameRepository : JpaRepository<Game, Long> {

    fun findAllByUserId(userId: Long): List<Game>

}