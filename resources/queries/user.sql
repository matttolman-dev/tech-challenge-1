-- name: user-creds-by-username
-- Retrieves the user credentials (id, password hash) by username
SELECT id, password, status from users WHERE username = :username AND username <> '<deleted user>';

-- name: create-user!
-- Creates a new user
INSERT OR IGNORE INTO users (id, username, password, status) VALUES (:id, :username, :password, 1);

-- name: user-balance
-- Gets the balance for the user
SELECT user_balance as balance, ctime
 FROM transactions
 WHERE user_id = :id
 UNION
 SELECT 500 as user_balance, 0 as ctime
 ORDER BY ctime DESC
 LIMIT 1;

-- name: user-can-do-op
-- A quick check to see if a user can perform an operation. Does not change balance
SELECT balance > (SELECT cost FROM operations WHERE id = :op) as can_do
FROM (SELECT user_balance as balance, ctime
      FROM transactions
      WHERE user_id = :id
      UNION
      SELECT 500 as user_balance, 0 as ctime
      ORDER BY ctime DESC
      LIMIT 1);

-- name: record-op!
-- Records an operation and adjusts the user balance
INSERT INTO transactions (id, user_id, operation_id, status, amount, operation_response, user_balance)
SELECT :txid, :user, :op, 1, -(SELECT cost FROM operations WHERE id = :op), :res, balance - (SELECT cost FROM operations WHERE id = :op)
    FROM (SELECT user_balance as balance, ctime
          FROM transactions
          WHERE user_id = :user
          UNION
          SELECT 500 as user_balance, 0 as ctime
          ORDER BY ctime DESC
          LIMIT 1);

-- name: add-balance!
-- Adds a balance to a user and records a transaction
BEGIN TRANSACTION;

UPDATE users SET balance = balance + :amount WHERE id = :user;

INSERT INTO transactions (id, operation_id, status, amount, operation_response, user_balance)
SELECT :txid, NULL, 1, :amount, NULL, balance
FROM users WHERE id = :user;

COMMIT;
END;
