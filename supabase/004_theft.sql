-- Kwata 1.7: theft protection (stolen mode, SIM change, thief trail)
create extension if not exists pgcrypto;

create table if not exists public.theft_events (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('wrong_pin','stop_attempt','sim_change','marked_stolen','recovered','test')),
  lat double precision, lng double precision, battery int,
  photo_path text, detail jsonb,
  created_at timestamptz not null default now()
);
create index if not exists theft_events_user on public.theft_events(user_id, created_at desc);

create table if not exists public.stolen (
  user_id uuid primary key references auth.users(id) on delete cascade,
  active boolean not null default false,
  token text unique,
  marked_by uuid, marked_device text,
  marked_at timestamptz, cleared_at timestamptz
);

create table if not exists public.trail (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  lat double precision not null, lng double precision not null,
  accuracy double precision, battery int,
  at timestamptz not null default now()
);
create index if not exists trail_user_at on public.trail(user_id, at desc);

-- helper: is target in my circle (or me)?
create or replace function public.in_my_circle(target uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select target = auth.uid() or exists (select 1 from circle_members where owner_id = auth.uid() and member_id = target);
$$;

alter table public.theft_events enable row level security;
alter table public.stolen enable row level security;
alter table public.trail enable row level security;

drop policy if exists te_insert on public.theft_events;
create policy te_insert on public.theft_events for insert to authenticated with check (user_id = auth.uid());
drop policy if exists te_select on public.theft_events;
create policy te_select on public.theft_events for select to authenticated using (public.in_my_circle(user_id));

drop policy if exists st_select on public.stolen;
create policy st_select on public.stolen for select to authenticated using (public.in_my_circle(user_id));

drop policy if exists tr_insert on public.trail;
create policy tr_insert on public.trail for insert to authenticated with check (user_id = auth.uid());
drop policy if exists tr_select on public.trail;
create policy tr_select on public.trail for select to authenticated using (public.in_my_circle(user_id));

-- mark a phone stolen: yourself (from another device) or someone in your circle
create or replace function public.mark_stolen(target uuid default null, device text default null) returns text
language plpgsql security definer set search_path = public as $$
declare t uuid := coalesce(target, auth.uid()); tok text;
begin
  if auth.uid() is null or not public.in_my_circle(t) then raise exception 'not allowed'; end if;
  insert into stolen(user_id, active, token, marked_by, marked_device, marked_at, cleared_at)
  values (t, true, encode(extensions.gen_random_bytes(12), 'hex'), auth.uid(), device, now(), null)
  on conflict (user_id) do update set active = true, marked_by = auth.uid(), marked_device = excluded.marked_device,
    marked_at = case when stolen.active then stolen.marked_at else now() end,
    token = coalesce(stolen.token, excluded.token), cleared_at = null;
  select token into tok from stolen where user_id = t;
  insert into theft_events(user_id, kind, detail) values (t, 'marked_stolen', jsonb_build_object('by', auth.uid()));
  return tok;
end $$;

create or replace function public.clear_stolen(target uuid default null) returns void
language plpgsql security definer set search_path = public as $$
declare t uuid := coalesce(target, auth.uid());
begin
  if auth.uid() is null or not public.in_my_circle(t) then raise exception 'not allowed'; end if;
  update stolen set active = false, cleared_at = now() where user_id = t;
  insert into theft_events(user_id, kind, detail) values (t, 'recovered', jsonb_build_object('by', auth.uid()));
end $$;

-- police timeline: anyone with the secret link can read the trail since the phone was marked stolen
create or replace function public.get_trail(t text) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare s stolen%rowtype; nm text; pts jsonb; evs jsonb;
begin
  select * into s from stolen where token = t;
  if not found then raise exception 'not found'; end if;
  select coalesce(name, username) into nm from profiles where id = s.user_id;
  select coalesce(jsonb_agg(jsonb_build_object('lat', lat, 'lng', lng, 'acc', accuracy, 'battery', battery, 'at', at) order by at), '[]'::jsonb) into pts
    from (select * from trail where user_id = s.user_id and at >= coalesce(s.marked_at, now()) - interval '2 hours' order by at desc limit 5000) x;
  select coalesce(jsonb_agg(jsonb_build_object('kind', kind, 'lat', lat, 'lng', lng, 'battery', battery, 'at', created_at, 'detail', detail) order by created_at), '[]'::jsonb) into evs
    from theft_events where user_id = s.user_id and kind <> 'test' and created_at >= coalesce(s.marked_at, now()) - interval '1 day';
  return jsonb_build_object('name', nm, 'active', s.active, 'marked_at', s.marked_at, 'cleared_at', s.cleared_at, 'points', pts, 'events', evs);
end $$;

grant execute on function public.mark_stolen(uuid, text) to authenticated;
grant execute on function public.clear_stolen(uuid) to authenticated;
grant execute on function public.get_trail(text) to anon, authenticated;
grant execute on function public.in_my_circle(uuid) to authenticated;

-- realtime
do $$ begin
  begin alter publication supabase_realtime add table public.theft_events; exception when others then null; end;
  begin alter publication supabase_realtime add table public.stolen; exception when others then null; end;
end $$;

-- photo storage: private bucket, folder = owner's user id; owner and their circle can read
insert into storage.buckets (id, name, public) values ('theft-photos', 'theft-photos', false) on conflict (id) do nothing;
drop policy if exists theft_photo_insert on storage.objects;
create policy theft_photo_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'theft-photos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists theft_photo_select on storage.objects;
create policy theft_photo_select on storage.objects for select to authenticated
  using (bucket_id = 'theft-photos' and public.in_my_circle(((storage.foldername(name))[1])::uuid));
alter publication supabase_realtime add table public.circle_members;
