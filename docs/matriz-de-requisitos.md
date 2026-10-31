# Matriz de requisitos

Rastreabilidade entre os requisitos do levantamento formal, o código que os implementa e os testes que os comprovam.
Conferida em 2026-10-29, no commit `0232182` (branch `develop`).

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
| Parcial | 6 | RF003, RF011, RNF001, RNF002, RNF003, RNF006 |
| Não iniciado | 10 | RF005, RF006, RF007, RF008, RF009, RF010, RF012, RNF004, RNF005, RNF007 |
| Fora do escopo | 1 | RF004 |
| **Total** | **19** | 12 RFs + 7 RNFs |

## Requisitos funcionais

| Requisito | Descrição curta | Situação | Onde está no código | Teste que comprova |
|---|---|---|---|---|
| RF001 | Cadastro de usuário com dados mínimos | Testado | `AuthController#register` → `AuthService#register`; `RegisterRequestDTO`; `MinAge`/`MinAgeValidator`; `MaxBytes`; `CrmCrpNumbers`; `Emails`; migrações V1–V3, V7, V13 | `AuthControllerTest#registerDependentStoresLowercasedEmail`<br>`AuthControllerTest#registerPsychologistIsCreated`<br>`AuthControllerTest#sameEmailWithDifferentCaseIsConflict`<br>`RegisterMinAgeTest#turning18TodayIsCreated`<br>`RegisterMinAgeTest#turning18TomorrowIsBadRequest`<br>`RegisterMinAgeTest#bornOnLeapDayIsAnAdultOnlyFromMarchFirstInANonLeapYear` |
| RF002 | Login e autenticação (JWT) | Testado | `LoginController#login` → `LoginService#login`; `JwtService`; `JwtAuthenticationFilter`; `SecurityConfig#securityFilterChain`; `RateLimitInterceptor`/`RateLimiterService` | `LoginControllerTest#loginReturnsTokenThatAuthenticatesRequests`<br>`LoginControllerTest#wrongPasswordAndUnknownEmailGetTheSameAnswer`<br>`LoginControllerTest#sixthAttemptFromSameClientIsTooManyRequests`<br>`LoginControllerTest#eleventhFailedAttemptOnSameEmailIsTooManyRequestsEvenFromDifferentClients`<br>`JwtAuthenticationFilterTest#missingTokenIsUnauthorized` |
| RF003 | Triagem inicial | Parcial | `TriageController` (`GET /triage/questions`, `POST /triage`, `PUT /triage`); `TriageService`; entidades `Triage`, `QuestionTriage`, `ResultQuestionTriage`; 10 perguntas em V5, V9, V11, V12, V15 (todas opcionais); respostas em V4, V6, V14 | `TriageControllerTest#getQuestionsReturnsSeededQuestionsInSnakeCase`<br>`TriageControllerTest#submitCreatesTriageAndSecondSubmitIsConflict`<br>`TriageControllerTest#submitAsPsychologistIsForbidden`<br>`TriageControllerTest#resubmitReplacesAnswersAndKeepsTriageId`<br>`TriageControllerTest#submitWithEmptyAnswersStoresNothingAndDoesNotUseTheSingleSubmission`<br>`TriageControllerTest#resubmitWithEmptyAnswersIsBadRequestAndKeepsTheTriage` |
| RF004 | Redirecionamento por risco | Fora do escopo | Não existe | — |
| RF005 | Participação em reuniões online | Não iniciado | Só o tipo enum `status_reuniao` em V1, sem tabela, entidade ou endpoint | — |
| RF006 | Moderação de reuniões | Não iniciado | Não existe | — |
| RF007 | Anonimato nas reuniões | Não iniciado | Não existe | — |
| RF008 | Publicação de conteúdos | Não iniciado | Não existe | — |
| RF009 | Relatos anônimos | Não iniciado | Não existe | — |
| RF010 | Aba comunidade | Não iniciado | Não existe | — |
| RF011 | Validação de psicólogos (CRM/CRP) | Parcial | `AuthService#register` (exige e normaliza o número); `CrmCrpNumbers#normalize`; `Psychologist` (`isValidate`, `validateTime`); V2, V13 | `AuthControllerTest#credentialNumberDifferingOnlyInFormattingIsRejected`<br>`AuthControllerTest#psychologistWithoutCredentialsIsBadRequest`<br>`AuthControllerTest#psychologistWithBlankCredentialNumberIsBadRequest` |
| RF012 | Gerenciamento de reuniões | Não iniciado | Não existe | — |

## Requisitos não funcionais

