CREATE TABLE users
(
    -- Not using auto incrementing ids for a few reasons
    -- First, it's a different field type depending on the SQL variant
    -- (SQLite: ROWID, Postgresql: SERIAL, MySQL: BIGINT AUTO_INCREMENT)
    -- Which means I'd have to have a different migration per SQL variant
    --  or I'd need a dedicated tool that generates sql from XML/JSON descriptions
    --  (e.g. Liquibase, some ORMs)
    -- For this simple project, I didn't want to get bogged down setting up additional
    --  tooling and an ORM isn't necessary
    -- Additionally, if we wanted to migrate the datastore to a NoSQL database then
    --  having GUIDs can sometimes make that transition simpler since many NoSQL
    --  databases don't support auto incrementing ids (most are eventually consistent)
    --  and often when a NoSQL database is used there are multiple servers making it
    --  difficult to do ID generation on the server side
    --
    -- For my ids I'm using KSUID (https://github.com/segmentio/ksuid)
    -- I chose KSUID for a few reasons:
    --  * It's not a very complicated standard, so maintaining or porting it is trivial
    --    * The hardest part is interfacing with a secure PRNG (while optional, it's generally recommended)
    --    * One way to make it easier to write a KSUID library is to accept a PRNG that generates 64-bit ints
    --      * The app can then pass in a secure PRNG - this may be especially important when dealing with VMs/containers
    --         with limited entropy (sometimes VMs/containers need to seed their PRNGs)
    --    * It's sortable by time (up to a second of precision) so it can give a general ordering
    --      * While it's not a perfect ordering, it's good enough for most use cases
    --          (e.g. get users created when there was a bug in production)
    --    * It's compact (27 characters vs UUID's 36 characters)
    --    * The simplicity allows it to be easily extended if needed (e.g. add another 64-bits of entropy to make a 192 bit value)
    --
    -- If data size/performance was a concern, KSUIDs can be stored in a 128 bit binary format.
    -- A KSUID is a 64-bit timestamp + 64-bit random number. Essentially it's two BIGINT fields. The text version is just Base62 encoded
    -- Many SQL databases support composit primary keys (keys made from multiple fields), so it could be stored as two numbers
    --
    -- For simplicity, I just went with storing only the Base62 text format instead of using the binary format
    -- One nice thing about KSUIDs is that even though they encode more information than UUIDv4, their text encoding is smaller
    --  (KSUIDS have 128 bits of data but 27 length strings, UUIDv4 has 122 bits of data but a 36 character length)
    --
    -- For those confused as to why UUIDv4 only has 122 bits of data, it's due to how they are encoded.
    -- Their encoding overwrites data bits in order to say "I'm a valid UUIDv4."
    -- This overwriting loses 6 bits of data.
    --
    -- Since KSUIDs are always 27 length strings, I'm using a CHARACTER(27).
    -- I'm using CHARACTER and not CHAR since CHARACTER is more portable than CHAR (e.g. SQLite doesn't support CHAR)
    --
    -- For more of my thoughts on globally unique identifiers, see my blog post: https://matthewtolman.com/article/overview-of-globally-unique-identifiers#__top
    id CHARACTER(27) NOT NULL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    -- Will be storing a password hash
    password TEXT NOT NULL,
    -- Counting balance in pennies to use ints and avoid IEEE rounding errors
    -- SQLite does not have an enum or bit type
    -- Using INT instead and mapping value in code
    -- 1 - active
    -- 0 - inactive
    status INT NOT NULL DEFAULT 1
);
--;;
CREATE TABLE statuses
(
    id INT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL
);

