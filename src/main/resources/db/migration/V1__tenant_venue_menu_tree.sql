create table tenant (
                        id          uuid primary key,
                        name        varchar(160) not null,
                        version     bigint       not null default 0,
                        created_at  timestamptz  not null default now(),
                        updated_at  timestamptz  not null default now()
);

create table venue (
                       id           uuid primary key,
                       tenant_id    uuid         not null references tenant (id),
                       name         varchar(160) not null,
                       menu_version bigint       not null default 0,
                       version      bigint       not null default 0,
                       created_at   timestamptz  not null default now(),
                       updated_at   timestamptz  not null default now()
);
create index ix_venue_tenant on venue (tenant_id);

create table station (
                         id          uuid primary key,
                         tenant_id   uuid        not null references tenant (id),
                         venue_id    uuid        not null references venue (id),
                         name        varchar(80) not null,
                         sort_order  int         not null default 0,
                         version     bigint      not null default 0,
                         created_at  timestamptz not null default now(),
                         updated_at  timestamptz not null default now(),
                         constraint uq_station_venue_name unique (venue_id, name)
);

create table category (
                          id          uuid primary key,
                          tenant_id   uuid         not null references tenant (id),
                          venue_id    uuid         not null references venue (id),
                          parent_id   uuid references category (id) on delete restrict,
                          name        varchar(120) not null,
                          sort_order  int          not null default 0,
                          station_id  uuid references station (id) on delete restrict,
                          active      boolean      not null default true,
                          version     bigint       not null default 0,
                          created_at  timestamptz  not null default now(),
                          updated_at  timestamptz  not null default now(),
                          constraint ck_category_not_own_parent check (parent_id is null or parent_id <> id)
);
create index ix_category_venue_parent on category (venue_id, parent_id, sort_order);