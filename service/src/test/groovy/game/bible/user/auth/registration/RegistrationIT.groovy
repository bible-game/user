package game.bible.user.auth.registration

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.user.User
import game.bible.common.util.security.TokenManager
import game.bible.user.auth.repository.PasswordResetTokenRepository
import org.springframework.security.crypto.password.PasswordEncoder
import game.bible.user.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for the Registration Feature
 * @since 5th June 2025
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationIT extends Specification implements RegistrationTrait {

    @Autowired MockMvc mockMvc
    @Autowired ObjectMapper mapper
    @Autowired UserRepository userRepo
    @Autowired PasswordResetTokenRepository resetTokenRepo
    @Autowired TokenManager tokens
    @Autowired PasswordEncoder encoder

    def endpoint = "/auth/register"

    def setup() {
        resetTokenRepo.deleteAll()
        userRepo.deleteAll()
    }

    def cleanup() {
        resetTokenRepo.deleteAll()
        userRepo.deleteAll()
    }

    def "should register user with correct response when valid registration data used"() {
        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(getData(email, "  password  "))))
                .andExpect(status().isCreated())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        def user = userRepo.findByEmail(email).get()
        tokens.getClaimsFrom(response)["sub"] == user.id.toString()
        encoder.matches("  password  ", user.password)

        and:
        userRepo.count() == 1

        and:
        noExceptionThrown()
    }

    def "should reject registration with correct response when duplicate email is used"() {
        given:
        def existingUser = new User(email, "", "", "", "", [], [], [])
        userRepo.save(existingUser)

        expect: 'datasource is seeded correctly for test'
        userRepo.findByEmail(email).get().email == email

        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(data)))
                .andExpect(status().isConflict())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        mapper.readTree(response).get("message").asText() == "This email is already registered."

        and:
        noExceptionThrown()
    }

    @Unroll
    def "should reject registration with correct response when #invalidData is used"() {
        when:
        def response = mockMvc.perform(post(endpoint)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(mapper.convertValue(data, Map) + [(field): value])))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        mapper.readTree(response).get("message").asText() == "Please check the submitted details."

        and:
        noExceptionThrown()

        where:
        invalidData        | field      | value
        "missing email"    | "email"    | null
        "missing password" | "password" | null
        "short password"   | "password" | "short"
        "oversized password" | "password" | "😀" * 19

    }

}