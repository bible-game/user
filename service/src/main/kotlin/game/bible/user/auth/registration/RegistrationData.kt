package game.bible.user.auth.registration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Registration Request Data
 * @since 5th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class RegistrationData (
    val email: String,
    val password: String
)