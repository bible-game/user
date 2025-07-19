
package game.bible.user.state

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.user.UserRepository
import game.bible.user.state.game.GameRepository
import game.bible.user.state.read.ReadRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.TestPropertySource
import game.bible.user.auth.login.LoginData
import spock.lang.Ignore
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for the State Feature
 * @since 27th June 2025
 */
@Ignore
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("classpath:config-test.yml")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StateIT extends Specification implements StateTrait {

    @Autowired MockMvc mockMvc
    @Autowired ObjectMapper mapper
    @Autowired UserRepository userRepo
    @Autowired GameRepository gameRepo
    @Autowired ReadRepository readRepo

    def setup() {
        userRepo.deleteAll()
        gameRepo.deleteAll()
        readRepo.deleteAll()
        userRepo.save(user)
    }

    def "should get game state"() {
        given:
        gameRepo.save(game)
        def token = loginAndGetToken()

        when:
        def response = mockMvc.perform(get("/state/game")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response.contains("playing")
        noExceptionThrown()
    }

    def "should get read state"() {
        given:
        readRepo.save(read)
        def token = loginAndGetToken()

        when:
        def response = mockMvc.perform(get("/state/read")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response.contains("passage-key")
        noExceptionThrown()
    }

    def "should register guess"() {
        given:
        def token = loginAndGetToken()

        when:
        def response = mockMvc.perform(post("/state/guess/$passageId")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(guessData)))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response.contains("book")
        noExceptionThrown()
    }

    def "should register read"() {
        given:
        def token = loginAndGetToken()

        when:
        def response = mockMvc.perform(post("/state/read")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(readData)))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString()

        then:
        response.contains("passage-key")
        noExceptionThrown()
    }

    private String loginAndGetToken() {
        def loginData = new LoginData(user.email, user.password)
        def response = mockMvc.perform(post("/auth/login")
                .contentType("application/json;charset=UTF-8")
                .content(mapper.writeValueAsString(loginData)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()

        return response
    }

}
