create table app_users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(255) not null,
    enabled boolean not null,
    created_at timestamp with time zone not null,
    constraint chk_app_users_email_lowercase check (email = lower(email))
);

create table app_user_roles (
    user_id uuid not null references app_users (id) on delete cascade,
    role varchar(32) not null,
    primary key (user_id, role),
    constraint chk_app_user_roles_role check (role in ('USER', 'ADMIN'))
);

create table refresh_token_families (
    id uuid primary key,
    user_id uuid not null references app_users (id) on delete cascade,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create index idx_refresh_token_families_user_id on refresh_token_families (user_id);

create table refresh_sessions (
    id uuid primary key,
    family_id uuid not null references refresh_token_families (id) on delete cascade,
    token_hash char(64) not null unique,
    issued_at timestamp with time zone not null,
    consumed_at timestamp with time zone
);

create index idx_refresh_sessions_family_id on refresh_sessions (family_id);
