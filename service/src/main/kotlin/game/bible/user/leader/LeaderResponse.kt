package game.bible.user.leader

/**
 * Leaderboard Response Object
 * @since 20th July 2025
 */
data class LeaderResponse (
    val id: Long,
    val firstname: String,
    val lastname: String,
    val gameStars: Long,
    val reviewStars: Long
)