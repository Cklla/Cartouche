-- Schéma de Cartouche : le backlog de jeux vidéo.
--
-- Cartouche partage le projet Supabase de Pellicule. Tout ce qui suit vit dans le schéma dédié
-- `cartouche` : cette migration ne crée, ne modifie et n'accorde rien dans `public`, `auth` ni
-- ailleurs, à une seule exception près : l'ajout de `cartouche.game` à la publication Realtime.
--
-- Chaque ligne appartient à un compte (`user_id`) et n'est visible que par lui (RLS). Les
-- contraintes CHECK reprennent, borne par borne, celles qu'imposaient auparavant les règles
-- Firestore : jamais plus strictes, sinon une donnée existante serait rejetée puis effacée
-- localement. En particulier, un titre vide reste accepté et une note de 0 aussi.
--
-- À appliquer une seule fois, à la main, dans l'éditeur SQL du tableau de bord. Un rejeu échoue
-- (le schéma existe déjà) sans rien modifier.

create schema cartouche;

create table cartouche.game (
    -- UUID généré par l'application (déjà celui de Room) : conservé tel quel à la migration, et
    -- le rejeu d'une écriture en attente reste idempotent. Aucune valeur par défaut, pour qu'un
    -- oubli côté client soit une erreur plutôt qu'un identifiant inventé par le serveur.
    id uuid primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title text not null check (char_length(title) <= 300),
    platform text not null check (char_length(platform) <= 200),
    genre text not null check (char_length(genre) <= 100),
    status text not null check (status in ('A_FAIRE', 'EN_COURS', 'TERMINE', 'ABANDONNE')),
    rawg_id bigint check (rawg_id between 0 and 9999999999),
    release_year integer check (release_year between 1950 and 2999),
    user_playtime_hours integer not null default 0
        check (user_playtime_hours between 0 and 100000),
    estimated_playtime_hastily_hours integer
        check (estimated_playtime_hastily_hours between 0 and 100000),
    estimated_playtime_normally_hours integer
        check (estimated_playtime_normally_hours between 0 and 100000),
    estimated_playtime_completely_hours integer
        check (estimated_playtime_completely_hours between 0 and 100000),
    -- 0 est accepté : les anciennes règles Firestore bornaient la note à 0..5.
    rating integer check (rating between 0 and 5),
    notes text not null default '' check (char_length(notes) <= 5000),
    cover_url text check (char_length(cover_url) <= 2000),
    -- Epoch en millisecondes, comme dans Room : aller-retour exact, aucune conversion de fuseau.
    -- Borne haute large (an 3000), seul l'ordre de grandeur compte.
    completed_at bigint check (completed_at between 0 and 32503680000000),
    abandoned_at bigint check (abandoned_at between 0 and 32503680000000),
    -- Liste courte, bornée en taille pour empêcher une ligne anormalement grosse (comme avant,
    -- la longueur de chaque élément n'est pas contrôlée).
    played_platforms text[] not null default '{}'
        check (cardinality(played_platforms) <= 20),
    igdb_lookup_attempted boolean not null default false
);

-- Les politiques RLS filtrent sur `user_id` ; la clé primaire couvre déjà `id`.
create index game_user_id_idx on cartouche.game (user_id);

alter table cartouche.game enable row level security;

-- `(select auth.uid())` plutôt que `auth.uid()` nu : évalué une fois par requête, pas par ligne.
create policy "jeux : lecture de ses lignes" on cartouche.game
    for select to authenticated
    using (user_id = (select auth.uid()));

create policy "jeux : insertion de ses lignes" on cartouche.game
    for insert to authenticated
    with check (user_id = (select auth.uid()));

-- `with check` : une ligne ne peut pas être réattribuée à un autre compte.
create policy "jeux : modification de ses lignes" on cartouche.game
    for update to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

create policy "jeux : suppression de ses lignes" on cartouche.game
    for delete to authenticated
    using (user_id = (select auth.uid()));

-- Droits d'accès aux rôles de l'API. Le RLS filtre les lignes, mais sans ces droits le rôle
-- `authenticated` est refusé avant même d'arriver aux politiques (SQLSTATE 42501) : un projet
-- Supabase récent n'accorde rien automatiquement, ni sur le schéma ni sur les tables.
grant usage on schema cartouche to authenticated;
grant select, insert, update, delete on cartouche.game to authenticated;

-- Aucun accès sans compte connecté.
revoke all on cartouche.game from anon;

-- Realtime sert de simple signal « quelque chose a changé » : l'application refait ensuite un
-- chargement complet. Les suppressions ne sont pas filtrées par le RLS côté Realtime, mais ne
-- transportent que la clé primaire (identité de réplication par défaut).
alter publication supabase_realtime add table cartouche.game;
