-- Kwata Tracker v1.5.0: 3D avatars, step counts, push tokens for Buzz alerts
alter table public.profiles add column if not exists avatar text;
alter table public.locations add column if not exists steps integer;
alter table public.locations add column if not exists steps_day date;

create table if not exists public.push_tokens (
  token text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
alter table public.push_tokens enable row level security;
drop policy if exists "read own push tokens" on public.push_tokens;
create policy "read own push tokens" on public.push_tokens
  for select to authenticated using (user_id = auth.uid());
drop policy if exists "delete own push tokens" on public.push_tokens;
create policy "delete own push tokens" on public.push_tokens
  for delete to authenticated using (user_id = auth.uid());

-- a phone's token always belongs to whoever is signed in on it now
create or replace function public.register_push_token(t text, p text default 'android')
returns void language sql security definer set search_path = public as $$
  insert into public.push_tokens (token, user_id, platform, updated_at)
  values (t, auth.uid(), coalesce(p, 'android'), now())
  on conflict (token) do update set user_id = auth.uid(), platform = excluded.platform, updated_at = now();
$$;
revoke all on function public.register_push_token(text, text) from public, anon;
grant execute on function public.register_push_token(text, text) to authenticated;
