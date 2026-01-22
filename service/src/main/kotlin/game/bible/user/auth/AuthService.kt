package game.bible.user.auth

import game.bible.config.model.core.SecurityConfig
import game.bible.user.User
import game.bible.user.UserRepository
import game.bible.user.auth.model.PasswordResetToken
import game.bible.user.auth.model.PasswordResetToken.ResetTokenState
import game.bible.user.auth.repository.PasswordResetTokenRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrElse

/**
 * General Auth Service Logic
 * @since 19th July 2025
 */
@Service
class AuthService(
    private val userRepo: UserRepository,
    private val resetTokenRepo: PasswordResetTokenRepository,
    private val securityConfig: SecurityConfig,
    private val passwordEncoder: PasswordEncoder,
) {

    private fun currentTime() = LocalDateTime.now()

    @Transactional
    fun updatePassword(data: PasswordResetData) {
        val resetToken = resetTokenRepo.findById(data.resetToken).let {
            val token = it.getOrElse { throw IllegalStateException("Token [${data.resetToken}] does not exist") }
            if (token.expiresAt.isBefore(currentTime())) {
                token.state = ResetTokenState.EXPIRED
                log.debug("Submitted reset token ${token.token} marked as EXPIRED")
                resetTokenRepo.save(token)
            }
            if (token.state !== ResetTokenState.ACTIVE) {
                throw IllegalStateException("Token [${data.resetToken}] has expired")
            }
            token
        }
        log.debug("Handling password reset request for User ID: [{}]", resetToken.user.id!!)

        try {
            userRepo.updatePassword(resetToken.user.id!!, passwordEncoder.encode(data.password))
            resetToken.state = ResetTokenState.USED
            resetTokenRepo.save(resetToken)
        } catch (e: Exception) {
            log.error("Error thrown when attempting password reset for User ID: [{}]", resetToken.user.id!!, e)
            throw e
        }
    }

    /**
     * Creates a new Password Reset Token to enable the user to perform the reset. If they have a token already, expire it
     * and create a replacement.
     *
     * Also, if there are any flagged as 'ACTIVE' but whose expiredAt is before the current time, set their state to 'EXPIRED'.
     *
     * @param user The User to create the PasswordResetToken for
     * @return The token string for use in the reset URL
     */
    @Transactional
    fun createPasswordResetToken(user: User): String {
        val resetConfig = securityConfig.getPasswordReset()!!
        val currentTime = currentTime()
        val existingToken = resetTokenRepo
            .getActiveTokensForUser(user.id!!)
            .let { tokens ->
                tokens.forEach { token ->
                    if (token.expiresAt.isBefore(currentTime)) {
                        token.state = ResetTokenState.EXPIRED
                        resetTokenRepo.save(token)
                    }
                }
                tokens.firstOrNull() { token -> token.state == ResetTokenState.ACTIVE }
            }

        val newToken = PasswordResetToken(
            user = user,
            expiresAt = currentTime.plusMinutes(resetConfig.getExpireInMins()!!),
        )
        if (existingToken != null) {
            log.info("Replacing Password Reset Token [{}] with new token [{}] for User ID [{}]", existingToken.token, existingToken.token, user.id)
            existingToken.state = ResetTokenState.REPLACED
            resetTokenRepo.save(existingToken)
        }
        val savedToken = resetTokenRepo.save(newToken)

        return savedToken.token!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
