package plad.services.nickname;

import plad.services.nickname.NicknameWordLists.Adjective;
import plad.services.nickname.NicknameWordLists.Animal;

import java.util.function.Predicate;
import java.util.random.RandomGenerator;

// Draws anonymous nicknames like "Morcego Sereno" or "Coruja Serena" (the adjective agrees with the animal's
// gender). The draw takes no input about the user: the nickname can never be derived from an id, e-mail or name.
// Production injects a SecureRandom; tests inject a seeded Random.
public class NicknameGenerator {

    // the suffix "Morcego Sereno 7" only appears after the plain draws kept colliding
    public static final int PLAIN_ATTEMPTS = 10;
    public static final int SUFFIX_ATTEMPTS = 50;
    public static final int MIN_SUFFIX = 2;
    public static final int MAX_SUFFIX = 999;

    private final NicknameWordLists words;
    private final RandomGenerator random;

    public NicknameGenerator(NicknameWordLists words, RandomGenerator random) {
        this.words = words;
        this.random = random;
    }

    public String draw() {
        Animal animal = words.animals().get(random.nextInt(words.animals().size()));
        Adjective adjective = words.adjectives().get(random.nextInt(words.adjectives().size()));
        return compose(animal, adjective);
    }

    public String drawWithSuffix() {
        return draw() + " " + random.nextInt(MIN_SUFFIX, MAX_SUFFIX + 1);
    }

    // Plain draws first; after PLAIN_ATTEMPTS collisions in a row, a number is appended. claim tries to reserve the
    // candidate and says whether it got it (a database insert guarded by the unique constraint, or a set add).
    public String claim(Predicate<String> claim) {
        for (int i = 0; i < PLAIN_ATTEMPTS; i++) {
            String candidate = draw();
            if (claim.test(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < SUFFIX_ATTEMPTS; i++) {
            String candidate = drawWithSuffix();
            if (claim.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free nickname after "
                + (PLAIN_ATTEMPTS + SUFFIX_ATTEMPTS) + " attempts");
    }

    public static String compose(Animal animal, Adjective adjective) {
        return animal.name() + " " + adjective.form(animal.gender());
    }

    public NicknameWordLists words() {
        return words;
    }
}
