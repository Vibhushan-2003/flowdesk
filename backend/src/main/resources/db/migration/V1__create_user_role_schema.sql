CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,

    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_users_email_ci
    ON users (LOWER(email));

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE RESTRICT
);

INSERT INTO roles (id, code, description) VALUES
(
    '00000000-0000-0000-0000-000000000001',
    'EMPLOYEE',
    'Standard employee who can submit and track requests'
),
(
    '00000000-0000-0000-0000-000000000002',
    'SUPPORT_ENGINEER',
    'Support engineer who handles assigned IT requests'
),
(
    '00000000-0000-0000-0000-000000000003',
    'TEAM_LEAD',
    'Support team lead responsible for supervision and escalation'
),
(
    '00000000-0000-0000-0000-000000000004',
    'ADMIN',
    'Platform administrator responsible for system configuration'
);