-- Isolation entre comptes, droits, contraintes CHECK et Realtime du schéma `cartouche`.
-- Chaque vérification lève une exception en cas d'écart.

insert into auth.users (id, email) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'a@exemple.fr'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'b@exemple.fr');

create function pg_temp.connecte_comme(uid uuid) returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', uid)::text, true);
    set local role authenticated;
end;
$$;

-- La requête doit être refusée **pour la bonne raison** : un refus d'un autre code SQLSTATE
-- (droit manquant au lieu d'une contrainte, par exemple) ferait passer un test à tort.
--   23502 not null · 23505 clé dupliquée · 23514 CHECK · 42501 droit ou RLS refusé
create function pg_temp.refuse(requete text, motif text, sqlstate_attendu text) returns void
language plpgsql as $$
begin
    begin
        execute requete;
    exception when others then
        if sqlstate <> sqlstate_attendu then
            raise exception 'Refus pour le mauvais motif (% au lieu de %) (%) : %',
                sqlstate, sqlstate_attendu, motif, requete;
        end if;
        return;
    end;
    raise exception 'Requête acceptée alors qu''elle devait être refusée (%) : %', motif, requete;
end;
$$;

-- Insère un jeu minimal en fixant une colonne (valeur SQL littérale) : la requête est acceptée
-- (`accepte_valeur`) ou refusée par la contrainte CHECK (`refuse_valeur`).
create function pg_temp.requete_valeur(colonne text, valeur text) returns text
language sql as $$
    select format(
        'insert into cartouche.game (id, title, platform, genre, status, %I) '
        'values (gen_random_uuid(), ''x'', ''x'', ''x'', ''A_FAIRE'', %s)', colonne, valeur)
$$;

create function pg_temp.accepte_valeur(colonne text, valeur text) returns void
language plpgsql as $$
begin
    execute pg_temp.requete_valeur(colonne, valeur);
end;
$$;

create function pg_temp.refuse_valeur(colonne text, valeur text) returns void
language plpgsql as $$
begin
    perform pg_temp.refuse(pg_temp.requete_valeur(colonne, valeur),
        colonne || ' = ' || valeur, '23514');
end;
$$;

-- A crée un jeu ; user_id vient de auth.uid() par défaut, les colonnes à valeur par défaut
-- prennent leur valeur.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game (id, title, platform, genre, status)
    values ('11111111-1111-1111-1111-111111111111', 'Jeu de A', 'PC', 'RPG', 'A_FAIRE');
do $$ begin
    assert (select user_id from cartouche.game) = 'aaaaaaaa-0000-0000-0000-000000000001', 'user_id par défaut';
    assert (select user_playtime_hours from cartouche.game) = 0, 'temps de jeu par défaut';
    assert (select notes from cartouche.game) = '', 'notes par défaut';
    assert (select played_platforms from cartouche.game) = '{}', 'plateformes jouées par défaut';
    assert (select igdb_lookup_attempted from cartouche.game) = false, 'drapeau IGDB par défaut';
end $$;
commit;

-- B ne voit rien, ne modifie rien, ne supprime rien.
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
do $$ begin
    assert (select count(*) from cartouche.game) = 0, 'B voit un jeu de A';
end $$;
update cartouche.game set title = 'piraté';
delete from cartouche.game;
select pg_temp.refuse(
    $q$insert into cartouche.game (id, user_id, title, platform, genre, status)
       values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'TERMINE')$q$,
    'insertion au nom de A', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game (id, title, platform, genre, status)
       values ('11111111-1111-1111-1111-111111111111', 'écrase', 'x', 'x', 'TERMINE')
       on conflict (id) do update set title = excluded.title$q$,
    'upsert sur l''identifiant de A', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game (id, title, platform, genre, status)
       values ('11111111-1111-1111-1111-111111111111', 'doublon', 'x', 'x', 'TERMINE')$q$,
    'même identifiant que A, sans upsert', '23505');
commit;

-- Rien n'a bougé pour A.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
do $$ begin
    assert (select title from cartouche.game) = 'Jeu de A', 'le jeu de A a été modifié';
    assert (select count(*) from cartouche.game) = 1, 'les jeux de A ont changé';
end $$;
-- A ne peut pas céder une ligne à B.
select pg_temp.refuse(
    $q$update cartouche.game set user_id = 'bbbbbbbb-0000-0000-0000-000000000002'$q$,
    'changement de propriétaire', '42501');
