alter table order_payment
    add column tendered_amount   numeric(10, 2),
    add column tendered_currency varchar(3),
    add column exchange_rate     numeric(13, 6),
    add column change_given      numeric(10, 2) not null default 0;

update order_payment p
set tendered_amount   = p.amount,
    tendered_currency = v.currency
    from orders o
         join venue v on v.id = o.venue_id
where o.id = p.order_id;

alter table order_payment
    alter column tendered_amount set not null,
alter column tendered_currency set not null,
    add constraint ck_order_payment_tendered check (tendered_amount > 0),
    add constraint ck_order_payment_tendered_currency check (tendered_currency ~ '^[A-Z]{3}$'),
    add constraint ck_order_payment_rate check (exchange_rate is null or exchange_rate > 0),
    add constraint ck_order_payment_change check (change_given >= 0);