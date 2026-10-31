# Decisões para revisão

Decisões de domínio que o time tomou e que o orientador ou um psicólogo do projeto precisa revisar. Cada linha diz o que o sistema faz hoje, onde isso está no código e qual teste falha se alguém mudar a decisão.
Conferida em 2026-10-31, sobre o commit `a0ecd2e` (branch `develop`) mais a mudança de papéis de acesso, a trilha de auditoria e o `GET /me`, ainda sem commit.

**Regra de manutenção:** toda regra de domínio nova entra nesta tabela no mesmo PR, com o teste que a fixa. Mudar uma decisão é mudar o teste: o teste e a linha da tabela mudam juntos.

## Situações

| Situação | Significado |
|---|---|
| Aguardando revisão | Está implementada e o time a adotou, mas ninguém de fora do time a revisou. |
| Aprovada por X em AAAA-MM-DD | O revisor (orientador ou psicólogo) aprovou. Preencher com o nome e a data da aprovação. |
| Pendente de implementação | Não existe código. A decisão ainda precisa ser tomada, ou foi tomada e falta fazer. |

## Quadro por situação

| Situação | Qtd | Decisões |
|---|---:|---|
| Aguardando revisão | 10 | 1, 2, 3, 4, 5, 6, 8, 10, 11, 12 |
| Aprovada | 0 | — |
| Pendente de implementação | 2 | 7, 9 |
| **Total** | **12** | |

## Decisões

