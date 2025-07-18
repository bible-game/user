package game.bible.user.auth.registration

import game.bible.user.User
import game.bible.user.UserRepository
import game.bible.user.state.game.Game
import game.bible.user.state.game.Guess
import game.bible.user.state.read.Read
import game.bible.user.state.review.GradingResult
import game.bible.user.state.review.Review
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

private val log = KotlinLogging.logger {}

/**
 * Registration Service-Logic
 * @since 5th June 2025
 */
@Service
class RegistrationService(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder) {

    /** Performs user registration */
    @Transactional //@Throws(RegistrationException::class)
    fun register(data: RegistrationData): User {
        val email = data.email // .normalise() -> TODO :: add to common utils (

        if (isExisting(email)) {
            throw Exception("registration.error.user-exists")
            // TODO :: common app exception and message from service-level error bundle
        }

        val password = passwordEncoder.encode(data.password)
        return createUser(email, password, data)
    }

    /** Determines if a user exists with a given email */
    @Transactional(readOnly = true)
    fun isExisting(email: String): Boolean {
        log.info { "Checking if user with email already exists [$email]" }
        return repository.findByEmail(email).isPresent
    }

    /** Creates a user */
    @Transactional
    fun createUser(
        email: String, password: String, data: RegistrationData): User {
        log.info { "Transferring browser state to persistent storage" }

        val user = User(
            email,
            password,
            data.firstname,
            data.lastname,
            data.church
        )
        user.games.addAll(data.games.map { g -> Game(g.passageId, g.guesses.first().passageBook, g.guesses.first().passageChapter, g.playing, g.stars, g.guesses.map { Guess(it.distance, it.percentage, it.book, it.chapter) } as MutableList, user) })
        user.reads.addAll(data.reads.map { Read(it.passageKey, it.book, it.chapter, it.verseStart, it.verseEnd, user) })
        user.reviews.addAll(data.reviews.map { Review(it.passageKey, it.date, it.stars, it.summary, it.answers, GradingResult(it.gradingResult.score, it.gradingResult.message), user) })

        return repository.save(user)
    }

}