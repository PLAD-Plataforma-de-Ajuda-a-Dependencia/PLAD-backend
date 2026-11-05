# Matriz de requisitos

Conferida em 2026-11-05, no commit `3f40940` (branch `feat/data-subject-rights`).

**Regra de manutenção:** todo PR que muda a situação de um requisito atualiza este arquivo (situação, código, teste e o quadro abaixo).

## Situações

| Situação | Significado |
|---|---|
| Testado | Existe código e pelo menos um teste exercita o requisito **pelo endpoint** (MockMvc ou servidor real). Testes só de serviço, repositório ou validador não bastam. |
| Implementado sem teste | Existe código, mas nenhum teste de endpoint o exercita. |
| Parcial | Parte do requisito existe; o que falta está explicado nas notas. |
| Não iniciado | Nada, ou só um esboço sem uso, no código. |
| Fora do escopo | Decisão do time de não fazer neste projeto. |

## Quadro por situação

| Situação | Qtd | Requisitos |
|---|---:|---|
| Testado | 2 | RF001, RF002 |
| Implementado sem teste | 0 | — |
| Parcial | 8 | RF003, RF007, RF011, RNF001, RNF002, RNF003, RNF004, RNF006 |
| Não iniciado | 8 | RF005, RF006, RF008, RF009, RF010, RF012, RNF005, RNF007 |
| Fora do escopo | 1 | RF004 |
| **Total** | **19** | 12 RFs + 7 RNFs |

## Requisitos funcionais

| Requisito | Descrição curta | Situação | Onde está no código | Teste que comprova |
|---|---|---|---|---|
| RF001 | Cadastro de usuário com dados mínimos | Testado | `AuthController#register` → `AuthService#register`; `RegisterRequestDTO`; `MinAge`/`MinAgeValidator`; `MaxBytes`; `CrmCrpNumbers`; `Emails`; `NicknameService#assignInitial` (apelido do dependente); `ConsentService` (aceite LGPD no cadastro, `POST /me/consents`); migrações V1–V3, V7, V13, V18–V21 | `AuthControllerTest#registerDependentStoresLowercasedEmail`<br>`AuthControllerTest#registerPsychologistIsCreated`<br>`AuthControllerTest#sameEmailWithDifferentCaseIsConflict`<br>`RegisterMinAgeTest#turning18TodayIsCreated`<br>`RegisterMinAgeTest#turning18TomorrowIsBadRequest`<br>`RegisterMinAgeTest#bornOnLeapDayIsAnAdultOnlyFromMarchFirstInANonLeapYear`<br>`AuthControllerTest#registerWithoutAcceptingTheTermsIsBadRequestAndCreatesNothing`<br>`MeConsentTest#aNewVersionMakesTheConsentPendingAgainUntilItIsAccepted` |
| RF002 | Login e autenticação (JWT) | Testado | `LoginController#login` → `LoginService#login`; `JwtService`; `JwtAuthenticationFilter`; `SecurityConfig#securityFilterChain`; `RateLimitInterceptor`/`RateLimiterService` | `LoginControllerTest#loginReturnsTokenThatAuthenticatesRequests`<br>`LoginControllerTest#wrongPasswordAndUnknownEmailGetTheSameAnswer`<br>`LoginControllerTest#sixthAttemptFromSameClientIsTooManyRequests`<br>`LoginControllerTest#eleventhFailedAttemptOnSameEmailIsTooManyRequestsEvenFromDifferentClients`<br>`JwtAuthenticationFilterTest#missingTokenIsUnauthorized` |
| RF003 | Triagem inicial | Parcial | `TriageController` (`GET /triage/questions`, `POST /triage`, `PUT /triage`); `TriageService`; `TriageReadController` (`GET /triages`, `GET /triages/{id}`, só psicólogo, com nome real) e `AdminTriageReadController` (`GET /admin/triages`, `GET /admin/triages/{id}`, só admin, só apelido) → `TriageReadService`; entidades `Triage`, `QuestionTriage`, `ResultQuestionTriage`; `TriageQuestionCatalog` (perguntas em cache); 10 perguntas em V5, V9, V11, V12, V15 (todas opcionais); respostas em V4, V6, V14; `updated_at` da triagem em V22 | `TriageControllerTest#getQuestionsReturnsSeededQuestionsInSnakeCase`<br>`TriageControllerTest#resubmitMovesUpdatedAtForwardAndLeavesCreatedAtAlone`<br>`TriageQuestionCatalogTest#secondCallComesFromTheCacheAndDoesNotQueryTheRepository`<br>`TriageControllerTest#submitCreatesTriageAndSecondSubmitIsConflict`<br>`TriageControllerTest#submitAsPsychologistIsForbidden`<br>`TriageControllerTest#resubmitReplacesAnswersAndKeepsTriageId`<br>`TriageControllerTest#submitWithEmptyAnswersStoresNothingAndDoesNotUseTheSingleSubmission`<br>`TriageControllerTest#resubmitWithEmptyAnswersIsBadRequestAndKeepsTheTriage`<br>`TriageReadControllerTest#adminListsTriagesNewestFirstAndReadsTheDetail`<br>`TriageReadControllerTest#psychologistReadsListAndDetailWithoutBeingValidated`<br>`TriageReadControllerTest#dependentCannotReadAnyTriageNotEvenTheirOwn` |
| RF004 | Redirecionamento por risco | Fora do escopo | Não existe | — |
| RF005 | Participação em reuniões online | Não iniciado | Só o tipo enum `status_reuniao` em V1, sem tabela, entidade ou endpoint | — |
| RF006 | Moderação de reuniões | Não iniciado | Não existe | — |
| RF007 | Anonimato nas reuniões | Parcial | Só a parte do apelido: `NicknameGenerator`, `NicknameWordLists`, `NicknameService`, `NicknameConfig`; `AuthService#register` (apelido do dependente na mesma transação); `MeController#rerollNickname` (`POST /me/nickname/reroll`, 5 por 24 h); `MeResponseDTO#nickname`; `TriageReadResponseDTO` (psicólogo: apelido + nome real) e `AdminTriageReadResponseDTO` (admin: só apelido); V18, V19 (Java), V20. Falta a sala de reunião (RF005), onde o apelido seria usado | `NicknameGeneratorTest#adjectiveAgreesInGenderWithTheAnimalForEveryPossiblePair`<br>`NicknameWordListsTest#noForbiddenWordIsInTheLists`<br>`NicknameServiceTest#afterTenCollisionsInARowItAppendsANumberAndRedrawsWhenThatCollidesToo`<br>`NicknameRerollControllerTest#rerollReturnsANewNicknameThatGetMeThenShows`<br>`NicknameRerollControllerTest#sixthRerollInADayIsRateLimitedWithRetryAfter`<br>`TriageReadControllerTest#psychologistWhoIsNotValidatedGetsNicknameAndRealNameButNeverTheEmail`<br>`TriageReadControllerTest#adminGetsOnlyTheNicknameNeverTheRealName`<br>`RealNameExposureTest#onlyTheListedDtosHaveARealNameComponent` |
| RF008 | Publicação de conteúdos | Não iniciado | Não existe | — |
| RF009 | Relatos anônimos | Não iniciado | Não existe | — |
| RF010 | Aba comunidade | Não iniciado | Não existe | — |
| RF011 | Validação de psicólogos (CRM/CRP) | Parcial | `AuthService#register` (exige e normaliza o número); `CrmCrpNumbers#normalize`; `Psychologist` (`isValidate`, `validateTime`); `MeResponseDTO#isValidated` (`GET /me` mostra o campo); V2, V13 | `AuthControllerTest#credentialNumberDifferingOnlyInFormattingIsRejected`<br>`AuthControllerTest#psychologistWithoutCredentialsIsBadRequest`<br>`AuthControllerTest#psychologistWithBlankCredentialNumberIsBadRequest` |
| RF012 | Gerenciamento de reuniões | Não iniciado | Não existe | — |

