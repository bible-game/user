package game.bible.user.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val emailClient: EmailClient
) {

    fun sendResetRequest(email: String, resetToken: String) {
        log.info("Building Password Reset request email for user: [{}] with token: [{}]", email, resetToken)
        emailClient.sendResetEmail(email, resetToken)
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
    }

}