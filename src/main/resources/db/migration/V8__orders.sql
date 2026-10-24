alter table dining_table add constraint uq_dining_table_id_venue unique (id, venue_id);

create table orders (
                        id          uuid primary key,
                        tenant_id   uuid        not null references tenant (id),
                        venue_id    uuid        not null references venue (id),
                        table_id    uuid        not null,
                        status      varchar(12) not null,
                        guests      int,
                        opened_by   uuid        not null references app_user (id),
                        opened_at   timestamptz not null,
                        version     bigint      not null default 0,
                        created_at  timestamptz not null default now(),
                        updated_at  timestamptz not null default now(),
                        constraint fk_orders_table foreign key (table_id, venue_id) references dining_table (id, venue_id),
                        constraint ck_orders_status check (status in ('OPEN', 'CLOSED')),
                        constraint ck_orders_guests check (guests between 1 and 50)
);
create unique index uq_orders_open_table on orders (table_id) where status = 'OPEN';
create index ix_orders_venue_status on orders (venue_id, status, opened_at);

create table order_line (
                            id           uuid primary key,
                            order_id     uuid           not null references orders (id) on delete cascade,
                            position     int            not null,
                            round        int            not null,
                            menu_item_id uuid           not null references menu_item (id),
                            name         varchar(160)   not null,
                            unit_price   numeric(10, 2) not null,
                            tax_rate     numeric(5, 2)  not null,
                            station_id   uuid references station (id),
                            quantity     int            not null,
                            comment      varchar(200),
                            added_by     uuid           not null references app_user (id),
                            added_at     timestamptz    not null,
                            version      bigint         not null default 0,
                            created_at   timestamptz    not null default now(),
                            updated_at   timestamptz    not null default now(),
                            constraint uq_order_line_position unique (order_id, position),
                            constraint ck_order_line_round check (round >= 1),
                            constraint ck_order_line_quantity check (quantity between 1 and 99),
                            constraint ck_order_line_price check (unit_price >= 0)
);

create table order_line_modifier (
                                     line_id     uuid           not null references order_line (id) on delete cascade,
                                     position    int            not null,
                                     modifier_id uuid           not null,
                                     group_name  varchar(120)   not null,
                                     name        varchar(120)   not null,
                                     price_delta numeric(10, 2) not null,
                                     primary key (line_id, position)
);