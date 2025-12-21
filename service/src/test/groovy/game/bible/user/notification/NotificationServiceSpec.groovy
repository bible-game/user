package game.bible.user.notification

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.config.model.service.UserConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification


class NotificationServiceSpec extends Specification {

    private static MockWebServer mockWebServer = new MockWebServer()
    private static NotificationService notificationService
    ObjectMapper mapper = new ObjectMapper()

    def setupSpec() {
        mockWebServer.start()

        UserConfig userConfig = new UserConfig()
        UserConfig.Communications comms = new UserConfig.Communications()

        ReflectionTestUtils.setField(comms, "baseUrl", mockWebServer.url("/").toString())
        ReflectionTestUtils.setField(comms, "apiKey", "someapikey")
        ReflectionTestUtils.setField(comms, "sendAddress", "hello@mail.com")
        ReflectionTestUtils.setField(comms, "secretKey", "shhhhhh")

        ReflectionTestUtils.setField(userConfig, "comms", comms)

        EmailClient emailClient = new EmailClient(userConfig)
        notificationService = new NotificationService(emailClient)
    }

    def cleanupSpec() {
        mockWebServer.shutdown()
    }

    def "when a an email address and reset token are provided, then send the password reset email"() {
        given:
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("{}"))

        def email = "user@mail.com"
        def resetToken = "thisisafaketokenbutthatsokay"

        when:
        notificationService.sendResetRequest(email, resetToken)

        then:
        noExceptionThrown()

        and:
        RecordedRequest received = mockWebServer.takeRequest()
        Map<String, Object> body = mapper.readValue(received.getBody().readUtf8(), Map<String, Object>.class)
        Map<String, Object> sentMessage = (body["Messages"] as List)[0] as Map

        sentMessage["From"]["Email"] == "hello@mail.com"
        sentMessage["To"][0]["Email"] == email
        sentMessage["Subject"] == "Your password reset request for bible.game"
    }

}
