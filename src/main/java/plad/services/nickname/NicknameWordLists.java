package plad.services.nickname;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// The word lists behind the nicknames. Plain Java on purpose (no Spring): the Flyway Java migration that
// gives nicknames to existing dependents loads it too.
public record NicknameWordLists(List<Animal> animals, List<Adjective> adjectives) {

    public static final String ANIMALS_RESOURCE = "/nicknames/animals.csv";
    public static final String ADJECTIVES_RESOURCE = "/nicknames/adjectives.csv";

    public enum Gender { MASCULINE, FEMININE }

    public record Animal(String name, Gender gender) {
    }

    // invariable adjectives ("Paciente", "Gentil") repeat the same form in both columns
    public record Adjective(String masculine, String feminine) {
        public String form(Gender gender) {
            return gender == Gender.MASCULINE ? masculine : feminine;
        }
    }

    public NicknameWordLists {
        if (animals.isEmpty() || adjectives.isEmpty()) {
            throw new IllegalArgumentException("Nickname word lists cannot be empty");
        }
        animals = List.copyOf(animals);
        adjectives = List.copyOf(adjectives);
    }

    public static NicknameWordLists load() {
        List<Animal> animals = new ArrayList<>();
        for (String[] row : readCsv(ANIMALS_RESOURCE, 2)) {
            animals.add(new Animal(row[0], parseGender(row[1])));
        }
        List<Adjective> adjectives = new ArrayList<>();
        for (String[] row : readCsv(ADJECTIVES_RESOURCE, 2)) {
            adjectives.add(new Adjective(row[0], row[1]));
        }
        return new NicknameWordLists(animals, adjectives);
    }

    private static Gender parseGender(String value) {
        return switch (value) {
            case "m" -> Gender.MASCULINE;
            case "f" -> Gender.FEMININE;
            default -> throw new IllegalStateException("Unknown gender '" + value + "' in " + ANIMALS_RESOURCE);
        };
    }

    // semicolon separated, first line is the header; blank lines are skipped
    private static List<String[]> readCsv(String resource, int columns) {
        try (InputStream in = NicknameWordLists.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String[]> rows = new ArrayList<>();
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(";", -1);
                if (cells.length != columns) {
                    throw new IllegalStateException("Bad row '" + line + "' in " + resource);
                }
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = cells[i].strip();
                    if (cells[i].isEmpty()) {
                        throw new IllegalStateException("Empty cell in row '" + line + "' of " + resource);
                    }
                }
                rows.add(cells);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
