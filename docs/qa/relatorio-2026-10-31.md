# Relatório de testes da PLAD — 2026-10-31

Escopo: backend em `develop` (commit `7ac2ee9`, HEAD limpo; as alterações não commitadas do seu working tree **não** entraram) e frontend na branch `feature` (`d4d5f85`). Tudo o que foi criado está na branch `test/qa-suite` dos dois repositórios.

Todo número deste relatório foi conferido contra os arquivos gerados em `docs/qa/2026-10-31/` (ver "Como conferir" no fim).

## 1. Quadro resumo

| Suíte | Total | Passou | Falhou | Cobertura / Mutation score |
|---|---:|---:|---:|---|
| Regressão backend (`./mvnw -B clean verify`, 39 classes, inclui `OpenApiContractTest`) | 265 | 265 | 0 | — |
| JaCoCo 0.8.16 | — | — | — | linhas 875/908 (**96,4%**), branches 213/244 (**87,3%**) |
| PIT 1.30.1 (services, validation, config, controllers) | 306 mutantes | 285 mortos | 17 sobreviveram, 4 sem cobertura | **93,1%** (test strength 94%) |
| E2E Playwright (API + UI do front) | 37 | 35 | 1 falha nova + 1 falha conhecida | — |
| Exploratório no Chrome (4e) | ver seção 6 | | | parcial: faltam GIF e 375 px |

Resumo dos achados:

- **1 bug novo no backend**: texto livre com 1000 emojis volta 400 (`INVALID_TRIAGE`); o esperado era 201.
- **1 falha conhecida** (marcada): psicólogo recém-cadastrado lê `/triages` (200), mas a regra do projeto pede 403 até a validação.
- **Pontos fracos de teste** reais: 6 (seção 4.1), cobrindo 7 dos 21 mutantes não mortos; os outros 14 são equivalentes ou falsos sobreviventes (seção 4.2).
- **Front**: 11 quebras ou incômodos (seção 5), nenhum bloqueante. Os mais relevantes são mensagens do back em inglês, `/adminPsico` e `/Calendario` com dados fictícios acessíveis a qualquer usuário logado e a falta de logout.

## 2. Regressão e cobertura

### 2.1 Regressão (log do `mvn verify` e XML do surefire, removidos do repositório; veja "Como conferir")

BUILD SUCCESS: 265 testes, 0 falhas, 0 erros, 0 ignorados, 39 classes. Os `ERROR` no log vêm de testes que exercitam o handler do 500 (`GlobalExceptionHandlerTest`, `ServiceUnavailableAndTraceTest`): ruído esperado.

