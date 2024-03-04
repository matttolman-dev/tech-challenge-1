--;;
INSERT INTO operations
(id, type, cost)
VALUES
    (1, 'addition', 10),
    (2, 'subtraction', 10),
    (3, 'multiplication', 15),
    (4, 'division', 20),
    (5, 'square_root', 40),
    (6, 'random_string', 100)
;
--;;
-- Create a "deleted user" placeholder for our (theoretical) foreign key constraints

INSERT INTO users
(id, username, password, status) VALUES ('000000000000000000000000000', '<deleted user>', '<deleted password>', 0);

--;;
INSERT INTO transactions
(id, user_balance) VALUES ('000000000000000000000000000', 0);

--;;

INSERT INTO statuses
(id, name) VALUES (0, "inactive"), (1, "active");