| # | Tema | O que o sistema faz | Fundamento | Onde no código | Teste que fixa a decisão | Situação |
|---|---|---|---|---|---|---|
| 1 | Classificação de risco da triagem fora do escopo (RF003/RF004) | A triagem só guarda e devolve o que o dependente respondeu. Nenhum campo, pontuação ou lógica lê as respostas, e não há redirecionamento por risco. | Decisão do time. O motivo não está registrado (ver nota 1). | `TriageService`; `TriageResponseDTO` (sem campo de risco); `docs/matriz-de-requisitos.md` (RF003, RF004) | Nenhum (ver Lacunas) | Aguardando revisão |
| 2 | Cadastro só para maiores de 18; "hoje" em America/Sao_Paulo; 29/02 completa anos em 01/03 | `birth_date` precisa dar pelo menos 18 anos na data de hoje no fuso de São Paulo, para dependente e psicólogo. Quem nasceu em 29/02 completa 18 em 01/03 nos anos não bissextos. Data futura e idade acima de 120 também dão 400 `VALIDATION_ERROR`. | Código Civil, art. 132, § 3º (29/02). Para o 18, ver nota 2. | `RegisterRequestDTO#birthDate` (`@MinAge(18)`); `MinAge`; `MinAgeValidator`; `ClockConfig` | `RegisterMinAgeTest#turning18TodayIsCreated`<br>`RegisterMinAgeTest#turning18TomorrowIsBadRequest`<br>`RegisterMinAgeTest#bornOnLeapDayIsAnAdultOnlyFromMarchFirstInANonLeapYear`<br>`RegisterMinAgeTest#eveOfTheBirthdayAt2330InSaoPauloIsBadRequestEvenThoughItIsTheBirthdayInUtc`<br>`RegisterMinAgeTest#psychologistUnder18IsBadRequestToo`<br>`MinAgeValidatorTest#bornOnLeapDayTurns18OnMarchFirstInANonLeapYear` | Aguardando revisão |
| 3 | Opção "Menos de 18 anos" removida da pergunta de faixa etária | A pergunta `AGE_RANGE` não tem mais a opção `AGE_UNDER_18`. Enviar esse código dá 400 `INVALID_TRIAGE`. As respostas antigas com esse código foram mantidas. | Consequência da decisão 2: a plataforma é só para maiores de 18. | `V12__remove_age_under_18_option.sql` | `TriageControllerTest#submitWithTheRemovedUnder18OptionIsBadRequest` | Aguardando revisão |
| 4 | Uma triagem por dependente; o reenvio substitui todas as respostas | `POST /triage` cria a triagem; uma segunda vez dá 409 `TRIAGE_ALREADY_EXISTS`. `PUT /triage` apaga todas as respostas e grava as novas, mantendo a mesma triagem. Se o reenvio for inválido, as respostas antigas ficam. | Decisão do time. Fundamento clínico não registrado. | `TriageService#submit`, `#resubmit`; `Triage` (`dependent_id` único); `V4__create_triages.sql` | `TriageControllerTest#submitCreatesTriageAndSecondSubmitIsConflict`<br>`TriageControllerTest#resubmitReplacesAnswersAndKeepsTriageId`<br>`TriageControllerTest#resubmitWithPartialAnswersReplacesThemAll`<br>`TriageServiceTest#resubmitWithInvalidAnswerKeepsPreviousAnswers` | Aguardando revisão |
| 5 | Todas as perguntas são opcionais; texto livre até 1000 caracteres | As 10 perguntas têm `required = false`. `POST /triage` sem respostas dá 204 e não grava nada (nem a triagem, então o dependente ainda pode enviar depois). `PUT /triage` sem respostas dá 400 `INVALID_TRIAGE` e mantém a triagem. Texto livre: um valor, não vazio, no máximo 1000. | Decisão do time. **Diverge do que foi pedido para este documento** (ver nota 5). | `V15__make_all_triage_questions_optional.sql`; `QuestionTriage#required`; `TriageService#validateValues` (`MAX_FREE_TEXT_LENGTH`) | `TriageControllerTest#getQuestionsReturnsSeededQuestionsInSnakeCase` (`required` falso em todas)<br>`TriageControllerTest#submitWithEmptyAnswersStoresNothingAndDoesNotUseTheSingleSubmission`<br>`TriageControllerTest#resubmitWithEmptyAnswersIsBadRequestAndKeepsTheTriage`<br>`TriageServiceTest#submitAcceptsFreeTextAtMaxLength`<br>`TriageServiceTest#submitRejectsFreeTextOverMaxLength` | Aguardando revisão |
| 6 | Limites de login: 5 por minuto por IP; 10 falhas em 15 minutos por e-mail | Por IP: 5 requisições por minuto em `/auth/login` e, separado, em `/auth/register`. Por e-mail (sem diferenciar maiúsculas): 10 falhas em 15 minutos, vindas de qualquer IP; um login correto devolve a vaga. Ao estourar, 429 `RATE_LIMITED` com `Retry-After`, sem revelar se a senha estava certa. | Decisão do time contra tentativa de adivinhar senha. O número exato não tem fundamento registrado. | `RateLimitInterceptor`; `RateLimiterService`; `LoginService` | `LoginControllerTest#sixthAttemptFromSameClientIsTooManyRequests`<br>`LoginControllerTest#eleventhFailedAttemptOnSameEmailIsTooManyRequestsEvenFromDifferentClients`<br>`LoginControllerTest#successfulLoginsDoNotCountTowardsEmailLimit`<br>`RateLimiterServiceTest#allowsFiveRequestsThenRejectsWithRetryAfter`<br>`RateLimiterServiceTest#loginEmailKeysAllowTenThenReject` | Aguardando revisão |
| 7 | Apelido anônimo: formato, listas de palavras e limite de trocas | Não existe. Nenhum campo, endpoint ou migração de apelido. O cadastro pede o nome real. | A definir (ver nota 7). | — | — | Pendente de implementação |
| 8 | Opções de gênero | O cadastro exige `gender` com um destes valores: `masculino`, `feminino`, `nao_binario`, `transexual`, `outro`. Outro valor dá 400 `BAD_REQUEST`. | A lista veio do esquema inicial e não foi revisada (ver nota 8). | `V1__create_enums_n_users.sql` (enum `sex`); `Sex`; `RegisterRequestDTO#gender` | `AuthControllerTest#unknownEnumValueIsBadRequest` (só rejeita valor desconhecido; não afirma a lista) | Aguardando revisão |
| 9 | Exclusão de conta: apagar ou anonimizar | Não existe. Não há endpoint de exclusão nem regra de retenção. | A decidir (ver nota 9). | — (esquema em `V2`, `V3`, `V4`) | — | Pendente de implementação |
| 10 | Papéis de acesso e primeiro administrador | Há três papéis: `dependente`, `psicologo` e `admin`. O papel (`type_user`) vai na claim `role` do token e vira a authority `ROLE_<VALOR>`; `/admin/**` exige `ROLE_ADMIN` (403 para os outros, 401 sem token). Token sem a claim `role` (emitido antes desta mudança) ou com papel desconhecido é inválido: 401, e a pessoa entra de novo. `/auth/register` com `type_user = admin` dá 400 `INVALID_REGISTRATION`. O primeiro admin nasce na subida da aplicação a partir de `ADMIN_EMAIL` e `ADMIN_PASSWORD`, só se as duas existirem e o e-mail ainda não estiver cadastrado; conta existente nunca é promovida nem tem a senha trocada. Senha com menos de 8 caracteres ou mais de 72 bytes impede a aplicação de subir. O admin não tem linha em `dependents` nem em `psychologists`, e usa nome `Administrador`, nascimento `2000-01-01` e gênero `outro` porque `users` exige esses campos. | Decisão do time (ver nota 10). | `V16__add_admin_user_type.sql`; `UserType`; `JwtService`; `JwtAuthenticationFilter`; `SecurityConfig`; `GlobalExceptionHandler#handleAccessDenied`; `AuthService#register`; `AdminBootstrapService`; `AdminBootstrapRunner` | `AdminAccessTest#dependentGetsForbiddenOnAdminRoutes`<br>`AdminAccessTest#psychologistGetsForbiddenOnAdminRoutes`<br>`AdminAccessTest#adminReachesAdminRoutes`<br>`AdminAccessTest#dependentGetsForbiddenOnMethodSecuredRoute`<br>`JwtAuthenticationFilterTest#signedTokenWithoutRoleIsUnauthorized`<br>`AuthControllerTest#registeringAnAdminIsBadRequestAndCreatesNothing`<br>`AdminBootstrapServiceTest#createsAdminWithNormalizedEmailAndHashedPassword`<br>`AdminBootstrapServiceTest#existingEmailIsNeverTouched`<br>`AdminBootstrapServiceTest#shortPasswordStopsTheStartup`<br>`AdminBootstrapRunnerTest#createsTheAdminWithoutDependentOrPsychologistRow` | Aguardando revisão |
| 11 | Trilha de auditoria do dado de saúde: só INSERT, sem conteúdo, papel (não nome) para o titular | Cada envio e reenvio de triagem grava uma linha em `audit_log` (`TRIAGE_SUBMITTED`, `TRIAGE_RESUBMITTED`) na mesma transação; envio sem respostas (204), duplicado (409) ou inválido não grava. A tabela só aceita INSERT: o banco rejeita UPDATE, DELETE e TRUNCATE, e não há chave estrangeira para `users`, então o log sobrevive à exclusão de conta. `details` guarda só ids, números, indicadores e rótulos de estado (o papel de quem agiu vai em `actor_role`); resposta, nome, e-mail e texto livre são rejeitados. `GET /admin/audit` (só admin) lista com filtro e paginação. `GET /me/access-log` (só dependente) mostra quando um terceiro leu a triagem e com qual **papel**, nunca quem. | LGPD art. 11 (dado sensível de saúde) e art. 18 (transparência ao titular); RNF003. O modelo do log (só INSERT, papel em vez de nome) é decisão do time (ver nota 11). | `V17__create_audit_log.sql`; `AuditLog`; `AuditAction`; `AuditService`; `TriageService#auditTriageWrite`; `AdminAuditController`; `MeController` | `AuditLogTriggerTest#updateIsRejected`<br>`AuditLogTriggerTest#deleteIsRejected`<br>`AuditLogTriggerTest#truncateIsRejected`<br>`AuditLogTriggerTest#insertWorksAndSurvivesAUserThatDoesNotExist`<br>`TriageAuditTest#submitRecordsTriageSubmittedWithIdsAndRoleOnly`<br>`TriageAuditTest#resubmitRecordsTriageResubmitted`<br>`TriageAuditTest#nothingIsRecordedWhenNothingIsStored`<br>`TriageAuditTest#detailsNeverHoldAnswersNameOrEmail`<br>`AuditServiceTest#freeTextUnderAnyOtherKeyIsRejectedAndNotSaved`<br>`AuditServiceTransactionTest#recordOutsideATransactionFailsAndWritesNothing`<br>`AdminAuditControllerTest#dependentGetsForbidden`<br>`AdminAuditControllerTest#psychologistGetsForbidden`<br>`MeAccessLogTest#entriesShowTheRoleAndNeverWhoReadIt`<br>`MeAccessLogTest#psychologistAndAdminGetForbidden` | Aguardando revisão |
| 12 | `GET /me`: campos por tipo, nome real só para o psicólogo, tipo lido do banco | Qualquer usuário autenticado lê a própria conta (`id`, `email`, `type_user`, `created_at`). Dependente recebe também `has_triage`; psicólogo recebe `real_name`, `credential_type`, `crm_crp_number` e `is_validated`; admin só os campos comuns. Os campos que não se aplicam ao tipo vêm `null` (a chave sempre existe). O dependente não recebe o nome real. O usuário vem do token, nunca da requisição, e o tipo vem do banco, não da claim `role`. Token de usuário que não existe mais dá 401. A leitura da própria conta não grava auditoria. | Decisão do time: o front escolhe a tela inicial depois do login. Nome real fora da resposta do dependente por causa do anonimato previsto (decisão 7). A auditoria é só para leitura por terceiros (decisão 11). | `MeController#me`; `MeService#findMe`; `MeResponseDTO` | `MeControllerTest` (um teste por tipo, `idInTheRequestIsIgnored`, `withoutTokenIsUnauthorized`) | Aguardando revisão |

