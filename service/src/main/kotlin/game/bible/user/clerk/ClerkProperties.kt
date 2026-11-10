package game.bible.user.clerk

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("clerk")
class ClerkProperties {
    var enabled: Boolean = false
    var issuer: String = ""
    var secretKey: String = ""
    var webhookSigningSecret: String = ""
    var instanceId: String = ""
    var authorizedParties: List<String> = emptyList()

    fun validate() {
        val uri = URI(issuer)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "CLERK_ISSUER must be the HTTPS Frontend API origin of the intended Clerk instance"
        }
        require(secretKey.isNotBlank()) { "CLERK_SECRET_KEY is required in Clerk mode" }
        require(authorizedParties.isNotEmpty() && authorizedParties.all { it.isNotBlank() && it != "*" }) {
            "CLERK_AUTHORIZED_PARTIES must list the permitted webapp origins"
        }
    }
}