| Classe | Testes | Passou | Falhou |
|---|---:|---:|---:|
| config.AdminAccessTest | 8 | 8 | 0 |
| config.AdminBootstrapRunnerTest | 3 | 3 | 0 |
| config.AdminBootstrapRunnerWiringTest | 1 | 1 | 0 |
| config.CorsConfigTest | 3 | 3 | 0 |
| config.ForwardedClientIpTrustedProxyTest | 2 | 2 | 0 |
| config.ForwardedClientIpUntrustedPeerTest | 1 | 1 | 0 |
| config.GlobalExceptionHandlerTest | 6 | 6 | 0 |
| config.JwtAuthenticationFilterTest | 8 | 8 | 0 |
| config.OpenApiConfigTest | 1 | 1 | 0 |
| **config.OpenApiContractTest** (regressão do contrato) | 5 | 5 | 0 |
| config.OpenApiDisabledTest | 2 | 2 | 0 |
| config.RequestIdFilterTest | 5 | 5 | 0 |
| config.SecurityErrorHandlerTest | 3 | 3 | 0 |
| config.ServiceUnavailableAndTraceTest | 7 | 7 | 0 |
| config.TestDataRunner | 1 | 1 | 0 |
| controllers.AdminAuditControllerTest | 9 | 9 | 0 |
| controllers.AuthControllerTest | 15 | 15 | 0 |
| controllers.LoginControllerTest | 12 | 12 | 0 |
| controllers.MeAccessLogTest | 7 | 7 | 0 |
| controllers.MeControllerTest | 9 | 9 | 0 |
| controllers.RegisterMinAgeTest | 7 | 7 | 0 |
| controllers.TriageAuditTest | 4 | 4 | 0 |
| controllers.TriageControllerTest | 28 | 28 | 0 |
| controllers.TriageReadControllerTest | 13 | 13 | 0 |
| exception.ErrorCodeTest | 1 | 1 | 0 |
| PladApplicationTests | 1 | 1 | 0 |
| repositories.AuditLogTriggerTest | 4 | 4 | 0 |
| repositories.InstantRoundTripTest | 3 | 3 | 0 |
| repositories.TriageRepositoryTest | 6 | 6 | 0 |
| services.AdminBootstrapServiceTest | 6 | 6 | 0 |
| services.AuditServiceTest | 7 | 7 | 0 |
| services.AuditServiceTransactionTest | 1 | 1 | 0 |
| services.AuthServiceTest | 6 | 6 | 0 |
| services.CrmCrpNumbersTest | 4 | 4 | 0 |
| services.JwtServiceTest | 11 | 11 | 0 |
| services.RateLimiterServiceTest | 7 | 7 | 0 |
| services.TriageServiceTest | 33 | 33 | 0 |
| validation.MaxBytesValidatorTest | 4 | 4 | 0 |
| validation.MinAgeValidatorTest | 11 | 11 | 0 |

`docs/openapi.json` está em dia com o contrato gerado: não rodei `-Dopenapi.update=true`.

Observação de ambiente: só há JDK 26.0.2. O parent do Boot compila com `release=25` e não precisou de flag extra.

### 2.2 Cobertura (JaCoCo, sem alterar o pom)

Duas tentativas, ambas registradas:

1. **Como pedido** (`jacoco:prepare-agent verify jacoco:report`; log da tentativa A, removido do repositório): o `prepare-agent` define a propriedade `argLine`, mas o `maven-surefire-plugin` do HEAD fixa `<argLine>-Duser.timezone=…</argLine>` sem `@{argLine}`. O agente nunca chega à JVM dos testes, não gera `jacoco.exec` e o `report` pula com "missing execution data file". Esse é o motivo pelo qual o pom modificado no seu working tree tem `@{argLine}`.
2. **Alternativa sem tocar no pom** (log da tentativa B, removido do repositório): `JAVA_TOOL_OPTIONS=-javaagent:org.jacoco.agent-0.8.16-runtime.jar=destfile=…,includes=plad.*` + `jacoco:report`. Funcionou com JDK 26 (73 classes analisadas, 265 testes).

Por pacote (do `jacoco.csv` da execução, removido do repositório):

| Pacote | Classes | Linhas | Branches | Instruções |
|---|---:|---|---|---:|
| plad | 1 | 1/3 (33,3%) | — | 37,5% |
| plad.config | 10 | 236/241 (97,9%) | 60/82 (73,2%) | 98,1% |
| plad.controllers | 6 | 33/33 (100%) | — | 100% |
| plad.dto | 17 | 33/33 (100%) | 2/4 (50,0%) | 99,0% |
| plad.entity | 12 | 141/165 (85,5%) | — | 87,5% |
| plad.exception | 10 | 36/36 (100%) | — | 100% |
| plad.services | 15 | 372/374 (99,5%) | 135/140 (96,4%) | 98,8% |
| plad.validation | 2 | 23/23 (100%) | 16/18 (88,9%) | 100% |
| **Total** | 73 | **875/908 (96,4%)** | **213/244 (87,3%)** | 97,4% |

As 10 classes com menor cobertura de linhas:

