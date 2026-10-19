create table refresh_token (
                               id         uuid primary key,
                               user_id    uuid        not null references app_user (id) on delete cascade,
                               family_id  uuid        not null,
                               token_hash varchar(64) not null,
                               expires_at timestamptz not null,
                               revoked_at timestamptz,
                               version    bigint      not null default 0,
                               created_at timestamptz not null default now(),
                               updated_at timestamptz not null default now()
);
create unique index uq_refresh_token_hash on refresh_token (token_hash);
create index ix_refresh_token_family on refresh_token (family_id);
create index ix_refresh_token_active_user on refresh_token (user_id) where revoked_at is null;