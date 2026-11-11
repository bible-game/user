package game.bible.user.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
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
        AuthenticationException::class,
        UsernameNotFoundException::class
    )
    fun authFailed(e: Throwable): ResponseEntity<Any> {
        log.error { "Authentication error: ${e.localizedMessage}" }
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(object {
                val error = "Invalid email or password"
            })
    }

    @ExceptionHandler(DuplicateKeyException::class)
    fun duplicate(e: DuplicateKeyException): ResponseEntity<Any> = ResponseEntity.status(409)
        .body(mapOf("message" to "This email is already registered."))

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    fun invalid(e: Exception): ResponseEntity<Any> = ResponseEntity.badRequest()
        .body(mapOf("message" to "Please check the submitted details."))

    companion object {
        private val log = KotlinLogging.logger { }
    }

}