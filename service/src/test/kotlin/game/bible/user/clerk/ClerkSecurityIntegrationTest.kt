package game.bible.user.clerk

import game.bible.user.UserRepository
import game.bible.user.User
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.transaction.annotation.Transactional
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Loads the real application: the legacy servlet filter must not run in Clerk mode. */
@SpringBootTest(properties = [
    "clerk.enabled=true",
    "clerk.issuer=https://test.clerk.accounts.dev",
    "clerk.secret-key=sk_test_not_a_real_key",
    "clerk.authorized-parties=http://localhost:3000",
    "clerk.instance-id=ins_test",
    "clerk.webhook-signing-secret=whsec_AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
])
@AutoConfigureMockMvc
@Transactional
class ClerkSecurityIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var repository: UserRepository
    @Autowired lateinit var games: game.bible.user.state.game.GameRepository

    @MockBean lateinit var decoder: JwtDecoder
    @MockBean lateinit var clerkClient: ClerkBackendClient

    @BeforeEach
    fun setup() {
        `when`(decoder.decode("malformed")).thenThrow(BadJwtException("invalid"))
    }

    @Test
    fun `verified sign-in provisions once and can merge guest progress through the existing API`() {
        val jwt = Jwt.withTokenValue("valid").header("alg", "RS256").subject("user_new").build()
        `when`(decoder.decode("valid")).thenReturn(jwt)
        `when`(clerkClient.retrieve("user_new")).thenReturn(ClerkIdentity("user_new", "new@example.com", "New", "Player", null))
        mvc.perform(get("/info").header("Authorization", "Bearer valid"))
            .andExpect(status().isOk).andExpect(jsonPath("$.firstname").value("New"))
            .andExpect(jsonPath("$.id").isNumber)
        mvc.perform(post("/state/sync").header("Authorization", "Bearer valid")
            .contentType("application/json").content("""
              {"games":[{"passageId":123,"playing":false,"stars":999,"guesses":[
                {"book":"John","chapter":"1","distance":0,"percentage":100,"passageBook":"John","passageChapter":"1"}]}]}
            """))
            .andExpect(status().isOk).andExpect(jsonPath("$.games").value(1))
        mvc.perform(get("/state/game").header("Authorization", "Bearer valid"))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].stars").value(5))
        verify(clerkClient, times(1)).retrieve("user_new")
    }

    @Test
    fun `an unverified imported account cannot read its existing history`() {
        repository.saveAndFlush(User("old@example.com").also { it.clerkUserId = "user_imported" })
        `when`(decoder.decode("imported")).thenReturn(Jwt.withTokenValue("imported").header("alg", "RS256").subject("user_imported").build())
        `when`(clerkClient.retrieve("user_imported")).thenThrow(ClerkAccountException(org.springframework.http.HttpStatus.FORBIDDEN, "email_verification_required"))
        mvc.perform(get("/state/game").header("Authorization", "Bearer imported"))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.error").value("email_verification_required"))
    }

    @Test
    fun `only Clerk chain is active and private endpoints require a bearer token`() {
        assertFalse(context.containsBean("tokenFilter"))
        assertFalse(context.containsBean("securityFilterChain"))
        mvc.perform(get("/info")).andExpect(status().isUnauthorized)
        mvc.perform(get("/state/game")).andExpect(status().isUnauthorized)
        mvc.perform(get("/info").header("Authorization", "Bearer malformed")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `legacy authentication routes cannot create accounts or reset passwords`() {
        mvc.perform(post("/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound)
        mvc.perform(post("/auth/login").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound)
        mvc.perform(get("/auth/validate-token").param("token", "legacy"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `profile completion retains existing names and ignores old church fields`() {
        `when`(decoder.decode("profile")).thenReturn(Jwt.withTokenValue("profile").header("alg", "RS256").subject("user_profile").build())
        `when`(clerkClient.retrieve("user_profile")).thenReturn(ClerkIdentity("user_profile", "profile@example.com", "First", "Last", null))
        mvc.perform(put("/info/profile").header("Authorization", "Bearer profile")
            .contentType("application/json").content("""{"skip":true}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.church").doesNotExist())
        mvc.perform(put("/info/profile").header("Authorization", "Bearer profile")
            .contentType("application/json").content("""{"church":"Changed","firstname":"Other"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.church").doesNotExist())
            .andExpect(jsonPath("$.firstname").value("First"))
    }

    @Test
    fun `profile save fills missing names without collecting church`() {
        `when`(decoder.decode("profile")).thenReturn(Jwt.withTokenValue("profile").header("alg", "RS256").subject("user_profile").build())
        `when`(clerkClient.retrieve("user_profile")).thenReturn(ClerkIdentity("user_profile", "profile@example.com", "", "", null))
        mvc.perform(put("/info/profile").header("Authorization", "Bearer profile")
            .contentType("application/json").content("""{"firstname":" First ","lastname":" Last ","church":" A church "}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.firstname").value("First"))
            .andExpect(jsonPath("$.lastname").value("Last"))
            .andExpect(jsonPath("$.church").doesNotExist())
        mvc.perform(get("/info").header("Authorization", "Bearer profile"))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.church").doesNotExist())
        org.junit.jupiter.api.Assertions.assertEquals("", repository.findByClerkUserId("user_profile").orElseThrow().church)
    }

    @Test
    fun `profile completion rejects missing and whitespace names including skip requests`() {
        `when`(decoder.decode("names")).thenReturn(Jwt.withTokenValue("names").header("alg", "RS256").subject("user_names").build())
        `when`(clerkClient.retrieve("user_names")).thenReturn(ClerkIdentity("user_names", "names@example.com", "", "", null))
        for (body in listOf("""{"skip":true}""", """{"firstname":"   ","lastname":"Smith"}""", """{"firstname":"Alex"}""")) {
            mvc.perform(put("/info/profile").header("Authorization", "Bearer names")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/info").header("Authorization", "Bearer names"))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(false))
    }

    @Test
    fun `an older skipped profile with blank names needs completion again`() {
        val account = User("repair@example.com").also {
            it.church = "Existing church"
            it.clerkUserId = "user_repair"
            it.clerkEmailVerifiedAt = java.sql.Timestamp(System.currentTimeMillis())
            it.profileCompletedAt = java.sql.Timestamp(System.currentTimeMillis())
        }
        repository.saveAndFlush(account)
        `when`(decoder.decode("repair")).thenReturn(Jwt.withTokenValue("repair").header("alg", "RS256").subject("user_repair").build())
        mvc.perform(get("/info").header("Authorization", "Bearer repair"))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(false))
        mvc.perform(put("/info/profile").header("Authorization", "Bearer repair")
            .contentType("application/json").content("""{"firstname":"Élodie","lastname":"O’Connor-李","church":"Replacement church","skip":true}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.firstname").value("Élodie"))
            .andExpect(jsonPath("$.lastname").value("O’Connor-李"))
            .andExpect(jsonPath("$.church").doesNotExist())
        org.junit.jupiter.api.Assertions.assertEquals("Existing church", repository.findById(account.id!!).orElseThrow().church)
    }

    @Test
    fun `profile completion requires authentication`() {
        mvc.perform(put("/info/profile").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `concurrent snapshots cannot insert the same game twice`() {
        val account = repository.saveAndFlush(User("parallel@example.com").also {
            it.clerkUserId = "user_parallel"
            it.clerkEmailVerifiedAt = java.sql.Timestamp(System.currentTimeMillis())
        })
        `when`(decoder.decode("parallel")).thenReturn(Jwt.withTokenValue("parallel").header("alg", "RS256").subject("user_parallel").build())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready = java.util.concurrent.CountDownLatch(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val futures = (1..2).map {
                pool.submit<Int> {
                    ready.countDown()
                    start.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    val response = mvc.perform(post("/state/sync").header("Authorization", "Bearer parallel")
                        .contentType("application/json").content("""
                        {"games":[{"passageId":321,"playing":false,"stars":5,"guesses":[
                          {"book":"John","chapter":"1","distance":0,"percentage":100,"passageBook":"John","passageChapter":"1"}]}]}
                        """))
                        .andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)
                    com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("games").asInt()
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            start.countDown()
            org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 1), futures.map { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }.sorted())
            org.junit.jupiter.api.Assertions.assertEquals(1, games.findAllByUserId(account.id!!).size)
        } finally {
            start.countDown()
            pool.shutdownNow()
            repository.deleteById(account.id!!)
        }
    }

    @Test
    fun `guests can still access the leaderboard`() {
        mvc.perform(get("/leader/top")).andExpect(status().isOk)
    }

    @Autowired lateinit var webhookStates: ClerkWebhookStateRepository
    @Autowired lateinit var accountStore: ClerkAccountStore
    @Autowired lateinit var resets: game.bible.user.auth.repository.PasswordResetTokenRepository
    @Autowired lateinit var entityManager: jakarta.persistence.EntityManager

    private val signingKey = ByteArray(32) { it.toByte() }
    private fun event(type: String, id: String, version: Long, first: String = "Updated", last: String = "Player", verified: Boolean = true): String = """
        {"object":"event","instance_id":"ins_test","type":"$type","timestamp":$version,"data":{
          "id":"$id","updated_at":$version,"first_name":"$first","last_name":"$last",
          "primary_email_address_id":"email_primary","email_addresses":[{"id":"email_primary",
          "email_address":"updated@example.com","verification":{"status":"${if (verified) "verified" else "unverified"}"}}]}}
    """.trimIndent()

    private fun signed(body: String, timestamp: String = java.time.Instant.now().epochSecond.toString()): org.springframework.test.web.servlet.ResultActions {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(signingKey, "HmacSHA256"))
        val signature = java.util.Base64.getEncoder().encodeToString(mac.doFinal("msg_test.$timestamp.$body".toByteArray()))
        // An unrelated malformed bearer must not cause the player filter to intercept signature auth.
        return mvc.perform(post("/webhooks/clerk").header("Authorization", "Bearer malformed")
            .header("svix-id", "msg_test").header("svix-timestamp", timestamp).header("svix-signature", "v1,$signature")
            .contentType("application/json").content(body))
    }

    private fun linked(id: String): User = repository.saveAndFlush(User("original@example.com", "hash", "Original", "Name", "Legacy church").also {
        it.clerkUserId = id
        it.clerkEmailVerifiedAt = java.sql.Timestamp(System.currentTimeMillis())
        it.profileCompletedAt = java.sql.Timestamp(System.currentTimeMillis())
    })

    @Test
    fun `signed profile events update linked identity without changing ID history or church`() {
        val user = linked("user_webprofile")
        val body = event("user.updated", user.clerkUserId!!, 2000, "Élodie", "O’Connor-李")
        signed(body).andExpect(status().isNoContent)
        signed(body).andExpect(status().isNoContent)
        signed(event("user.updated", user.clerkUserId!!, 1000, "Stale")).andExpect(status().isNoContent)
        entityManager.clear()
        val updated = repository.findById(user.id!!).orElseThrow()
        org.junit.jupiter.api.Assertions.assertEquals("Élodie", updated.firstname)
        org.junit.jupiter.api.Assertions.assertEquals("O’Connor-李", updated.lastname)
        org.junit.jupiter.api.Assertions.assertEquals("updated@example.com", updated.email)
        org.junit.jupiter.api.Assertions.assertEquals("Legacy church", updated.church)
        org.junit.jupiter.api.Assertions.assertNotNull(updated.profileCompletedAt)
        `when`(decoder.decode("updated-profile")).thenReturn(Jwt.withTokenValue("updated-profile").header("alg", "RS256").subject(user.clerkUserId!!).build())
        mvc.perform(get("/info").header("Authorization", "Bearer updated-profile"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(user.id!!))
            .andExpect(jsonPath("$.firstname").value("Élodie"))
            .andExpect(jsonPath("$.lastname").value("O’Connor-李"))
        val leaders = mvc.perform(get("/leader/top").param("limit", "100"))
            .andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)
        val leader = com.fasterxml.jackson.databind.ObjectMapper().readTree(leaders).first { it.path("id").asLong() == user.id!! }
        org.junit.jupiter.api.Assertions.assertEquals("Élodie", leader.path("firstname").asText())
        // An older in-flight synchronous profile read cannot undo an event that already committed.
        accountStore.provision(ClerkIdentity(user.clerkUserId!!, "original@example.com", "Old", "Name", null, 1000))
        org.junit.jupiter.api.Assertions.assertEquals("Élodie", repository.findById(user.id!!).orElseThrow().firstname)
    }

    @Test
    fun `unverified email and removed names clear verification and completion without accepting unverified email`() {
        val user = linked("user_webunverified")
        signed(event("user.updated", user.clerkUserId!!, 2000, "", verified = false)).andExpect(status().isNoContent)
        entityManager.clear()
        val updated = repository.findById(user.id!!).orElseThrow()
        org.junit.jupiter.api.Assertions.assertEquals("original@example.com", updated.email)
        org.junit.jupiter.api.Assertions.assertNull(updated.clerkEmailVerifiedAt)
        org.junit.jupiter.api.Assertions.assertNull(updated.profileCompletedAt)
        org.junit.jupiter.api.Assertions.assertThrows(ClerkAccountException::class.java) {
            accountStore.provision(ClerkIdentity(user.clerkUserId!!, "original@example.com", "Old", "Name", null, 1000))
        }
    }

    @Test
    fun `deletion removes account progress and reset tokens and permanently blocks replay or provisioning`() {
        val user = linked("user_webdelete")
        user.games.add(game.bible.user.state.game.Game(654L, "John", "1", false, 5,
            mutableListOf(game.bible.user.state.game.Guess(0, 100, "John", "1")), user))
        user.reads.add(game.bible.user.state.read.Read("John1", "John", "1", "1", "2", user))
        user.reviews.add(game.bible.user.state.review.Review("John1", "2026-11-10", 5, "summary", listOf("answer"),
            game.bible.user.state.review.GradingResult(5, "grade"), user))
        repository.saveAndFlush(user)
        resets.saveAndFlush(game.bible.user.auth.model.PasswordResetToken(user = user, expiresAt = java.time.LocalDateTime.now().plusHours(1)))
        val body = event("user.deleted", user.clerkUserId!!, 3000)
        signed(body).andExpect(status().isNoContent)
        signed(body).andExpect(status().isNoContent)
        signed(event("user.updated", user.clerkUserId!!, 4000)).andExpect(status().isNoContent)
        entityManager.clear()
        org.junit.jupiter.api.Assertions.assertFalse(repository.existsById(user.id!!))
        org.junit.jupiter.api.Assertions.assertTrue(webhookStates.findById(user.clerkUserId!!).orElseThrow().deleted)
        for (table in listOf("game", "guess", "game_guesses", "read", "review", "answer", "grading_result", "password_reset_token")) {
            org.junit.jupiter.api.Assertions.assertEquals(0L, (entityManager.createNativeQuery("SELECT count(*) FROM $table").singleResult as Number).toLong(), table)
        }
        val leaders = mvc.perform(get("/leader/top").param("limit", "100"))
            .andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)
        org.junit.jupiter.api.Assertions.assertFalse(com.fasterxml.jackson.databind.ObjectMapper().readTree(leaders).any { it.path("id").asLong() == user.id!! })
        // Even an old still-signed session plus a stale Backend API result cannot recreate deleted data.
        `when`(decoder.decode("deleted-profile")).thenReturn(Jwt.withTokenValue("deleted-profile").header("alg", "RS256").subject(user.clerkUserId!!).build())
        `when`(clerkClient.retrieve(user.clerkUserId!!)).thenReturn(ClerkIdentity(user.clerkUserId!!, "resurrect@example.com", "No", "Return", null))
        mvc.perform(get("/info").header("Authorization", "Bearer deleted-profile"))
            .andExpect(status().isUnauthorized)
        org.junit.jupiter.api.Assertions.assertFalse(repository.existsById(user.id!!))
    }

    @Test
    fun `unknown updates never create or link accounts and unknown deletion blocks future provisioning`() {
        val user = repository.saveAndFlush(User("updated@example.com", "hash"))
        signed(event("user.updated", "user_unknown", 2000)).andExpect(status().isNoContent)
        org.junit.jupiter.api.Assertions.assertNull(repository.findById(user.id!!).orElseThrow().clerkUserId)
        org.junit.jupiter.api.Assertions.assertFalse(repository.findByClerkUserId("user_unknown").isPresent)
        signed(event("user.deleted", "user_unknown", 3000)).andExpect(status().isNoContent)
        org.junit.jupiter.api.Assertions.assertTrue(webhookStates.findById("user_unknown").orElseThrow().deleted)
    }

    @Test
    fun `bad signatures stale attempts wrong instances and malformed payloads cannot change data`() {
        val user = linked("user_webinvalid")
        val body = event("user.deleted", user.clerkUserId!!, 3000)
        mvc.perform(post("/webhooks/clerk").contentType("application/json").content(body)).andExpect(status().isBadRequest)
        mvc.perform(post("/webhooks/clerk").header("svix-id", "msg_test")
            .header("svix-timestamp", java.time.Instant.now().epochSecond.toString()).header("svix-signature", "v1,invalid")
            .contentType("application/json").content(body)).andExpect(status().isBadRequest)
        signed(body, java.time.Instant.now().minusSeconds(301).epochSecond.toString()).andExpect(status().isBadRequest)
        signed(body.replace("ins_test", "ins_other")).andExpect(status().isBadRequest)
        signed("{not JSON").andExpect(status().isBadRequest)
        signed(body.replace("\"timestamp\":3000", "\"timestamp\":null")).andExpect(status().isBadRequest)
        signed(event("session.created", "user_ignored", 1000)).andExpect(status().isNoContent)
        org.junit.jupiter.api.Assertions.assertTrue(repository.existsById(user.id!!))
        org.junit.jupiter.api.Assertions.assertFalse(webhookStates.findById(user.clerkUserId!!).isPresent)
    }


    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `simultaneous update and deletion serialize across deliveries and deletion wins`() {
        val user = linked("user_webparallel")
        webhookStates.saveAndFlush(ClerkWebhookState(user.clerkUserId!!))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val deliveries = listOf(event("user.updated", user.clerkUserId!!, 5000), event("user.deleted", user.clerkUserId!!, 3000))
            val futures = deliveries.map { body -> pool.submit<Int> {
                start.await(5, java.util.concurrent.TimeUnit.SECONDS)
                signed(body).andReturn().response.status
            } }
            start.countDown()
            org.junit.jupiter.api.Assertions.assertEquals(listOf(204, 204), futures.map { it.get(10, java.util.concurrent.TimeUnit.SECONDS) })
            org.junit.jupiter.api.Assertions.assertFalse(repository.existsById(user.id!!))
            org.junit.jupiter.api.Assertions.assertTrue(webhookStates.findById(user.clerkUserId!!).orElseThrow().deleted)
        } finally {
            start.countDown()
            pool.shutdownNow()
            if (repository.existsById(user.id!!)) repository.deleteById(user.id!!)
            webhookStates.deleteById(user.clerkUserId!!)
        }
    }

}
