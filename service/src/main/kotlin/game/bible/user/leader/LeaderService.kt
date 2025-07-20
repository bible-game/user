package game.bible.user.leader

import game.bible.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Leaderboard Service Logic
 * @since 29th July 2025
 */
@Service
class LeaderService(private val repository: UserRepository) {

    fun getLeaders(limit: Int): List<LeaderResponse> {
        return repository.getLeaders(PageRequest.of(0, limit)).map {
            LeaderResponse(it[0] as Long, it[1] as String, it[2] as String, it[3] as Long, it[4] as Long)
        }
    }
}