| # | Classe | Linhas | Branches | Instruções |
|---:|---|---|---|---:|
| 1 | plad.PladApplication | 1/3 (33,3%) | sem branches | 37,5% |
| 2 | plad.entity.Dependent | 9/13 (69,2%) | sem branches | 64,3% |
| 3 | plad.entity.QuestionTriage | 21/28 (75,0%) | sem branches | 77,3% |
| 4 | plad.entity.ResultQuestionTriage | 10/13 (76,9%) | sem branches | 77,4% |
| 5 | plad.entity.Psychologist | 19/23 (82,6%) | sem branches | 80,8% |
| 6 | plad.entity.Triage | 10/12 (83,3%) | sem branches | 85,7% |
| 7 | plad.entity.User | 24/28 (85,7%) | sem branches | 84,1% |
| 8 | plad.config.OpenApiConfig | 71/74 (95,9%) | 24/38 (63,2%) | 96,8% |
| 9 | plad.config.SecurityConfig | 38/39 (97,4%) | 4/6 (66,7%) | 97,2% |
| 10 | plad.services.TriageReadService | 42/43 (97,7%) | 1/2 (50,0%) | 97,1% |

Leitura: as classes 1 a 7 são o `main` e getters/setters/`equals` das entidades (baixo risco). As que merecem atenção são as de branches: `OpenApiConfig` (63%) e `SecurityConfig` (67%).

## 3. Mutação (PIT)

- `org.pitest:pitest-maven:1.30.1` + `pitest-junit5-plugin:1.2.3`, perfil `mutation` no `pom.xml` desta branch (o pom do `develop` não foi alterado).
- Alvo: `plad.services.*`, `plad.validation.*`, `plad.config.*`, `plad.controllers.*`. 4 threads (metade dos 8 núcleos).
- O escopo completo coube no limite: terminou em **cerca de 30 min** (< 40 min), então **não foi preciso reduzir** para só services e validation. A execução dividiu a CPU com os testes E2E e o Chrome, então o tempo é um limite superior.
- `withHistory` foi removido do perfil: no PIT 1.30 o histórico exige um plugin à parte (arcmutate) e o build falhava.
- Cobertura de linhas das classes mutadas: 655/666 (98%).

| Pacote | Mutantes | Mortos | Sobreviveram | Sem cobertura | **Mutation score** |
|---|---:|---:|---:|---:|---:|
| plad.services | 162 | 154 | 5 | 3 | **95,1%** |
| plad.validation | 17 | 17 | 0 | 0 | **100%** |
| plad.config | 115 | 102 | 12 | 1 | **88,7%** |
| plad.controllers | 12 | 12 | 0 | 0 | **100%** |
| **Total** | 306 | 285 | 17 | 4 | **93,1%** |

Os 21 mutantes não mortos estão em `pit-summary.txt`; o relatório HTML do PIT não foi versionado (veja "Como conferir").

## 4. Pontos fracos de teste (mutantes que indicam teste fraco)

### 4.1 Teste fraco de verdade (pedem teste novo)

| # | Mutante | O que falta testar |
|---|---|---|
| 1 | `AuthService.register` L87: removida a chamada `psychologist.setGraduate(...)` sobreviveu | Os testes enviam `"graduate": "Psicologia"` (`AuthControllerTest:221`, `RegisterMinAgeTest:174`), mas **nunca conferem que foi gravado**. Se o campo parasse de ser salvo, nada falharia. |
| 2 | `MeService.findMe` L31 (usuário do token não existe mais): **sem cobertura** | Não há teste de `GET /me` com token de usuário removido. O `CLAUDE.md` documenta 401 `INVALID_CREDENTIALS` para esse caso, e nada o protege. |
| 3 | `TriageReadService.actorRole` L76 (leitor não existe mais): **sem cobertura** | Mesmo caso, agora em `/triages`. |
| 4 | `AdminBootstrapService.createIfAbsent` L47 e L48: `<`→`<=` e `>`→`>=` sobrevivem | Faltam testes de **fronteira** da senha do admin: exatamente 8 caracteres (deve aceitar) e exatamente 72 bytes (deve aceitar). Hoje só se testa "curta" e "acima de 72". |
| 5 | `RateLimiterService.evictIdleBuckets` L64: `>=`→`>` sobrevive | O método já recebe `nowNanos` como parâmetro, então dá para testar o instante exato do período de refill. Teste barato. |
| 6 | `GlobalExceptionHandler.reasonPhrase` L198: negação sobrevive | Os testes conferem `code` dos erros mapeados pelo Spring (405, 415…), mas não o `message`. Com o mutante, todo `message` vira "Request failed" e nada falha. |

