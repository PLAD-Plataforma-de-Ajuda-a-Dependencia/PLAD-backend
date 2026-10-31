package plad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import plad.services.AdminBootstrapService;

// ADMIN_EMAIL and ADMIN_PASSWORD come from the environment only; without both, nothing happens
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(
            AdminBootstrapService adminBootstrapService,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_PASSWORD:}") String adminPassword
    ) {
        this.adminBootstrapService = adminBootstrapService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.createIfAbsent(adminEmail, adminPassword);
    }
}
