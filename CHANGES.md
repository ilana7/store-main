# Changes

This document covers what was changed and why. It is in three parts: defects found in the
existing codebase, the four numbered tasks, and the bonus CI task. Assumptions that a reviewer
might reasonably challenge are called out explicitly, along with the things deliberately left
undone.

Everything described here was verified against a real PostgreSQL 16.2 instance, not only against
unit tests. Where a claim is a measurement, the measurement is included.

---

## 1. Defects found in the existing codebase

These were found during a review pass before starting the numbered tasks.

### 1.1 `@Data` on bidirectional entities caused `StackOverflowError`

Lombok's `@Data` generates `equals`, `hashCode` and `toString` across *all* fields. Because
`Customer` holds `List<Order>` and `Order` holds `Customer`, each call recursed until the stack
was exhausted. Verified by running the compiled entities directly:

```
hashCode()        -> StackOverflowError
toString()        -> StackOverflowError
equals(distinct)  -> StackOverflowError
```

It was masked only because the existing tests built a `Customer` with an empty order list. Any
populated graph — which is exactly what `GET /customer` produces — would have triggered it, as
would any `HashSet<Customer>` or any log line printing an entity.

**Fix:** replaced `@Data` with `@Getter`, `@Setter`, `@ToString` and
`@EqualsAndHashCode(onlyExplicitlyIncluded = true)`, with equality on the id only and lazy
associations excluded from `toString`. Excluding lazy fields from `toString` also prevents a
`LazyInitializationException` from a stray log statement.

Note the accepted trade-off: id-only equality means two unsaved entities (both with a null id)
compare equal. That is the conventional compromise for JPA entities and is safe here, since
equality is only relied on for persistent instances.

### 1.2 The seed data left the identity sequences unadvanced, breaking every POST

`schema.sql` declares `id BIGSERIAL`, but `data.sql` inserts explicit ids 1–100 and 1–10000.
Inserting an explicit value does not advance the owning sequence, so `nextval` still returned 1
and the first insert through the application collided with an existing row:

```
POST /customer -> HTTP 500
ERROR: duplicate key value violates unique constraint "customer_pkey"
Detail: Key (id)=(1) already exists
```

Both POST endpoints were broken on any freshly seeded database, and would have stayed broken for
the first 100 customer inserts and 10,000 order inserts.

**Fix:** changeset `4-reset-identity-sequences` calls `setval` via `pg_get_serial_sequence` for
both tables. It is written as a new changeset rather than an edit to `data.sql`, so it applies
cleanly to a database that has already run the seed — editing an applied changeset would fail
Liquibase's checksum validation.

### 1.3 No index on the `order.customer_id` foreign key

PostgreSQL does not automatically index foreign key columns, and the schema declared none. Every
lookup of a customer's orders was a sequential scan:

```
-- before
Seq Scan on "order"  (rows=96)  Rows Removed by Filter: 9905   Buffers: shared hit=85
-- after
Bitmap Index Scan on idx_order_customer_id  (rows=96)          Buffers: shared hit=57
```

**Fix:** changeset `3-order-customer-id-index`.

### 1.4 `POST /order` returned an incomplete customer

The controller persisted whatever `Customer` object the client sent. Since a client sends only
`{"id": 1}`, the response echoed that stub back:

```
POST /order -> {"customer":{"id":1,"name":null}}
```

The association itself was stored correctly; only the response was wrong. The original test
stubbed `customerRepository.findById(1L)` without the controller ever calling it, which suggests
the lookup was intended and never written.

**Fix:** `OrderService.create` now resolves the customer (and, after task 4, the products) from
the database, so the response reflects real rows and an unknown reference returns 404.

### 1.5 The OpenAPI specification did not match the implementation

- `Order.customer` was documented as `{id, description}`; the code returns `{id, name}`.
- The `POST /order` request body had a typo, `descriptioon`.
- The `POST /order` request body omitted `customer` entirely, so a client following the spec
  literally could not create a valid order — `customer_id` is `NOT NULL`.
- `/products` declared `id` as `string` on GET and `integer` on POST, and its GET returned a
  bare object rather than an array despite being described as "get all products".
- All `201` responses declared no body, though every POST returns a populated DTO.
- Client-supplied `id` was documented on every POST, but the entities use
  `@GeneratedValue(IDENTITY)`; a non-null id sends `save()` down the merge path rather than
  insert.

