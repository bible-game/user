package game.bible.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Date
import java.util.Optional

/**
 * user Data Access
 *
 * @author J. R. Smith
 * @since 9th December 2024
 */
@Repository
interface userRepository : JpaRepository<user, Long> {

    fun findByDate(date: Date): Optional<user>

}