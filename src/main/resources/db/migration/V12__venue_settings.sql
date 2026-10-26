alter table venue
    add column country  char(2) not null default 'FR',
    add column currency char(3) not null default 'EUR',
    add constraint ck_venue_country check (country ~ '^[A-Z]{2}$'),
    add constraint ck_venue_currency check (currency ~ '^[A-Z]{3}$');