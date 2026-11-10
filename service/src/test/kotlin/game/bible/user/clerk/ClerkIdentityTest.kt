package game.bible.user.clerk

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ClerkIdentityTest {
    private val mapper = ObjectMapper()
    private fun data(status: String = "verified", externalId: String = "42") = mapper.readTree("""
        {"id":"user_abc", "primary_email_address_id":"id_primary", "external_id":"$externalId",
         "email_addresses":[
           {"id":"id_other", "email_address":"wrong@example.com", "verification":{"status":"verified"}},
           {"id":"id_primary", "email_address":"player@example.com", "verification":{"status":"$status"}}],
         "first_name":null,"last_name":"Smith"}
    """)

    @Test
    fun `uses the verified primary address and trusted migration ID`() {
        val identity = ClerkIdentity.from(data(), "user_abc")
        assertEquals("player@example.com", identity.email)
        assertEquals(42L, identity.externalId)
        assertEquals("", identity.firstname)
    }

    @Test
    fun `unverified primary is rejected even when another address is verified`() {
        val error = assertThrows(ClerkAccountException::class.java) { ClerkIdentity.from(data("unverified"), "user_abc") }
        assertEquals(HttpStatus.FORBIDDEN, error.status)
        assertEquals("email_verification_required", error.code)
    }

    @Test
    fun `rejects mismatched identities and invalid migration IDs`() {
        assertThrows(ClerkAccountException::class.java) { ClerkIdentity.from(data(), "user_someoneelse") }
        assertThrows(ClerkAccountException::class.java) { ClerkIdentity.from(data(externalId = "nonsense"), "user_abc") }
    }
}
