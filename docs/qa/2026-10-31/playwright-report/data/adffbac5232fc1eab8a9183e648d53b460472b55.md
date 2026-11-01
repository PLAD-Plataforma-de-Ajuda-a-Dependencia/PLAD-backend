# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: api.spec.ts >> /triages e auditoria >> FALHA CONHECIDA: psicólogo recém-cadastrado recebe 403 em /triages
- Location: e2e\api.spec.ts:259:5

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 403
Received: 200
```

# Test source

```ts
  163 |         expect(empty.status()).toBe(204)
  164 |         const me = await (await request.get(`${API}/me`, { headers: bearer(token) })).json()
  165 |         expect(me.has_triage).toBe(false)
  166 |         const real = await request.post(`${API}/triage`, { data: { answers: [firstChoiceAnswer(await questions(request, token))] }, headers: bearer(token) })
  167 |         expect(real.status()).toBe(201)
  168 |     })
  169 | 
  170 |     test('PUT vazio: 400 INVALID_TRIAGE e a triagem guardada permanece', async ({ request }) => {
  171 |         const { token } = await newSession(request, dependente())
  172 |         await request.post(`${API}/triage`, { data: { answers: [firstChoiceAnswer(await questions(request, token))] }, headers: bearer(token) })
  173 |         const put = await request.put(`${API}/triage`, { data: { answers: [] }, headers: bearer(token) })
  174 |         expect(put.status()).toBe(400)
  175 |         expect((await put.json()).code).toBe('INVALID_TRIAGE')
  176 |         expect((await (await request.get(`${API}/me`, { headers: bearer(token) })).json()).has_triage).toBe(true)
  177 |     })
  178 | 
  179 |     test('AGE_UNDER_18 como resposta: 400', async ({ request }) => {
  180 |         const { token } = await newSession(request, dependente())
  181 |         const qs = await questions(request, token)
  182 |         const age = qs.find((q) => q.options.some((o) => o.code.startsWith('AGE_'))) ?? qs.find((q) => q.question_type === 'single_choice')!
  183 |         const res = await request.post(`${API}/triage`, { data: { answers: [{ question_id: age.id, values: ['AGE_UNDER_18'] }] }, headers: bearer(token) })
  184 |         expect(res.status()).toBe(400)
  185 |         expect((await res.json()).code).toBe('INVALID_TRIAGE')
  186 |     })
  187 | 
  188 |     test('texto livre com 1000 emojis: 201', async ({ request }) => {
  189 |         const { token } = await newSession(request, dependente())
  190 |         const free = (await questions(request, token)).find((q) => q.question_type === 'free_text')!
  191 |         const res = await request.post(`${API}/triage`, { data: { answers: [{ question_id: free.id, values: ['😀'.repeat(1000)] }] }, headers: bearer(token) })
  192 |         expect(res.status(), await res.text()).toBe(201)
  193 |     })
  194 | 
  195 |     test('psicólogo não envia triagem: 403', async ({ request }) => {
  196 |         const { token } = await newSession(request, psicologo())
  197 |         const res = await request.post(`${API}/triage`, { data: { answers: [] }, headers: bearer(token) })
  198 |         expect(res.status()).toBe(403)
  199 |         expect((await res.json()).code).toBe('FORBIDDEN')
  200 |     })
  201 | })
  202 | 
  203 | test.describe('/triages e auditoria', () => {
  204 |     test('dependente 403; admin 200 sem real_name nem e-mail; psicólogo 200', async ({ request }) => {
  205 |         const dep = await newSession(request, dependente())
  206 |         await request.post(`${API}/triage`, { data: { answers: [firstChoiceAnswer(await questions(request, dep.token))] }, headers: bearer(dep.token) })
  207 | 
  208 |         expect((await request.get(`${API}/triages`, { headers: bearer(dep.token) })).status()).toBe(403)
  209 | 
  210 |         const admin = await loginToken(request, ADMIN.email, ADMIN.password)
  211 |         const adminRes = await request.get(`${API}/triages?size=5`, { headers: bearer(admin) })
  212 |         expect(adminRes.status()).toBe(200)
  213 |         const raw = await adminRes.text()
  214 |         expect(raw).not.toContain('real_name')
  215 |         expect(raw).not.toContain(dep.user.email)
  216 |         expect(raw).not.toContain('"email"')
  217 |         expect(JSON.parse(raw).items.length).toBeGreaterThan(0)
  218 | 
  219 |         const psi = await newSession(request, psicologo())
  220 |         expect((await request.get(`${API}/triages`, { headers: bearer(psi.token) })).status()).toBe(200)
  221 |     })
  222 | 
  223 |     test('TRIAGE_VIEWED em /admin/audit e em /me/access-log (sem actor_id); psicólogo 403 em /admin/audit', async ({ request }) => {
  224 |         const dep = await newSession(request, dependente())
  225 |         const posted = await (await request.post(`${API}/triage`, { data: { answers: [firstChoiceAnswer(await questions(request, dep.token))] }, headers: bearer(dep.token) })).json()
  226 |         const depId = (await (await request.get(`${API}/me`, { headers: bearer(dep.token) })).json()).id
  227 | 
  228 |         const admin = await loginToken(request, ADMIN.email, ADMIN.password)
  229 |         const read = await request.get(`${API}/triages/${posted.id}`, { headers: bearer(admin) })
  230 |         expect(read.status()).toBe(200)
  231 | 
  232 |         const audit = await request.get(`${API}/admin/audit?subject_user_id=${depId}&action=TRIAGE_VIEWED`, { headers: bearer(admin) })
  233 |         expect(audit.status()).toBe(200)
  234 |         const entries = (await audit.json()).items
  235 |         expect(entries).toHaveLength(1)
  236 |         expect(entries[0]).toMatchObject({ action: 'TRIAGE_VIEWED', subject_user_id: depId, entity_id: posted.id })
  237 |         expect(entries[0].details.actor_role).toBe('admin')
  238 | 
  239 |         // a visão do titular nunca revela quem leu (LGPD art. 18): só o papel
  240 |         const log = await request.get(`${API}/me/access-log`, { headers: bearer(dep.token) })
  241 |         expect(log.status()).toBe(200)
  242 |         const logBody = await log.json()
  243 |         const items = logBody.items ?? logBody
  244 |         expect(items.length).toBeGreaterThanOrEqual(1)
  245 |         expect(items[0].action).toBe('TRIAGE_VIEWED')
  246 |         expect(items[0].actor_role).toBe('admin')
  247 |         expect(JSON.stringify(logBody)).not.toContain('actor_id')
  248 | 
  249 |         const psi = await newSession(request, psicologo())
  250 |         const denied = await request.get(`${API}/admin/audit`, { headers: bearer(psi.token) })
  251 |         expect(denied.status()).toBe(403)
  252 |         expect((await denied.json()).code).toBe('FORBIDDEN')
  253 |     })
  254 | 
  255 |     // Regra do projeto: só psicólogo VALIDADO lê triagem. A validação ainda não existe (is_validate nunca vira true
  256 |     // e TriageReadController libera qualquer psicólogo), então hoje o back devolve 200. test.fail() mantém a suíte
  257 |     // verde enquanto a falha é conhecida; quando a regra for implementada o teste "passa" e o Playwright acusa
  258 |     // que a marca deve ser removida.
  259 |     test('FALHA CONHECIDA: psicólogo recém-cadastrado recebe 403 em /triages', async ({ request }) => {
  260 |         test.fail(true, 'Falha conhecida: a validação de psicólogo ainda não existe; hoje qualquer psicólogo lê triagens (200)')
  261 |         const psi = await newSession(request, psicologo())
  262 |         const res = await request.get(`${API}/triages`, { headers: bearer(psi.token) })
> 263 |         expect(res.status()).toBe(403)
      |                              ^ Error: expect(received).toBe(expected) // Object.is equality
  264 |     })
  265 | })
  266 | 
  267 | test.describe('documentação', () => {
  268 |     test('Swagger UI abre em /swagger-ui/index.html', async ({ request }) => {
  269 |         const res = await request.get(`${API}/swagger-ui/index.html`, { headers: { 'X-Forwarded-For': nextIp() } })
  270 |         expect(res.status()).toBe(200)
  271 |         expect(res.headers()['content-type']).toContain('text/html')
  272 |         expect(await res.text()).toContain('swagger-ui')
  273 |     })
  274 | })
  275 | 
```