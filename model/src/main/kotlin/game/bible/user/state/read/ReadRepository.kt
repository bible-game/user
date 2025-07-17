package game.bible.user.state.read

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Read Data Access
 * @since 5th June 2024
 */
@Repository
interface ReadRepository : JpaRepository<Read, Long> {

    fun findAllByUserId(userId: Long): List<Read>

}