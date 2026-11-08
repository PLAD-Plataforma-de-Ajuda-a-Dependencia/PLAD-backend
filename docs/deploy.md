# Deploy

Como subir a API em contêiner. O perfil `prod` não tem valor padrão para banco nem para o segredo do JWT: falta uma variável, a aplicação não sobe.

## Variáveis de ambiente

| Variável | Obrigatória | Para que serve |
|---|---|---|
| `DB_URL` | sim (prod) | URL JDBC, ex.: `jdbc:postgresql://host:5432/banco` |
| `DB_USER` | sim (prod) | usuário do banco |
| `DB_PASSWORD` | sim (prod) | senha do banco |
| `JWT_SECRET` | sim | segredo do JWT, no mínimo 32 bytes (`openssl rand -base64 48`). Vazio, curto demais ou o valor de exemplo antigo (`troque-por-...`) abortam a subida com `JWT_SECRET ausente ou com menos de 32 bytes`, sem imprimir o valor |
| `LIVEKIT_URL` | sim (prod) | endereço WebSocket do LiveKit que o navegador abre, `wss://...`. Em prod `ws://` é recusado (uma página https não abre `ws://`). Fora de prod o padrão é `ws://localhost:7880` (`livekit-server --dev`) |
| `LIVEKIT_API_KEY` | sim | chave de API do LiveKit (na LiveKit Cloud: projeto → Settings → API Keys). Vazia ou o valor de exemplo (`troque-por-...`) abortam a subida |
| `LIVEKIT_API_SECRET` | sim | segredo de API do LiveKit, no mínimo 32 bytes (o mesmo par de Settings → API Keys). Assina o token de entrada na sala e autentica as chamadas ao LiveKit |
| `MEETING_IDENTITY_SECRET` | sim | chave **só da PLAD**, no mínimo 32 bytes (`openssl rand -base64 48`), que gera a identidade opaca de cada pessoa em cada sala. Nunca sai do backend: o LiveKit não a conhece. Trocá-la muda as identidades de todas as salas abertas |
| `CORS_ALLOWED_ORIGINS` | não, mas defina em prod | origens do front, separadas por vírgula, sem `*`. O padrão é `http://localhost:5173` |
| `ADMIN_EMAIL` + `ADMIN_PASSWORD` | não | criam o primeiro admin na subida. As duas ou nenhuma |
| `TRUSTED_PROXIES` | não | proxies em que a API confia para ler o IP do cliente (seção abaixo) |
| `JAVA_OPTS` | não | opções da JVM. Padrão da imagem: `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` |

Se `DB_URL`, `DB_USER`, `DB_PASSWORD`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` ou `MEETING_IDENTITY_SECRET` faltarem com o perfil `prod`, `ProdEnvironmentCheck` aborta a subida listando os nomes que faltam. `LIVEKIT_API_SECRET` e `MEETING_IDENTITY_SECRET` curtos demais ou com o valor de exemplo abortam com o nome da variável, sem imprimir o valor.

## Sala de reunião ao vivo (LiveKit)

O vídeo roda no LiveKit (Cloud ou próprio); a API só decide quem entra e quem fala. `POST /meetings/{id}/join` assina, localmente, um token para a sala `plad-meeting-{id}` e devolve com o endereço `LIVEKIT_URL`. A moderação (liberar ou cortar microfone e câmera, remover) e o fechamento da sala ao cancelar ou encerrar a reunião chamam a API do LiveKit, com prazo de 6 s por chamada; se ele não responde, a rota dá 503 `ROOM_UNAVAILABLE` com `Retry-After` e nada muda do lado da PLAD.

- A API precisa alcançar o LiveKit pela rede (saída HTTPS para o host de `LIVEKIT_URL`: o SDK troca `wss://` por `https://` sozinho). O navegador precisa alcançar o mesmo endereço por WebSocket.
- Em desenvolvimento: `livekit-server --dev` (chave `devkey`, segredo `secret`) serve de LiveKit local, mas o segredo de 6 caracteres é recusado pela API; gere um par próprio com `livekit-server --keys "chave: segredo-com-32-bytes-ou-mais"`.
- A janela de entrada vai de 10 minutos antes do início até o fim da reunião. O token vale **2 minutos** desde que é emitido (não acompanha o fim da reunião): o LiveKit só o confere na primeira conexão e renova o de quem já está conectado, e **não invalida o token de quem foi removido**. Por isso o front pede um token novo em `POST /meetings/{id}/join` a cada entrada e nunca o guarda; quem foi removido leva 403 nesse pedido e só consegue conectar direto no LiveKit pelos minutos que restam do token que já tinha.
- A mão levantada não usa metadados: o token do dependente não tem `canUpdateOwnMetadata`, e o front a manda como mensagem de dados ao psicólogo. Nada a configurar no LiveKit para isso.

