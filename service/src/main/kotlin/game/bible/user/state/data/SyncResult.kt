package game.bible.user.state.data

/**
 * Sync Response Data: how many records the sync added or updated
 * @since 14th October 2026
 */
data class SyncResult (
    val games: Int,
    val reads: Int,
    val reviews: Int
)
