alter table category
    add column tax_rate_id uuid references venue_tax_rate (id);

create index ix_category_tax_rate on category (tax_rate_id);

insert into venue_tax_rate (id, tenant_id, venue_id, name, rate, sort_order)
select gen_random_uuid(), v.tenant_id, v.id, p.name, p.rate, p.sort_order
from venue v
         join (values ('FR', 'Food and soft drinks', 10.00, 0),
                      ('FR', 'Alcohol', 20.00, 1),
                      ('FR', 'Reduced', 5.50, 2),
                      ('CH', 'Standard', 8.10, 0),
                      ('CH', 'Reduced', 2.60, 1)) as p(country, name, rate, sort_order)
              on p.country = v.country
where not exists (select 1 from venue_tax_rate r where r.venue_id = v.id);

insert into venue_tax_rate (id, tenant_id, venue_id, name, rate, sort_order)
select gen_random_uuid(), v.tenant_id, v.id, 'Standard', 10.00, 0
from venue v
where not exists (select 1 from venue_tax_rate r where r.venue_id = v.id);

with recursive subtree as (select c.id as root_id, c.id
                           from category c
                           where c.parent_id is null
                           union all
                           select s.root_id, c.id
                           from category c
                                    join subtree s on c.parent_id = s.id),
               item_rates as (select s.root_id, i.tax_rate, count(*) as items
                              from subtree s
                                       join menu_item i on i.category_id = s.id and not i.archived
                              group by s.root_id, i.tax_rate),
               usual_rate as (select distinct on (root_id) root_id, tax_rate
from item_rates
order by root_id, items desc, tax_rate)
update category c
set tax_rate_id = coalesce(
        (select r.id
         from usual_rate u
                  join venue_tax_rate r on r.venue_id = c.venue_id and r.rate = u.tax_rate
         where u.root_id = c.id
         order by r.sort_order
        limit 1),
        (select r.id
         from venue_tax_rate r
         where r.venue_id = c.venue_id
         order by r.sort_order
         limit 1))
where c.parent_id is null;

alter table category
    add constraint ck_category_root_tax_rate check (parent_id is not null or tax_rate_id is not null);