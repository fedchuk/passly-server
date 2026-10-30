alter table venue
    add column time_zone varchar(64) not null default 'Europe/Paris';

update venue
set time_zone = case country
                    when 'CH' then 'Europe/Zurich'
                    when 'ES' then 'Europe/Madrid'
                    when 'PT' then 'Europe/Lisbon'
                    when 'IT' then 'Europe/Rome'
                    when 'DE' then 'Europe/Berlin'
                    when 'AT' then 'Europe/Vienna'
                    when 'BE' then 'Europe/Brussels'
                    when 'NL' then 'Europe/Amsterdam'
                    when 'LU' then 'Europe/Luxembourg'
                    when 'GB' then 'Europe/London'
                    when 'IE' then 'Europe/Dublin'
                    when 'PL' then 'Europe/Warsaw'
                    when 'CZ' then 'Europe/Prague'
                    when 'UA' then 'Europe/Kyiv'
                    else 'Europe/Paris'
    end;

create table voucher (
                         id                uuid primary key,
                         tenant_id         uuid          not null references tenant (id),
                         venue_id          uuid          not null references venue (id),
                         code              varchar(8)    not null,
                         name              varchar(80)   not null,
                         kind              varchar(12)   not null,
                         percent           numeric(5, 2),
                         amount            numeric(10, 2),
                         valid_until       date          not null,
                         issued_at         timestamptz   not null,
                         redeemed_order_id uuid references orders (id),
                         redeemed_at       timestamptz,
                         version           bigint        not null default 0,
                         created_at        timestamptz   not null default now(),
                         updated_at        timestamptz   not null default now(),
                         constraint uq_voucher_venue_code unique (venue_id, code),
                         constraint ck_voucher_code check (code ~ '^[A-HJ-NP-Z2-9]{8}$'),
                         constraint ck_voucher_kind check (kind in ('REUSABLE', 'SINGLE_USE')),
                         constraint ck_voucher_value check (
                             (percent is not null and amount is null and percent > 0 and percent <= 100)
                                 or (percent is null and amount is not null and amount > 0)
                             ),
                         constraint ck_voucher_redeemed check (
                             (redeemed_order_id is null and redeemed_at is null)
                                 or (redeemed_order_id is not null and redeemed_at is not null and kind = 'SINGLE_USE')
                             )
);

create index ix_voucher_venue_issued on voucher (venue_id, issued_at desc);

alter table orders
    add column voucher_id         uuid,
    add column voucher_code       varchar(8),
    add column voucher_name       varchar(80),
    add column voucher_kind       varchar(12),
    add column voucher_percent    numeric(5, 2),
    add column voucher_amount     numeric(10, 2),
    add column voucher_applied_by uuid references app_user (id),
    add column voucher_applied_at timestamptz,
    add constraint ck_orders_voucher check (
        (voucher_id is null and voucher_code is null and voucher_name is null and voucher_kind is null
            and voucher_percent is null and voucher_amount is null
            and voucher_applied_by is null and voucher_applied_at is null)
            or (voucher_id is not null and voucher_code is not null and voucher_name is not null
            and voucher_kind in ('REUSABLE', 'SINGLE_USE')
            and ((voucher_percent is not null and voucher_amount is null) or (voucher_percent is null and voucher_amount is not null))
            and voucher_applied_by is not null and voucher_applied_at is not null)
        );