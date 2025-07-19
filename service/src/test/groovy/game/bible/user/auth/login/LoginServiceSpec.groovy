package game.bible.user.auth.login

import game.bible.common.util.security.TokenManager
import game.bible.config.model.core.SecurityConfig
import game.bible.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for {@link LoginService}
 * @since 5th June 2025
 */
class LoginServiceSpec extends Specification implements LoginTrait {

    def authManager = Stub(AuthenticationManager)
    def tokenManager = Mock(TokenManager)
    def userRepo = Mock(UserRepository)
    def config = Stub(SecurityConfig)

    def jwt = 'token'
    def req = Stub(HttpServletRequest)
    def res = Stub(HttpServletResponse)

    @Subject
    def service = new LoginService(userRepo, authManager, tokenManager, config)

    @Ignore
    def "should return jwt when valid credentials are provided"() {
        given:
        1 * userRepo.findByEmail(data.email) >> Optional.of(user)

        when:
        def result = service.login(req, res, data)

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
        service.login(req, res,  data)

        then:
        thrown(Exception)
    }

    @Ignore
    def "should throw exception when incorrect password is used"() {
        given:
        def existing = user
        existing.password = 'obscure-password'

        and:
        userRepo.findByEmail(email) >> Optional.of(existing)

        when:
        service.login(req, res, data)

        then:
        thrown(Exception)
    }

}