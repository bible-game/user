package game.bible.user.auth.repository

import game.bible.user.auth.model.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PasswordResetTokenRepository: JpaRepository<PasswordResetToken, String> {

    @Query("""
       SELECT PasswordResetToken prt
       FROM PasswordResetToken 
       WHERE prt.state = 'ACTIVE' AND prt.user.id = :userId
    """)
    fun getActiveTokensForUser(userId: Long): List<PasswordResetToken>

}