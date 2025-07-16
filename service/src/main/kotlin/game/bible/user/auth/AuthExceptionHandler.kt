package game.bible.user.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception::class)
    fun internalError(e: Throwable) {
        log.error(e) { "Internal error: ${e.localizedMessage}" }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(
        InsufficientAuthenticationException::class,
        UsernameNotFoundException::class
    )
    fun authFailed(e: Throwable): ResponseEntity<Any> {
        log.error { "Authentication error: ${e.localizedMessage}" }
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(object {
                val error = e.localizedMessage
            })
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }

}