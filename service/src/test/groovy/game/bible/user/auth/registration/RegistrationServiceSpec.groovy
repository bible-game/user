package game.bible.user.auth.registration

import game.bible.user.User
import game.bible.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for {@link RegistrationService}
 * @since 5th June 2025
 */
class RegistrationServiceSpec extends Specification implements RegistrationTrait {

    def userRepo = Mock(UserRepository)
    def encoder = Stub(PasswordEncoder)

    @Subject
    def service = new RegistrationService(userRepo, encoder)

    def "should create user when unique registration email is provided"() {
        given:
        userRepo.findByEmail(email) >> Optional.empty()

        when:
        service.register(data)

        then:
        1 * userRepo.save(_ as User) >> new User()
        noExceptionThrown()
    }

    def "should reject registration when provided email already exists"() {
        given:
        userRepo.findByEmail(email) >> Optional.of(new User())

        when:
        service.register(data)

        then:
        thrown(Exception)
        0 * userRepo.save(_ as User)
    }

}