**Extensão do banco:** a migração V25 (reuniões) executa `CREATE EXTENSION IF NOT EXISTS btree_gist`, que impede duas reuniões agendadas do mesmo psicólogo de se cruzarem. O usuário de `DB_USER` precisa poder criá-la: `btree_gist` é extensão confiável desde o PostgreSQL 13, então o dono do banco basta; em serviços gerenciados que bloqueiam isso, crie a extensão antes com um superusuário (`CREATE EXTENSION btree_gist;`) e a migração só confere que ela existe.

## Perfis

- sem perfil: valores locais (`localhost:5432/dbtest`, `postgres`/`postgres`), docs fechadas. É o que os testes e o `./mvnw spring-boot:run` usam.
- `dev`: Swagger UI ligada e log `DEBUG` do pacote `plad`.
- `prod`: banco só por variável, docs fechadas. A imagem já sobe com `SPRING_PROFILES_ACTIVE=prod`. O devtools não vai no jar.

## Imagem

`Dockerfile` em dois estágios: build com Maven + JDK 25 (sem rodar os testes, que precisam de Docker e rodam no CI) e runtime com JRE 25 (`eclipse-temurin:25-jre-alpine`), usuário não-root, porta 8080.

```
docker build -t plad-api .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/banco -e DB_USER=... -e DB_PASSWORD=... \
  -e JWT_SECRET=... -e CORS_ALLOWED_ORIGINS=https://app.exemplo.com plad-api
```

## docker compose

`docker compose up` sobe o banco (`pladDB`) e a API (`pladAPI`, porta 8080 só em `127.0.0.1`). A API espera o banco ficar saudável e só fica `healthy` quando a readiness responde `UP`. `DB_PASSWORD`, `JWT_SECRET` e as variáveis da sala ao vivo (`LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `MEETING_IDENTITY_SECRET`) são obrigatórios: copie `.env.example` para `.env` (ignorado pelo git) e preencha o `JWT_SECRET` com um valor gerado (os valores de exemplo `troque-por-...` são recusados na subida); a URL e o par de chaves do LiveKit vêm do projeto na LiveKit Cloud (Settings → API Keys) e o `MEETING_IDENTITY_SECRET` também sai de `openssl rand -base64 48`. Variáveis exportadas no shell têm prioridade sobre o `.env`.

```
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
# edite o .env: JWT_SECRET=$(openssl rand -base64 48)
docker compose up -d --build
curl localhost:8080/actuator/health/readiness   # {"status":"UP"}
```

Para só o banco (rodando a API pelo Maven): `docker compose up -d db`.

Variáveis opcionais como `TRUSTED_PROXIES` ou `JAVA_OPTS` entram por um `docker-compose.override.yml` (não versionado) com `services.api.environment`.

## Sessão: access token e cookie de refresh

O login devolve no corpo um JWT de 15 minutos (o front o guarda só em memória) e define o cookie `refresh_token` (7 dias, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/auth`). `POST /auth/refresh` troca o cookie por um par novo; `POST /auth/logout` o revoga.

