alter table orders
    add column closed_by uuid references app_user (id),
    add column closed_at timestamptz,
    add constraint ck_orders_closed check (
        (status = 'OPEN' and closed_by is null and closed_at is null)
            or (status = 'CLOSED' and closed_by is not null and closed_at is not null)
        );

create table order_payment (
                               id         uuid primary key,
                               order_id   uuid           not null references orders (id) on delete cascade,
                               position   int            not null,
                               method     varchar(12)    not null,
                               amount     numeric(10, 2) not null,
                               taken_by   uuid           not null references app_user (id),
                               taken_at   timestamptz    not null,
                               version    bigint         not null default 0,
                               created_at timestamptz    not null default now(),
                               updated_at timestamptz    not null default now(),
                               constraint uq_order_payment_position unique (order_id, position),
                               constraint ck_order_payment_method check (method in ('CASH', 'CARD')),
                               constraint ck_order_payment_amount check (amount > 0)
);