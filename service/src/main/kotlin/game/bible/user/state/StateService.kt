package game.bible.user.state

import game.bible.user.UserRepository
import game.bible.user.state.data.GameData
import game.bible.user.state.data.GuessData
import game.bible.user.state.data.ReadData
import game.bible.user.state.data.ReviewData
import game.bible.user.state.data.SyncData
import game.bible.user.state.data.SyncResult
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
import org.springframework.transaction.annotation.Transactional
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

    /**
     * Merges browser state recorded while logged out into a user's persisted state.
     * A finished game on the server stands; otherwise the copy with more guesses wins. Stars are recalculated here
     * rather than trusted. Reads and reviews are added for passages the user doesn't already have.
     */
    @Transactional
    fun sync(userId: Long, data: SyncData): SyncResult {
        val user = userRepository.findById(userId).get()

        val games = gameRepository.findAllByUserId(userId).associateBy { it.passageId }
        var syncedGames = 0
        for (local in data.games.distinctBy { it.passageId }) {
            val guesses = scoredGuesses(local) ?: continue
            val existing = games[local.passageId]
            if (existing != null && (!existing.playing || existing.guesses.size >= guesses.size)) continue

            val game = existing ?: Game(local.passageId, local.guesses.first().passageBook, local.guesses.first().passageChapter, true, 0, mutableListOf(), user)
            game.guesses.clear()
            game.guesses.addAll(guesses)
            score(game)
            gameRepository.save(game)
            syncedGames++
        }

        val readKeys = readRepository.findAllByUserId(userId).map { it.passageKey }.toSet()
        val reads = data.reads.distinctBy { it.passageKey }.filter { it.passageKey !in readKeys }
            .map { Read(it.passageKey, it.book, it.chapter, it.verseStart, it.verseEnd, user) }
        readRepository.saveAll(reads)

        val reviewKeys = reviewRepository.findAllByUserId(userId).map { it.passageKey }.toSet()
        val reviews = data.reviews.distinctBy { it.passageKey }.filter { it.passageKey !in reviewKeys }
            .map { Review(it.passageKey, it.date, it.stars, it.summary, it.answers, GradingResult(it.gradingResult.score, it.gradingResult.message), user) }
        reviewRepository.saveAll(reviews)

        log.info { "Synced [${syncedGames}] games, [${reads.size}] reads and [${reviews.size}] reviews for user [$userId]" }
        return SyncResult(syncedGames, reads.size, reviews.size)
    }

    /** The guesses of a local game, cut off at the win or the fifth guess; null if there are none */
    private fun scoredGuesses(local: GameData): List<Guess>? {
        if (local.guesses.isEmpty()) return null
        val win = local.guesses.indexOfFirst { it.distance == 0 }
        val count = if (win >= 0) minOf(win + 1, 5) else minOf(local.guesses.size, 5)

        return local.guesses.take(count).map { Guess(it.distance, it.percentage, it.book, it.chapter) }
    }

    /** Sets whether a game is over and its stars, as createGuess does */
    private fun score(game: Game) {
        val won = game.guesses.last().distance == 0
        game.playing = !(won || game.guesses.size == 5)
        game.stars = if (won) (5 + 1 - game.guesses.size) else 0
    }

}