### 4.2 Equivalentes ou não confiáveis (ignorados, com o motivo)

| Mutante | Por que ignorei |
|---|---|
| **`OpenApiConfig`**: L62, L69-70, L113 e as lambdas `internalServerErrorResponse` (L143, L149) e `serviceUnavailableResponse` (L157, L166), 10 sobreviventes + 1 sem cobertura | **Falsos sobreviventes do PIT, não teste fraco.** Esse código roda uma vez, na criação do contexto Spring / geração do documento springdoc, que fica em cache entre os testes; o PIT muta a classe depois. **Verifiquei à mão** o mutante de L143 (inverti a condição de `containsKey("500")`): `OpenApiContractTest` falha numa JVM nova (log da verificação manual, removido do repositório). Os demais seguem o mesmo mecanismo, mas só L143 foi comprovado; L69-70 (`sort` das tags) pode ser equivalente neste SO porque a ordem de varredura já sai ordenada. |
| `RateLimiterService.refund` L51: removido `lastSeenNanos.set(...)` | Equivalente na prática: o `refund` ocorre logo após o `consume` (que já atualizou o `lastSeen`) e a evicção só age após um período de refill inteiro (minutos). Só um relógio injetável distinguiria. |
| `SecurityErrorHandler.write` L47: removido `setCharacterEncoding(UTF-8)` | Equivalente: o corpo vai por `getOutputStream()` já em UTF-8 e as mensagens dos handlers são ASCII ("Access denied"); só mudaria o parâmetro `charset` do `Content-Type`. |
| `MeService.findMe` L36 (psicólogo sem linha em `psychologists`): sem cobertura | Ramo defensivo inalcançável: `@MapsId` + FK impedem o estado. Não vale teste. |

## 5. Falhas dos testes E2E

### 5.1 Falha nova — texto livre com 1000 emojis volta 400 (esperado 201)

- Teste: `e2e/api.spec.ts` › "texto livre com 1000 emojis: 201".
- Resposta real: `400 {"code":"INVALID_TRIAGE","message":"Question 10 text exceeds 1000 characters",…}`.
- Causa provável: `TriageService.java:198` compara `text.length() > MAX_FREE_TEXT_LENGTH`. `String.length()` conta unidades UTF-16, e cada emoji ocupa 2 (1000 emojis = 2000). O limite documentado é "1000 caracteres". Correção sugerida: `text.codePointCount(0, text.length())`.
- O front tem o mesmo critério (`maxLength={1000}` do `<textarea>` também é em unidades UTF-16), então ele já impede mais de ~500 emojis e **mascara** o problema na tela. Só a API direta o expõe.
- Gravidade: baixa (borda de entrada), mas é divergência entre o que a regra diz e o que o código faz.

### 5.2 Falha conhecida — psicólogo recém-cadastrado lê `/triages` (marcada)

