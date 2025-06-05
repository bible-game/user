package game.bible.user.generation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.OpenAIClient
import com.openai.models.ChatCompletion
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import game.bible.config.model.domain.BibleConfig
import game.bible.config.model.integration.BibleApiConfig
import game.bible.config.model.integration.ChatGptConfig
import game.bible.user.user
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.Date

private val log = KotlinLogging.logger {}

/**
 * user Generation Service Logic
 *
 * @author J. R. Smith
 * @since 13th January 2025
 */
@Service
class GenerationService(
    private val api: BibleApiConfig,
    private val bible: BibleConfig,
    private val chat: ChatGptConfig,
    private val client: OpenAIClient,
    private val mapper: ObjectMapper,
    private val restClient: RestClient
) {

    /** Generates a random bible user */
    fun random(date: Date): user {
        val testament = bible.getTestaments()!!.random()

        val division = testament.getDivisions()!!.random()
        val book = division.getBooks()!!.random()
        val chapter = "${(1..book.getChapters()!!).random()}"
        val verses = book.getVerses()!![chapter.toInt()]

        val text = fetchText("${book.getKey()!!}+$chapter")
        val summary = summarise(text)

        return user(date, book.getName()!!, chapter, "", summary, verses, text)
    }

    private fun fetchText(userId: String): String {
        log.info { "Attempting to fetch text for $userId" }
        val url = "${api.getBaseUrl()}/$userId"

        val response = restClient.get()
                        .uri(url).retrieve()
                        .body(String::class.java)

        val jsonNode: JsonNode = mapper.readTree(response)
        return jsonNode["text"].asText()
    }

     private fun summarise(text: String): String {
         log.info { "Asking ChatGPT for text summary [${text.substring(0, 20)}...]" }

         val createParams = ChatCompletionCreateParams.builder()
             .model(ChatModel.GPT_4O_MINI)
             .maxCompletionTokens(2048)
             .addDeveloperMessage(chat.getPromptDeveloper()!!)
             .addUserMessage(chat.getPromptUser()!! + text)
             .build()

         var summary = ""
         client.chat().completions().create(createParams).choices().stream()
             .flatMap { choice: ChatCompletion.Choice -> choice.message().content().stream() }
             .forEach { x: String? -> summary += x }

         return summary
     }

}