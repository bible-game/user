package game.bible.user.auth.repository

import game.bible.user.auth.model.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository

@Repository
interface PasswordResetTokenRepository: JpaRepository<PasswordResetToken, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    fun deleteForUser(userId: Long): Int

    @Query("""
       SELECT prt
       FROM PasswordResetToken prt
       WHERE prt.state = 'ACTIVE' AND prt.user.id = :userId
    """)
    fun getActiveTokensForUser(userId: Long): List<PasswordResetToken>

}