- Teste: "FALHA CONHECIDA: psicólogo recém-cadastrado recebe 403 em /triages", marcado com `test.fail()`. Na suíte ele conta como esperado; quando a validação for implementada o Playwright passa a acusar que a marca deve ser removida.
- Hoje: 200. Causa: `TriageReadController` usa `hasAnyRole('ADMIN','PSICOLOGO')` e o `CLAUDE.md` registra que `is_validate` "não participa". `is_validated` já aparece em `/me`, sempre `false`.
- Efeito: qualquer pessoa que se cadastre como psicólogo lê as triagens, que são dados de saúde (LGPD art. 11). Cada leitura fica auditada, mas a barreira não existe.

### 5.3 O que passou

Todos os itens da lista do 4c passaram: cadastro (adulto 201; 18 amanhã 400 em `birth_date`; 18 hoje 201; `admin` 400; e-mail em maiúsculas 409; CRP `06/n` e `06 / n` 400; psicólogo sem registro 400), login (mesma resposta para senha errada e e-mail inexistente; 6ª tentativa 429 com `Retry-After`), `/me` dos dois papéis, 401 em JSON, `X-Request-Id` (devolve o enviado; gera UUID para valor inválido), perguntas sem `AGE_UNDER_18`, triagem (201, 409, PUT 200, vazia 204 sem gravar, PUT vazio 400 mantendo a triagem, `AGE_UNDER_18` 400, psicólogo 403), `/triages` (dependente 403, admin 200 sem `real_name`/e-mail, psicólogo 200), auditoria (`TRIAGE_VIEWED` em `/admin/audit` e em `/me/access-log` sem `actor_id`; psicólogo 403) e Swagger UI. Mais 13 testes de UI (cadastro dependente e psicólogo, menor de 18, e-mail repetido, formulário vazio, login com senha errada, triagem completa, 409 → substituir, 3 rotas protegidas, token vencido, rota inexistente).

Interpretação: "sem `actor_id`" apliquei à visão do dependente (`/me/access-log`, que mostra só o papel). `/admin/audit` **inclui** `actor_id` por desenho (o admin vê quem leu).

## 6. Quebras no front (exploratório no Chrome, item 4e)

Ambiente: front em `http://localhost:5173` apontando para o **meu** backend em `:8081`. A porta 8080 já estava ocupada por um backend seu (PID 19656, aberto às 18:51 pelo IDE), que **não toquei**; `VITE_API_URL` foi sobrescrita só no ambiente do processo do Vite, sem alterar `.env` nem código. Console: nas leituras feitas (home, cadastro, login, triagem, `/adminPsico`, `/Calendario`) não houve erro nem warning, só `[vite]` e a dica do React DevTools. Rede: nenhuma falha de CORS, timeout ou 5xx; os únicos 4xx foram os esperados dos testes (400 do cadastro de menor, 401 de senha errada, 403 do psicólogo na triagem).

Onde o token fica: **`localStorage`, chave `plad.token`**. `sessionStorage` vazio. O backend não envia `Set-Cookie` (conferido com `curl -i` no login). O JavaScript fica com acesso ao token (exposto a XSS); é uma escolha de projeto, anotada aqui.