## Requisitos não funcionais

| Requisito | Descrição curta | Situação | Onde está no código | Teste que comprova |
|---|---|---|---|---|
| RNF001 | Usabilidade | Parcial | `GlobalExceptionHandler`/`ErrorResponseDTO` (erro padronizado); `OpenApiConfig` e `docs/openapi.json` (contrato); `http/plad.http` | `OpenApiContractTest#apiDocsMatchCommittedSnapshot` |
| RNF002 | Segurança (HTTPS) | Parcial | `SecurityConfig` (BCrypt, JWT stateless, CORS por origens explícitas); `RateLimiterService`; `application.properties` (`server.forward-headers-strategy`, `TRUSTED_PROXIES`, docs fechadas por padrão) | `JwtAuthenticationFilterTest#malformedTokenIsUnauthorized`<br>`CorsConfigTest#preflightFromDisallowedOriginIsBlocked`<br>`OpenApiDisabledTest#apiDocsRequireAuthenticationWhenDisabled` |
| RNF003 | Privacidade e confidencialidade | Parcial | `SecurityConfig#passwordEncoder` (BCrypt); `JwtService` (token só com `userId` e papel); `TriageController` (usuário vem do token); `GlobalExceptionHandler` (sem detalhe interno na resposta ou no log); `UserResponseDTO`; trilha de auditoria: `V17__create_audit_log.sql` (só INSERT), `AuditService`, `AdminAuditController` (`GET /admin/audit`), `MeController` (`GET /me/access-log`); `TriageReadService` (grava `TRIAGE_VIEWED` a cada triagem lida); consentimento LGPD: `ConsentService`, `Consent`, `MeController#acceptConsents` (`POST /me/consents`), V21; direitos do titular: `AccountDeletionService` e `MeController#deleteMyAccount` (`DELETE /me`), `MeExportService` e `MeController#exportMyData` (`GET /me/export`), V24 | `LoginControllerTest#wrongPasswordAndUnknownEmailGetTheSameAnswer`<br>`MeConsentTest#aNewVersionMakesTheConsentPendingAgainUntilItIsAccepted`<br>`ServiceUnavailableAndTraceTest#unexpectedFailureReturnsTheTraceThatAppearsInTheLog`<br>`AuditLogTriggerTest#updateIsRejected`<br>`TriageAuditTest#submitRecordsTriageSubmittedWithIdsAndRoleOnly`<br>`TriageAuditTest#detailsNeverHoldAnswersNameOrEmail`<br>`AdminAuditControllerTest#dependentGetsForbidden`<br>`MeAccessLogTest#entriesShowTheRoleAndNeverWhoReadIt`<br>`TriageReadControllerTest#detailReadRecordsOneViewWithActorSubjectAndRole`<br>`TriageReadControllerTest#listRecordsOneViewPerTriageReturned`<br>`TriageReadControllerTest#readingTheSameTriageTwiceRecordsTwoViews`<br>`TriageReadControllerTest#viewEntriesNeverHoldAnswersNameOrEmail`<br>`TriageReadControllerTest#dependentSeesTheReadInTheirAccessLogWithTheRoleButNotTheReader`<br>`AccountDeletionTest#deletingADependentLeavesNoRowWithTheirIdExceptInTheAuditLog`<br>`AccountDeletionTest#afterTheDeleteTheOldCredentialsAreDead`<br>`MeExportTest#dependentGetsAccountNicknamesConsentsAndTriageWithQuestionText` |
| RNF004 | Disponibilidade 24/7 | Parcial | `Dockerfile` (JRE 25, não-root); `docker-compose.yml` (`api` com healthcheck na readiness, `restart: unless-stopped`); actuator com `/actuator/health/liveness` e `/readiness` liberados em `SecurityConfig` (readiness inclui o banco, `application.properties`); `ProdEnvironmentCheck` e `application-prod.properties` (prod sem credencial padrão); `GlobalExceptionHandler` (503 com `Retry-After` se o banco cai). Guia em `docs/deploy.md` | `ActuatorHealthTest#readinessAnswersUpWithoutTokenAndWithoutDetails`<br>`ActuatorHealthTest#everythingElseUnderActuatorStillNeedsAToken`<br>`ProdEnvironmentCheckTest#prodWithoutDatabaseVariablesStopsAndNamesEveryMissingOne`<br>`ProdProfilePropertiesTest#databaseSettingsAreRequiredPlaceholdersWithoutDefault`<br>`ServiceUnavailableAndTraceTest` |
| RNF005 | Resposta em até 2 s | Não iniciado | Nada configurado; `spring.jpa.open-in-view=false` em `application.properties` | — |
| RNF006 | Escalabilidade | Parcial | `SecurityConfig` (`SessionCreationPolicy.STATELESS`); `RateLimiterService` (em memória, por JVM); `CacheConfig` (cache em memória das perguntas da triagem) | `TriageQuestionCatalogTest#secondCallComesFromTheCacheAndDoesNotQueryTheRepository` (só o cache) |
| RNF007 | Estabilidade da videochamada | Não iniciado | Não existe | — |

