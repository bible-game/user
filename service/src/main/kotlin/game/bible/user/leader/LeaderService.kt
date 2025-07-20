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
        }.sortedBy {
            it.gameStars + it.reviewStars
        }
    }

    fun getRank(userId: Long): RankResponse {
        val players = repository.getAllPlayers()
        val sortedPlayers = players.map {
            val gameStars = it[1] as Long
            val reviewStars = it[2] as Long
            val totalStars = gameStars + reviewStars
            Pair(it[0] as Long, totalStars)
        }.sortedByDescending { it.second }

        val rank = sortedPlayers.indexOfFirst { it.first == userId }.toLong() + 1
        return RankResponse(rank = rank, totalPlayers = players.size)
    }
}
