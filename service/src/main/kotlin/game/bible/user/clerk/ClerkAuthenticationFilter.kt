package game.bible.user.clerk

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.filter.OncePerRequestFilter

class ClerkAuthenticationFilter(private val decoder: JwtDecoder, private val accounts: ClerkAccountResolver) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val path = req.requestURI.removePrefix(req.contextPath)
        // Old auth is deliberately unavailable in Clerk mode, including direct public registration.
        if (path == "/auth" || path.startsWith("/auth/")) {
            res.status = 404
            return
        }
        if ((req.method == "GET" && (path == "/health" || path.startsWith("/leader/"))) || req.method == "OPTIONS") {
            chain.doFilter(req, res)
            return
        }
        // The webhook uses its own signature, never a player bearer token.
        if (req.method == "POST" && path == "/webhooks/clerk") {
            chain.doFilter(req, res)
            return
        }
        val header = req.getHeader("Authorization")
        if (header == null) {
            chain.doFilter(req, res)
            return
        }
        val userId = try {
            if (!header.startsWith("Bearer ", ignoreCase = true) || header.substring(7).isBlank()) {
                reject(res, 401, "invalid_token")
                return
            }
            accounts.resolve(decoder.decode(header.substring(7)).subject)
        } catch (e: JwtException) {
            reject(res, 401, "invalid_token")
            return
        } catch (e: ClerkAccountException) {
            reject(res, e.status.value(), e.code)
            return
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.setContext(context)
        try {
            chain.doFilter(req, res)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun reject(res: HttpServletResponse, status: Int, code: String) {
        SecurityContextHolder.clearContext()
        res.status = status
        res.contentType = "application/json"
        res.writer.write("{\"error\":\"$code\"}")
    }
}
