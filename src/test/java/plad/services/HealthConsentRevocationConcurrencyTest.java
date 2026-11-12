package plad.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import plad.config.TestConsents;
import plad.config.TestcontainersConfig;
import plad.entity.Dependent;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.ConsentRequiredException;
import plad.repositories.DependentRepository;
import plad.repositories.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A revocation of the health data consent and a write of health data at the same time (decision 35). The write gate
// (ConsentService#requireHealth) reads the acceptance FOR SHARE and the revocation UPDATEs it first, so one waits for the
// other: a triage written while the revocation runs is erased by it, and a write that comes during an uncommitted
// revocation is refused once it commits. Deliberately NOT @Transactional: each side commits on its own, on its own
// thread. The user is committed and removed in @AfterEach (the cascades take the rest); nothing here writes to audit_log.
@SpringBootTest
@Import(TestcontainersConfig.class)
class HealthConsentRevocationConcurrencyTest {

    // long enough for a side that is NOT blocked to finish, so a missing lock fails the test
    private static final long BLOCKED_FOR_MS = 700;

    @Autowired
    private ConsentService consentService;
    @Autowired
    private HealthConsentRevocationService revocationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void removeWhatTheTestCommitted() {
        for (Long id : userIds) {
            jdbcTemplate.update("delete from users where id = ?", id); // dependents, consents and triages cascade
        }
    }

    // the write passed the gate first; the revocation waits for it and then erases the triage it stored
    @Test
    void aTriageWrittenWhileTheRevocationRunsIsErasedByIt() throws Exception {
        Long dependent = newDependentWithConsent();
        CountDownLatch gatePassed = new CountDownLatch(1);
        CountDownLatch finishWrite = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = pool.submit(() -> inTransaction(() -> {
                consentService.requireHealth(dependent);
                gatePassed.countDown();
                await(finishWrite);
                jdbcTemplate.update("insert into triages (dependent_id, created_at, updated_at) values (?, now(), now())",
                        dependent);
            }));
            assertThat(gatePassed.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> revoke = pool.submit(() -> revocationService.revoke(dependent));
            assertThatThrownBy(() -> revoke.get(BLOCKED_FOR_MS, TimeUnit.MILLISECONDS))
                    .as("the revocation waits for the write that already passed the gate")
                    .isInstanceOf(TimeoutException.class);

            finishWrite.countDown();
            write.get(10, TimeUnit.SECONDS);
            revoke.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from triages where dependent_id = ?", Long.class, dependent))
                .isZero();
        assertThat(consentService.isHealthPending(dependent)).isTrue();
    }

    // the revocation updated the row and has not committed; the write waits at the gate and is refused once it commits
    @Test
    void aWriteThatArrivesDuringTheRevocationIsRefused() throws Exception {
        Long dependent = newDependentWithConsent();
        CountDownLatch revoked = new CountDownLatch(1);
        CountDownLatch commitRevocation = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> revoke = pool.submit(() -> inTransaction(() -> {
                revocationService.revoke(dependent);
                revoked.countDown();
                await(commitRevocation);
            }));
            assertThat(revoked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> write = pool.submit(() -> inTransaction(() -> consentService.requireHealth(dependent)));
            assertThatThrownBy(() -> write.get(BLOCKED_FOR_MS, TimeUnit.MILLISECONDS))
                    .as("the gate waits for the revocation in flight")
                    .isInstanceOf(TimeoutException.class);

            commitRevocation.countDown();
            revoke.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> write.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ConsentRequiredException.class);
        } finally {
            pool.shutdownNow();
        }
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test did not release the latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Long newDependentWithConsent() {
        User user = new User();
        user.setRealName("Concorrencia Saude");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1995, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
        userRepository.save(user);
        userIds.add(user.getId());
        Dependent row = new Dependent();
        row.setNickname(plad.TestNicknames.unique());
        row.setUser(user);
        dependentRepository.save(row);
        TestConsents.grantHealth(jdbcTemplate, user.getId());
        return user.getId();
    }
}