- **Front e API no mesmo site.** `SameSite=Strict` faz o navegador não enviar o cookie em requisições entre sites. `app.exemplo.com` e `api.exemplo.com` são o mesmo site (a porta não conta, então `localhost:5173` e `localhost:8080` também); `exemplo.com` e `outro.com` não são, e o refresh nunca funcionaria.
- **`CORS_ALLOWED_ORIGINS` precisa ter a origem exata do front** (esquema + host + porta). `/auth/refresh` e `/auth/logout` respondem 403 a qualquer `Origin` fora da lista, ou sem `Origin`. O front deve chamar login, refresh e logout com `credentials: 'include'`.
- **HTTPS.** O cookie é `Secure`: em HTTP puro o navegador não o guarda (exceto em `localhost` no Chrome e no Firefox). Para desenvolver em HTTP fora de `localhost`, `AUTH_REFRESH_COOKIE_SECURE=false`; nunca em produção.
- **Limpeza.** Todo dia às 03:00 (America/Sao_Paulo) um job apaga refresh tokens expirados ou revogados há mais de 7 dias (`auth.refresh-token.cleanup-retention-days`) e loga quantos apagou. Com mais de uma instância, o job roda em cada uma; apagar é idempotente.
- **Derrubar todas as sessões de um usuário**: `UserSessionService#revokeAllSessions`. Ele incrementa `users.token_version`, o que invalida os access tokens, e revoga os refresh tokens. É o que `PUT /me/password` chama ao trocar a senha (a sessão que trocou recebe um par novo; as outras caem). Esse endpoint responde com `Set-Cookie`, então o front deve chamá-lo com `credentials: 'include'`, como login, refresh e logout.

## Health

Só duas rotas respondem sem token, e sem detalhes:

- `GET /actuator/health/liveness`: a JVM está de pé. Não olha o banco, para que um banco fora do ar não faça a plataforma reiniciar a API em loop.
- `GET /actuator/health/readiness`: a API pode receber tráfego. Inclui o banco; com ele fora do ar responde 503 `{"status":"DOWN"}`.

`/actuator/health` e qualquer outra rota do actuator exigem token (401). Só o endpoint `health` está exposto.

## TRUSTED_PROXIES

A API limita `POST /auth/login`, `POST /auth/register` e `POST /auth/refresh` por IP do cliente (5 por minuto) e grava o IP junto do aceite dos termos. O Tomcat só lê o IP real de `X-Forwarded-For` quando quem abriu a conexão TCP está em `TRUSTED_PROXIES` (lista de CIDRs); em qualquer outro caso vale o IP da conexão.

- **O padrão serve** quando a API só é acessível pelo proxy da plataforma. O padrão cobre loopback e as faixas privadas (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `100.64.0.0/10`, `::1/128`, `fc00::/7`, `fe80::/10`), onde costuma ficar o proxy interno de uma plataforma de hospedagem (confira o da sua) e a rede do compose.
- **Com CDN na frente** (ex.: Cloudflare), o último salto até a API passa a ser a borda da CDN, que tem IP público. Acrescente as faixas publicadas por ela (Cloudflare: https://www.cloudflare.com/ips/). O valor de `TRUSTED_PROXIES` **substitui** o padrão, então mantenha também as faixas privadas do proxy interno:

  ```
  TRUSTED_PROXIES=127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7,173.245.48.0/20,103.21.244.0/22,...
  ```

  Sem isso a API não confia no `X-Forwarded-For` da CDN: todos os usuários caem no IP da CDN e **dividem o mesmo limite de 5 por minuto**, e o IP gravado no aceite deixa de ser o do usuário. Quem erra o login derruba o login de todo mundo.
- **Não alargue demais.** Confiar numa faixa pública que não é do seu proxy deixa qualquer cliente forjar `X-Forwarded-For` e fugir do limite de taxa. Liste só as faixas do que realmente fica na frente.
- A API também deve ficar inacessível por fora do proxy/CDN (firewall ou rede privada); do contrário, quem chama direto não passa pelo proxy e o IP vale o da conexão.
- As faixas das CDNs mudam: revise a lista quando o provedor publicar mudanças.
