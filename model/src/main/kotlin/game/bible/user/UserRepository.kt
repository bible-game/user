package game.bible.user

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

}