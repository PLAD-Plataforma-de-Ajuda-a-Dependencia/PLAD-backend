package plad.services.nickname;

import org.junit.jupiter.api.Test;
import plad.services.nickname.NicknameWordLists.Adjective;
import plad.services.nickname.NicknameWordLists.Animal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameWordListsTest {

    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]+");

    private final NicknameWordLists words = NicknameWordLists.load();

    @Test
    void listsHaveTheExpectedSize() {
        assertThat(words.animals()).hasSizeBetween(70, 90);
        assertThat(words.adjectives()).hasSizeBetween(50, 70);
    }

    @Test
    void bothGendersExistAmongTheAnimals() {
        assertThat(words.animals()).extracting(Animal::gender)
                .contains(NicknameWordLists.Gender.MASCULINE, NicknameWordLists.Gender.FEMININE);
    }

    @Test
    void noWordIsRepeated() {
        assertThat(words.animals().stream().map(a -> normalize(a.name())).toList()).doesNotHaveDuplicates();
        assertThat(words.adjectives().stream().map(a -> normalize(a.masculine())).toList()).doesNotHaveDuplicates();
    }

    @Test
    void noForbiddenWordIsInTheLists() throws IOException {
        Set<String> forbidden = forbiddenWords();
        assertThat(forbidden).contains("veado", "porco", "burro", "vaca", "galinha", "piranha", "anta", "cobra", "jumento");

        Stream<String> allWords = Stream.concat(
                words.animals().stream().map(Animal::name),
                words.adjectives().stream().flatMap(a -> Stream.of(a.masculine(), a.feminine())));
        // compare whole words, so "Lobo-guará" is checked as "lobo" and "guara"
        List<String> offending = allWords
                .flatMap(word -> Arrays.stream(NON_LETTERS.split(normalize(word))))
                .filter(forbidden::contains)
                .toList();
        assertThat(offending).isEmpty();
    }

    @Test
    void everyPossibleNicknameFitsTheColumnEvenWithTheLargestSuffix() {
        int longest = 0;
        for (Animal animal : words.animals()) {
            for (Adjective adjective : words.adjectives()) {
                longest = Math.max(longest, NicknameGenerator.compose(animal, adjective).length());
            }
        }
        assertThat(longest + 1 + String.valueOf(NicknameGenerator.MAX_SUFFIX).length()).isLessThanOrEqualTo(60);
    }

    private static Set<String> forbiddenWords() throws IOException {
        try (InputStream in = NicknameWordListsTest.class.getResourceAsStream("/nicknames/forbidden.txt")) {
            assertThat(in).isNotNull();
            Set<String> forbidden = new HashSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String word = line.strip();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    forbidden.add(word);
                }
            }
            return forbidden;
        }
    }

    private static String normalize(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
