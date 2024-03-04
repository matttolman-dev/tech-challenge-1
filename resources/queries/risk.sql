-- name: auth-risk-rating
-- Determines how risky an auth login request is
-- We do it by seeing if a there is a match
SELECT COUNT(*) >= 8 as risky
    FROM auth_events
              WHERE attempt_success = 0
                AND (username_fingerprint = :uname
                  OR ip_fingerprint = :ip
                  OR device_fingerprint = :device)
                AND time(attempt_time) >= time('now', '-30 minutes');

-- name: auth-log-attempt!
-- Logs an authentication attempt
INSERT INTO auth_events
    (username_fingerprint, ip_fingerprint, device_fingerprint, attempt_success)
    VALUES (:uname, :ip, :device, :success);
