begin;

insert into bodeul_realtime_auth.allowed_firebase_projects (project_id)
values ('bodeul-dev')
on conflict (project_id) do nothing;

delete from bodeul_realtime_auth.allowed_firebase_projects
where project_id <> 'bodeul-dev';

commit;
