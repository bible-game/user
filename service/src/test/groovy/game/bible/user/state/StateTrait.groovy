
package game.bible.user.state

import game.bible.user.User
import game.bible.user.state.data.GuessData
import game.bible.user.state.data.ReadData
import game.bible.user.state.game.Game
import game.bible.user.state.game.Guess
import game.bible.user.state.read.Read

/**
 * State-related Test Logic
 * @since 27th June 2025
 */
trait StateTrait {

    long userId = 1L
    long passageId = 1L

    User user = new User('email', 'password', [game], [read])

    ReadData readData = new ReadData('passage-key')

    GuessData guessData = new GuessData('book', 'chapter', 1, 2)

    Read read = new Read('passage-key', user)

    Guess guess = new Guess('book', 'chapter', 1, 2)

    Game game = new Game(1L, true, 0, [guess], user)

}
