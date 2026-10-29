package plad.services;

import java.util.Locale;

// crm_crp_number is stored uppercased with only ASCII letters and digits (see V13) so "06/12345" and "06 / 12345" are the same credential
final class CrmCrpNumbers {

    private CrmCrpNumbers() {
    }

    static String normalize(String number) {
        return number.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
