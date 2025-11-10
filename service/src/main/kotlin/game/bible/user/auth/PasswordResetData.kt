package game.bible.user.auth

/**
 * Models the payload received for Password reset requests
 *
 * @author James Smith (jrsmth)
 * @author Hayden Eastwell (haydende)
 */
data class PasswordResetData(
    val resetToken: String,
    val password: String
)
