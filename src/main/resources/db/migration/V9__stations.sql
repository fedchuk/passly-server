update menu_item set station_id = null where archived and station_id is not null;

insert into station (id, tenant_id, venue_id, name, sort_order)
select gen_random_uuid(), v.tenant_id, v.id, d.name, d.sort_order
from venue v
         cross join (values ('Kitchen', 0), ('Bar', 1)) as d(name, sort_order)
where not exists (select 1 from station s where s.venue_id = v.id);

insert into station (id, tenant_id, venue_id, name, sort_order)
select gen_random_uuid(), v.tenant_id, v.id, 'Kitchen',
       (select coalesce(max(s.sort_order), -1) + 1 from station s where s.venue_id = v.id)
from venue v
where exists (select 1 from category c where c.venue_id = v.id and c.parent_id is null and c.station_id is null)
  and not exists (select 1 from station s where s.venue_id = v.id and lower(s.name) = 'kitchen');

update category c
set station_id = (select s.id
                  from station s
                  where s.venue_id = c.venue_id and lower(s.name) = 'kitchen'
                  order by s.sort_order
    limit 1)
where c.parent_id is null
  and c.station_id is null;

create unique index uq_station_venue_lower_name on station (venue_id, lower(name));
