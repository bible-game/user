
package game.bible.user.state

import game.bible.user.UserRepository
import game.bible.user.state.game.Game
import game.bible.user.state.game.GameRepository
import game.bible.user.state.read.Read
import game.bible.user.state.read.ReadRepository
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for {@link StateService}
 * @since 27th June 2025
 */
class StateServiceSpec extends Specification implements StateTrait {

    def gameRepo = Mock(GameRepository)
    def readRepo = Mock(ReadRepository)
    def userRepo = Mock(UserRepository)

    @Subject
    def service = new StateService(gameRepo, readRepo, userRepo, reviewRepository)

    def "should retrieve game state"() {
        given:
        1 * gameRepo.findAllByUserId(userId) >> [game]

        when:
        def result = service.retrieveGameState(userId)

        then:
        result == [game]
        noExceptionThrown()
    }

    def "should throw exception when no game state"() {
        given:
        1 * gameRepo.findAllByUserId(userId) >> []

        when:
        service.retrieveGameState(userId)

        then:
        thrown(Exception)
    }

    def "should retrieve read state"() {
        given:
        1 * readRepo.findAllByUserId(userId) >> [read]

        when:
        def result = service.retrieveReadState(userId)

        then:
        result == [read]
        noExceptionThrown()
    }

    def "should throw exception when no read state"() {
        given:
        1 * readRepo.findAllByUserId(userId) >> []

        when:
        service.retrieveReadState(userId)

        then:
        thrown(Exception)
    }

    def "should create read"() {
        given:
        1 * userRepo.findById(userId) >> Optional.of(user)
        1 * readRepo.save(_ as Read) >> read
        1 * readRepo.findAllByUserId(userId) >> [read]

        when:
        def result = service.createRead(userId, readData)

        then:
        result == [read]
        noExceptionThrown()
    }

    def "should create guess for new game"() {
        given:
        1 * gameRepo.findByPassageIdAndUserId(passageId, userId) >> Optional.empty()
        1 * userRepo.findById(userId) >> Optional.of(user)
        1 * gameRepo.save(_) >> game

        when:
        def result = service.createGuess(userId, passageId, guessData)

        then:
        result == [guess]
        noExceptionThrown()
    }

    def "should create guess for existing game"() {
        given:
        1 * gameRepo.findByPassageIdAndUserId(passageId, userId) >> Optional.of(game)
        1 * gameRepo.save(_) >> game

        when:
        def result = service.createGuess(userId, passageId, guessData)

        then:
        result.size() == 2
        noExceptionThrown()
    }

    def "should create winning guess"() {
        given:
        def winningData = new GuessData('book', 'chapter', 0, 100)

        and:
        1 * gameRepo.findByPassageIdAndUserId(passageId, userId) >> Optional.of(game)
        1 * gameRepo.save(_) >> game

        when:
        def result = service.createGuess(userId, passageId, winningData)

        then:
        result.size() == 2
        noExceptionThrown()
    }

    def "should create losing guess"() {
        given:
        def losingGame = new Game(1L, false, 0, [guess, guess, guess, guess, guess], user)

        and:
        1 * gameRepo.findByPassageIdAndUserId(passageId, userId) >> Optional.of(game)
        1 * gameRepo.save(_) >> losingGame

        when:
        def result = service.createGuess(userId, passageId, guessData)

        then:
        result.size() == 5
        noExceptionThrown()
    }

    def "should throw exception for inactive game"() {
        given:
        game.playing = false
        1 * gameRepo.findByPassageIdAndUserId(passageId, userId) >> Optional.of(game)

        when:
        service.createGuess(userId, passageId, guessData)

        then:
        thrown(Exception)
    }

}
