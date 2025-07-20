package game.bible.user.leader

/**
 * Leaderboard Response Object
 * @since 20th July 2025
 */
data class LeaderResponse (
    val id: Int,
    val firstname: String,
    val lastname: String,
    val gameStars: Int,
    val reviewStars: Int
)