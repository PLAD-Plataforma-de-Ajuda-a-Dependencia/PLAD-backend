package plad.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.User;
import plad.entity.UserType;
import plad.services.AuditService;
import plad.services.JwtService;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the audit table accepts no deletes, so every test runs in a rolled-back transaction and filters on ids of its own
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditService auditService;

    private long subjectA;
    private long subjectB;

    @BeforeEach
    void recordEntries() {
        subjectA = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
        subjectB = subjectA + 1;
        auditService.record(AuditAction.TRIAGE_SUBMITTED, subjectA, UserType.dependente, subjectA, "TRIAGE", 10L, null);
        auditService.record(AuditAction.TRIAGE_RESUBMITTED, subjectA, UserType.dependente, subjectA, "TRIAGE", 10L, null);
        auditService.record(AuditAction.TRIAGE_SUBMITTED, subjectB, UserType.dependente, subjectB, "TRIAGE", 11L, null);
    }

    // access

    @Test
    void dependentGetsForbidden() throws Exception {
        forbidden(call("/admin/audit", UserType.dependente));
    }

    @Test
    void psychologistGetsForbidden() throws Exception {
        forbidden(call("/admin/audit", UserType.psicologo));
    }

    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // content

    @Test
    void adminListsEntriesNewestFirstInSnakeCase() throws Exception {
        call("/admin/audit?subject_user_id=" + subjectA, UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_RESUBMITTED"))
                .andExpect(jsonPath("$.items[1].action").value("TRIAGE_SUBMITTED"))
                .andExpect(jsonPath("$.items[0].subject_user_id").value(subjectA))
                .andExpect(jsonPath("$.items[0].actor_id").value(subjectA))
                .andExpect(jsonPath("$.items[0].entity").value("TRIAGE"))
                .andExpect(jsonPath("$.items[0].entity_id").value(10))
                .andExpect(jsonPath("$.items[0].details.actor_role").value("dependente"))
                .andExpect(jsonPath("$.items[0].occurred_at").isNotEmpty());
    }

    @Test
    void filtersBySubjectAndByAction() throws Exception {
        call("/admin/audit?subject_user_id=" + subjectA + "&action=TRIAGE_SUBMITTED", UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_SUBMITTED"));

        call("/admin/audit?subject_user_id=" + subjectB + "&action=TRIAGE_RESUBMITTED", UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void filtersByActionAloneAcrossUsers() throws Exception {
        call("/admin/audit?action=TRIAGE_READ&size=100", UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.subject_user_id == " + subjectA + ")]").isEmpty());
    }

    @Test
    void withoutFiltersListsEverythingIncludingTheseEntries() throws Exception {
        call("/admin/audit?size=100", UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.subject_user_id == " + subjectB + ")]").isNotEmpty());
    }

    // pagination

    @Test
    void paginates() throws Exception {
        call("/admin/audit?subject_user_id=" + subjectA + "&size=1&page=1", UserType.admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_SUBMITTED"));
    }

    @Test
    void invalidParametersAreBadRequest() throws Exception {
        for (String query : new String[]{"action=NOT_AN_ACTION", "size=101", "size=0", "page=-1", "subject_user_id=abc"}) {
            call("/admin/audit?" + query, UserType.admin)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    private ResultActions call(String path, UserType type) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setTypeUser(type);
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(user)));
    }

    private static void forbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
