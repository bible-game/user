
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

    User user = new User(
        'email',
        'password',
        "John",
        "Smith",
        "My Church",
        [game], [read], []
    )

    ReadData readData = new ReadData(
        'passage-key',
        'book',
        'chapter',
        'verse-start',
        'verse-end'
    )

    GuessData guessData = new GuessData(
        'book',
        'chapter',
        1,
        2,
        'passage-book',
        'passage-chapter'
    )

    Read read = new Read(
        'passage-key',
        'book',
        'chapter',
        'verse-start',
        'verse-end',
        user
    )

    Guess guess = new Guess(1, 2, 'book', 'chapter')

    Game game = new Game(
        1L,
        'passage-book',
        'passage-chapter',
        true,
        0,
        [guess],
        user
    )

}
