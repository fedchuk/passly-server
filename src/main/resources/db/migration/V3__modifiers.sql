create table modifier_group (
                                id          uuid primary key,
                                tenant_id   uuid         not null references tenant (id),
                                venue_id    uuid         not null references venue (id),
                                name        varchar(120) not null,
                                min_select  int          not null,
                                max_select  int          not null,
                                version     bigint       not null default 0,
                                created_at  timestamptz  not null default now(),
                                updated_at  timestamptz  not null default now(),
                                constraint ck_modifier_group_limits check (min_select >= 0 and max_select >= 1 and min_select <= max_select)
);
create index ix_modifier_group_venue on modifier_group (venue_id);

create table modifier (
                          id          uuid primary key,
                          group_id    uuid           not null references modifier_group (id) on delete cascade,
                          name        varchar(120)   not null,
                          price_delta numeric(10, 2) not null default 0,
                          position    int            not null
);
create index ix_modifier_group on modifier (group_id, position);
create unique index uq_modifier_group_name on modifier (group_id, lower(name));

create table menu_item_modifier_group (
                                          menu_item_id uuid not null references menu_item (id) on delete cascade,
                                          group_id     uuid not null references modifier_group (id) on delete restrict,
                                          position     int  not null,
                                          primary key (menu_item_id, position)
);
create index ix_item_modifier_group_group on menu_item_modifier_group (group_id);