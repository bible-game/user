package game.bible.user.auth.login

import game.bible.user.User
import game.bible.user.state.game.Game
import game.bible.user.state.read.Read
import spock.lang.Shared

trait LoginTrait {

    @Shared String email = "email@address.com"
    @Shared String password = "password"

    /** Return sample login data */
    LoginData getData() {
        return getData(email, password)
    }

    /** Return sample login data */
    LoginData getData(email, password) {
        def data = new LoginData(email, password)

        return data
    }

    /** Return sample user data */
    User getUser() {
         def user = new User(email, password, [new Game()], [new Read()])

        return user
    }

}