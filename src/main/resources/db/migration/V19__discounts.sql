create table venue_discount (
                                id         uuid primary key,
                                tenant_id  uuid          not null references tenant (id),
                                venue_id   uuid          not null references venue (id),
                                name       varchar(80)   not null,
                                percent    numeric(5, 2) not null,
                                sort_order int           not null,
                                version    bigint        not null default 0,
                                created_at timestamptz   not null default now(),
                                updated_at timestamptz   not null default now(),
                                constraint ck_venue_discount_percent check (percent > 0 and percent <= 100)
);

create unique index uq_venue_discount_venue_lower_name on venue_discount (venue_id, lower(name));
create index ix_venue_discount_venue on venue_discount (venue_id);

alter table orders
    add column discount_id         uuid,
    add column discount_name       varchar(80),
    add column discount_percent    numeric(5, 2),
    add column discount_applied_by uuid references app_user (id),
    add column discount_applied_at timestamptz,
    add constraint ck_orders_discount check (
        (discount_id is null and discount_name is null and discount_percent is null
            and discount_applied_by is null and discount_applied_at is null)
            or (discount_id is not null and discount_name is not null and discount_percent > 0 and discount_percent <= 100
            and discount_applied_by is not null and discount_applied_at is not null)
        );