| # | Tela | Passo para reproduzir | O que aconteceu | Erro de console/rede | Gravidade | Print |
|---:|---|---|---|---|---|---|
| 1 | `/criar-conta?tipo=dependente` | Preencher com data de nascimento de 17 anos e enviar | Mostra `birth_date: must be at least 18 years old`: texto em inglês e com o nome técnico do campo. O front lê só `message` e ignora `code` e `errors[]` | `POST /auth/register` 400 `VALIDATION_ERROR` | atrapalha | `front/07-cadastro-menor-de-18-erro-do-back-em-ingles.jpg` |
| 2 | `/login` | Senha errada | `Invalid email or password` em inglês numa UI em português | `POST /auth/login` 401 `INVALID_CREDENTIALS` | atrapalha | `front/09-login-senha-errada-invalid-email-or-password.jpg` |
| 3 | `/Triagem` (psicólogo ou admin) | Entrar como psicólogo, responder/pular e Concluir | O login sempre leva à triagem, que é de dependente. Ao enviar: `Only dependents can answer the triage` (inglês). Psicólogo e admin não têm tela própria | `POST /triage` 403 `FORBIDDEN` | atrapalha | `front/17-psicologo-triagem-only-dependents-can-answer.jpg`, `front/20-admin-apos-login-vai-para-triagem.jpg` |
| 4 | `/adminPsico` | Logado como psicólogo, abrir `/adminPsico` | "Painel do administrador" abre para qualquer usuário logado (`RotaProtegida` não olha o papel). Lista **dados fictícios** fixos no código (Ana Beatriz Souza, CRP 06/123456…), com Aprovar/Rejeitar que não chamam o back | nenhum | atrapalha (risco de acesso: o back não é afetado) | `front/18-adminpsico-dados-ficticios-psicologo.jpg` |
| 5 | `/Calendario` | Logado com qualquer usuário | Mostra "Livre de álcool há 27 dias", "R$ 180" e "você e 4.956 outros" para um usuário recém-criado. São valores fixos no código, que parecem reais | nenhum | atrapalha | `front/19-calendario-dados-ficticios.jpg` |
| 6 | `/Triagem` | Responder 1 pergunta, voltar do navegador, avançar | **Voltar** sai da triagem e cai em `/criar-conta` (a escolha de perfil, mesmo logado). **Avançar** recarrega a triagem **do início**, com as respostas perdidas e sem aviso. Não há rota por pergunta | nenhum | atrapalha | `front/12-voltar-do-navegador-sai-da-triagem.jpg`, `front/13-avancar-do-navegador-recomeca-triagem.jpg` |
| 7 | Todas | Procurar "sair" | **Não existe logout** em nenhuma tela (`grep` no `src/`: só o `clearToken()` automático do 401). Para sair é preciso apagar o storage na mão | nenhum | atrapalha | — (verificado no código) |
| 8 | `/login` e `/criar-conta` | Abrir com sessão ativa | Não redireciona quem já está logado: o usuário vê o formulário de novo | nenhum | visual | — (verificado no código; a tela de perfil abriu logada ao voltar do navegador, print 12) |
| 9 | `/Calendario` (menu lateral) | Clicar em "Reunião" ou "Comunidade" | `/reuniao` e `/comunidade` não têm rota e caem em `*` → home. (O `CLAUDE.md` do front cita `/painel`, mas "Painel" já aponta para `/calendario`) | nenhum | visual | — (verificado no código; não cliquei no navegador) |
| 10 | `/login` | Clicar em "Esqueceu a senha?" | O link aponta para `#esqueceu-senha`, uma âncora sem destino: nada acontece | nenhum | visual | `front/08-login-conta-criada.jpg` |
| 11 | `/criar-conta?tipo=dependente` | Janela de 634 px de altura | O botão "Criar conta" fica abaixo da dobra: o rodapé não é fixo e é preciso rolar | nenhum | visual | `front/03-cadastro-dependente-vazio.jpg` |

Observações (não são quebras):

- **Funcionou como esperado**: formulário vazio, e-mail inválido e senha curta são barrados pelo navegador, sem chamar a API (prints `04`, `05`, `06`); sem token ou token vencido redireciona para `/login` (`16`, `21`); duplo clique em "Criar conta" e em "Concluir" gerou **uma** requisição (1 `POST /auth/register`, 1 `POST /triage`); a triagem completa envia e mostra "Triagem enviada!" (`15`).
- O CORS libera só `Authorization` e `Content-Type` e **não expõe** `Retry-After` nem `X-Request-Id` (sem `Access-Control-Expose-Headers`). O front não consegue ler o tempo de espera do 429 (a mensagem fixa "Aguarde um minuto" funciona por sorte) nem mostrar o id de suporte do erro 500.
- `GET /triage/questions` é chamado 2 vezes por carga da tela. É o StrictMode do React no `npm run dev`; deve sumir no build.
- O `CLAUDE.md` do front diz que erros vêm como `{message, timestamp}`; o backend atual devolve `{code, message, timestamp, errors[], trace}`.
- O backend registra no startup `Using generated security password` (usuário padrão do Spring Security, sem uso). Só informativo.

