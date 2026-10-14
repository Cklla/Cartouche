-- Reproduit le strict minimum de Supabase pour jouer les migrations sur un Postgres nu :
-- rôles, schéma `auth`, `auth.uid()` et publication Realtime. Inutile sur un vrai projet
-- Supabase, où tout cela existe déjà.
--
-- Aucun droit par défaut : un projet Supabase récent n'en accorde pas aux rôles de l'API, ni sur
-- les nouveaux schémas ni sur les nouvelles tables. Chaque schéma et chaque table doivent
-- recevoir leurs GRANT dans les migrations, sinon les tests échouent comme l'application.
--
-- Le schéma `public` contient un objet témoin (`public.temoin`) : le banc compare le catalogue
-- avant et après les migrations pour prouver qu'elles n'y touchent pas (le vrai `public`
-- appartient à Pellicule).

create role anon nologin;
create role authenticated nologin;
create role supabase_auth_admin nologin;

create schema auth;
create table auth.users (id uuid primary key default gen_random_uuid(), email text);

create function auth.uid() returns uuid
language sql stable
as $$
    select nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::uuid
$$;

grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant select on auth.users to authenticated;

create table public.temoin (id integer primary key);
grant select on public.temoin to authenticated;

create publication supabase_realtime;
