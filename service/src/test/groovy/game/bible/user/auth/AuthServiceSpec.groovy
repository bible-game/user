package game.bible.user.auth

import game.bible.user.User
import game.bible.user.UserRepository
import game.bible.user.auth.model.PasswordResetToken
import game.bible.user.auth.repository.PasswordResetTokenRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceUnit
import jakarta.transaction.Transactional
import org.hibernate.Session

import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification

import static game.bible.user.auth.model.PasswordResetToken.ResetTokenState

/**
 * Tests for functions of the AuthService
 *
 * @author Hayden Eastwell (haydende)
 */
@DirtiesContext
@SpringBootTest
class AuthServiceSpec extends Specification {

    EntityManager entityManager

    @PersistenceUnit EntityManagerFactory emFactory
    @Autowired PasswordResetTokenRepository resetTokenRepo
    @Autowired UserRepository userRepo
    @Autowired AuthService authService

    def setup() {
        this.entityManager = emFactory.createEntityManager()
    }

    def 'updatePassword - when the user has an ACTIVE token, then replace their password with the provided one'() {
        given: "We create a Hibernate Transaction"
        def session = entityManager.unwrap(Session.class)
        def tx = session.getTransaction()
        tx.begin()

        and: "Create a User"
        def user = new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        entityManager.persist(user)

        and: "We create an ACTIVE password reset token for this user"
        def now = LocalDateTime.now()
        def resetToken = new PasswordResetToken(null, user, ResetTokenState.ACTIVE, now, now.plusMinutes(30L))
        entityManager.persist(resetToken)
        tx.commit()

        and: "We create PasswordResetData for our AuthService call"
        def resetData = new PasswordResetData(resetToken.token, "N3wpassword!")

        when: "We call the AuthService to apply the new password"
        tx.begin()
        authService.updatePassword(resetData)
        tx.commit()

        then: "Expect no exceptions thrown"
        noExceptionThrown()

        and: "Verify that the new password has been applied"
        tx.begin()
        def updatedUser = userRepo.findById(user.id).get()
        updatedUser.password == "N3wpassword!"

        and: "Verify that the password reset token is now USED"
        def updatedToken = resetTokenRepo.findById(resetToken.token).get()
        updatedToken.state == ResetTokenState.USED
        tx.commit()
    }

    def 'updatePassword - when an invalid token is provided, throw an error'() {
        given: "Create the PasswordResetData"
        def data = new PasswordResetData("aninvalidToken", "doesn'tmatterwhat'shere")

        when: "We call the AuthService to reset the password"
        def result = authService.updatePassword(data)

        then: "Verify the exception is thrown with the right message"
        def thrown = thrown(IllegalStateException.class)
        thrown.message == "Token [${data.resetToken}] does not exist"
    }

    @Transactional
    def 'updatePassword - when an EXPIRED token is provided, throw an error'() {
        given: "We create a User to associate with the Reset Token"
        def user = userRepo.save(
            new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        )

        and: "We create an EXPIRED password reset token for this user"
        def now = LocalDateTime.now()
        def resetToken = resetTokenRepo.save(
            new PasswordResetToken(null, user, ResetTokenState.EXPIRED, now, now.plusMinutes(30L))
        )

        and: "Create the PasswordResetData"
        def data = new PasswordResetData(resetToken.token, "thiswillnotwork")

        when: "We call the AuthService to reset the password"
        def result = authService.updatePassword(data)

        then: "Verify the right exception is thrown with the right message"
        def thrown = thrown(IllegalStateException.class)
        thrown.message == "Token [${data.resetToken}] has expired"
    }

    @Transactional
    def 'createPasswordResetToken - when the user has an active token already, replace it'() {
        given: "We create a User to associate with the Reset Token"
        def user = userRepo.save(
            new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        )

        and: "We create an ACTIVE password reset token for this user"
        def now = LocalDateTime.now()
        def resetToken = resetTokenRepo.save(
            new PasswordResetToken(null, user, ResetTokenState.ACTIVE, now, now.plusMinutes(30L))
        )

        when: "We call the AuthService to create the password reset token"
        def result = authService.createPasswordResetToken(user)

        then: "We verify that a new token has been created"
        def newToken = resetTokenRepo.findById(result).get()
        newToken.state == ResetTokenState.ACTIVE

        and: "We verify that the previous ACTIVE token has been marked as REPLACED"
        def previousToken = resetTokenRepo.findById(resetToken.token).get()
        previousToken.state == ResetTokenState.REPLACED

        and: "We verify that there are no other ACTIVE tokens"
        resetTokenRepo.getActiveTokensForUser(user.id).size() == 1
    }

    @Transactional
    def 'createPasswordResetToken - when the user has EXPIRED, REPLACED and USED tokens, create a new one'() {
        given: "We create a User to associate with the Reset Token"
        def user = userRepo.save(
            new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        )

        and: "We create multiple EXPIRED, REPLACED and USED tokens for this user"
        def now = LocalDateTime.now()
        def tokens = resetTokenRepo.saveAll([
            new PasswordResetToken(null, user, ResetTokenState.EXPIRED, now.minusMinutes(30L), now),
            new PasswordResetToken(null, user, ResetTokenState.REPLACED, now.minusMinutes(60L), now.minusMinutes(30L)),
            new PasswordResetToken(null, user, ResetTokenState.USED, now.minusMinutes(20L), now.plusMinutes(10L))
        ])

        when: "We call the AuthService to create the password reset token"
        def result = authService.createPasswordResetToken(user)

        then: "We verify that a new token has been created"
        def newToken = resetTokenRepo.findById(result).get()
        newToken.state == ResetTokenState.ACTIVE

        and: "We verify that there are no other ACTIVE tokens"
        resetTokenRepo.getActiveTokensForUser(user.id).size() == 1
    }

    @Transactional
    def 'createPasswordResetToken - when the user has no tokens, create a new one'() {
        given: "We create a User to associate with the Reset Token"
        def user = userRepo.save(
            new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        )

        when: "We call the AuthService to create the password reset token"
        def result = authService.createPasswordResetToken(user)

        then: "We verify that a new token has been created"
        def newToken = resetTokenRepo.findById(result).get()
        newToken.state == ResetTokenState.ACTIVE

        and: "We verify that there are no other ACTIVE tokens"
        resetTokenRepo.getActiveTokensForUser(user.id).size() == 1
    }

    @Transactional
    def 'createPasswordResetToken - when the user an ACTIVE token whose expireAt has passed, apply the EXPIRED state and create a new one'() {
        given: "We create a User to associate with the Reset Token"
        def user = userRepo.save(
            new User("user@mail.com", "password", "John", "Smith", "Some Church", [], [], [])
        )

        and: "We create an ACTIVE token with an expireAt value before now"
        def now = LocalDateTime.now()
        def token = resetTokenRepo.save(
            new PasswordResetToken(null, user, ResetTokenState.ACTIVE, now.minusMinutes(31L), now.minusMinutes(1L))
        )

        when: "We call the AuthService to create the password reset token"
        def result = authService.createPasswordResetToken(user)

        then: "We verify that a new token has been created"
        def newToken = resetTokenRepo.findById(result).get()
        newToken.state == ResetTokenState.ACTIVE

        and: "We verify that the previous token is now EXPIRED"
        def oldToken = resetTokenRepo.findById(token.token).get()
        oldToken.state == ResetTokenState.EXPIRED
    }

}
