-- name: user-creds-by-username
-- Retrieves the user credentials (id, password hash) by username
SELECT id, password, status from users WHERE username = :username AND username <> '<deleted user>';

-- name: create-user!
-- Creates a new user
INSERT OR IGNORE INTO users (id, username, password, status) VALUES (:id, :username, :password, 1);

-- name: user-balance
-- Gets the balance for the user. All users start with $5.00 by default
SELECT user_balance as balance, order_id
 FROM transactions
 WHERE user_id = :id
 UNION
 SELECT 500 as user_balance, 0 as order_id
 ORDER BY order_id DESC
 LIMIT 1;

-- name: user-can-do-op
-- A quick check to see if a user can perform an operation. Does not change balance
SELECT balance > (SELECT cost FROM operations WHERE id = :op) as can_do
FROM (SELECT user_balance as balance, order_id
      FROM transactions
      WHERE user_id = :id
      UNION
      SELECT 500 as user_balance, 0 as order_id
      ORDER BY order_id DESC
      LIMIT 1);

-- name: record-op!
-- Records an operation and adjusts the user balance
INSERT INTO transactions (id, user_id, operation_id, status, amount, operation_response, user_balance, order_id)
SELECT :txid, :user, :op, 1, -(SELECT cost FROM operations WHERE id = :op), :res, balance - (SELECT cost FROM operations WHERE id = :op), order_id + 1
    FROM (SELECT user_balance as balance, order_id
          FROM transactions
          WHERE user_id = :user
          UNION
          SELECT 500 as user_balance, 0 as order_id
          ORDER BY order_id DESC
          LIMIT 1);

-- name: add-balance!
-- Adds a balance to a user and records a transaction
INSERT INTO transactions (id, user_id, operation_id, status, amount, operation_response, user_balance, order_id)
SELECT :txid, :user, NULL, 1, :amount, NULL, balance + :amount, order_id + 1
FROM (SELECT user_balance as balance, order_id
      FROM transactions
      WHERE user_id = :user
      UNION
      SELECT 500 as user_balance, 0 as order_id
      ORDER BY order_id DESC
      LIMIT 1);

-- name: history
-- Gets the history for a user
SELECT t.id as id, t.order_id as cursor, o.type as operation, t.user_balance as balance, t.status as status, t.amount as amount, t.operation_response as response FROM transactions t
           LEFT JOIN operations o ON t.operation_id = o.id
WHERE user_id = :user AND order_id > :cursor
ORDER BY order_id ASC
LIMIT :page_size;

-- name: history-end
-- Gets the size of history for a user
SELECT order_id as id FROM transactions WHERE user_id = :user ORDER BY order_id DESC LIMIT 1;
