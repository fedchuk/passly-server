create table venue_accepted_currency (
                                         venue_id    uuid           not null references venue (id) on delete cascade,
                                         currency    char(3)        not null,
                                         position    int            not null,
                                         rate        numeric(13, 6) not null,
                                         rate_set_at timestamptz    not null,
                                         primary key (venue_id, currency),
                                         constraint uq_venue_accepted_currency_position unique (venue_id, position),
                                         constraint ck_venue_accepted_currency_code check (currency ~ '^[A-Z]{3}$'),
                                         constraint ck_venue_accepted_currency_rate check (rate > 0)
);