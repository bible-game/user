package game.bible.user.auth.login

import game.bible.user.UserRepository
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for {@link LoginService}
 * @since 5th June 2025
 */
class LoginServiceSpec extends Specification implements LoginTrait {

    def tokenManager = Mock(TokenManager)
    def userRepo = Mock(UserRepository)

    def jwt = 'token'

    @Subject
    def service = new LoginService(userRepo, tokenManager)

    def "should return jwt when valid credentials are provided"() {
        given:
        1 * userRepo.findByEmail(data.email) >> Optional.of(user)

        when:
        def result = service.login(data)

        then:
        result == jwt

        and:
        1 * tokenManager.generateFor(user.id) >> jwt
        noExceptionThrown()

    }

    def "should throw exception when unknown email is used"() {
        given:
        userRepo.findByEmail(email) >> Optional.empty()

        when:
        service.login(data)

        then:
        thrown(CardinalException)
    }

    def "should throw exception when incorrect password is used"() {
        given:
        def existing = user
        existing.password = 'obscure-password'

        and:
        userRepo.findByEmail(email) >> Optional.of(existing)

        when:
        service.login(data)

        then:
        thrown(Exception)
    }

}