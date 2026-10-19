create table app_user (
                          id             uuid primary key,
                          email          varchar(254) not null,
                          display_name   varchar(120) not null,
                          password_hash  varchar(255) not null,
                          platform_admin boolean      not null default false,
                          enabled        boolean      not null default true,
                          version        bigint       not null default 0,
                          created_at     timestamptz  not null default now(),
                          updated_at     timestamptz  not null default now()
);
create unique index uq_app_user_email on app_user (lower(email));

create table membership (
                            user_id   uuid        not null references app_user (id) on delete cascade,
                            position  int         not null,
                            tenant_id uuid        not null references tenant (id),
                            venue_id  uuid references venue (id),
                            role      varchar(20) not null,
                            primary key (user_id, position),
                            constraint ck_membership_role check (role in ('OWNER', 'MANAGER', 'WAITER', 'KITCHEN')),
                            constraint ck_membership_scope check ((role = 'OWNER') = (venue_id is null))
);
create unique index uq_membership_venue on membership (user_id, venue_id) where venue_id is not null;
create unique index uq_membership_owner on membership (user_id, tenant_id) where venue_id is null;
create index ix_membership_venue on membership (venue_id);