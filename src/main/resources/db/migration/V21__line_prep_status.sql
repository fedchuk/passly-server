alter table order_line
    add column prep_status       varchar(12) not null default 'NEW',
    add column status_changed_at timestamptz,
    add constraint ck_order_line_prep_status check (prep_status in ('NEW', 'IN_PROGRESS', 'READY', 'SERVED'));

update order_line
set prep_status       = 'SERVED',
    status_changed_at = added_at;

alter table order_line
    alter column status_changed_at set not null;

create index ix_order_line_station_board on order_line (station_id, added_at) where prep_status <> 'SERVED';

update category c
set station_id = (select s.id from station s where s.venue_id = c.venue_id order by s.sort_order limit 1)
where c.parent_id is null
  and c.station_id is null;

alter table category
    add constraint ck_category_root_station check (parent_id is not null or station_id is not null);