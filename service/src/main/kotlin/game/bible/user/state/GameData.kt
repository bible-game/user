package game.bible.user.state

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Game Request Data
 * @since 17th July 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class GameData (
    val passageId: Long,
    var stars: Int,
    val guesses: MutableList<GuessData>,
    val playing: Boolean
)