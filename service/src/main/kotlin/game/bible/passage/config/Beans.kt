package game.bible.user.config

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import game.bible.config.ReloadableConfig
import game.bible.config.model.integration.BibleApiConfig
import game.bible.config.model.integration.ChatGptConfig
import game.bible.config.model.service.userConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

/**
 * Bean Configuration
 *
 * @author J. R. Smith
 * @since 13th January 2025
 */
@Configuration
@Import(
    ReloadableConfig::class,
    userConfig::class,
    BibleApiConfig::class,
    ChatGptConfig::class)
class Beans {

    @Bean
    fun restClient(): RestClient {
        return RestClient.create()
    }

    @Bean fun openAIClient(chat: ChatGptConfig): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .apiKey(chat.getApiKey()!!)
            .build()
    }

}