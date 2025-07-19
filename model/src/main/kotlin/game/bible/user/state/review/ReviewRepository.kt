package game.bible.user.state.review

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Review Data Access
 * @since 17th July 2024
 */
@Repository
interface ReviewRepository : JpaRepository<Review, Long> {

    fun findAllByUserId(userId: Long): List<Review>

}