# EP-01 — Fundação

> Setup do projeto, multi-tenancy, autenticação e controle de acesso.  
> Todas as features deste épico são **pré-requisito** para todos os demais.

---

## F-01 · Setup e Infraestrutura Base

**Prioridade:** Alta  
**RF relacionados:** —  
**Descrição:** Estrutura inicial do projeto, configurações globais e pipeline.

### Stories

- **S-01.1** Criar projeto Spring Boot 4.0.6 com estrutura feature-based clean architecture
  - Criar pacotes `shared/` e `feature/` conforme arquitetura definida
  - Configurar `application.yml` com profiles: `local`, `dev`, `prod`
  - Adicionar todas as dependências Maven definidas no planejamento

- **S-01.2** Configurar banco de dados e Flyway
  - Configurar datasource PostgreSQL por profile
  - Criar migration `V1__create_schema_base.sql` com tabelas: `organizations`, `users`, `roles`, `permissions`, `role_permissions`
  - Garantir que Flyway rode automaticamente no startup

- **S-01.3** Configurar Dockerfile e pipeline de build
  - Criar `Dockerfile` com `eclipse-temurin:26-jre-alpine`
  - Configurar GitHub Actions: build → test → push para Azure Container Registry
  - Expor `/actuator/health` como liveness/readiness probe

- **S-01.4** Configurar tratamento global de erros
  - Criar `GlobalExceptionHandler` com `@RestControllerAdvice`
  - Padronizar response body de erro: `{ "code", "message", "timestamp" }`
  - Mapear exceções de domínio para HTTP status codes

---

## F-02 · Gestão de Organizações

**Prioridade:** Alta  
**RF relacionados:** RF01, RF02  
**Descrição:** Cada organização é um tenant isolado. O onboarding cria a org e o primeiro admin.

### Stories

- **S-02.1** Criar entidade e migration de `organizations`
  - Campos: `id (UUID)`, `name`, `slug (único)`, `created_at`
  - Slug gerado automaticamente a partir do nome (ex: "Minha Empresa" → `minha-empresa`)

- **S-02.2** Implementar `TenantContextHolder`
  - `ThreadLocal<UUID>` populado pelo JWT filter a cada request
  - Todo repository recebe `organizationId` obrigatoriamente nas queries

- **S-02.3** Endpoint de criação de organização (super-admin / onboarding)
  - `POST /api/v1/organizations`
  - Cria org + primeiro usuário com role `ADMIN`
  - Retorna JWT de acesso já com `organizationId` no payload

---

## F-03 · Roles e Permissões

**Prioridade:** Alta  
**RF relacionados:** RF01, RF02, RF03, RF04, RF05  
**Descrição:** Sistema de roles configuráveis com permissões granulares por módulo. Roles padrão são globais (sem org); roles customizadas pertencem à organização.

### Modelo de dados

roles → id, organization_id (nullable), name, is_default, cloned_from_role_id
permissions → id, code, description, module
role_permissions → role_id, permission_id

### Roles padrão (seed)

| Role        | Permissões-chave                                             |
| ----------- | ------------------------------------------------------------ |
| `ADMIN`     | Todas                                                        |
| `TECH_LEAD` | `AZURE_READ`, `GITHUB_READ`, `AI_READ_ALL`, `DASHBOARD_TEAM`, `DASHBOARD_INDIVIDUAL`, `CONNECTOR_TOKEN_MANAGE_OWN` |
| `MANAGER`   | `AZURE_READ`, `GITHUB_READ`, `AI_READ_ALL`, `DASHBOARD_STRATEGIC` |
| `DEVELOPER` | `AI_READ_OWN`, `DASHBOARD_INDIVIDUAL`, `CONNECTOR_TOKEN_MANAGE_OWN` |

### Tabela de permissões

