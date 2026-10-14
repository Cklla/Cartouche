#!/usr/bin/env bash
# Rejoue les migrations puis les tests d'isolation sur un Postgres jetable (Docker).
# Usage : supabase/tests/lancer.sh
#
# MIGRATIONS_DIR (facultatif) : dossier de migrations à rejouer à la place de
# supabase/migrations. Sert à vérifier que les tests détectent une régression (politique ou
# GRANT retiré dans une copie de la migration) sans toucher au vrai fichier.
set -euo pipefail

cd "$(dirname "$0")/../.."
migrations_dir="${MIGRATIONS_DIR:-supabase/migrations}"
conteneur="cartouche-test-rls-$$"
trap 'docker rm -f "$conteneur" >/dev/null 2>&1 || true' EXIT

# Garde-fou statique : la migration s'exécute une fois, à la main, sur un projet partagé avec
# Pellicule. Aucune instruction destructive ni changement de droits par défaut (commentaires
# `--` ignorés).
if sed 's/--.*$//' "$migrations_dir"/*.sql \
    | grep -inE '\b(drop|truncate)\b|\bdelete[[:space:]]+from\b|alter[[:space:]]+default[[:space:]]+privileges'; then
    echo "Instruction interdite dans les migrations (voir lignes ci-dessus)." >&2
    exit 1
fi

docker run -d --name "$conteneur" -e POSTGRES_PASSWORD=test postgres:18 >/dev/null
until docker exec "$conteneur" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
sleep 2

executer() {
    docker exec -i "$conteneur" psql -U postgres -v ON_ERROR_STOP=1 -q <"$1"
}

# Catalogue des schémas qui n'appartiennent pas à Cartouche (structure, droits, politiques).
instantane() {
    # pg_dump ajoute un jeton aléatoire (`\restrict`) à chaque sortie : exclu de la comparaison.
    docker exec "$conteneur" pg_dump -U postgres -s -x -n public -n auth \
        | grep -vE '^\\(un)?restrict '
    docker exec "$conteneur" psql -U postgres -At -c \
        "select n.nspname, c.relname, c.relacl, c.relrowsecurity from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname in ('public', 'auth') order by 1, 2"
    docker exec "$conteneur" psql -U postgres -At -c \
        "select nspname, nspacl from pg_namespace where nspname in ('public', 'auth') order by 1"
}

executer supabase/tests/00_simulation_supabase.sql
avant="$(instantane)"

for migration in "$migrations_dir"/*.sql; do
    executer "$migration"
done

apres="$(instantane)"
if [ "$avant" != "$apres" ]; then
    echo "Les migrations ont modifié le schéma public ou auth :" >&2
    diff <(echo "$avant") <(echo "$apres") >&2 || true
    exit 1
fi

executer supabase/tests/rls.sql