## Notas

### Parcial

- **RF003:** a coleta de respostas existe (perguntas, validação por tipo, todas opcionais, `POST` sem nenhuma resposta devolve 204 e não salva a triagem, uma triagem por dependente, reenvio por `PUT` que exige ao menos uma resposta). A **classificação de risco está fora do escopo, por decisão do time**: nenhum campo, score ou lógica lê as respostas, então a triagem só guarda e devolve o que o dependente respondeu. A triagem é lida por admin e psicólogo em `GET /triages` e `GET /triages/{id}` (paginado, mais recente primeiro; o dependente aparece só pelo id). O dependente não tem rota de leitura, nem da própria triagem: recebe as respostas na resposta do `POST`/`PUT` (a cópia dos dados, com a triagem e o texto das perguntas, sai em `GET /me/export`: decisão 18).
- **RF007:** o apelido anônimo existe (cadastro, `GET /me`, troca limitada, triagem lida por psicólogo e admin), mas só faz sentido na sala de reunião, que depende de RF005. Falta decidir como o anonimato convive com o cadastro, que hoje pede nome real (só o psicólogo o vê, decisão 15). As listas de palavras aguardam revisão (decisão 7).
- **RF011:** o número é obrigatório para psicólogo, normalizado e único, mas **não é validado**. `is_validate` e `validate_time` nunca são alterados em `src/main` (só `InstantRoundTripTest` chama `setValidateTime`). Faltam consulta ao conselho (CFP/CFM), fluxo de aprovação e qualquer efeito de `is_validate` no login ou nas permissões; um psicólogo não validado entra como qualquer outro e **lê triagens como qualquer outro** (`TriageReadControllerTest#psychologistReadsListAndDetailWithoutBeingValidated`; decisão 13 em `docs/decisoes-para-revisao.md`). `GET /me` mostra `is_validated` ao psicólogo, sempre `false` hoje, porque nada em `src/main` o altera (só testes, como `MeControllerTest`, o ligam). Os testes acima cobrem só o que existe (guardar, normalizar, rejeitar duplicado ou vazio).
- **RNF001:** o repositório é só backend, sem interface. Usabilidade aqui se resume à API (erros padronizados em todos os casos, contrato OpenAPI versionado). A usabilidade da interface depende do front-end.
- **RNF002:** autenticação, hash de senha, CORS e limite de tentativas existem e têm teste. **HTTPS não está configurado**: sem `server.ssl`, redirecionamento ou HSTS próprio, e o proxy que terminaria o TLS não está no repositório. Nenhum teste cobre HTTPS.
- **RNF003:** senha com BCrypt, token sem dado pessoal, respostas e logs sem detalhe interno e triagem sempre pelo id do token. Há trilha de auditoria (`audit_log`, só INSERT, gravada na mesma transação): `TRIAGE_SUBMITTED` e `TRIAGE_RESUBMITTED` já são gravadas, sem respostas, nome ou e-mail, o admin consulta em `GET /admin/audit` e o dependente vê em `GET /me/access-log` quando e por qual papel sua triagem foi lida. A leitura da triagem por admin ou psicólogo (`GET /triages`, `GET /triages/{id}`) grava `TRIAGE_VIEWED` na mesma transação, uma entrada por triagem devolvida e uma nova a cada leitura, e o `access-log` já mostra essas entradas ao dependente (testado de ponta a ponta). O consentimento LGPD existe (decisão 16): o cadastro exige o aceite dos termos e da política em vigor e `POST /me/consents` aceita uma versão nova, mas não há revogação e quem não aceitou não é bloqueado. Exclusão (`DELETE /me`, decisão 9) e exportação (`GET /me/export`, decisão 18) dos dados do titular existem e têm teste. Faltam a retenção do próprio log (que guarda o id do titular mesmo depois da exclusão) e a criptografia dos dados de saúde (triagem, data de nascimento, gênero). O dependente não lê triagem de ninguém, nem a própria (403 nas duas rotas de leitura). Qualquer psicólogo, validado ou não, lê qualquer triagem; isso espera revisão (decisão 13).
- **RNF006:** o JWT stateless permite várias instâncias, mas o limitador de taxa vive na memória de cada JVM (com N instâncias o limite efetivo sobe e reinicia a cada deploy), o pool Hikari está no padrão e o único cache (`CacheConfig`, só as perguntas da triagem) também vive na memória de cada JVM; ele é seguro porque as perguntas só mudam por migração, ou seja, num deploy novo. Nenhum teste cobre a escala em si.

