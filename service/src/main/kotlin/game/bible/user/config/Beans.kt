package game.bible.user.config

import com.fasterxml.jackson.databind.ObjectMapper
import game.bible.common.util.security.TokenFilter
import game.bible.common.util.security.TokenManager
import game.bible.user.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import game.bible.config.ReloadableConfig
import game.bible.config.model.core.SecurityConfig
import jakarta.servlet.http.HttpServletRequest

/**
 * Bean Configuration
 * @since 5th June 2025
 */
@Configuration
@Import(
    ReloadableConfig::class,
    SecurityConfig::class)
class Beans {

    @Bean
    fun userDetailsService(userRepository: UserRepository): UserDetailsService =
        CustomUserDetailsService(userRepository)

    @Bean
    fun authenticationProvider(userRepository: UserRepository): AuthenticationProvider =
        DaoAuthenticationProvider()
            .also {
                it.setUserDetailsService(userDetailsService(userRepository))
                it.setPasswordEncoder(passwordEncoder())
            }

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
        TokenFilter(manager).excludes(listOf("/auth", "/health"))


}