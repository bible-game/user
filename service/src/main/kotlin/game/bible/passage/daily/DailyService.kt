package game.bible.user.daily

import game.bible.user.user
import game.bible.user.userRepository
import game.bible.user.generation.GenerationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.util.Date


private val log = KotlinLogging.logger {}

/**
 * Daily user Service Logic
 *
 * @author J. R. Smith
 * @since 7th December 2024
 */
@Service
class DailyService(
    private val generator: GenerationService,
    private val userRepository: userRepository) {

    private var date: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")

    /** Generates a bible user and retrieves it from storage */
    fun retrieveuser(date: Date): user {
        val entry = userRepository.findByDate(date)

        return if (entry.isPresent) {
            entry.get()

        } else generateuser(date)
    }

    /** Retrieves paginated list of historic daily user */
    fun retrieveDates(page: Int): List<String> {
        val users = userRepository.findAll()
        // TODO :: for a logged in user, return paginated results of all dates (attempted [color?], won [star?], not-attempted [question mark?])
        // for non-logged in user, just return all existing dates (paginate as required)
        // pagination -> pull back a month at a time!

        return users.map { date.format(it.date) }
    }

    private fun generateuser(date: Date): user {
        log.info { "No entry exists for [$date]! Generating random user" }
        val randomuser = generator.random(date)

        return userRepository.save(randomuser)
    }

}