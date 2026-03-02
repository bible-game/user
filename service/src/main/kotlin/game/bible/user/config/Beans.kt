package game.bible.user.config

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.common.util.security.TokenFilter
import game.bible.common.util.security.TokenManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import game.bible.config.ReloadableConfig
import game.bible.config.model.core.SecurityConfig
import game.bible.config.model.integration.ChatGptConfig
import game.bible.config.model.service.UserConfig
import game.bible.user.notification.EmailClient
import jakarta.servlet.http.HttpServletRequest

/**
 * Bean Configuration
 * @since 5th June 2025
 */
@Configuration
@Import(
    ReloadableConfig::class,
    ChatGptConfig::class,
    SecurityConfig::class,
    UserConfig::class,
    EmailClient::class,
)
class Beans {

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun tokenManager(
        config: SecurityConfig,
        mapper: ObjectMapper,
        request: HttpServletRequest
    ): TokenManager = TokenManager(config, mapper, request)

    @Bean
    fun tokenFilter(manager: TokenManager): TokenFilter =
        TokenFilter(manager).excludes(listOf("/auth", "/health", "/leader"))

}