## Notas

1. **Risco (decisão 1).** A matriz só registra "por decisão do time". Falta o motivo (por exemplo, ausência de instrumento validado ou de profissional para classificar). Se a classificação entrar no escopo, RF003 e RF004 mudam de situação juntos.
   - *Pergunta ao revisor:* confirmar que a triagem pode apenas coletar respostas, sem classificar nem encaminhar.

2. **Maioridade (decisão 2).** O 29/02 tem fundamento legal citado. O limite de 18 anos é regra da plataforma; o Código Civil, art. 5º, põe o fim da menoridade aos 18 anos, e isso é uma sugestão de fundamento a confirmar, não uma decisão registrada pelo time.
   - Quem faz aniversário amanhã ainda é menor: às 23h30 de São Paulo, quando em UTC já é o dia seguinte, o cadastro é recusado.
   - O teto de 120 anos é só para barrar data absurda.
   - *Pergunta ao revisor:* o limite de 18 anos deve valer também para o psicólogo? Hoje vale.

3. **Faixa etária (decisão 3).** A migração mantém as respostas antigas com `AGE_UNDER_18` de propósito, para não apagar dado já coletado. Ela não vale para novos envios.

4. **Triagem única (decisão 4).** O reenvio é "tudo ou nada": quem manda só 2 respostas no `PUT` perde as outras. Não há histórico das versões anteriores.
   - *Pergunta ao revisor:* o dependente deve poder refazer a triagem quantas vezes quiser, e o histórico deve ser guardado?

