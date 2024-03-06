-- name: user-creds-by-username
-- Retrieves the user credentials (id, password hash) by username
SELECT id, password, status
from users
WHERE username = :username
  AND username <> '<deleted user>';

-- name: create-user!
-- Creates a new user
INSERT OR IGNORE INTO users (id, username, password, status)
VALUES (:id, :username, :password, 1);

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
SELECT :txid,
       :user,
       :op,
       1,
       -(SELECT cost FROM operations WHERE id = :op),
       :res,
       balance - (SELECT cost FROM operations WHERE id = :op),
       order_id + 1
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
SELECT :txid,
       :user,
       NULL,
       1,
       :amount,
       NULL,
       balance + :amount,
       order_id + 1
FROM (SELECT user_balance as balance, order_id
      FROM transactions
      WHERE user_id = :user
      UNION
      SELECT 500 as user_balance, 0 as order_id
      ORDER BY order_id DESC
      LIMIT 1);

-- name: history
-- Gets the history for a user
SELECT t.ctime              as time,
       t.id                 as id,
       t.order_id           as 'order',
       o.type               as operation,
       t.user_balance       as balance,
       t.status             as status,
       t.amount             as amount,
       t.operation_response as response
FROM transactions t
         LEFT JOIN operations o ON t.operation_id = o.id
WHERE user_id = :user
-- Using a descending sort to show the most recent history item first
ORDER BY order_id DESC
LIMIT :page_size
    -- The offset model isn't very efficient since it still retrieves the skipped entries
    -- However, it does let us easily use a "skip to page" pagination
    -- A more efficient solution would be to return a cursor and use that
    -- For instance, our order id can be a cursor
    -- The downside is that our pagination would have to be linear and not random access
    -- Or we would have to use infinite scrolling instead (which also doesn't need as much bounds detection)
    -- Personally, I would use infinite scrolling for the history, but the ask was to
    --   implement pagination specifically, so I went with the offset model
OFFSET :page_size * :page;

-- name: history-bounds
-- Gets the size of history for a user
SELECT COUNT(*) as total
FROM transactions
WHERE user_id = :user;

-- name: search-history
-- Searches history for a user
SELECT t.ctime              as time,
       t.id                 as id,
       t.order_id           as 'order',
       o.type               as operation,
       t.user_balance       as balance,
       t.status             as status,
       t.amount             as amount,
       t.operation_response as response
FROM transactions t
         LEFT JOIN operations o ON t.operation_id = o.id
WHERE user_id = :user
  AND (o.type LIKE :filter
      OR t.operation_response LIKE :filter
      OR (t.operation_id IS NULL AND 'added funds' LIKE :filter))
ORDER BY order_id DESC
LIMIT :page_size
OFFSET :page_size * :page;

-- name: search-bounds
-- Gets the boundaries for a search
SELECT COUNT(*) as total
FROM transactions t
         LEFT JOIN operations o ON t.operation_id = o.id
WHERE user_id = :user
  AND (o.type LIKE :filter
    OR t.operation_response LIKE :filter
    OR (t.operation_id IS NULL AND 'added funds' LIKE :filter))
ORDER BY order_id DESC;