**Fix:** the specification was rewritten to match the implementation, `id` was removed from all
request bodies, `201` responses now carry their schemas, and `required` markers were added to
mirror the `NOT NULL` columns. Leftover `x-stoplight` editor keys were removed.

### 1.6 Data generator could emit invalid SQL

`utils/generateData.js` interpolated faker output into single-quoted SQL with no escaping. A
generated name such as `O'Brien` would produce a syntactically invalid statement. The committed
`data.sql` happens to contain no apostrophes — all 10,100 lines were checked — so this was latent
rather than active.

**Fix:** values are escaped (`'` → `''`) before interpolation. `data.sql` was deliberately *not*
regenerated: the committed data is valid, and regenerating it would rewrite 10,000 lines and
invalidate the changeset checksum for no benefit.

### 1.7 Build and test hygiene

- `lombok-mapstruct-binding` was declared as `implementation`. It is an annotation-processor
  helper: on the compile classpath it does nothing useful and ships in the jar. Moved to
  `annotationProcessor` and added to `testAnnotationProcessor`.
- `spring-boot-starter-data-jpa` was declared again as `testImplementation`, already available
  via `implementation`. Removed.
- `OrderContollerTests.java` was misspelled; it compiled only because the class inside is
  package-private. Renamed.
- `@RequiredArgsConstructor` on a test class with no final fields — removed.
- A stubbed `customerRepository.findById` that the controller never called — removed.
- Assertions used JSONPath deep scans (`$..name`), which pass regardless of response shape.
  Replaced with exact paths plus explicit length assertions.
- `@ComponentScan(basePackageClasses = ...)` pulled a whole package into a `@WebMvcTest`;
  replaced with a targeted `@Import`.

### 1.8 Sample data was applied to every environment

`db.changelog-2.yaml` had no context, so 10,100 rows of generated data would be inserted into
production.

**Fix:** the seed changesets are tagged `context: dev`, and `spring.liquibase.contexts` defaults
to `dev` with `LIQUIBASE_CONTEXTS` as the override. Verified: running with `LIQUIBASE_CONTEXTS=prod`
against an empty database applies the five schema changesets, skips both seed changesets,
creates all six tables, and returns `[]` from every collection endpoint.

### 1.9 Other

- Database credentials were hardcoded; they now read `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
  with the previous values as defaults, so local behaviour is unchanged.
- Entities did not mirror the schema's constraints. Added `@Column(nullable = false)`,
  an explicit `@JoinColumn`, and `optional = false` on the `@ManyToOne`.
- The two nested DTOs were named `CustomerOrderDTO` and `OrderCustomerDTO` — one transposition
  apart and easy to misread. Renamed to `NestedOrderDTO` and `NestedCustomerDTO`.
- `OrderMapper.orderToOrderCustomerDTO` took a `Customer`, contradicting its name. Renamed to
  `customerToNestedCustomerDTO`.

---

## 2. Task 1 — find an order by id

`GET /order/{id}` returns a single order, or 404 as an RFC 7807 `ProblemDetail`:

```
GET /order/5      -> 200  {"id":5,"description":"Recycled Wooden Shoes","customer":{...},"products":[...]}
GET /order/999999 -> 404  {"title":"Not Found","status":404,"detail":"Order 999999 not found", ...}
```

Error handling is centralised in a `@RestControllerAdvice` rather than repeated per controller,
so the `404` and `400` shapes are consistent across all three resources.

**Assumption:** 404 rather than 200-with-null is the correct response for a missing resource
addressed by id.

---

## 3. Task 2 — search customers by name

`GET /customer?query=` performs a case-insensitive substring match. Omitting the parameter
returns every customer, so the existing contract is unchanged.

```
?query=dian     -> Dianne Lemke              (substring of a first name)
?query=orisse   -> Dr. Winifred Morissette   (mid-surname substring)
?query=WINIFRED -> Dr. Winifred Morissette   (case-insensitive)
?query=%        -> 0 matches                 (wildcard escaped, not honoured)
```

**Interpretation of the requirement.** The brief says "a substring of one of the words in their
name". A whitespace-free query cannot span a word boundary, so a plain `LIKE '%query%'` is
*exactly* equivalent to matching within a single word. The only divergence is a query that itself
contains whitespace, which may then match across words. That was judged acceptable rather than
worth rejecting.

**Wildcard escaping.** `%` and `_` are LIKE metacharacters. Without escaping, `?query=%` would
return every customer. Input is escaped using `!` as the escape character — deliberately not a
backslash, which would need escaping again inside the JPQL string literal. Unit tests pin this
behaviour.

**Indexing.** A leading-wildcard `LIKE` cannot use a btree index. Changeset
`5-customer-name-trigram-index` enables `pg_trgm` and creates a GIN index on `LOWER(name)`, which
matches the expression the query uses.

**Honest caveat:** at 100 customers the planner correctly prefers a sequential scan, because the
table is too small for the index to pay. Forcing `enable_seqscan=off` confirms the index *can*
serve the query:

```
Bitmap Index Scan on idx_customer_name_trgm
  Index Cond: (lower((name)::text) ~~ '%orisse%'::text)
