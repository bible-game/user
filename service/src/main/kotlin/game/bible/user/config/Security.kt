package game.bible.user.config

import game.bible.common.util.security.TokenFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Service-level Security Settings
 */
@Configuration @EnableWebSecurity
class Security(
    private val tokenFilter: TokenFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): DefaultSecurityFilterChain {
        http
            .csrf { it.disable() }
            .addFilterAfter(tokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/leader/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/auth/validate-token").permitAll()
                    // FixMe: Security should be tighter on this - Can we use the PasswordResetToken to authenticate?
                    .requestMatchers(HttpMethod.PUT, "/auth/update-password").permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()
    }

}