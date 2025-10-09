package game.bible.user.token

import org.springframework.data.repository.CrudRepository

interface RefreshTokenRepository: CrudRepository<RefreshToken, String> {

    fun findAllByUserId(userId: Long): List<RefreshToken>

}