### Fora do escopo

- **RF004:** depende da classificação de risco do RF003, que o time deixou de fora. Se a classificação entrar no escopo, os dois requisitos mudam de situação juntos.

### Não iniciado (o que falta)

- **RF005, RF006, RF012:** modelo de reunião (o enum `status_reuniao` está órfão), endpoints e a sala em si. RF006 e RF012 dependem de RF005.
- **RF008, RF009, RF010:** nenhuma entidade, endpoint ou migração de conteúdo, relato ou comunidade. RF009 e RF010 dependem de uma decisão de anonimato.
- **RNF004:** a imagem, as probes de liveness/readiness e o compose existem e têm teste, e o perfil `prod` exige as variáveis do banco. Faltam deploy de fato (nenhum ambiente de produção no repositório), monitoramento e alertas, mais de uma instância (o limitador de taxa vive na memória de cada JVM, ver RNF006) e HTTPS (ver RNF002). O CI (`.github/workflows/ci.yml`, em PR e em push para `main` e `develop`) compila, testa e constrói a imagem (job `docker-build`), sem publicá-la nem subir a API. Nenhuma medida de disponibilidade existe.
- **RNF005:** faltam timeouts de pool e de consulta (tudo no padrão, 30 s no Hikari) e um teste de latência ou carga.
- **RNF007:** falta tudo (provedor de vídeo ou WebRTC, sinalização, monitoramento de qualidade); depende de RF005.
