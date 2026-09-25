# RiskGuard Study Guide

## What is it?

A REST API that scores a payment transaction for fraud risk, from 0 to 100, and explains the score.
It is a Java/Spring Boot rewrite of the idea behind FraudGuard (Python, XGBoost + SHAP). Instead of a
trained model it uses three hand-written rules. The goal was to learn Spring Boot.

## What does it do?

1. Receives a transaction: `accountId`, `amount`, `merchantCountry`.
2. Runs three rules, each adding points and a reason:

   | Rule | Trigger | Points |
   |------|---------|--------|
   | High amount | amount > 100000 | +40 |
   | Foreign merchant | country not in IN, US, GB, SG, AE | +25 |
   | Velocity | 3+ earlier transactions from the account in the last 10 minutes | +35 |

3. Adds the points, caps at 100, and flags the transaction if the total is 50 or more.
4. Saves the transaction and returns `{transactionId, riskScore, flagged, reasons[]}`.

Because the rules are independent, flagging needs a combination. High amount alone is 40 (not flagged),
and high amount plus foreign merchant is 65 (flagged).

## What shows on the frontend?

One page at `localhost:8080`:

- A form (account, amount, country) and three preset buttons: Low risk, Flagged, Velocity.
- A result card with the big score number (green or red), a FLAGGED / NOT FLAGGED badge, the transaction
  id, a score bar, and the "Why this score" list of reasons.
- Validation errors shown per field.
- A footer with the rules and a link to the H2 database console.

It is not a history dashboard. It scores one transaction at a time.

## How the code fits together

```
Browser / curl
   -> TransactionController      receives the request, @Valid checks it
   -> RiskScoringService         applies the rules, builds the response
   -> TransactionRepository      Spring Data JPA, saves to H2
GlobalExceptionHandler           turns errors into 400 / 404 responses
```

| Class | Role |
|-------|------|
| `TransactionController` | `POST /api/transactions/score`, `GET /api/transactions/{id}` |
| `RiskScoringService` | all the scoring logic |
| `TransactionRepository` | `findByAccountIdAndTimestampAfter` is used for the velocity rule |
| `Transaction` | JPA entity, the database table |
| `TransactionRequest` | input DTO with validation annotations |
| `RiskResponse` | immutable output DTO |
| `GlobalExceptionHandler` | 400 for validation errors, 404 for missing transaction |

## Spring Boot concepts it demonstrates

- **Constructor injection** instead of `@Autowired` fields. Dependencies are explicit and easy to mock.
- **Spring Data JPA:** a query is derived from the method name, with no SQL written.
- **Bean Validation:** `@NotBlank`, `@NotNull`, `@Positive` on the DTO, triggered by `@Valid`.
- **`@RestControllerAdvice`:** one place that maps exceptions to HTTP responses.
- **DTOs vs entities:** the request and response shapes are separate from the database entity.
- **Auto-configuration:** H2, JPA and the web server are set up from dependencies and
  `application.properties`.
- **Unit testing with Mockito:** the service is tested without starting Spring.

## Likely interview questions

- **Why constructor injection?** Fields can be `final`, the class works without Spring, and tests just
  call `new RiskScoringService(mockRepo)`.
- **Why is the velocity rule "prior" transactions?** The current transaction is saved after scoring, so
  the query only sees earlier ones. Three earlier plus the current one is the 4th transaction.
- **Why use `BigDecimal` for amount?** Floating-point types cannot represent money exactly.
- **What does `ddl-auto=update` do?** Hibernate creates or alters tables to match the entities on startup.
- **How would you make it production-ready?** Move thresholds to config, use a real database, add
  authentication, use a `Clock` bean for testable time, add integration tests, and return a DTO from the GET.
- **How does this differ from FraudGuard?** FraudGuard learns weights from data and explains with SHAP.
  RiskGuard has fixed rules, but its reasons are exact rather than approximated.

## Commands

```bash
./run.sh              # start the app and open the browser
./mvnw clean test     # run the 11 unit tests
```
