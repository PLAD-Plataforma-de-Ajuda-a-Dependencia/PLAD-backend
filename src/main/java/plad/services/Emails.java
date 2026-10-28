package plad.services;

import java.util.Locale;

// emails are stored lowercased (see V7) so register and login must normalize the same way
final class Emails {

    private Emails() {
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
