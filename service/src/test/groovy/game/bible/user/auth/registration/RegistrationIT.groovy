package game.bible.user.auth.registration

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.user.User
import game.bible.user.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for the Registration Feature
 * @since 5th June 2025
 */
@Ignore
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationIT extends Specification implements RegistrationTrait {

    @Autowired MockMvc mockMvc
    @Autowired ObjectMapper mapper
    @Autowired UserRepository userRepo

    def endpoint = "/auth/register"

    def setup() {
        userRepo.deleteAll()
    }

    def "should register user with correct response when valid registration data used"() {
        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(data)))
                .andExpect(status().isCreated())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        User user = mapper.readValue(response, User.class)
        user.email == email

        and:
        userRepo.findById(user.id).get() == user

        and:
        noExceptionThrown()
    }

    def "should reject registration with correct response when duplicate email is used"() {
        given:
        def existingUser = new User(email, "")
        userRepo.save(existingUser)

        expect: 'datasource is seeded correctly for test'
        userRepo.findByEmail(email).get().email == email

        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response == "User already exists with this email address"
        // FixMe :: messages.get("registration.failure.user_exists")

        and:
        noExceptionThrown()
    }

    @Unroll
    def "should reject registration with correct response when #invalidData is used"() {
        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(rogue)))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response == expected

        and:
        noExceptionThrown()

        where: // FixMe :: access to message bundle (expected)
        invalidData         | rogue                    | expected
        "missing email"     | getData(null, password)  | "Cannot register due to rogue data (email must be present) "
        "missing password"  | getData(email, null)     | "Cannot register due to rogue data (password must be present) "

    }

}