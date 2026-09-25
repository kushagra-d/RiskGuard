# RiskGuard

RiskGuard is a transaction risk-scoring REST API built with Spring Boot 3 and Java 21. You POST a
transaction, and it returns a risk score from 0 to 100, a flag, and the list of rules that produced
the score. Every scored transaction is stored in an in-memory H2 database and can be fetched by id.

## Why this exists

This is Spring Boot practice that mirrors the concept of my Python project **FraudGuard**, an XGBoost
fraud classifier with SHAP explainability. RiskGuard swaps the ML model for a transparent rule engine,
so the focus is on Spring Boot itself: constructor injection, Spring Data JPA, bean validation,
centralised exception handling, and Mockito unit tests. The explainability idea carries over: each
rule that fires adds a human-readable reason, so the score is never a black box.

## Scoring rules

| Rule | Trigger | Points |
|------|---------|--------|
| High amount | `amount > 100000` | +40 |
| Foreign merchant | `merchantCountry` not in `IN, US, GB, SG, AE` | +25 |
| Velocity | 3 or more earlier transactions from the same `accountId` in the last 10 minutes | +35 |

The points are summed and capped at 100. A transaction is **flagged** when the total is 50 or more.

## Run it

Requires only Java 21 (`brew install openjdk@21` on macOS). Maven is bundled through the wrapper.

```bash
git clone https://github.com/kushagra-d/RiskGuard.git
cd RiskGuard
./run.sh
```

`run.sh` starts the app and opens the demo page at `http://localhost:8080`, where you can score
transactions and see the reasons. Press `Ctrl+C` to stop.

Without the script: `./mvnw spring-boot:run` (or `mvn spring-boot:run` if Maven is installed).

The H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:riskguard`, user `sa`,
empty password). The database is in memory and resets on each restart.

Run the tests with:

```bash
./mvnw clean test
```

## API

### `POST /api/transactions/score`

Low risk: small domestic purchase.

```bash
curl -s -X POST http://localhost:8080/api/transactions/score \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC-1001", "amount": 2500.00, "merchantCountry": "IN"}'
```

```json
{"transactionId":1,"riskScore":0,"flagged":false,"reasons":[]}
```

Flagged: large amount at a foreign merchant.

```bash
curl -s -X POST http://localhost:8080/api/transactions/score \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC-2002", "amount": 250000.00, "merchantCountry": "RU"}'
```

```json
{
  "transactionId": 2,
  "riskScore": 65,
  "flagged": true,
  "reasons": [
    "High amount: 250000.00 exceeds the 100000 threshold (+40)",
    "Foreign merchant: country RU is not in the trusted set [AE, GB, IN, SG, US] (+25)"
  ]
}
```

Velocity: send the same request from one account four times within 10 minutes. The fourth response
adds `High velocity: 3 transactions from account ACC-3003 in the last 10 minutes (+35)`.

```bash
for i in 1 2 3 4; do
  curl -s -X POST http://localhost:8080/api/transactions/score \
    -H "Content-Type: application/json" \
    -d '{"accountId": "ACC-3003", "amount": 100.00, "merchantCountry": "US"}'; echo
done
```

Invalid input returns HTTP 400 with a field-to-message map:

```bash
curl -s -X POST http://localhost:8080/api/transactions/score \
  -H "Content-Type: application/json" \
  -d '{"accountId": "", "amount": -5, "merchantCountry": "IN"}'
```

### `GET /api/transactions/{id}`

Returns the stored transaction, or HTTP 404 if the id does not exist.

```bash
curl -s http://localhost:8080/api/transactions/2
```

## Project layout

```
com.kushagra.riskguard
├── controller/   TransactionController
├── service/      RiskScoringService
├── repository/   TransactionRepository
├── model/        Transaction (JPA entity)
├── dto/          TransactionRequest, RiskResponse
└── exception/    GlobalExceptionHandler, TransactionNotFoundException
```
