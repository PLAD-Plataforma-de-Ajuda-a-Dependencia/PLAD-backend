# Deploy

Como subir a API em contêiner. O perfil `prod` não tem valor padrão para banco nem para o segredo do JWT: falta uma variável, a aplicação não sobe.

## Variáveis de ambiente

| Variável | Obrigatória | Para que serve |
|---|---|---|
| `DB_URL` | sim (prod) | URL JDBC, ex.: `jdbc:postgresql://host:5432/banco` |
| `DB_USER` | sim (prod) | usuário do banco |
| `DB_PASSWORD` | sim (prod) | senha do banco |
| `JWT_SECRET` | sim | segredo do JWT, no mínimo 32 bytes (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | não, mas defina em prod | origens do front, separadas por vírgula, sem `*`. O padrão é `http://localhost:5173` |
| `ADMIN_EMAIL` + `ADMIN_PASSWORD` | não | criam o primeiro admin na subida. As duas ou nenhuma |
| `TRUSTED_PROXIES` | não | proxies em que a API confia para ler o IP do cliente (seção abaixo) |
| `JAVA_OPTS` | não | opções da JVM. Padrão da imagem: `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` |

Se `DB_URL`, `DB_USER` ou `DB_PASSWORD` faltarem com o perfil `prod`, `ProdEnvironmentCheck` aborta a subida listando os nomes que faltam.

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

`docker compose up` sobe o banco (`pladDB`) e a API (`pladAPI`, porta 8080 só em `127.0.0.1`). A API espera o banco ficar saudável e só fica `healthy` quando a readiness responde `UP`. `DB_PASSWORD` e `JWT_SECRET` são obrigatórios: copie `.env.example` para `.env` (ignorado pelo git) e troque o `JWT_SECRET`. Variáveis exportadas no shell têm prioridade sobre o `.env`.

```
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
# edite o .env: JWT_SECRET=$(openssl rand -base64 48)
docker compose up -d --build
curl localhost:8080/actuator/health/readiness   # {"status":"UP"}
```

Para só o banco (rodando a API pelo Maven): `docker compose up -d db`.

Variáveis opcionais como `TRUSTED_PROXIES` ou `JAVA_OPTS` entram por um `docker-compose.override.yml` (não versionado) com `services.api.environment`.

## Health

Só duas rotas respondem sem token, e sem detalhes:

- `GET /actuator/health/liveness`: a JVM está de pé. Não olha o banco, para que um banco fora do ar não faça a plataforma reiniciar a API em loop.
- `GET /actuator/health/readiness`: a API pode receber tráfego. Inclui o banco; com ele fora do ar responde 503 `{"status":"DOWN"}`.

`/actuator/health` e qualquer outra rota do actuator exigem token (401). Só o endpoint `health` está exposto.

## TRUSTED_PROXIES

A API limita `POST /auth/login` e `POST /auth/register` por IP do cliente (5 por minuto) e grava o IP junto do aceite dos termos. O Tomcat só lê o IP real de `X-Forwarded-For` quando quem abriu a conexão TCP está em `TRUSTED_PROXIES` (lista de CIDRs); em qualquer outro caso vale o IP da conexão.

- **O padrão serve** quando a API só é acessível pelo proxy da plataforma. O padrão cobre loopback e as faixas privadas (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `100.64.0.0/10`, `::1/128`, `fc00::/7`, `fe80::/10`), onde costuma ficar o proxy interno de uma plataforma de hospedagem (confira o da sua) e a rede do compose.
- **Com CDN na frente** (ex.: Cloudflare), o último salto até a API passa a ser a borda da CDN, que tem IP público. Acrescente as faixas publicadas por ela (Cloudflare: https://www.cloudflare.com/ips/). O valor de `TRUSTED_PROXIES` **substitui** o padrão, então mantenha também as faixas privadas do proxy interno:

  ```
  TRUSTED_PROXIES=127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7,173.245.48.0/20,103.21.244.0/22,...
  ```

  Sem isso a API não confia no `X-Forwarded-For` da CDN: todos os usuários caem no IP da CDN e **dividem o mesmo limite de 5 por minuto**, e o IP gravado no aceite deixa de ser o do usuário. Quem erra o login derruba o login de todo mundo.
- **Não alargue demais.** Confiar numa faixa pública que não é do seu proxy deixa qualquer cliente forjar `X-Forwarded-For` e fugir do limite de taxa. Liste só as faixas do que realmente fica na frente.
- A API também deve ficar inacessível por fora do proxy/CDN (firewall ou rede privada); do contrário, quem chama direto não passa pelo proxy e o IP vale o da conexão.
- As faixas das CDNs mudam: revise a lista quando o provedor publicar mudanças.
