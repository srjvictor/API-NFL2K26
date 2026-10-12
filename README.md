# 🏈 NFL API

API RESTful sobre a NFL desenvolvida com **Spring Boot 4.1.1** e **Java 17**: times, jogadores, estádios, patrocinadores e partidas.

## 🚀 Demo online

| Recurso | URL |
|---|---|
| Swagger UI | https://nfl-api-w1hc.onrender.com/swagger-ui.html |
| Raiz HATEOAS | https://nfl-api-w1hc.onrender.com/api |
| OpenAPI (JSON) | https://nfl-api-w1hc.onrender.com/v3/api-docs/v1 e `/v3/api-docs/v2` |

## Como executar localmente

```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs/v1 e `/v3/api-docs/v2` |
| Raiz HATEOAS | http://localhost:8080/api |
| Console H2 | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:nfldb`, usuário `sa`, sem senha) |

**Chave ADMIN de desenvolvimento:** `nfl_admin_dev_key_123` (no Swagger, clique em **Authorize**).
O banco é populado automaticamente com 5 times, 6 estádios, 10 jogadores, 5 patrocinadores e 4 partidas.

## Produção e deploy

### Perfil `prod`

Ative com `SPRING_PROFILES_ACTIVE=prod`. Diferenças em relação ao desenvolvimento:

| Variável de ambiente | Obrigatória | Para quê |
|---|---|---|
| `NFL_ADMIN_KEY` | **sim** (mín. 16 caracteres) | Chave ADMIN. Sem ela a aplicação não sobe; a chave de dev deixa de existir. |
| `NFL_CORS_ALLOWED_ORIGINS` | não | Frontends autorizados, separados por vírgula (ex.: `https://meu-front.vercel.app`). Padrão: nenhum. |
| `NFL_RATE_LIMIT_CAPACITY` | não | Requisições por minuto por IP (padrão 60). |
| `PORT` | não | Porta HTTP (padrão 8080; Render/Railway definem sozinhos). |

Além disso: console H2 desligado e `server.forward-headers-strategy=framework`, para que atrás do proxy HTTPS da
plataforma os links HATEOAS saiam com `https://seu-dominio` e o rate limit enxergue o IP real do cliente.

Rodando o `.jar` com o perfil de produção:

```bash
mvnw.cmd clean package
set SPRING_PROFILES_ACTIVE=prod
set NFL_ADMIN_KEY=uma-chave-longa-e-secreta
java -jar target/nfl-api-0.0.1-SNAPSHOT.jar
```

### Docker

```bash
docker build -t nfl-api .
docker run -p 8080:8080 -e NFL_ADMIN_KEY=uma-chave-longa-e-secreta nfl-api
```

A imagem é multi-stage (build com JDK 17, execução só com JRE 17) e roda com um usuário sem privilégios.

## Stack

Spring Boot 4.1.1 · Spring Web MVC · Spring Data JPA · H2 · Bean Validation · Spring HATEOAS · Springdoc OpenAPI 3.1.1 · Lombok · Maven

## Estrutura

```
src/main/java/com/nfl/api
├── config/        AppProperties, CorsConfig, OpenApiConfig, DataSeeder
├── domain/        Entidades JPA (+ enums/)
├── dto/           Records de request/response com validação e @Schema
├── exception/     ApiError, exceções de negócio e GlobalExceptionHandler (@RestControllerAdvice)
├── repository/    Repositórios Spring Data com consultas JPQL personalizadas
├── service/       Regras de negócio (transacionais)
└── web/
    ├── assembler/ RepresentationModelAssemblers (HATEOAS)
    ├── controller/
    └── filter/    RateLimitFilter → ApiKeyAuthFilter → IdempotencyFilter
```

## Parte 1

### Entidades e relacionamentos

| Entidade | Relacionamentos | Enums |
|---|---|---|
| `Team` | **One-to-One** com `Stadium` · **One-to-Many** com `Player` · **Many-to-Many** com `Sponsor` | `Conference`, `Division` |
| `Stadium` | One-to-One (lado inverso) com `Team` | `RoofType` |
| `Player` | Many-to-One com `Team` | `Position` |
| `Sponsor` | Many-to-Many (lado inverso) com `Team` | — |
| `Game` | Many-to-One com `Team` (mandante e visitante) e `Stadium` | `GameStatus` |

Além delas: `ApiKey` (autenticação) e `IdempotencyRecord` (idempotência). As entidades e os DTOs de entrada usam Bean Validation (`@NotBlank`, `@Size`, `@Pattern`, `@Min/@Max`, `@Past`, `@Positive`, `@Digits`, `@Email`...).

### Endpoints

Todas as listagens são paginadas (`?page=0&size=10&sort=name,asc`) e retornam `PagedModel` com links `first/prev/next/last`.

