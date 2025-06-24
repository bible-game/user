package game.bible.user.state

import game.bible.user.state.game.Game
import game.bible.user.state.game.GameRepository
import game.bible.user.state.read.ReadRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * State Service-Logic
 * @since 24th June 2025
 */
@Service
class StateService(
    private val gameRepository: GameRepository,
    private val readRepository: ReadRepository
) {

    /** Retrieves the played games state for a given user */
    fun retrieveGameState(userId: Long): List<Game> {
        val state = gameRepository.findAllByUserId(userId)

        return state.ifEmpty { throw Exception() }
    }

}