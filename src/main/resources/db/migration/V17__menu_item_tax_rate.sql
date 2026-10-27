alter table menu_item
    add column tax_rate_id uuid references venue_tax_rate (id);

create index ix_menu_item_tax_rate on menu_item (tax_rate_id);

insert into venue_tax_rate (id, tenant_id, venue_id, name, rate, sort_order)
select gen_random_uuid(),
       r.tenant_id,
       r.venue_id,
       'VAT ' || trim(trailing '.' from trim(trailing '0' from r.tax_rate::text)) || '%',
       r.tax_rate,
       (select coalesce(max(t.sort_order), -1) from venue_tax_rate t where t.venue_id = r.venue_id)
           + row_number() over (partition by r.venue_id order by r.tax_rate)
from (select distinct i.tenant_id, i.venue_id, i.tax_rate
      from menu_item i
      where not i.archived
        and not exists (select 1 from venue_tax_rate t where t.venue_id = i.venue_id and t.rate = i.tax_rate)) r
    on conflict do nothing;

with recursive effective as (select c.id, c.tax_rate_id
                             from category c
                             where c.parent_id is null
                             union all
                             select c.id, coalesce(c.tax_rate_id, e.tax_rate_id)
                             from category c
                                      join effective e on c.parent_id = e.id)
update menu_item i
set tax_rate_id = (select t.id
                   from venue_tax_rate t
                   where t.venue_id = i.venue_id
                     and t.rate = i.tax_rate
                   order by t.sort_order
    limit 1)
from effective e
    join venue_tax_rate inherited on inherited.id = e.tax_rate_id
where i.category_id = e.id
  and not i.archived
  and inherited.rate <> i.tax_rate;

alter table menu_item
    alter column tax_rate drop not null;