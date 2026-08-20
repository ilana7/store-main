const { faker}  = require('@faker-js/faker');

const N = 100; // Number of customers
const M = 10_000; // Number of orders

// PostgreSQL escapes a single quote by doubling it. Without this, a generated value
// such as "O'Brien" produces syntactically invalid SQL.
const sqlString = (value) => `'${String(value).replace(/'/g, "''")}'`;

// Generate customers
for (let i = 1; i <= N; i++) {
    console.log(`INSERT INTO customer (id, name) VALUES (${i}, ${sqlString(faker.name.fullName())});`);
}

// Generate orders
for (let i = 1; i <= M; i++) {
    const customerId = Math.ceil(Math.random() * N);
    console.log(`INSERT INTO "order" (id, description, customer_id) VALUES (${i}, ${sqlString(faker.commerce.productName())}, ${customerId});`);
}
