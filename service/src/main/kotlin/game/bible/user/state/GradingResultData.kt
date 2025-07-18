package game.bible.user.state

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class GradingResultData (
    val score: Int,
    val message: String,
)