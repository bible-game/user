package game.bible.user

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * User Data Access
 * @since 5th June 2024
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): Optional<User>

    @Modifying
    @Query("UPDATE User u SET u.password = :password WHERE u.id = :id")
    fun updatePassword(id: Long, password: String)

    @Query("SELECT u.id, u.firstname, u.lastname, (SELECT COALESCE(sum(g.stars), 0) FROM Game g WHERE g.user.id = u.id), (SELECT COALESCE(SUM(r.stars), 0) FROM Review r WHERE r.user.id = u.id) FROM User u ORDER BY u.id DESC")
    fun getLeaders(pageable: Pageable): List<List<Any>>

    @Query("SELECT u.id, (SELECT COALESCE(sum(g.stars), 0) FROM Game g WHERE g.user.id = u.id), (SELECT COALESCE(SUM(r.stars), 0) FROM Review r WHERE r.user.id = u.id) FROM User u")
    fun getAllPlayers(): List<List<Any>>

}