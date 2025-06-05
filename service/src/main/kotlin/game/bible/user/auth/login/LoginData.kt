package game.bible.user.auth.login

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Login Request Data
 * @since 5th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class LoginData (
    val email: String,
    val password: String
)