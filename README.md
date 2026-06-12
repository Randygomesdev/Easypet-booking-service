# Easypet — Booking Service

Microsserviço de agendamentos da plataforma [Easypet](https://github.com/randygomesdev). Responsável pelo ciclo completo de um agendamento — criação, confirmação, atendimento, cancelamento e analytics do parceiro.

## Funcionalidades

- Criação e gerenciamento de agendamentos (consultas, vacinação, banho e tosa, hospedagem)
- Fluxo de status: `PENDING` → `CONFIRMED` → `IN_PROGRESS` → `COMPLETED` / `CANCELLED`
- Encaixe de agendamentos (`isFittingRequest`)
- Verificação de disponibilidade de profissional e horário
- Prevenção de conflito em hospedagem (boarding overlap)
- Analytics do parceiro: receita, novos clientes, agendamentos por dia
- Integração com partner-service (validação de serviços e profissionais)
- Autenticação via JWT

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 + Gradle |
| Segurança | Spring Security 6 + JJWT 0.12.6 |
| Banco de dados | PostgreSQL + Flyway |
| Mapeamento | MapStruct |
| Documentação | SpringDoc OpenAPI 2.7 |

## Parte do Ecossistema Easypet

```
API Gateway :8080
      │
      ▼
Booking Service :8084
      │
      ├── /api/v1/bookings/**
      └── integra com Partner Service :8083
```

## Como executar

### 1. Pré-requisitos

- Java 21+
- Gradle 8+
- PostgreSQL em execução
- Partner Service em execução (para validações)

### 2. Configurar variáveis de ambiente

Crie um arquivo `.env` na raiz do projeto:

```properties
SERVER_PORT=8084

BOOKING_DB_URL=jdbc:postgresql://localhost:5435/booking_db
BOOKING_DB_USERNAME=seu_usuario
BOOKING_DB_PASSWORD=sua_senha

# JWT — mesmo segredo do auth-service e gateway
JWT_SECRET=sua_chave_secreta

# URLs de integração
PARTNER_SERVICE_URL=http://localhost:8083
BOOKING_SERVICE_URL=http://localhost:8084
```

### 3. Executar

```bash
./gradlew bootRun
```

O serviço iniciará em `http://localhost:8084/api/v1`.  
Swagger UI: `http://localhost:8084/api/v1/swagger-ui.html`

## Migrações do Banco de Dados

| Versão | Descrição |
|--------|-----------|
| V1 | Criação da tabela de agendamentos |
| V2 | Adição do campo `deleted_at` (soft delete) |
| V3 | Adição de campos de hospedagem e `service_id` |
| V4 | Adição de campos de pagamento |
| V5 | Extensão com dados de profissional (`staff`) |

## Status do Agendamento

```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
    └──────────────────────────────→ CANCELLED
```

---

Desenvolvido por [Innker Code](https://github.com/randygomesdev)