### Pendente

- **GIF** do fluxo cadastro → login → triagem: o fluxo rodou inteiro no Chrome e os prints `02`, `03`, `07`–`10`, `14` e `15` mostram cada etapa, mas os frames da gravação se perderam quando uma chamada em lote deu timeout. Ao tentar regravar, o Chrome travou de novo (`Page.captureScreenshot` expirou em 30 s, como no início da sessão: janela provavelmente em segundo plano). **Não há GIF.**
- **Largura de celular (375 px)**: **não executado**, pelo mesmo motivo.
- Telas vistas por papel: dependente (home, perfil, cadastro, login, triagem, calendário), psicólogo (triagem, `/adminPsico`, `/Calendario`) e admin (login e triagem). O admin não abriu `/adminPsico` e `/Calendario`; são os mesmos componentes que o psicólogo viu.

## 7. Recomendações (em ordem)

1. **Backend**: contar code points no limite de texto livre (`TriageService:198`) e ajustar o `maxLength` do `<textarea>`.
2. **Backend**: implementar a validação de psicólogo e trocar a regra de `/triages`; o teste marcado como conhecido passa a ser a trava.
3. **Testes**: adicionar os 6 testes da seção 4.1 (o mais valioso é o de `graduate` gravado e o do token de usuário removido em `/me` e `/triages`).
4. **Front**: traduzir/mapear `code` e `errors[]` (hoje só lê `message`); expor `Retry-After` e `X-Request-Id` no CORS; adicionar logout; separar o pós-login por papel e proteger `/adminPsico` por papel; remover ou rotular os dados fictícios de `/Calendario` e `/adminPsico`.
5. **Mutação**: se o PIT virar rotina, excluir `OpenApiConfig` ou rodá-lo com JVM nova por mutante, para não gerar falsos sobreviventes.

## Como conferir

Os relatórios gerados (HTML do JaCoCo, PIT e Playwright, XML do surefire, logs do Maven) saíram do repositório; só ficam `numeros-verificados.txt`, `pit-summary.txt` e as screenshots em `front/`.

| Número | Onde está |
|---|---|
| 265 testes / 39 classes | não versionado; `./mvnw verify` regenera o XML em `target/surefire-reports/` |
| Cobertura | não versionado; o HTML é publicado como artifact `jacoco-report` do job `build` no CI (14 dias) e `./mvnw verify` o regenera em `target/site/jacoco/` |
| Mutação | `pit-summary.txt` (versionado); o HTML/XML completo do PIT não foi mantido |
| Playwright | não versionado; regenere com o comando abaixo. As screenshots citadas ficam em `front/` |
| Cálculos | `numeros-verificados.txt` (gerado por script a partir dos arquivos acima) |

Os testes Playwright estão no repositório do front, branch `test/qa-suite`: `e2e/api.spec.ts`, `e2e/ui.spec.ts`, `e2e/helpers.ts`, `playwright.config.ts`. Para reproduzir: backend com perfil dev e `ADMIN_EMAIL`/`ADMIN_PASSWORD`; `API_URL=http://localhost:<porta> VITE_API_URL=http://localhost:<porta> npx playwright test`. O Playwright usa o Edge (`channel: 'msedge'`) porque não há Chrome instalado no caminho padrão; `PW_CHANNEL=chrome` troca.

Dados de teste: os testes criaram contas `*@plad.dev` no banco `dbtest` (`pladDB`) e entradas em `audit_log` (append-only, não removíveis). O backend de teste foi iniciado em `:8081` e o Vite em `:5173`; ambos foram deixados rodando para a retomada do exploratório.
