package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.CrmCrpType;

// Who runs a meeting, as every authenticated role sees it (decision 23). The council registration is there so a
// dependent can check the person who leads the meeting. The name is `name`, never a real_name key, and there is no
// e-mail. Accounts created before CRM was refused at sign-up (decision 33) still read CRM here.
@Schema(description = "The psychologist who hosts the meeting")
public record MeetingHostDTO(
        @Schema(description = "Name of the psychologist", example = "Dra. Helena Duarte")
        String name,
        @Schema(description = "Professional council of the host: CRP. Accounts created before CRM was refused at "
                + "sign-up may still show CRM", example = "CRP")
        CrmCrpType credentialType,
        @Schema(description = "Normalized council registration number (letters and digits only)", example = "06123456")
        String crmCrpNumber
) {
}
