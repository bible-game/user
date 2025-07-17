package game.bible.user.state.game

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Game Data Access
 * @since 5th June 2024
 */
@Repository
interface GameRepository : JpaRepository<Game, Long> {

    fun findAllByUserId(userId: Long): List<Game>

    fun findByPassageIdAndUserId(passageId: Long, userId: Long): Optional<Game>

}