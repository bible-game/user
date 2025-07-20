package game.bible.user.leader

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes Leaderboard-related Actions
 * @since 29th July 2025
 */
@RestController
@RequestMapping("/leader")
class LeaderController(private val service: LeaderService) {

    /** Returns the top-scoring players */
    @GetMapping("/top")
    fun getTop(@RequestParam(defaultValue = "3") limit: Int): ResponseEntity<Any> {
        return ResponseEntity.ok(service.getLeaders(limit))
    }

    /** Returns the player's rank */
    @GetMapping("/rank")
    fun getRank(@RequestParam userId: Long): ResponseEntity<Any> {
        return ResponseEntity.ok(service.getRank(userId))
    }

}
