package game.bible.user.config

import game.bible.user.UserRepository
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.Subject

import java.sql.Timestamp

/**
 * Unit tests for {@link SessionFilter}
 * @since 14th October 2026
 */
class SessionFilterSpec extends Specification {

    def repository = Mock(UserRepository)
    def chain = Mock(FilterChain)
    def req = new MockHttpServletRequest()
    def res = new MockHttpServletResponse()

    @Subject
    def filter = new SessionFilter(repository)

    def setup() {
        SecurityContextHolder.getContext().authentication = new UsernamePasswordAuthenticationToken("1", null, [])
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "should reject a token issued before the password was changed"() {
        given:
        req.setAttribute("claims", [sub: "1", iat: new Date(1_000_000L)])
        repository.findPasswordChangedAt(1L) >> new Timestamp(2_000_000L)

        when:
        filter.doFilter(req, res, chain)

        then:
        0 * chain.doFilter(_, _)
        res.status == 401
        SecurityContextHolder.getContext().authentication == null
    }

    def "should accept a token issued after the password was changed"() {
        given:
        req.setAttribute("claims", [sub: "1", iat: new Date(3_000_000L)])
        repository.findPasswordChangedAt(1L) >> new Timestamp(2_000_000L)

        when:
        filter.doFilter(req, res, chain)

        then:
        1 * chain.doFilter(req, res)
        res.status == 200
    }

    def "should accept any token when the password has never been changed"() {
        given:
        req.setAttribute("claims", [sub: "1", iat: new Date(1_000_000L)])
        repository.findPasswordChangedAt(1L) >> null

        when:
        filter.doFilter(req, res, chain)

        then:
        1 * chain.doFilter(req, res)
    }

    def "should leave unauthenticated requests alone"() {
        given:
        SecurityContextHolder.clearContext()

        when:
        filter.doFilter(req, res, chain)

        then:
        0 * repository.findPasswordChangedAt(_)
        1 * chain.doFilter(req, res)
    }

}
