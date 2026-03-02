package game.bible.user.auth.registration

import spock.lang.Shared

trait RegistrationTrait {

    @Shared String email = "email@address.com"
    @Shared String password = "password"

    /** Return sample email as string */
    String getEmail() { email }

    /** Return sample registration data */
    RegistrationData getData() {
        return getData(email, password)
    }

    /** Return sample registration data */
    RegistrationData getData(String email, String password) {
        def data = new RegistrationData(
            email,
            password,
            'John',
            'Smith',
            'Some Church',
            [], [], []
        )

        return data
    }

}