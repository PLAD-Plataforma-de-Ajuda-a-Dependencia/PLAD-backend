package plad.services.nickname;

import org.junit.jupiter.api.Test;
import plad.services.nickname.NicknameWordLists.Adjective;
import plad.services.nickname.NicknameWordLists.Animal;
import plad.services.nickname.NicknameWordLists.Gender;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameGeneratorTest {

    private static final Pattern ENDS_WITH_NUMBER = Pattern.compile(" (\\d+)$");

    private final NicknameWordLists words = NicknameWordLists.load();
    private final NicknameGenerator generator = new NicknameGenerator(words, new Random(42));

    @Test
    void adjectiveAgreesInGenderWithTheAnimalForEveryPossiblePair() {
        for (Animal animal : words.animals()) {
            for (Adjective adjective : words.adjectives()) {
                String nickname = NicknameGenerator.compose(animal, adjective);

                String expectedAdjective = animal.gender() == Gender.MASCULINE ? adjective.masculine() : adjective.feminine();
                String wrongAdjective = animal.gender() == Gender.MASCULINE ? adjective.feminine() : adjective.masculine();
                assertThat(nickname).isEqualTo(animal.name() + " " + expectedAdjective);
                if (!adjective.masculine().equals(adjective.feminine())) {
                    assertThat(nickname).doesNotEndWith(" " + wrongAdjective);
                }
            }
        }
    }

    @Test
    void everyDrawnNicknameIsAnAnimalWithTheAdjectiveInItsGender() {
        for (int i = 0; i < 5_000; i++) {
            String nickname = generator.draw();

            Animal animal = words.animals().stream()
                    .filter(a -> nickname.startsWith(a.name() + " "))
                    .findFirst().orElseThrow(() -> new AssertionError("no animal in '" + nickname + "'"));
            String adjective = nickname.substring(animal.name().length() + 1);
            assertThat(words.adjectives().stream().map(a -> a.form(animal.gender())))
                    .as("'%s' must be an adjective in the gender of %s", adjective, animal.name())
                    .contains(adjective);
        }
    }

    @Test
    void sameSeedGivesTheSameSequenceAndDifferentSeedsDiffer() {
        List<String> a = draws(new NicknameGenerator(words, new Random(1)), 20);
        List<String> b = draws(new NicknameGenerator(words, new Random(1)), 20);
        List<String> c = draws(new NicknameGenerator(words, new Random(2)), 20);

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }

    @Test
    void suffixedDrawEndsWithANumberInTheAllowedRange() {
        for (int i = 0; i < 2_000; i++) {
            String nickname = generator.drawWithSuffix();

            var matcher = ENDS_WITH_NUMBER.matcher(nickname);
            assertThat(matcher.find()).as(nickname).isTrue();
            assertThat(Integer.parseInt(matcher.group(1)))
                    .isBetween(NicknameGenerator.MIN_SUFFIX, NicknameGenerator.MAX_SUFFIX);
        }
    }

    @Test
    void aCollisionMakesItDrawAgain() {
        AtomicInteger attempts = new AtomicInteger();

        String nickname = generator.claim(candidate -> attempts.incrementAndGet() == 3);

        assertThat(attempts).hasValue(3);
        assertThat(ENDS_WITH_NUMBER.matcher(nickname).find()).isFalse();
    }

    @Test
    void afterTenCollisionsInARowItAppendsANumber() {
        List<String> tried = new ArrayList<>();

        String nickname = generator.claim(candidate -> {
            tried.add(candidate);
            return tried.size() == NicknameGenerator.PLAIN_ATTEMPTS + 1;
        });

        assertThat(tried.subList(0, NicknameGenerator.PLAIN_ATTEMPTS)).noneMatch(c -> ENDS_WITH_NUMBER.matcher(c).find());
        assertThat(nickname).matches(".+ \\d+");
    }

    @Test
    void givesUpWithAnErrorWhenEverythingIsTaken() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> generator.claim(candidate -> {
            attempts.incrementAndGet();
            return false;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(NicknameGenerator.PLAIN_ATTEMPTS + NicknameGenerator.SUFFIX_ATTEMPTS);
    }

    @Test
    void drawingTakesNoInputAboutTheUser() throws NoSuchMethodException {
        // the nickname can never be derived from an id, e-mail or name: the draw methods have no parameters
        assertThat(NicknameGenerator.class.getMethod("draw").getParameterCount()).isZero();
        assertThat(NicknameGenerator.class.getMethod("drawWithSuffix").getParameterCount()).isZero();
    }

    private static List<String> draws(NicknameGenerator generator, int count) {
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drawn.add(generator.draw());
        }
        return drawn;
    }
}