-- A peut modifier son jeu, et rejouer un upsert sans créer de doublon (écriture idempotente).
update cartouche.game set status = 'EN_COURS', rating = 4;
insert into cartouche.game (id, title, platform, genre, status)
    values ('11111111-1111-1111-1111-111111111111', 'Jeu de A', 'PC', 'RPG', 'TERMINE')
    on conflict (id) do update set status = excluded.status;
do $$ begin
    assert (select count(*) from cartouche.game) = 1, 'upsert : doublon créé';
    assert (select status from cartouche.game) = 'TERMINE', 'upsert : statut non mis à jour';
end $$;
-- Supprimer une ligne absente n'est pas une erreur (rejeu d'une suppression).
delete from cartouche.game where id = gen_random_uuid();
commit;

-- Contraintes CHECK : reprise, borne par borne, de firestore.rules (jamais plus strictes).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');

-- Obligatoires : title, platform, genre, status, igdbLookupAttempted (et l'identifiant).
select pg_temp.refuse($q$insert into cartouche.game (title, platform, genre, status) values ('x', 'x', 'x', 'A_FAIRE')$q$, 'identifiant absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'titre absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, genre, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'plateforme absente', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'genre absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre) values (gen_random_uuid(), 'x', 'x', 'x')$q$, 'statut absent', '23502');
select pg_temp.refuse(pg_temp.requete_valeur('igdb_lookup_attempted', 'null'), 'drapeau IGDB nul', '23502');

-- Statut : quatre valeurs, rien d'autre (la casse compte).
insert into cartouche.game (id, title, platform, genre, status) values
    (gen_random_uuid(), 'x', 'x', 'x', 'A_FAIRE'),
    (gen_random_uuid(), 'x', 'x', 'x', 'EN_COURS'),
    (gen_random_uuid(), 'x', 'x', 'x', 'TERMINE'),
    (gen_random_uuid(), 'x', 'x', 'x', 'ABANDONNE');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', 'VU')$q$, 'statut inconnu', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', 'termine')$q$, 'statut en minuscules', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', '')$q$, 'statut vide', '23514');

-- Textes : un titre vide était accepté par les anciennes règles, il l'est ici.
insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), '', '', '', 'A_FAIRE');
insert into cartouche.game (id, title, platform, genre, status)
    values (gen_random_uuid(), repeat('é', 300), repeat('é', 200), repeat('é', 100), 'A_FAIRE');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), repeat('x', 301), 'x', 'x', 'A_FAIRE')$q$, 'titre de 301 caractères', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', repeat('x', 201), 'x', 'A_FAIRE')$q$, 'plateforme de 201 caractères', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', repeat('x', 101), 'A_FAIRE')$q$, 'genre de 101 caractères', '23514');
select pg_temp.accepte_valeur('notes', 'repeat(''x'', 5000)');
select pg_temp.refuse_valeur('notes', 'repeat(''x'', 5001)');
select pg_temp.refuse(pg_temp.requete_valeur('notes', 'null'), 'notes nulles', '23502');
select pg_temp.accepte_valeur('cover_url', 'repeat(''x'', 2000)');
select pg_temp.refuse_valeur('cover_url', 'repeat(''x'', 2001)');
select pg_temp.accepte_valeur('cover_url', 'null');

-- Entiers optionnels : valeurs limites acceptées, un cran au-delà refusé, null accepté.
select pg_temp.accepte_valeur(c, v)
from (values ('rating', '0'), ('rating', '5'), ('rating', 'null'),
             ('rawg_id', '0'), ('rawg_id', '9999999999'), ('rawg_id', 'null'),
             ('release_year', '1950'), ('release_year', '2999'), ('release_year', 'null'),
             ('estimated_playtime_hastily_hours', '0'), ('estimated_playtime_hastily_hours', '100000'),
             ('estimated_playtime_hastily_hours', 'null'),
             ('estimated_playtime_normally_hours', '0'), ('estimated_playtime_normally_hours', '100000'),
             ('estimated_playtime_normally_hours', 'null'),
             ('estimated_playtime_completely_hours', '0'), ('estimated_playtime_completely_hours', '100000'),
             ('estimated_playtime_completely_hours', 'null'),
             ('user_playtime_hours', '0'), ('user_playtime_hours', '100000'),
             ('completed_at', '0'), ('completed_at', '32503680000000'), ('completed_at', 'null'),
             ('abandoned_at', '0'), ('abandoned_at', '32503680000000'), ('abandoned_at', 'null'))
    as l (c, v);