5. **Perguntas opcionais (decisão 5).** O pedido original para este documento dizia "todas as perguntas são obrigatórias, menos o texto livre". O código faz o contrário: o commit `e754582` (migração V15) tornou todas opcionais, e os testes fixam isso. Esta linha registra o código. Se a regra pretendida for a do pedido, é preciso uma migração nova (a V15 não pode ser editada) e a mudança dos testes acima.
   - O limite de 1000 conta unidades de texto do Java (UTF-16), não caracteres visíveis: um emoji vale 2, então cabem cerca de 500 emojis. O banco aceita 1000 emojis (`TriageRepositoryTest#freeTextWith1000EmojisIsSaved`), mas o serviço não.
   - *Pergunta ao revisor:* o limite de 1000 deve contar caracteres visíveis? Deixar tudo opcional é aceitável do ponto de vista clínico?

6. **Limites de login (decisão 6).** Os baldes recarregam aos poucos, não em janela fixa: 5 por minuto por IP é uma vaga a cada 12 segundos; 10 em 15 minutos por e-mail é uma vaga a cada 90 segundos. O contador fica em memória, por instância: com várias instâncias o limite efetivo sobe e zera a cada deploy (ver RNF006 na matriz).
   - *Pergunta ao revisor:* um usuário legítimo bloqueado por 90 segundos após 10 erros é aceitável?