| Módulo    | Código                                                       |
| --------- | ------------------------------------------------------------ |
| Usuários  | `USER_MANAGE`                                                |
| Roles     | `ROLE_MANAGE`                                                |
| Azure     | `AZURE_CONFIGURE`, `AZURE_READ`                              |
| GitHub    | `GITHUB_CONFIGURE`, `GITHUB_READ`                            |
| IA        | `AI_READ_OWN`, `AI_READ_ALL`                                 |
| Dashboard | `DASHBOARD_INDIVIDUAL`, `DASHBOARD_TEAM`, `DASHBOARD_STRATEGIC` |
| Preços    | `PRICE_TABLE_MANAGE`                                         |
| Conector  | `CONNECTOR_TOKEN_MANAGE_OWN`, `CONNECTOR_TOKEN_MANAGE_ALL`   |

### Stories

- **S-03.1** Criar migration `V2__roles_permissions.sql`
  - Tabelas `roles`, `permissions`, `role_permissions`
  - Seeds das roles padrão e todas as permissões

- **S-03.2** Implementar `UseCase`: listar roles disponíveis para a org
  - Retorna roles padrão (org_id IS NULL) + roles customizadas da org atual

- **S-03.3** Implementar `UseCase`: criar role customizada (clone)
  - `POST /api/v1/roles/clone`
  - Body: `{ "sourceRoleId": "uuid", "newName": "DEVELOPER_SENIOR" }`
  - Copia todas as `role_permissions` da role origem para a nova
  - Permissão requerida: `ROLE_MANAGE`

- **S-03.4** Implementar `UseCase`: atualizar permissões de uma role
  - `PUT /api/v1/roles/{id}/permissions`
  - Body: `{ "add": ["AI_READ_ALL"], "remove": ["AI_READ_OWN"] }`
  - Bloquear alteração de roles padrão (`is_default = true`)
  - Permissão requerida: `ROLE_MANAGE`

- **S-03.5** Implementar `UseCase`: deletar role customizada
  - Impedir deleção se houver usuários atribuídos à role
  - Permissão requerida: `ROLE_MANAGE`

---

## F-04 · Gestão de Usuários

**Prioridade:** Alta  
**RF relacionados:** RF01, RF02, RF03, RF04, RF05  
**Descrição:** CRUD de usuários dentro da organização, sempre filtrado por `organization_id`.

### Stories

- **S-04.1** Criar migration `V3__users.sql`
  - Campos: `id`, `organization_id`, `email`, `password_hash`, `name`, `role_id`, `github_id (nullable)`, `active`, `created_at`

- **S-04.2** Implementar `UseCase`: criar usuário
  - `POST /api/v1/users`
  - Admin informa email, nome e role
  - Sistema gera senha temporária e envia por e-mail (ou exibe uma vez — definir)
  - Permissão requerida: `USER_MANAGE`

- **S-04.3** Implementar `UseCase`: listar, editar e desativar usuário
  - `GET /api/v1/users` — lista da org
  - `PUT /api/v1/users/{id}` — altera nome, role
  - `DELETE /api/v1/users/{id}` — soft delete (`active = false`)
  - Permissão requerida: `USER_MANAGE`

---

## F-05 · Autenticação

**Prioridade:** Alta  
**RF relacionados:** RF06, RF07  
**Descrição:** Login local com JWT stateless e vinculação de conta GitHub via OAuth2.

### Stories

- **S-05.1** Implementar login local (email + senha)
  - `POST /api/v1/auth/login` — retorna `{ accessToken, expiresIn }`
  - JWT payload: `{ sub: userId, orgId, permissions[], iat, exp }`
  - Senha hasheada com BCrypt
  - Permissão requerida: —

- **S-05.2** Implementar `JwtAuthenticationFilter`
  - Valida JWT em todo request
  - Popula `SecurityContext` e `TenantContextHolder` com `orgId`
  - Retorna 401 para token inválido/expirado

- **S-05.3** Implementar autorização por permissão
  - Configurar `@PreAuthorize("hasAuthority('PERMISSAO')")` nos controllers
  - Carregar permissões da role do usuário no JWT (evitar query por request)

- **S-05.4** Implementar OAuth2 GitHub (vinculação de conta)
  - `GET /api/v1/auth/github` — inicia flow OAuth2
  - `GET /api/v1/auth/github/callback` — recebe code, valida, salva `github_id` no usuário
  - Requer usuário já autenticado (vinculação, não criação de conta)
  - Permissão requerida: usuário autenticado
