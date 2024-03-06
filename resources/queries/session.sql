-- name: session-by-id
-- Grabs session information based on the id
SELECT user_id, last_auth_time, ctime, auth_level FROM sessions WHERE session_id = :id;

-- name: create-session!
-- Creates a new session
INSERT INTO sessions (session_id, user_id, auth_level)
VALUES (:session, :user, :level);

-- name: set-session-auth-level!
-- Changes session auth level
UPDATE sessions SET auth_level = :auth_level, last_auth_time = CURRENT_TIMESTAMP WHERE session_id = :id;

-- name: clear-session!
-- Clears a session from storage
DELETE FROM sessions WHERE session_id = :id;

-- name: cleanup-sessions!
-- Clears old sessions from storage
DELETE FROM sessions
       WHERE time('now', '-40 hours') > time(ctime);