--;;
CREATE TABLE operations
(
    -- operations are determined by migrations, so IDs can be managed manually
    id INT NOT NULL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    -- Counting cost in pennies so we can use ints
    -- Also avoids IEEE rounding errors
    cost INT NOT NULL
);
--;;
CREATE TABLE transactions
(
    id CHARACTER(27) NOT NULL PRIMARY KEY,
    operation_id INT NULL,
    user_id CHARACTER(27) NOT NULL default '000000000000000000000000000',
    status INT NOT NULL DEFAULT 1,
    amount INT NULL,
    operation_response TEXT NULL,
    user_balance INT NOT NULL CHECK ( user_balance >= 0 ),
    ctime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Timestamps are only precise to the second in SQLite
    -- Our ids are only precise to the second in ordering
    -- So, we add an extra "order_id" to tell us how to order transactions for a user
    order_id INT NOT NULL DEFAULT 1
);
--;;
CREATE INDEX idx_transactions_user
ON transactions (user_id);
--;;
CREATE INDEX idx_transactions_ctime
ON transactions (user_id, order_id DESC);
--;;
CREATE TABLE sessions
(
    -- Not using KSUID for session ids since session ids should be a token
    -- Session IDs should definitely not be predictable
    -- However, the timestamp portion of KSUID does make it more predictable
    --
    -- I'm using a session table so that sessions can be revoked by the server
    -- Revoking sessions happens by simply deleting the record
    -- However, with stateless JWTs there is no record to delete, only an expiration time in the JWT
    -- To make a JWT revokable state needs to be added somewhere, hence the sessions table
    -- For now, I'm hard deleting sessions since I don't want to track them for the following reasons:
    --   * We don't want to ever "restore" an old session (that's a security issue, e.g. when a device is stolen)
    --   * I'm using other means to track logins, logouts, and login attempts. Those other means should be used for risk analysis
    --   * At some point session-specific data should be added (e.g. a device id to mitigate session hijacking)
    session_id TEXT NOT NULL PRIMARY KEY,
    user_id CHARACTER(27) NOT NULL,
    -- Used to determine what access level the use has
    -- useful for enforcing zoned security; e.g enforce multi-factor authentication (MFA) for account controls (delete, change password, etc)
    auth_level INT NOT NULL,
    -- Time in a high-sensitivity zone (e.g. password change) should expire and the user should get "downgraded" to a lower zone
    last_auth_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ctime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
--;;
-- At some point this would be a candidate to move into another service or at least another database
-- Queries to this table may get expensive since we'll be trying to rate limit attempts by IP, device id, etc. over a time window
CREATE TABLE auth_events
(
    -- Using a secure hash of the attempted username
    -- Sometimes people will try different emails/personal information that we don't want to store in plain text
    -- (e.g. sometimes users enter usernames for other websites when they can't remember)
    -- If the database gets compromised we don't want our auth tracking to compromise other user accounts (e.g. their bank account)
    username_fingerprint TEXT NOT NULL,
    -- IP of remote address (useful for correlating events)
    ip_fingerprint TEXT NOT NULL,
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- We're mostly interested in failed attempts vs successful attempts
    attempt_success INT NOT NULL
);

-- SQLite doesn't support foreign keys by default
-- I added these here in comments though to show how I would enforce foreign key relations in the DB layer

-- -- For our users table, our statuses should be restricted
-- -- We want to restrict status deletion and cascade updates
-- ALTER TABLE users
--     ADD CONSTRAINT UserStatuses
--     FOREIGN KEY (status) REFERENCES statuses(id)
--     ON DELETE RESTRICT
--     ON UPDATE CASCADE;

-- ALTER TABLE transactions
--     ADD CONSTRAINT TransactionStatuses
--     FOREIGN KEY (status) REFERENCES statuses(id)
--     ON DELETE RESTRICT
--     ON UPDATE CASCADE;

-- -- For our transactions table, we don't want to restrict user data deletion (e.g. privacy law compliance)
-- -- But, we also will want to keep record of what operations are most commonly used, when they're most heavily used, etc
-- -- We also want to keep the user id in sync with any updates to the user
-- -- We do this by having a default "user deleted" value (a placeholder user will be inserted below)
-- -- That way if a user is ever fully deleted, we mark the transactions as belonging to a deleted user
-- -- If the user id ever changes, we'll also cascade that change
-- ALTER TABLE transactions
--     ADD CONSTRAINT UserTransactions
--     FOREIGN KEY (user_id) REFERENCES users(id)
--     ON DELETE SET DEFAULT
--     ON UPDATE CASCADE;
--
-- -- Operations are manually managed by developers. We shouldn't be changing them without a plan
-- -- By restricting changes we avoid developers accidentally breaking existing transactions
-- ALTER TABLE transactions
--     ADD CONSTRAINT OperationsTransactions
--     FOREIGN KEY (operation_id) REFERENCES operations(id)
--     ON DELETE RESTRICT
--     ON UPDATE RESTRICT;
--
-- -- For sessions, we don't want to restrict user deletion if there is an active session
-- -- (user deletion could happen for privacy law compliance)
-- -- However, we would want to invalidate any sessions for a user that doesn't exist
-- -- Additionally, if we ever update our IDs to a different GUID/format, we'd want to cascade that change to avoid all users being logged out
-- ALTER TABLE sessions
--     ADD CONSTRAINT UserSession
--     FOREIGN KEY (user_id) REFERENCES users(id)
--     ON DELETE CASCADE
--     ON UPDATE CASCADE;