```

So the index is correctly built and will be used at production data volumes. It is not delivering
a speedup at the sample data size, and it would be wrong to claim otherwise.

---

## 4. Task 3 — GET endpoint performance

### The problem

The endpoints issued one query per row to load associations. Measured by logging every statement
PostgreSQL received:

| Endpoint | SELECTs before | SELECTs after |
|---|---:|---:|
| `GET /customer` | 12 | **1** |
| `GET /order` | 1,012 | **1** |
| `GET /products` | 533 | **1** |
| `GET /order/{id}` | 3 | **1** |

Response payloads are byte-identical before and after (503,642 / 2,076,687 / 421,254 bytes) and
row counts unchanged (101 / 10,003 / 5,320), so this is purely a reduction in round trips and not
a change of contract.

### The fix

1. **`@EntityGraph` on the repository methods**, turning each N+1 into a single fetch-joined
   query. Applied to `findAll` and `findById` on all three repositories and to the customer
   search.
2. **`spring.jpa.open-in-view: false`.** These belong together. With open-in-view enabled, lazy
   loads silently succeed *during serialisation*, which is precisely where N+1 hides. Disabling
   it means any association the DTOs need must be fetched inside the service transaction, and
   anything missed fails loudly in development rather than quietly costing round trips in
   production.
3. **The foreign key index** from §1.3, which makes the join itself cheap.

### Why round trips are the right metric

The brief specifies that the database is not co-located and latency is high. Locally the
wall-clock improvement is modest because round-trip time is near zero. At a realistic 20ms of
latency, `GET /order` moves from roughly 20 seconds of accumulated round trips to roughly 20
milliseconds. The query count is the number being optimised.

### Trade-off

Fetch-joining a collection multiplies rows: 10,003 orders × ~2 products each is ~20,004 rows for
a single query. This trades bandwidth for round trips, which is the correct direction under high
latency but not free.

### Not done, and recommended next

`GET /order` returns 2MB unbounded. Pagination is the real fix and is the single highest-value
remaining change, but it alters the documented contract, so it is raised here as a recommendation
rather than applied unilaterally. `default_batch_fetch_size: 10` was already present in the
configuration and has been left in place; it is a useful safety net for any association that is
not explicitly fetch-joined.

---

## 5. Task 4 — products

### Model

`Product` (id, description) in a many-to-many relationship with `Order` via an `order_product`
join table. The join table's primary key covers `(order_id, product_id)`; the reverse lookup
(which orders contain a product) has its own index, since a composite primary key cannot serve a
query on its second column.

**Assumptions:**

- **Many-to-many, not a join entity.** The brief mentions no quantity or line-item attributes, so
  there is nothing for a join entity to carry. If quantities are ever needed this becomes an
  `OrderLine` entity, which is a schema change.
- **`Set`, not `List`.** Hibernate treats a `List` without an order column as a bag and deletes
  then re-inserts every join row on any modification; a `Set` issues targeted inserts and
  deletes. Responses are sorted by id so output is stable between calls.

### Endpoints

```
POST /products      -> 201, the created product
GET  /products      -> all products, each with the ids of the orders containing it
GET  /products/{id} -> one product, or 404
GET  /order/{id}    -> now includes the order's products
```

### The "1 or more products" rule

The brief states an order contains one or more products, but the 10,000 supplied orders had none.
Rather than leave the data contradicting the rule, the seed was backfilled:

- 5,319 products, derived from the distinct order descriptions already present (which were
  generated product names, so they read realistically)
- 20,004 order/product links
- **zero** orders without products, spread evenly at 1, 2 or 3 products each

The backfill is expressed as SQL in changeset `7-product-seed-data` rather than checked in as a
further 25,000 `INSERT` statements, and is tagged `context: dev` alongside the rest of the seed.

The rule is enforced on creation in `OrderService`:

```
POST /order with no products          -> 400  "An order must contain at least one product id"
POST /order with an unknown product   -> 404  "Product 999999 not found"
```

**Assumption:** the rule is enforced in the service layer rather than the schema. "At least one
row in a join table" is not expressible as a database constraint without triggers, and a trigger
was judged disproportionate.

---

## 6. Bonus — CI pipeline and Docker image

### `Dockerfile`

Multi-stage. The build stage compiles from a clean checkout with no local toolchain required, and
extracts Spring Boot's **layered jar** so that dependencies, loader, snapshot dependencies and
application code occupy separate image layers — a code-only change then repushes a few hundred
kilobytes rather than the whole jar. The runtime stage is JRE-only, runs as an unprivileged user
(uid 10001), and sets `-XX:MaxRAMPercentage=75.0` so the JVM sizes its heap from the container's
limit rather than the host's. Final image: 477MB, against a 629MB JDK base.

Verified locally: the image builds, runs against PostgreSQL, serves every endpoint correctly, and
`docker exec ... id -u` confirms it is not running as root.

### `.github/workflows/ci.yml`

Two jobs. **`verify`** validates the Gradle wrapper (a tampered wrapper jar would otherwise
execute arbitrary code in CI), runs `spotlessCheck` as its own step so formatting failures are
visible in the job list, runs `./gradlew build`, publishes test and coverage reports as artifacts,
and then does what unit tests cannot: boots the application against a real PostgreSQL service
container to prove the **migrations apply and the endpoints serve traffic** — once with seed data,
and once with `LIQUIBASE_CONTEXTS=prod` against an empty database to prove the schema changesets
do not depend on sample rows.

**`image`** builds the image with layer caching, smoke-tests it against PostgreSQL *before* any
push, asserts it does not run as root, scans it with Trivy, and publishes to GHCR — never on pull
requests.

**Not executed.** This project is not a git repository and has no remote, so the workflow is
validated as well-formed YAML but has never run. It requires a repository and a first push.

**One deliberate choice:** the Trivy scan is set to `exit-code: 0`, so it reports without failing
the build. Base images routinely carry unfixed CVEs, and a pipeline that turns red on an upstream
project's release cadence trains people to ignore it. Gating on CRITICAL is a one-character
change if that is the preferred policy.

---

## 7. Deliberately not done

Each of these was identified and consciously left, rather than missed.

| Item | Reason |
|---|---|
| **Pagination on the collection endpoints** | The highest-value remaining change, but it alters the documented contract. |
| **Request DTOs with `@Valid`** | Controllers still bind entities directly, so a client can send an `id` the spec forbids, and `cascade = ALL` means a nested `orders` array on `POST /customer` would be persisted. The correct fix is dedicated request DTOs — a larger change than the tasks required. |
| **Renaming the `"order"` table** | The quoting is correct and consistent, and singular naming is used throughout. Renaming would be churn. |
| **A JaCoCo coverage threshold** | Coverage is reported but never enforced. Choosing the number is a team policy decision. |
| **Removing the unused Liquibase Gradle plugin** | Applied but never configured; harmless, and may be wanted for CI tasks. |
| **Upgrading `@faker-js/faker`** | `faker.name` is deprecated in v8, but the dependency is pinned to `^7`, where it remains valid. |
| **An actuator health endpoint** | Would improve the container's orchestration story; outside the brief. |

---

## 8. Running it

Unchanged from the original README:

```shell
docker run -d --name postgres --restart always \
  -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data -p 5433:5432 \
  postgres:16.2 postgres -c wal_level=logical

./gradlew bootRun
```

Or as a container:

```shell
docker build -t store:local .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/store \
  store:local
```

Tests (no database required — all 22 are unit or web-slice tests):

```shell
./gradlew build
```

To deploy without sample data, set `LIQUIBASE_CONTEXTS=prod`.
