<div align="center">

# 💳 PaySense

### AI-Powered Personal Finance & Payment Platform

A production-grade, microservices-based fintech platform simulating real-world digital payment systems like Paytm, PhonePe, and Razorpay — featuring UPI transfers, wallet management, real-time fraud detection, and an AI-powered financial advisor built on the **Model Context Protocol (MCP)** with **LLM integration**.

---

[Features](#-features) · [Architecture](#-architecture) · [Tech Stack](#-tech-stack) · [Getting Started](#-getting-started) · [MCP & AI](#-mcp-server--llm-integration) · [Project Structure](#-project-structure) · [License](#-license)

</div>

---

## 🎯 Why PaySense?

Modern fintech applications demand far more than CRUD operations — they require event-driven architectures, real-time fraud analysis, financial-grade data integrity, and increasingly, AI-powered user experiences. **PaySense** was built to solve these challenges head-on:

- **Real-World Complexity** — Goes beyond tutorials by implementing idempotent payments, optimistic locking, double-entry bookkeeping, and circuit breakers — patterns used by actual payment companies.
- **Microservices Done Right** — Six independently deployable services communicating via REST and Apache Kafka, each owning its own database schema.
- **AI-Native Finance** — Integrates an MCP (Model Context Protocol) server with Claude LLM to provide users with an intelligent financial advisor that can query spending data, set budgets, and offer personalized insights — all through natural conversation.
- **Production Mindset** — Dockerized infrastructure, CI/CD pipelines, Razorpay payment gateway integration (test mode), and designed for AWS EKS deployment.

---

## ✨ Features

### 💸 Payments
- **UPI Transfers** — Send money via Virtual Payment Address (VPA) with instant settlement
- **NEFT Payments** — Bank-to-bank transfers using account number + IFSC with batch settlement
- **Wallet Operations** — Digital wallet with top-up, pay, daily limits (₹10,000), and automatic midnight reset
- **Razorpay Integration** — Real payment gateway (test mode) for wallet top-ups with cryptographic signature verification
- **Idempotency** — Client-provided idempotency keys prevent duplicate payment processing
- **Optimistic Locking** — Version-based concurrency control on account and wallet balances

### 🔐 Authentication & Security
- **JWT Authentication** — Stateless auth with access + refresh token rotation
- **BCrypt Password Hashing** — Strength-12 encryption for all stored passwords
- **Role-Based Access Control** — USER and ADMIN roles with Spring Security 6
- **Refresh Token Security** — SHA-256 hashed tokens with device fingerprinting and IP tracking

### 🛡️ Fraud Detection
- **Rule-Based Risk Engine** — Configurable rules with cumulative risk scoring (0–100)
- **Real-Time Evaluation** — Synchronous pre-payment fraud checks via REST + Circuit Breaker
- **Async Monitoring** — Kafka-driven event processing for deeper analysis
- **Blacklist Management** — Instantly block suspicious VPAs
- **Detection Rules** — High amount (>₹50K), rapid frequency (>5 txns/10 min), odd hours (1–5 AM), new device, and more

### 📊 Transactions & Budgeting
- **Immutable Ledger** — Double-entry bookkeeping (DEBIT + CREDIT for every payment) — entries are never updated or deleted
- **Transaction History** — Full audit trail with balance snapshots at every point in time
- **Budget Management** — Set monthly category budgets (FOOD, TRANSPORT, etc.) with alerts when approaching limits
- **Spending Analytics** — Category-wise breakdowns and trend analysis

### 🔔 Real-Time Notifications
- **Server-Sent Events (SSE)** — Instant push notifications to the Angular frontend
- **Multi-Channel** — In-app, email, SMS, and push notification support
- **Event-Driven** — Triggered by Kafka events for payment confirmations, fraud alerts, and budget warnings

### 🤖 AI Financial Advisor
- **Natural Language Interface** — Chat with an AI assistant about your finances
- **MCP Tool Calling** — The LLM uses structured tools to query real financial data
- **Personalized Insights** — Spending summaries, budget recommendations, and anomaly detection
- **Conversation History** — Persistent chat history for contextual follow-ups

---

## 🏗️ Architecture

PaySense follows an **event-driven microservices architecture** where services communicate via REST (synchronous) and Apache Kafka (asynchronous).

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                                 │
│                   Angular 17 SPA (:4200)                            │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     GATEWAY LAYER                                   │
│              Spring Cloud Gateway (:8080)                           │
│         (Routing · Rate Limiting · Load Balancing)                  │
└────────────────────────┬────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────────────────┐
          ▼              ▼              ▼            ▼
┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌──────────┐
│ Auth Service │ │Payment Svc   │ │Fraud Svc │ │ MCP/AI   │
│   (:8081)    │ │  (:8082)     │ │ (:8084)  │ │ (:8086)  │
└──────┬───────┘ └───┬──────┬──┘ └────┬─────┘ └────┬─────┘
       │             │      │         │             │
       │             │  ┌───▼─────────▼───┐         │
       │             │  │  Apache Kafka   │         │
       │             │  │    (:9092)      │         │
       │             │  └───┬─────────┬───┘         │
       │             │      ▼         ▼             │
       │             │ ┌──────────┐ ┌───────────┐   │
       │             │ │Txn Svc   │ │Notif Svc  │   │
       │             │ │ (:8083)  │ │ (:8085)   │   │
       │             │ └────┬─────┘ └─────┬─────┘   │
       │             │      │             │         │
       ▼             ▼      ▼             ▼         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       DATA LAYER                                    │
│      PostgreSQL 16 (:5432)          Redis 7 (:6379)                 │
│   ┌──────┬────────┬───────┬────────────┬───────┐                    │
│   │ auth │payment │ txn   │  fraud     │notif  │mcp │               │
│   │schema│ schema │schema │  schema    │schema │schema              │
│   └──────┴────────┴───────┴────────────┴───────┘                    │
└─────────────────────────────────────────────────────────────────────┘
```

### Service Breakdown

| Service | Port | Schema | Responsibility |
|---------|------|--------|---------------|
| **Auth Service** | 8081 | `auth` | JWT login, register, refresh tokens, Spring Security 6 |
| **Payment Service** | 8082 | `payment` | UPI, NEFT, wallet ops, Razorpay integration, idempotency |
| **Transaction Service** | 8083 | `transaction` | Immutable ledger, double-entry bookkeeping, budgets |
| **Fraud Service** | 8084 | `fraud` | Rule engine, risk scoring, blacklist management |
| **Notification Service** | 8085 | `notification` | Email/SMS/push alerts, SSE real-time streaming |
| **MCP Server** | 8086 | `mcp` | AI financial advisor, Claude API, MCP tool execution |

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|-----------|---------|---------|
| **Java** | 21 | Core language (virtual threads, pattern matching, records) |
| **Spring Boot** | 3.2.4 | Microservice framework |
| **Spring Security 6** | — | Authentication & authorization |
| **Spring Cloud Gateway** | — | API gateway, routing, rate limiting |
| **Spring Data JPA** | — | ORM / database access |
| **Spring Kafka** | — | Event-driven messaging |
| **Resilience4j** | — | Circuit breaker for inter-service calls |
| **JJWT** | 0.12.5 | JWT token generation & validation |
| **Razorpay Java SDK** | — | Payment gateway integration |
| **Lombok** | — | Boilerplate reduction |
| **SpringDoc OpenAPI** | 2.3.0 | Auto-generated Swagger API docs |

### Frontend
| Technology | Version | Purpose |
|-----------|---------|---------|
| **Angular** | 17 | SPA framework with standalone components |
| **TypeScript** | 5.4 | Type-safe development |
| **TailwindCSS** | 3.4 | Utility-first styling |
| **RxJS** | 7.8 | Reactive state & HTTP management |

### Infrastructure
| Technology | Version | Purpose |
|-----------|---------|---------|
| **PostgreSQL** | 16 (Alpine) | Primary database (6 schemas, 15 tables) |
| **Apache Kafka** | 7.6.0 (Confluent) | Async event streaming between services |
| **Redis** | 7 (Alpine) | Caching, rate limiting, idempotency |
| **Docker Compose** | 3.8 | Local development orchestration |
| **Zookeeper** | 7.6.0 | Kafka cluster coordination |
| **pgAdmin 4** | — | Database administration UI |
| **Kafka UI** | — | Kafka topic monitoring & management |
| **Nginx** | — | Reverse proxy & static file serving |

### DevOps & Cloud
| Technology | Purpose |
|-----------|---------|
| **GitHub Actions** | CI/CD pipeline |
| **Docker** | Containerization of all services |
| **AWS EKS** | Kubernetes deployment target |

---

## 🤖 MCP Server & LLM Integration

PaySense features a dedicated **MCP (Model Context Protocol) Server** that bridges the gap between the user and their financial data through AI-powered natural language interactions.

### What is MCP?

The **Model Context Protocol** is a standardized way for LLMs (Large Language Models) to interact with external tools and data sources. In PaySense, the MCP server exposes structured financial tools that an LLM (Claude by Anthropic) can invoke to answer user queries with real data.

### How It Works

```
┌──────────┐     ┌──────────────┐     ┌───────────────────┐     ┌────────────────┐
│  User    │────▶│ Angular Chat │────▶│  MCP Server       │────▶│  Claude LLM    │
│ "What did│     │   Widget     │     │  (:8086)          │     │  (Anthropic)   │
│  I spend │     └──────────────┘     │                   │     └───────┬────────┘
│  on food │                          │  ┌─────────────┐  │             │
│  this    │                          │  │ Tool Layer  │◀─┼─────────────┘
│  month?" │                          │  │             │  │  Tool calls:
│          │                          │  │• get_spend  │  │  get_spending_summary
│          │◀─────────────────────────│  │• set_budget │  │    (category: FOOD,
│ "You     │  AI-generated response   │  │• get_balance│  │     month: current)
│  spent   │  with real data          │  │• anomaly    │  │
│  ₹4,230" │                          │  └─────────────┘  │
└──────────┘                          └───────────────────┘
```

### MCP Tools Available

| Tool | Description |
|------|-------------|
| `get_spending_summary` | Retrieves spending breakdown by category for a given time period |
| `get_account_balance` | Fetches current account and wallet balances |
| `set_budget` | Creates or updates monthly category budgets |
| `get_budget_status` | Shows budget utilization and remaining amounts |
| `get_recent_transactions` | Lists recent transactions with filters |
| `detect_anomalies` | Identifies unusual spending patterns |

### LLM Integration Details

- **Model**: Claude (Anthropic) — chosen for superior tool-calling capabilities and financial reasoning
- **Protocol**: MCP (Model Context Protocol) over REST
- **Conversation Persistence**: All user-AI conversations are stored in the `mcp.ai_conversations` table with tool call metadata
- **Tool Usage Logging**: Every tool invocation is logged in `mcp.tool_usage_log` for audit and analytics
- **Security**: All MCP endpoints are JWT-protected — the AI can only access data belonging to the authenticated user

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Node.js 18+** & npm
- **Docker Desktop** (for PostgreSQL, Redis, Kafka)
- **Maven 3.9+**

### 1. Clone the Repository

```bash
git clone https://github.com/PRANIT262/paysense.git
cd paysense
```

### 2. Start Infrastructure (Docker)

```bash
docker compose up -d
```

This spins up:
- **PostgreSQL 16** on port `5432` (auto-runs DB init scripts)
- **Redis 7** on port `6379`
- **pgAdmin** on port `5050` (admin@paysense.com / admin123)
- **Kafka** on port `9092`
- **Kafka UI** on port `8090`
- **Zookeeper** on port `2181`

### 3. Start Backend Services

```powershell
cd paysense-backend
./start-services.ps1
```

Or start individually:

```bash
# From paysense-backend directory
cd auth-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run
cd fraud-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd mcp-server && mvn spring-boot:run
```

### 4. Start Frontend

```bash
cd paysense-frontend
npm install
npm start
```

The Angular app starts on **http://localhost:4200** with proxy configuration routing API calls to the appropriate backend services.

### 5. Access the Application

| Service | URL |
|---------|-----|
| **PaySense App** | http://localhost:4200 |
| **pgAdmin** | http://localhost:5050 |
| **Kafka UI** | http://localhost:8090 |
| **Swagger (Auth)** | http://localhost:8081/swagger-ui.html |
| **Swagger (Payment)** | http://localhost:8082/swagger-ui.html |

### Environment Variables

Copy the `.env.example` to `.env` and configure:

```env
# Razorpay (Test Mode)
RAZORPAY_KEY_ID=your_test_key_id
RAZORPAY_KEY_SECRET=your_test_key_secret

# Claude API (for MCP Server)
ANTHROPIC_API_KEY=your_api_key

# JWT
JWT_SECRET=your_jwt_secret_key
```

---

## 📁 Project Structure

```
paysense/
├── docker-compose.yml                  # Full infrastructure stack
├── .github/workflows/                  # CI/CD pipeline definitions
│
├── paysense-backend/                   # Java 21 + Spring Boot 3.2.4
│   ├── pom.xml                         # Parent POM (multi-module Maven)
│   ├── start-services.ps1              # PowerShell script to start all services
│   ├── auth-service/                   # JWT auth, registration, login
│   │   └── src/main/java/com/paysense/auth/
│   │       ├── config/                 # Security, JWT, CORS config
│   │       ├── controller/             # REST endpoints
│   │       ├── dto/                    # Request/Response objects
│   │       ├── entity/                 # JPA entities (User, RefreshToken)
│   │       ├── filter/                 # JWT authentication filter
│   │       ├── repository/             # Spring Data JPA repos
│   │       └── service/                # Business logic
│   ├── payment-service/                # UPI, NEFT, wallet, Razorpay
│   ├── transaction-service/            # Ledger, budgets, analytics
│   ├── fraud-service/                  # Risk engine, blacklists
│   ├── notification-service/           # SSE, email, SMS, push
│   └── mcp-server/                     # AI advisor, Claude, MCP tools
│
├── paysense-frontend/                  # Angular 17 + TailwindCSS
│   ├── src/app/
│   │   ├── auth/                       # Login, register, guards, interceptors
│   │   ├── dashboard/                  # Main dashboard with payments UI
│   │   └── services/                   # Payment, notification, AI services
│   ├── proxy.conf.json                 # Dev proxy to backend services
│   └── tailwind.config.js
│
└── docs/                               # Architecture & database documentation
    ├── database/init-scripts/          # SQL scripts (auto-run by Docker)
    │   ├── 1schemas.sql                # 6 schemas
    │   ├── 2auth-tables.sql            # Auth tables
    │   ├── 3payment-tables.sql         # Payment tables
    │   └── 4other-tables.sql           # Transaction, fraud, notification, MCP tables
    └── paysense_project_understanding.md
```

---

## 🗄️ Database Design

Single PostgreSQL instance with **6 isolated schemas** (one per microservice) — no cross-schema foreign keys to respect service boundaries.

| Schema | Tables | Key Concepts |
|--------|--------|-------------|
| `auth` | `users`, `refresh_tokens` | BCrypt hashing, token rotation, device tracking |
| `payment` | `accounts`, `wallets`, `vpa_registry`, `payment_requests` | Optimistic locking, idempotency keys, UTR generation |
| `transaction` | `ledger_entries`, `budgets` | Immutable append-only ledger, double-entry bookkeeping |
| `fraud` | `fraud_checks`, `blacklisted_vpas`, `fraud_rules`, `user_devices` | Risk scoring, rule engine, device fingerprinting |
| `notification` | `notifications` | Multi-channel delivery, read tracking, JSONB metadata |
| `mcp` | `ai_conversations`, `tool_usage_log` | Chat history with tool call metadata, usage analytics |

---

## 🔌 API Overview

### Auth Service (`/api/auth`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/register` | Create new user account |
| `POST` | `/login` | Authenticate and receive JWT tokens |
| `POST` | `/refresh` | Refresh expired access token |
| `POST` | `/logout` | Revoke refresh token |
| `GET` | `/me` | Get authenticated user profile |

### Payment Service (`/api/payments`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/upi` | Send money via UPI (VPA) |
| `POST` | `/neft` | Send money via NEFT (account + IFSC) |
| `POST` | `/wallet/topup` | Add money to wallet (Razorpay) |
| `POST` | `/wallet/pay` | Pay from wallet balance |
| `POST` | `/vpa` | Register a new VPA |
| `GET` | `/accounts/{userId}` | Get account details |

### Fraud Service (`/api/fraud`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/check` | Evaluate payment for fraud risk |
| `POST` | `/blacklist` | Add VPA to blacklist |
| `GET` | `/blacklist` | List blacklisted VPAs |
| `DELETE` | `/blacklist/{vpa}` | Remove VPA from blacklist |

### Notification Service (`/api/notifications`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/{userId}` | Get all notifications |
| `PATCH` | `/{id}/read` | Mark notification as read |
| `GET` | `/{userId}/stream` | SSE real-time notification stream |

### AI Service (`/api/ai`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/chat` | Send message to AI financial advisor |
| `GET` | `/history` | Retrieve conversation history |

---

## 🛣️ Roadmap

- [x] Database architecture (6 schemas, 15 tables)
- [x] Docker infrastructure (Postgres, Redis, Kafka, pgAdmin, Kafka UI)
- [x] Auth Service (JWT, registration, login, refresh tokens)
- [x] Angular authentication (login, register, guards, interceptors)
- [x] Payment Service (UPI, NEFT, wallet, Razorpay)
- [x] Dashboard UI with payments interface
- [ ] Fraud Service (rule engine, risk scoring)
- [ ] Notification Service (SSE, Kafka consumers)
- [ ] Transaction Service (ledger, budgets, analytics)
- [ ] MCP Server (Claude integration, financial tools)
- [ ] AI Chat Widget in Angular
- [ ] Full Docker containerization of all services
- [ ] AWS EKS deployment
- [ ] GitHub Actions CI/CD pipeline

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<div align="center">

**Built by [Pranit](https://github.com/PRANIT262)**

*PaySense — Because your money deserves intelligence.*

</div>