7. **Apelido anônimo (decisão 7).** Depende de RF007 e RF009, ainda não iniciados, e do conflito com o cadastro, que hoje pede nome real. Falta decidir:
   - o formato do apelido (por exemplo, adjetivo + animal + número);
   - as listas de palavras, que um psicólogo deve revisar para não incluir termos ofensivos, gatilhos ou que identifiquem alguém;
   - quantas trocas de apelido são permitidas e em qual período.

8. **Gênero (decisão 8).** Os cinco valores vieram do esquema inicial (V1). Sendo enum nativo do Postgres, mudar a lista exige migração nova e atualização de `docs/openapi.json`.
   - *Pergunta ao revisor:* a lista, incluindo `transexual` e `outro` sem campo livre, está adequada? O campo deve aceitar "prefiro não informar"?

9. **Exclusão de conta (decisão 9).** Hoje só `result_questions_triages.triage_id` tem `ON DELETE CASCADE`; `dependents`, `psychologists` e `triages` referenciam sem cascata. Opções:
   - *apagar*: remove usuário, subtipo, triagem e respostas, o que exige definir a ordem de exclusão ou cascatas novas;
   - *anonimizar*: mantém as linhas e remove os dados pessoais.
   - Decidir também o que fazer com conteúdo que a pessoa publicou, quando RF008 a RF010 existirem. Ligado à LGPD (RNF003 na matriz) e à retenção dos dados de saúde.

10. **Papéis de acesso (decisão 10).** O papel fica no token até ele expirar (24 h): se um dia existir troca de papel, o token antigo vale o papel velho até vencer. Os dados fixos do admin (`Administrador`, `2000-01-01`, `outro`) só preenchem colunas obrigatórias; não são dados de uma pessoa.
   - *Pergunta ao revisor:* o admin deve poder ver dados de triagem (dado de saúde)? Nenhum endpoint `/admin` lê triagem; o único é `GET /admin/audit`, que não devolve dado de saúde. Se o admin passar a ler triagem, o acesso precisa de regra própria e passa pelo `AuditService` (decisão 11).

11. **Trilha de auditoria (decisão 11).**
   - O papel de quem leu fica em `details.actor_role` (e não em coluna nem por consulta a `users`) para sobreviver à exclusão da conta. O titular vê o papel, não o nome (LGPD art. 18), então não consegue identificar o profissional; o admin vê o `actor_id`.
   - `TRIAGE_READ` existe mas nada a grava: ainda não há endpoint em que psicólogo ou admin leiam a triagem. Quem criar essa leitura deve chamar o `AuditService` na mesma transação (regra no `CLAUDE.md`).
   - Ler o log (`GET /admin/audit`) não é auditado, porque ele não devolve dado de saúde.
   - *Pergunta ao revisor:* por quanto tempo o log deve ser mantido? Hoje não há retenção nem expurgo (a tabela não aceita DELETE), e o log guarda o id do titular mesmo depois da exclusão da conta (decisão 9).

## Lacunas

Decisões sem um teste que as fixe. Enquanto isso, mudar a decisão não faz nenhum teste falhar.

- **Decisão 1:** nada impede que alguém adicione pontuação de risco. Um teste poderia afirmar que a resposta de triagem não tem campo de risco.
- **Decisão 8:** nenhum teste afirma a lista de valores do gênero. Um teste poderia comparar `Sex.values()` com os rótulos do enum do banco.
- **Decisões 7 e 9:** sem código. O teste entra no PR que implementar cada uma.
