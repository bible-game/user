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
        return repository.getLeaders(PageRequest.of(0, limit)) as List<LeaderResponse>
    }
}
