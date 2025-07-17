package game.bible.user.auth.registration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import game.bible.user.state.GameData
import game.bible.user.state.ReadData
import game.bible.user.state.ReviewData

/**
 * Registration Request Data
 * @since 5th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class RegistrationData (
    val email: String,
    val password: String,
    val firstname: String,
    val lastname: String,
    val church: String,

    // State
    val games: MutableList<GameData>, // question? Build completion on frontend from Game & Read details
    val reads: MutableList<ReadData>,
    val reviews: MutableList<ReviewData>
)