create table idempotency_key (
                                 user_id         uuid         not null references app_user (id) on delete cascade,
                                 idem_key        varchar(255) not null,
                                 scope           varchar(200) not null,
                                 fingerprint     varchar(64)  not null,
                                 response_status int,
                                 response_body   text,
                                 created_at      timestamptz  not null default now(),
                                 primary key (user_id, idem_key)
);
create index ix_idempotency_key_created on idempotency_key (created_at);