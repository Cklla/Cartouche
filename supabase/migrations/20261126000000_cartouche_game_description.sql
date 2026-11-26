-- Descriptions des jeux : table `cartouche.game_description`.
--
-- Une ligne par jeu au plus : le texte de présentation d'IGDB (`summary`, en anglais) et sa
-- traduction française (`summary_fr`). Le texte vit dans une table à part, et non dans des colonnes
-- de `cartouche.game`, parce que l'application et le site relisent toute la table `game` à chaque
-- signal Realtime : des textes de plusieurs centaines de caractères par jeu alourdiraient chaque
-- synchronisation de tous les appareils, y compris de ceux qui ne les affichent pas. Cette table-ci
-- n'est lue qu'à l'ouverture d'une fiche.
--
-- Cette migration ne crée que la table, son index, ses politiques RLS et ses droits. Elle ne touche
-- ni `cartouche.game`, ni `public`, ni les droits existants, ni la publication Realtime.
--
-- Colonnes :
--   - `game_id`  : le jeu décrit, clé primaire (une description par jeu). La suppression du jeu
--                  supprime sa description (`on delete cascade`).
--   - `user_id`  : le compte propriétaire, rempli par `auth.uid()` comme pour `cartouche.game`.
--   - `igdb_id`  : l'identifiant IGDB dont le texte est tiré. Le client le compare à celui du jeu :
--                  une description dont l'identifiant diffère (jeu relié à une autre fiche IGDB
--                  depuis) est périmée et sera remplacée. Même borne que `cartouche.game.igdb_id`.
--   - `summary`  : le texte original ; `null` = IGDB n'a pas de description pour ce jeu (résultat
--                  définitif, enregistré pour ne plus le redemander).
--   - `summary_fr` : la traduction ; `null` tant qu'il n'y en a pas. Sans texte original, il ne peut
--                  pas y avoir de traduction (contrainte `game_description_traduction_check`).
--   - `translation_model` : identifiant du modèle qui a traduit, pour retraduire un jour les textes
--                  d'un modèle abandonné.
--   - `created_at` : date d'enregistrement.
--
-- Bornes de longueur, en caractères : l'original le plus long observé fait 1097 caractères, la
-- fonction de traduction le coupe à 4000 avant envoi, et le rapport traduction / original accepté
-- est de 2,0 au plus. Les `check` laissent de la marge sans rendre la ligne anormalement grosse :
-- 5000 pour l'original, 10000 pour la traduction.
--
-- Politiques : un compte ne lit, n'insère, ne modifie et ne supprime que ses lignes, et seulement
-- pour un jeu qui est à lui (la ligne `cartouche.game` référencée doit porter le même `user_id`) :
-- impossible de poser une description sur le jeu d'un autre compte.
--
-- Sûre pour les versions déjà installées de l'application et du site : elles ne connaissent pas la
-- table et ne la lisent ni ne l'écrivent. `cartouche.game` et sa publication Realtime sont inchangées.
--
-- À appliquer une seule fois, à la main, dans l'éditeur SQL du tableau de bord, après une
-- sauvegarde. Un rejeu échoue (la table existe déjà) sans rien modifier. Si une lecture répond
-- ensuite `PGRST205` (table introuvable dans le cache de schéma), lancer
-- `notify pgrst, 'reload schema';`.
--
-- Retour arrière (à lancer à la main ; efface les descriptions et traductions déjà enregistrées,
-- qui sont ensuite redemandées à l'ouverture des fiches tant que l'application les attend ;
-- une fois l'application ou le site livrés avec la fonctionnalité, retirer la table sans mettre
-- ces clients à jour fait échouer la lecture des descriptions, sans toucher aux jeux) :
--   drop table cartouche.game_description;

create table cartouche.game_description (
    game_id uuid primary key references cartouche.game (id) on delete cascade,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    igdb_id bigint not null check (igdb_id between 1 and 9999999999),
    summary text check (char_length(summary) <= 5000),
    summary_fr text check (char_length(summary_fr) <= 10000),
    translation_model text check (char_length(translation_model) <= 100),
    created_at timestamptz not null default now(),
    constraint game_description_traduction_check check (summary is not null or summary_fr is null)
);

-- Les politiques RLS filtrent sur `user_id` ; la clé primaire couvre déjà `game_id`.
create index game_description_user_id_idx on cartouche.game_description (user_id);

alter table cartouche.game_description enable row level security;

-- `(select auth.uid())` plutôt que `auth.uid()` nu : évalué une fois par requête, pas par ligne.
create policy "descriptions : lecture de ses lignes" on cartouche.game_description
    for select to authenticated
    using (user_id = (select auth.uid()));

-- Le jeu référencé doit être à soi. La sous-requête lit `cartouche.game` sous le RLS de l'appelant
-- (qui ne voit que ses jeux) ; le `user_id` explicite rend la règle indépendante de ce détail.
create policy "descriptions : insertion pour ses jeux" on cartouche.game_description
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and exists (
            select 1 from cartouche.game g
            where g.id = game_description.game_id and g.user_id = (select auth.uid())
        )
    );

-- `with check` : une ligne ne peut être ni réattribuée à un autre compte, ni déplacée vers le jeu
-- d'un autre.
create policy "descriptions : modification de ses lignes" on cartouche.game_description
    for update to authenticated
    using (user_id = (select auth.uid()))
    with check (
        user_id = (select auth.uid())
        and exists (
            select 1 from cartouche.game g
            where g.id = game_description.game_id and g.user_id = (select auth.uid())
        )
    );

create policy "descriptions : suppression de ses lignes" on cartouche.game_description
    for delete to authenticated
    using (user_id = (select auth.uid()));

-- Même démarche que pour `cartouche.game` : on repart de zéro avant d'accorder, pour que ni
-- `truncate` (qui ignore le RLS), ni `references`, ni `trigger`, ni un accès pour `anon` ne
-- subsistent par héritage de privilèges par défaut. Le droit d'usage du schéma pour
-- `authenticated` existe déjà (migration initiale) : rien à y ajouter. Pas de Realtime : la
-- description n'est lue qu'à l'ouverture d'une fiche.
revoke all on cartouche.game_description from anon, authenticated;
grant select, insert, update, delete on cartouche.game_description to authenticated;
