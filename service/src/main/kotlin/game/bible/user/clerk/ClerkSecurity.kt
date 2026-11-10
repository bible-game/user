package game.bible.user.clerk

import game.bible.user.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@ConditionalOnProperty(prefix = "clerk", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(ClerkProperties::class)
class ClerkSecurity {
    @Bean
    fun clerkBackendClient(properties: ClerkProperties): ClerkBackendClient {
        properties.validate()
        return ClerkBackendClient(properties)
    }

    @Bean
    fun clerkJwtDecoder(properties: ClerkProperties): JwtDecoder {
        properties.validate()
        return ClerkJwtDecoder.create(properties)
    }

    @Bean
    fun clerkAccounts(repository: UserRepository, client: ClerkBackendClient, store: ClerkAccountStore) =
        ClerkAccountResolver(repository, client, store)

    @Bean
    fun clerkSecurityFilterChain(http: HttpSecurity, decoder: JwtDecoder, accounts: ClerkAccountResolver): DefaultSecurityFilterChain {
        http.csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(ClerkAuthenticationFilter(decoder, accounts), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint { _, response, _ -> response.sendError(401) } }
            .authorizeHttpRequests {
                it.requestMatchers("/auth", "/auth/**").denyAll()
                    .requestMatchers(HttpMethod.POST, "/webhooks/clerk").permitAll()
                    .requestMatchers(HttpMethod.GET, "/health", "/leader/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().authenticated()
            }
        return http.build()
    }
}
