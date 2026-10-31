package plad.config;

import org.junit.jupiter.api.Test;
import plad.services.AdminBootstrapService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminBootstrapRunnerWiringTest {

    private final AdminBootstrapService service = mock(AdminBootstrapService.class);

    @Test
    void passesTheEnvironmentValuesToTheService() {
        new AdminBootstrapRunner(service, "admin@example.com", "senha-admin-123").run(null);

        verify(service).createIfAbsent("admin@example.com", "senha-admin-123");
    }
}
