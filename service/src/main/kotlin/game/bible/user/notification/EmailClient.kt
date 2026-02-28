package game.bible.user.notification

import com.mailjet.client.ClientOptions
import com.mailjet.client.MailjetClient
import com.mailjet.client.transactional.SendContact
import com.mailjet.client.transactional.SendEmailsRequest
import com.mailjet.client.transactional.TransactionalEmail
import com.mailjet.client.transactional.response.SendEmailsResponse
import game.bible.config.model.service.UserConfig

class EmailClient {

    private val userConfig: UserConfig
    private val client: MailjetClient

    constructor(userConfig: UserConfig) {
        this.userConfig = userConfig
        val comms = userConfig.getComms()
        val options = ClientOptions.builder()
            .apiKey(comms!!.getApiKey())
            .apiSecretKey(comms.getSecretKey())
            .baseUrl(comms.getBaseUrl())
            .build()

        this.client = MailjetClient(options)
    }

    fun sendResetEmail(email: String, resetToken: String): SendEmailsResponse {
        val email = TransactionalEmail.builder()
            .to(SendContact(email))
            .from(SendContact(userConfig.getComms()!!.getSendAddress()))
            .templateID(userConfig.getComms()!!.getTemplateId())
            .templateLanguage(true)
            .variable("resetLink", "${userConfig.getResetUrl()!!}?token=$resetToken")
            .build()

        val request = SendEmailsRequest.builder()
            .message(email)
            .build()

        return request.sendWith(this.client)
    }

}