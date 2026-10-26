create table venue_tax_rate (
                                id         uuid primary key,
                                tenant_id  uuid          not null references tenant (id),
                                venue_id   uuid          not null references venue (id),
                                name       varchar(80)   not null,
                                rate       numeric(5, 2) not null,
                                sort_order int           not null,
                                version    bigint        not null default 0,
                                created_at timestamptz   not null default now(),
                                updated_at timestamptz   not null default now(),
                                constraint ck_venue_tax_rate_rate check (rate >= 0 and rate <= 100)
);

create unique index uq_venue_tax_rate_venue_lower_name on venue_tax_rate (venue_id, lower(name));
create index ix_venue_tax_rate_venue on venue_tax_rate (venue_id);

insert into venue_tax_rate (id, tenant_id, venue_id, name, rate, sort_order)
select gen_random_uuid(),
       r.tenant_id,
       r.venue_id,
       'VAT ' || trim(trailing '.' from trim(trailing '0' from r.tax_rate::text)) || '%',
       r.tax_rate,
       (row_number() over (partition by r.venue_id order by r.tax_rate)) - 1
from (select distinct v.tenant_id, i.venue_id, i.tax_rate
      from menu_item i
               join venue v on v.id = i.venue_id
      where not i.archived) r;