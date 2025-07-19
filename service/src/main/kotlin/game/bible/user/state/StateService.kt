package game.bible.user.state

import game.bible.user.UserRepository
import game.bible.user.state.data.GuessData
import game.bible.user.state.data.ReadData
import game.bible.user.state.data.ReviewData
import game.bible.user.state.game.Game
import game.bible.user.state.game.GameRepository
import game.bible.user.state.game.Guess
import game.bible.user.state.read.Read
import game.bible.user.state.read.ReadRepository
import game.bible.user.state.review.GradingResult
import game.bible.user.state.review.Review
import game.bible.user.state.review.ReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
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
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository
) {

    /** Retrieves the played game state for a given user */
    fun retrieveGameState(userId: Long): List<Game> {
        val state = gameRepository.findAllByUserId(userId)

        return state
    }

    /** Retrieves the read state for a given user */
    fun retrieveReadState(userId: Long): List<Read> {
        val state = readRepository.findAllByUserId(userId)

        return state
    }

    /** Retrieves the review state for a given user */
    fun retrieveReviewState(userId: Long): List<Review> {
        val state = reviewRepository.findAllByUserId(userId)

        return state
    }

    /** Creates a guess record against a given user */
    fun createGuess(userId: Long, passageId: Long, data: GuessData): List<Guess> {
        val guess = Guess(data.distance, data.percentage, data.book, data.chapter)

        var game = gameRepository.findByPassageIdAndUserId(passageId, userId).getOrNull()

        if (game == null) {
            val user = userRepository.findById(userId).get()
            game = Game(passageId, data.passageBook, data.passageChapter,true, 0, mutableListOf(guess), user)

        } else if (!game.playing) {
            return game.guesses

        } else {
            game.guesses.addLast(guess)
        }

        val won = (guess.distance == 0)
        if (game.guesses.size == 5 || won) {
            game.playing = false
            game.stars = if (won) (5 + 1 - game.guesses.size) else 0
        }

        return gameRepository.save(game).guesses
    }

    /** Creates a read record against a given user */
    fun createRead(userId: Long, data: ReadData): List<Read> {
        val user = userRepository.findById(userId).get()
        val read = Read(data.passageKey, data.book, data.chapter, data.verseStart, data.verseEnd, user)

        readRepository.save(read)
        return readRepository.findAllByUserId(userId)
    }

    /** Creates a review record against a given user */
    fun createReview(userId: Long, data: ReviewData): List<Review> {
        val user = userRepository.findById(userId).get()
        val result = GradingResult(data.gradingResult.score, data.gradingResult.message)
        val review = Review(data.passageKey, data.date, data.stars, data.summary, data.answers, result, user)

        reviewRepository.save(review)
        return reviewRepository.findAllByUserId(userId)
    }

}