| Requisito | Descrição curta | Situação | Onde está no código | Teste que comprova |
|---|---|---|---|---|
| RNF001 | Usabilidade | Parcial | `GlobalExceptionHandler`/`ErrorResponseDTO` (erro padronizado); `OpenApiConfig` e `docs/openapi.json` (contrato); `http/plad.http` | `OpenApiContractTest#apiDocsMatchCommittedSnapshot` |
| RNF002 | Segurança (HTTPS) | Parcial | `SecurityConfig` (BCrypt, JWT stateless, CORS por origens explícitas); `RateLimiterService`; `application.properties` (`server.forward-headers-strategy`, `TRUSTED_PROXIES`, docs fechadas por padrão) | `JwtAuthenticationFilterTest#malformedTokenIsUnauthorized`<br>`CorsConfigTest#preflightFromDisallowedOriginIsBlocked`<br>`OpenApiDisabledTest#apiDocsRequireAuthenticationWhenDisabled` |
| RNF003 | Privacidade e confidencialidade | Parcial | `SecurityConfig#passwordEncoder` (BCrypt); `JwtService` (token só com `userId` e papel); `TriageController` (usuário vem do token); `GlobalExceptionHandler` (sem detalhe interno na resposta ou no log); `UserResponseDTO`; trilha de auditoria: `V17__create_audit_log.sql` (só INSERT), `AuditService`, `AdminAuditController` (`GET /admin/audit`), `MeController` (`GET /me/access-log`) | `LoginControllerTest#wrongPasswordAndUnknownEmailGetTheSameAnswer`<br>`ServiceUnavailableAndTraceTest#unexpectedFailureReturnsTheTraceThatAppearsInTheLog`<br>`AuditLogTriggerTest#updateIsRejected`<br>`TriageAuditTest#submitRecordsTriageSubmittedWithIdsAndRoleOnly`<br>`TriageAuditTest#detailsNeverHoldAnswersNameOrEmail`<br>`AdminAuditControllerTest#dependentGetsForbidden`<br>`MeAccessLogTest#entriesShowTheRoleAndNeverWhoReadIt` |
| RNF004 | Disponibilidade 24/7 | Não iniciado | Só `GlobalExceptionHandler` (503 com `Retry-After` se o banco cai) e `restart: unless-stopped` do Postgres em `docker-compose.yml` | — |
| RNF005 | Resposta em até 2 s | Não iniciado | Nada configurado; `spring.jpa.open-in-view=false` em `application.properties` | — |
| RNF006 | Escalabilidade | Parcial | `SecurityConfig` (`SessionCreationPolicy.STATELESS`); `RateLimiterService` (em memória, por JVM) | — |
| RNF007 | Estabilidade da videochamada | Não iniciado | Não existe | — |

## Notas

### Parcial

- **RF003:** a coleta de respostas existe (perguntas, validação por tipo, todas opcionais, `POST` sem nenhuma resposta devolve 204 e não salva a triagem, uma triagem por dependente, reenvio por `PUT` que exige ao menos uma resposta). A **classificação de risco está fora do escopo, por decisão do time**: nenhum campo, score ou lógica lê as respostas, então a triagem só guarda e devolve o que o dependente respondeu. Também não há `GET /triage` para ler a triagem de volta; só `GET /triage/questions`.
- **RF011:** o número é obrigatório para psicólogo, normalizado e único, mas **não é validado**. `is_validate` e `validate_time` nunca são alterados em `src/main` (só `InstantRoundTripTest` chama `setValidateTime`). Faltam consulta ao conselho (CFP/CFM), fluxo de aprovação e qualquer efeito de `is_validate` no login ou nas permissões; um psicólogo não validado entra como qualquer outro. Os testes acima cobrem só o que existe (guardar, normalizar, rejeitar duplicado ou vazio).
- **RNF001:** o repositório é só backend, sem interface. Usabilidade aqui se resume à API (erros padronizados em todos os casos, contrato OpenAPI versionado). A usabilidade da interface depende do front-end.
- **RNF002:** autenticação, hash de senha, CORS e limite de tentativas existem e têm teste. **HTTPS não está configurado**: sem `server.ssl`, redirecionamento ou HSTS próprio, e o proxy que terminaria o TLS não está no repositório. Nenhum teste cobre HTTPS.
- **RNF003:** senha com BCrypt, token sem dado pessoal, respostas e logs sem detalhe interno e triagem sempre pelo id do token. Há trilha de auditoria (`audit_log`, só INSERT, gravada na mesma transação): `TRIAGE_SUBMITTED` e `TRIAGE_RESUBMITTED` já são gravadas, sem respostas, nome ou e-mail, o admin consulta em `GET /admin/audit` e o dependente vê em `GET /me/access-log` quando e por qual papel sua triagem foi lida. Como nenhum endpoint lê a triagem de terceiros, `TRIAGE_READ` ainda não é gravada e o `access-log` só é exercitado com entradas inseridas pelo teste; a leitura por psicólogo ou admin, quando existir, precisa chamar o `AuditService`. Faltam itens de LGPD (consentimento, exportação e exclusão de dados, retenção do próprio log) e criptografia dos dados de saúde (triagem, data de nascimento, gênero). Não há teste de que um usuário não lê dados de outro, porque nenhum endpoint expõe dados de terceiros.
- **RNF006:** o JWT stateless permite várias instâncias, mas o limitador de taxa vive na memória de cada JVM (com N instâncias o limite efetivo sobe e reinicia a cada deploy), o pool Hikari está no padrão e não há cache. Nenhum teste cobre isso.

### Fora do escopo

- **RF004:** depende da classificação de risco do RF003, que o time deixou de fora. Se a classificação entrar no escopo, os dois requisitos mudam de situação juntos.

### Não iniciado (o que falta)

- **RF005, RF006, RF007, RF012:** modelo de reunião (o enum `status_reuniao` está órfão), endpoints, papéis (hoje o JWT não traz authorities) e a sala em si. RF006 e RF012 dependem de RF005. RF007 também exige decidir como o anonimato convive com o cadastro, que hoje pede nome real.
- **RF008, RF009, RF010:** nenhuma entidade, endpoint ou migração de conteúdo, relato ou comunidade. RF009 e RF010 dependem de uma decisão de anonimato.
- **RNF004:** faltam Dockerfile, health/readiness (sem actuator), deploy e monitoramento. O CI (`.github/workflows/ci.yml`) só compila e testa.
- **RNF005:** faltam timeouts de pool e de consulta (tudo no padrão, 30 s no Hikari) e um teste de latência ou carga.
- **RNF007:** falta tudo (provedor de vídeo ou WebRTC, sinalização, monitoramento de qualidade); depende de RF005.