| Recurso | Endpoints |
|---|---|
| **Times** `/api/teams` | `GET` · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` · `GET /search?conference=&division=&name=` · `GET /{id}/players` · `GET /{id}/sponsors` · `PUT /{id}/sponsors/{sponsorId}` · `DELETE /{id}/sponsors/{sponsorId}` |
| **Jogadores** `/api/players` | `GET` · `GET /{id}` (**v1 e v2**) · `POST` · `PUT /{id}` · `DELETE /{id}` · `GET /search?position=&teamId=&name=` |
| **Estádios** `/api/stadiums` | `GET` · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` · `GET /search?minCapacity=&roofType=` · `GET /{id}/games` |
| **Patrocinadores** `/api/sponsors` | `GET` · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` · `GET /search?industry=&minValue=` · `GET /{id}/teams` |
| **Partidas** `/api/games` | `GET` · `GET /{id}` · `POST` · `PUT /{id}` · `PATCH /{id}/score` · `DELETE /{id}` · `GET /search?season=&week=&status=` · `GET /team/{teamId}` |
| **Chaves** `/api/keys` | `POST /register` (público) · `POST` · `GET` · `GET /me` · `GET /{id}` · `PATCH /{id}/revoke` · `DELETE /{id}` |

Códigos de status: `200` consulta/atualização · `201` + `Location` criação · `204` exclusão · `400` validação · `401` sem chave · `403` nível insuficiente · `404` não encontrado · `405` método · `409` conflito · `415` content-type · `422` regra de negócio · `429` rate limit · `500` erro interno.

### HATEOAS

Cada recurso traz `self`, `update`, `delete`, a coleção e os relacionados (ex.: um time aponta para `players`, `sponsors`, `games`, `stadium`; uma partida para `homeTeam`, `awayTeam`, `stadium` e — se não estiver cancelada — `updateScore`). A navegação começa em `GET /api`.

## Parte 2

### Idempotência (`X-Idempotency-Key`)

Todo `POST` exige o header. O `IdempotencyFilter` grava a chave (por cliente) com o hash da requisição:

- primeira chamada → processa e armazena a resposta 2xx;
- repetição com o mesmo corpo → devolve a resposta original com `Idempotent-Replayed: true`, **sem criar outro registro**;
- mesma chave com corpo diferente → `422`; chave ainda em processamento → `409`;
- respostas de erro não são guardadas (o cliente pode corrigir e tentar de novo); as chaves expiram em 24 h.

### Autenticação por chave (`X-API-Key`)

As chaves são aleatórias (`nfl_` + 256 bits) e apenas o **hash SHA-256** é salvo. Níveis de acesso:

| Nível | Permissões |
|---|---|
| `READ_ONLY` | GET (obtida no auto-cadastro público) |
| `READ_WRITE` | GET, POST, PUT, PATCH |
| `ADMIN` | tudo, inclusive DELETE e gerenciamento de chaves |

### Rate limiting

Janela fixa por IP (padrão 60 req/min, configurável em `nfl.rate-limit.*`). Toda resposta traz `X-RateLimit-Limit`, `X-RateLimit-Remaining` e `X-RateLimit-Reset`. Ao estourar: **429** com `Retry-After`.

### CORS

`CorsFilter` como primeiro filtro da cadeia. Origens permitidas: `http://localhost:3000`, `:4200`, `:5173`. Métodos: GET, POST, PUT, PATCH, DELETE, OPTIONS. Headers expostos: `Location`, `X-RateLimit-*`, `Retry-After`, `Idempotent-Replayed`, `X-API-Version`, `Deprecation`. Outras origens recebem 403.

### Versionamento (`X-API-Version`)

Usa o suporte nativo de versionamento do Spring Framework 7 (`spring.mvc.apiversion.use.header=X-API-Version`, padrão `1`).
`GET /api/players/{id}`:

- **v1** (padrão): campos planos (`firstName`, `lastName`, `teamId`, `teamName`); responde com `Deprecation: true`;
- **v2**: `fullName`, `age` calculada, `measurements` (métrico + imperial) e `team` como objeto aninhado.

Versão não suportada → 400. No Swagger, escolha a versão em **Select a definition**.

## Exemplos com curl

```bash
# Auto-cadastro de chave (público)
curl -X POST http://localhost:8080/api/keys/register \
  -H "Content-Type: application/json" -H "X-Idempotency-Key: $(uuidgen)" \
  -d '{"owner":"Meu App","email":"dev@exemplo.com"}'

# Listar times da NFC East
curl -H "X-API-Key: nfl_admin_dev_key_123" "http://localhost:8080/api/teams/search?conference=NFC&division=EAST"

# Jogador nas duas versões
curl -H "X-API-Key: nfl_admin_dev_key_123" http://localhost:8080/api/players/1
curl -H "X-API-Key: nfl_admin_dev_key_123" -H "X-API-Version: 2" http://localhost:8080/api/players/1

# Criar um time (repita o comando: a resposta volta com Idempotent-Replayed: true e nada é duplicado)
curl -i -X POST http://localhost:8080/api/teams \
  -H "X-API-Key: nfl_admin_dev_key_123" -H "Content-Type: application/json" -H "X-Idempotency-Key: lions-001" \
  -d '{"name":"Detroit Lions","city":"Detroit","abbreviation":"DET","conference":"NFC","division":"NORTH","foundedYear":1930,"superBowlTitles":0}'
```
