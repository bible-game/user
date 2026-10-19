package game.bible.user.state.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Sync Request Data: browser state recorded while logged out
 * @since 14th October 2026
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SyncData (
    val games: List<GameData> = listOf(),
    val reads: List<ReadData> = listOf(),
    val reviews: List<ReviewData> = listOf()
)
