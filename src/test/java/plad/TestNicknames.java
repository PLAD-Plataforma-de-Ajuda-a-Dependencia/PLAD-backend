package plad;

import java.util.concurrent.atomic.AtomicLong;

// dependents.nickname is NOT NULL and UNIQUE: test fixtures that build a Dependent by hand take one from here
public final class TestNicknames {

    private static final AtomicLong COUNTER = new AtomicLong();

    private TestNicknames() {
    }

    public static String unique() {
        return "Teste Apelido " + COUNTER.incrementAndGet();
    }
}
