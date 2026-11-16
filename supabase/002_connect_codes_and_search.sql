-- Kwata Tracker: connect codes + people search. Safe to re-run.
create table if not exists public.connect_codes (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  code text unique not null,
  expires_at timestamptz not null default now() + interval '24 hours'
);
alter table public.connect_codes enable row level security;
drop policy if exists "own code" on public.connect_codes;
create policy "own code" on public.connect_codes for select using (user_id = auth.uid());

create or replace function public.create_connect_code() returns text
language plpgsql security definer set search_path = public as $$
declare c text; a text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; i int;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not exists (select 1 from profiles where id = auth.uid()) then raise exception 'save your profile first'; end if;
  loop
    c := '';
    for i in 1..6 loop c := c || substr(a, 1 + floor(random() * length(a))::int, 1); end loop;
    exit when not exists (select 1 from connect_codes where code = c);
  end loop;
  insert into connect_codes (user_id, code, expires_at) values (auth.uid(), c, now() + interval '24 hours')
  on conflict (user_id) do update set code = excluded.code, expires_at = excluded.expires_at;
  return c;
end $$;

create or replace function public.redeem_connect_code(c text)
returns table (id uuid, username text, name text)
language plpgsql security definer set search_path = public as $$
declare o uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not exists (select 1 from profiles where profiles.id = auth.uid()) then raise exception 'save your profile first'; end if;
  select cc.user_id into o from connect_codes cc
   where cc.code = upper(regexp_replace(coalesce(c, ''), '[^A-Za-z0-9]', '', 'g')) and cc.expires_at > now();
  if o is null then raise exception 'code not found or expired'; end if;
  if o = auth.uid() then raise exception 'that is your own code'; end if;
  insert into circle_members (owner_id, member_id) values (auth.uid(), o), (o, auth.uid()) on conflict do nothing;
  update circle_invites set status = 'accepted'
   where status = 'pending' and ((from_id = auth.uid() and to_id = o) or (from_id = o and to_id = auth.uid()));
  return query select p.id, p.username, p.name from profiles p where p.id = o;
end $$;

create or replace function public.search_profiles(q text)
returns table (id uuid, username text, name text)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, p.name from profiles p
  where auth.uid() is not null and p.id <> auth.uid() and p.username is not null
    and length(trim(coalesce(q, ''))) >= 2
    and (p.username like replace(replace(lower(ltrim(trim(q), '@')), '\', ''), '_', '\_') || '%'
         or p.name ilike '%' || replace(replace(trim(q), '%', ''), '_', '\_') || '%'
         or (p.phone is not null and p.phone = regexp_replace(q, '[^0-9+]', '', 'g')))
  order by (p.username = lower(ltrim(trim(q), '@'))) desc, p.username
  limit 10;
$$;

revoke all on function public.create_connect_code() from public, anon;
revoke all on function public.redeem_connect_code(text) from public, anon;
revoke all on function public.search_profiles(text) from public, anon;
grant execute on function public.create_connect_code() to authenticated;
grant execute on function public.redeem_connect_code(text) to authenticated;
grant execute on function public.search_profiles(text) to authenticated;
select 'kwata ok' as result;
