package game.bible.user.auth

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.user.User
import game.bible.user.UserRepository
import game.bible.user.auth.model.PasswordResetToken
import game.bible.user.auth.repository.PasswordResetTokenRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PersistenceUnit
import jakarta.transaction.Transactional
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hibernate.Session
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.LocalDateTime

import static game.bible.user.auth.model.PasswordResetToken.ResetTokenState
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT extends Specification {

    static MockWebServer mockWebServer = new MockWebServer()
    static ObjectMapper mapper = new ObjectMapper()

    EntityManager entityManager

    @PersistenceUnit EntityManagerFactory emFactory

    @Autowired MockMvc mockMvc
    @Autowired UserRepository userRepo
    @Autowired PasswordResetTokenRepository resetTokenRepo

    User user

    def setup() {
        user = userRepo.save(
            new User(
                "user@mail.com",
                "p\$ssw0rd",
                "John",
                "Smith",
                "My Local Church",
                [], [], []
            )
        )
        entityManager = emFactory.createEntityManager()
    }

    def cleanup() {
        resetTokenRepo.deleteAll()
        userRepo.deleteAll()
    }

    def setupSpec() {
        mockWebServer.start(56556)
    }

    def cleanupSpec() {
        mockWebServer.shutdown()
    }

    def "when a user requests to reset their password, then generate a new token and send an email"() {
        given: "We prepare a response for requests to the MockWebServer"
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("{}"))

        when: "We request a password reset for our user"
        def response = mockMvc.perform(
            post("/auth/request-password-reset")
                .queryParam("email", user.email)
        )
            .andDo(print())

        then: "Verify no exceptions thrown"
        noExceptionThrown()

        and: "Verify HTTP 200 response"
        response.andExpect(status().isOk())

        and: "Verify an ACTIVE reset token has been created for our user"
        def tokens = resetTokenRepo.getActiveTokensForUser(user.id)
        tokens.size() == 1

        and: "Verify that the MockWebServer got our request"
        RecordedRequest received = mockWebServer.takeRequest()
        Map<String, Object> body = mapper.readValue(received.getBody().readUtf8(), Map<String, Object>.class)
        Map<String, Object> sentMessage = (body["Messages"] as List)[0] as Map

        sentMessage["From"]["Email"] == "hello@bible.game"
        sentMessage["To"][0]["Email"] == user.email
        sentMessage["Subject"] == "Your password reset request for bible.game"
        (sentMessage["HTMLPart"] as String).contains("reset?token=${tokens[0].token}")

    }

    @Transactional
    def "when a user requests to reset their password, and they already have an active token, then the old token is replaced and a new email is sent"() {
        given: "We create an ACTIVE PasswordResetToken for our user"
        def now = LocalDateTime.now()
        def oldResetToken = resetTokenRepo.save(
            new PasswordResetToken(
                null, user, ResetTokenState.ACTIVE, now, now.plusMinutes(30L)
            )
        )

        and: "We prepare a response for requests to the MockWebServer"
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("{}"))

        when: "We send a request a password reset for our user"
        def response = mockMvc.perform(
            post("/auth/request-password-reset")
                .queryParam("email", user.email)
        )
            .andDo(print())

        then: "Verify no exceptions thrown"
        noExceptionThrown()

        and: "Verify HTTP 200 response"
        response.andExpect(status().isOk())

        and: "Verify that a new ACTIVE reset token has been created for our user, and the previous is now REPLACED"
        def activeTokens = resetTokenRepo.getActiveTokensForUser(user.id)
        activeTokens.size() == 1

        def currentResetToken = activeTokens[0]
        currentResetToken.token != oldResetToken.token

        def oldTokenUpdated = resetTokenRepo.findById(oldResetToken.token).get()
        oldTokenUpdated.state == ResetTokenState.REPLACED

        and: "Verify that the MockWebServer got our request"
        RecordedRequest received = mockWebServer.takeRequest()
        Map<String, Object> body = mapper.readValue(received.getBody().readUtf8(), Map<String, Object>.class)
        Map<String, Object> sentMessage = (body["Messages"] as List)[0] as Map

        sentMessage["From"]["Email"] == "hello@bible.game"
        sentMessage["To"][0]["Email"] == user.email
        sentMessage["Subject"] == "Your password reset request for bible.game"
        (sentMessage["HTMLPart"] as String).contains("reset?token=${currentResetToken.token}")
    }

    def "when a user submits a new password, and they have an ACTIVE reset token, then the new password is applied"() {
        given: "We create an ACTIVE PasswordResetToken for our user"
        def now = LocalDateTime.now()
        def session = entityManager.unwrap(Session.class)
        def tx = session.getTransaction()

        tx.begin()
        // Requires User obj from current Transaction - don't know why
        def txUser = entityManager
            .createQuery("SELECT user FROM User user", User.class)
            .getResultList()
            .getFirst()

        def resetToken = new PasswordResetToken(
            null, txUser, ResetTokenState.ACTIVE, now, now.plusMinutes(30L)
        )
        entityManager.persist(resetToken)
        tx.commit()

        and: "We create a PasswordResetData object for our request"
        def resetData = new PasswordResetData(resetToken.token, "N3wpass!")

        when: "We submit a new password for our user"
        def response = mockMvc.perform(
            put("/auth/update-password")
                .content(mapper.writeValueAsBytes(resetData))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(print())

        then: "Verify no exceptions thrown"
        noExceptionThrown()

        and: "Verify HTTP 200 response"
        response.andExpect(status().isOk())

        and: "Verify that the user's password has been updated"
        def updatedUser = userRepo.findById(user.id).get()
        updatedUser.password == resetData.password

        and: "Verify that the PasswordResetToken is now marked as USED"
        def updatedToken = resetTokenRepo.findById(resetToken.token).get()
        updatedToken.state == ResetTokenState.USED
    }

    def "when a user submits a new password, but they don't have an ACTIVE reset token, then an error is returned"() {
        given: "We create a PasswordResetData object for our request"
        def resetData = new PasswordResetData("thisisafaketoken", "Doesn'treallym4atter")

        when: "We submit a new password for our user"
        def response = mockMvc.perform(
            put("/auth/update-password")
                .content(mapper.writeValueAsBytes(resetData))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(print())

        then: "Verify no exceptions thrown"
        noExceptionThrown()

        and: "Verify HTTP 400 Response with appropriate message"
        response
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("message").value("Token [${resetData.resetToken}] does not exist" as String))

        and: "Verify the user's password is unchanged"
        def session = entityManager.unwrap(Session.class)
        def tx = session.getTransaction()
        tx.begin()

        def latestUser = userRepo.findById(user.id).get()
        latestUser.password == user.password
    }

}
