package game.bible.user.state

import game.bible.user.UserRepository
import game.bible.user.state.game.Game
import game.bible.user.state.game.GameRepository
import game.bible.user.state.game.Guess
import game.bible.user.state.read.ReadRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.Date
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

/**
 * State Service-Logic
 * @since 24th June 2025
 */
@Service
class StateService(
    private val gameRepository: GameRepository,
    private val readRepository: ReadRepository,
    private val userRepository: UserRepository
) {

    /** Retrieves the played game state for a given user */
    fun retrieveGameState(userId: Long): List<Game> {
        val state = gameRepository.findAllByUserId(userId)

        return state.ifEmpty { throw Exception() }
    }

    /** Creates a guess against a given user */
    fun createGuess(userId: Long, passageId: Long, data: GuessData): List<Guess> {
        val guess = Guess(data.book, data.chapter, data.distance, data.percentage)

        var game = gameRepository.findByPassageIdAndUserId(passageId, userId).getOrNull()

        if (game == null) {
            val user = userRepository.findById(userId).get()
            game = Game(passageId, true, 0, mutableListOf(guess), user)

        } else {
            game.guesses.addLast(guess)
        }

        return gameRepository.save(game).guesses
    }

}