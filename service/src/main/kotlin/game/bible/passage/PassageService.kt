package game.bible.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class userService

fun main(args: Array<String>) {
	runApplication<userService>(*args)
}
