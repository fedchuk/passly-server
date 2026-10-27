create table venue_role (
                            id         uuid primary key,
                            tenant_id  uuid        not null references tenant (id),
                            venue_id   uuid        not null references venue (id),
                            name       varchar(60) not null,
                            sort_order int         not null,
                            version    bigint      not null default 0,
                            created_at timestamptz not null default now(),
                            updated_at timestamptz not null default now()
);

create unique index uq_venue_role_venue_lower_name on venue_role (venue_id, lower(name));
create index ix_venue_role_venue on venue_role (venue_id);

create table venue_role_permission (
                                       role_id    uuid        not null references venue_role (id) on delete cascade,
                                       permission varchar(30) not null,
                                       primary key (role_id, permission)
);

insert into venue_role (id, tenant_id, venue_id, name, sort_order)
select gen_random_uuid(), v.tenant_id, v.id, p.name, p.sort_order
from venue v
         cross join (values ('Manager', 0), ('Waiter', 1), ('Kitchen', 2)) as p(name, sort_order);

insert into venue_role_permission (role_id, permission)
select r.id, p.permission
from venue_role r
         join (values ('Manager', 'VIEW_MENU'),
                      ('Manager', 'EDIT_MENU'),
                      ('Manager', 'MANAGE_STOP_LIST'),
                      ('Manager', 'MANAGE_TABLES'),
                      ('Manager', 'TAKE_ORDERS'),
                      ('Manager', 'CLOSE_ORDERS'),
                      ('Manager', 'PREPARE_ORDERS'),
                      ('Manager', 'MANAGE_VENUE'),
                      ('Waiter', 'VIEW_MENU'),
                      ('Waiter', 'TAKE_ORDERS'),
                      ('Waiter', 'CLOSE_ORDERS'),
                      ('Kitchen', 'VIEW_MENU'),
                      ('Kitchen', 'MANAGE_STOP_LIST'),
                      ('Kitchen', 'PREPARE_ORDERS')) as p(name, permission)
              on p.name = r.name;

alter table membership
    add column role_id uuid references venue_role (id);

update membership m
set role_id = r.id
    from venue_role r
where r.venue_id = m.venue_id
  and upper(r.name) = m.role;

alter table membership
drop constraint ck_membership_role,
    drop constraint ck_membership_scope,
    alter column role drop not null,
    add constraint ck_membership_role_scope check ((venue_id is null) = (role_id is null));

create index ix_membership_role on membership (role_id);