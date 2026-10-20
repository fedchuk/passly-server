create table outbox_event (
                              position       bigint generated always as identity,
                              id             uuid         not null primary key,
                              aggregate_type varchar(40)  not null,
                              aggregate_id   uuid         not null,
                              event_type     varchar(80)  not null,
                              topic          varchar(120) not null,
                              message_key    varchar(120) not null,
                              payload        jsonb        not null,
                              created_at     timestamptz  not null default now(),
                              published_at   timestamptz
);

create index ix_outbox_unpublished on outbox_event (position) where published_at is null;