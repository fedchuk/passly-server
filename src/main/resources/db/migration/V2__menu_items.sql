create table menu_item (
                           id           uuid primary key,
                           tenant_id    uuid           not null references tenant (id),
                           venue_id     uuid           not null references venue (id),
                           category_id  uuid references category (id) on delete restrict,
                           name         varchar(160)   not null,
                           description  text,
                           price        numeric(10, 2) not null check (price >= 0),
                           tax_rate     numeric(5, 2)  not null check (tax_rate between 0 and 100),
                           station_id   uuid references station (id) on delete restrict,
                           sort_order   int            not null default 0,
                           available    boolean        not null default true,
                           archived     boolean        not null default false,
                           version      bigint         not null default 0,
                           created_at   timestamptz    not null default now(),
                           updated_at   timestamptz    not null default now(),
                           constraint ck_menu_item_placement check (
                               (archived and category_id is null) or (not archived and category_id is not null)
                               )
);

create index ix_menu_item_category on menu_item (category_id, sort_order) where not archived;
create index ix_menu_item_venue on menu_item (venue_id) where not archived;