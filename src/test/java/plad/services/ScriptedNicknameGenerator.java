package plad.services;

import plad.services.nickname.NicknameGenerator;
import plad.services.nickname.NicknameWordLists;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.random.RandomGenerator;

// A generator that returns exactly the nicknames the test scripts, so a collision can be forced.
// claim() is inherited, so the real retry logic runs against the scripted draws.
public class ScriptedNicknameGenerator extends NicknameGenerator {

    private final Deque<String> plain = new ArrayDeque<>();
    private final Deque<String> suffixed = new ArrayDeque<>();

    public ScriptedNicknameGenerator() {
        super(NicknameWordLists.load(), RandomGenerator.getDefault());
    }

    public ScriptedNicknameGenerator plain(String... nicknames) {
        plain.addAll(Arrays.asList(nicknames));
        return this;
    }

    public ScriptedNicknameGenerator suffixed(String... nicknames) {
        suffixed.addAll(Arrays.asList(nicknames));
        return this;
    }

    @Override
    public String draw() {
        if (plain.isEmpty()) {
            throw new AssertionError("the test scripted too few plain draws");
        }
        return plain.poll();
    }

    @Override
    public String drawWithSuffix() {
        if (suffixed.isEmpty()) {
            throw new AssertionError("the test scripted too few suffixed draws");
        }
        return suffixed.poll();
    }
}
