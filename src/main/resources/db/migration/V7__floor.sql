alter table venue add column floor_version bigint not null default 0;

create table dining_area (
                             id          uuid primary key,
                             tenant_id   uuid        not null references tenant (id),
                             venue_id    uuid        not null references venue (id),
                             name        varchar(80) not null,
                             sort_order  int         not null default 0,
                             version     bigint      not null default 0,
                             created_at  timestamptz not null default now(),
                             updated_at  timestamptz not null default now(),
                             constraint uq_dining_area_id_venue unique (id, venue_id)
);
create unique index uq_dining_area_venue_name on dining_area (venue_id, lower(name));

create table dining_table (
                              id          uuid primary key,
                              tenant_id   uuid        not null references tenant (id),
                              venue_id    uuid        not null references venue (id),
                              area_id     uuid        not null,
                              label       varchar(20) not null,
                              seats       int         not null,
                              sort_order  int         not null default 0,
                              active      boolean     not null default true,
                              plan_x      int,
                              plan_y      int,
                              version     bigint      not null default 0,
                              created_at  timestamptz not null default now(),
                              updated_at  timestamptz not null default now(),
                              constraint fk_dining_table_area foreign key (area_id, venue_id) references dining_area (id, venue_id),
                              constraint ck_dining_table_seats check (seats between 1 and 50),
                              constraint ck_dining_table_plan check ((plan_x is null) = (plan_y is null))
);
create unique index uq_dining_table_venue_label on dining_table (venue_id, lower(label));
create index ix_dining_table_area on dining_table (area_id, sort_order);