select pg_temp.refuse_valeur(c, v)
from (values ('rating', '-1'), ('rating', '6'),
             ('rawg_id', '-1'), ('rawg_id', '10000000000'),
             ('release_year', '1949'), ('release_year', '3000'),
             ('estimated_playtime_hastily_hours', '-1'), ('estimated_playtime_hastily_hours', '100001'),
             ('estimated_playtime_normally_hours', '-1'), ('estimated_playtime_normally_hours', '100001'),
             ('estimated_playtime_completely_hours', '-1'), ('estimated_playtime_completely_hours', '100001'),
             ('user_playtime_hours', '-1'), ('user_playtime_hours', '100001'),
             ('completed_at', '-1'), ('completed_at', '32503680000001'),
             ('abandoned_at', '-1'), ('abandoned_at', '32503680000001'))
    as r (c, v);
-- Le temps de jeu saisi n'a pas de valeur nulle (le modèle est un Int non nul).
select pg_temp.refuse(pg_temp.requete_valeur('user_playtime_hours', 'null'), 'temps de jeu nul', '23502');

-- Plateformes jouées : vide, jusqu'à 20 éléments, pas 21.
select pg_temp.accepte_valeur('played_platforms', '''{}''');
select pg_temp.accepte_valeur('played_platforms', 'array_fill(''PC''::text, array[20])');
select pg_temp.refuse_valeur('played_platforms', 'array_fill(''PC''::text, array[21])');
select pg_temp.refuse(pg_temp.requete_valeur('played_platforms', 'null'), 'plateformes jouées nulles', '23502');

-- Les contraintes valent aussi à la modification.
select pg_temp.refuse($q$update cartouche.game set rating = 6$q$, 'note hors bornes en modification', '23514');
select pg_temp.refuse($q$update cartouche.game set status = 'VU'$q$, 'statut inconnu en modification', '23514');
commit;

-- Suppression : A retire son jeu.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
delete from cartouche.game where id = '11111111-1111-1111-1111-111111111111';
do $$ begin
    assert (select count(*) from cartouche.game where id = '11111111-1111-1111-1111-111111111111') = 0,
        'le jeu supprimé est toujours là';
end $$;
commit;

-- Sans compte connecté, rien n'est lisible ni écrivable.
begin;
set local role anon;
select pg_temp.refuse('select * from cartouche.game', 'lecture anonyme', '42501');
select pg_temp.refuse($q$insert into cartouche.game (id, user_id, title, platform, genre, status) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'A_FAIRE')$q$, 'insertion anonyme', '42501');
commit;

-- Même si le schéma était un jour ouvert à `anon`, la table lui reste fermée (annulé ensuite).
begin;
grant usage on schema cartouche to anon;
set local role anon;
select pg_temp.refuse('select * from cartouche.game', 'lecture anonyme, schéma ouvert', '42501');
select pg_temp.refuse($q$insert into cartouche.game (id, user_id, title, platform, genre, status) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'A_FAIRE')$q$, 'insertion anonyme, schéma ouvert', '42501');
rollback;

-- Un compte connecté ne peut pas créer d'objet dans le schéma, ni y toucher à la structure.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('create table cartouche.intrus (id int)', 'création de table', '42501');
select pg_temp.refuse('truncate cartouche.game', 'truncate par un compte', '42501');
commit;

-- Le schéma `public` (propriété de Pellicule) reste tel quel : le compte y garde son seul droit.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('insert into public.temoin values (1)', 'écriture dans public', '42501');
commit;

-- Realtime : seule `cartouche.game` rejoint la publication.
do $$ begin
    assert (select count(*) from pg_publication_tables where pubname = 'supabase_realtime') = 1,
        'la publication Realtime contient autre chose que cartouche.game';
    assert exists (select 1 from pg_publication_tables
                   where pubname = 'supabase_realtime' and schemaname = 'cartouche' and tablename = 'game'),
        'cartouche.game absente de la publication Realtime';
end $$;

select 'OK : tous les tests RLS passent' as resultat;
