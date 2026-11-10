package game.bible.user.clerk

import game.bible.user.User
import game.bible.user.UserRepository
import game.bible.user.state.game.Game
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@ContextConfiguration(classes = [ClerkAccountStoreTest.Config::class])
@Import(ClerkAccountStore::class)
class ClerkAccountStoreTest {
    @Configuration
    @EntityScan("game.bible.user")
    @EnableJpaRepositories("game.bible.user")
    class Config

    @Autowired lateinit var repository: UserRepository
    @Autowired lateinit var store: ClerkAccountStore

    @Test
    fun `repeated provisioning creates one account`() {
        val identity = ClerkIdentity("user_new", "new@example.com", "New", "Player", null)
        val id = store.provision(identity)
        assertEquals(id, store.provision(identity))
        assertEquals(1, repository.count())
        assertNotNull(repository.findById(id).get().clerkEmailVerifiedAt)
    }

    @Test
    fun `migration retains numeric ID password church and history`() {
        val original = User("old@example.com", "bcrypt_hash", "Old", "Player", "A church")
        original.games.add(Game(12L, "John", "1", false, 4, mutableListOf(), original))
        repository.saveAndFlush(original)
        val id = store.provision(ClerkIdentity("user_migrated", "verified@example.com", "New", "Name", original.id))
        val migrated = repository.findById(id).get()
        assertEquals(original.id, id)
        assertEquals("bcrypt_hash", migrated.password)
        assertEquals("A church", migrated.church)
        assertEquals(12L, migrated.games.single().passageId)
        assertEquals(4, migrated.games.single().stars)
        assertEquals("user_migrated", migrated.clerkUserId)
    }

    @Test
    fun `matching email alone cannot claim a legacy account`() {
        val original = repository.saveAndFlush(User("player@example.com", "hash", "Existing", "Player"))
        val error = assertThrows(ClerkAccountException::class.java) {
            store.provision(ClerkIdentity("user_new", "PLAYER@example.com", "New", "Player", null))
        }
        assertEquals("account_migration_required", error.code)
        assertNull(original.clerkUserId)
        assertEquals(1, repository.count())
    }

    @Test
    fun `migration cannot replace a different Clerk identity`() {
        val original = User("player@example.com").also { it.clerkUserId = "user_existing" }
        repository.saveAndFlush(original)
        assertThrows(ClerkAccountException::class.java) {
            store.provision(ClerkIdentity("user_attacker", "other@example.com", "", "", original.id))
        }
        assertEquals("user_existing", original.clerkUserId)
    }
}
