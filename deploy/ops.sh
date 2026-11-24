#!/bin/sh
# Linux host helper. Never deploys automatically, deletes volumes, or restores data.
set -eu
action=${1:-status}
envfile=${2:-.env.staging}
case "$envfile" in .env.staging|.env.production) ;; *) echo 'Use .env.staging or .env.production' >&2; exit 1;; esac
test -f "$envfile"
dc() { docker compose --env-file "$envfile" -f compose.yaml "$@"; }
case "$action" in
 check) dc config --quiet ;;
 init) dc up -d --wait mysql redis ;;
 grant)
   test "${3:-}" = --approve-privilege-changes
   dc exec -T -e SPA_APPROVE_PRIVILEGE_CHANGES=YES mysql sh /opt/spa/grant-app.sh ;;
 build) dc config --quiet; dc build app ;;
 start) dc config --quiet; dc up -d --wait ;;
 status) dc ps ;;
 diagnose) dc ps; dc logs --tail=100 app nginx ;;
 backup)
   dir="backups/container-$(date -u +%Y%m%dT%H%M%SZ)"
   umask 077; mkdir "$dir"
   dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$dir/database.sql"
   test -s "$dir/database.sql"
   sha256sum "$dir/database.sql" > "$dir/SHA256SUMS"
   echo 'Backup created. Restore drill in a new isolated database is still required.' ;;
 rollback)
   image=${3:?Provide an existing reviewed image tag}
   case "$image" in spa-agent:*) ;; *) echo 'Invalid image tag' >&2; exit 1;; esac
   docker image inspect "$image" >/dev/null
   SPA_IMAGE="$image" dc up -d --no-deps --no-build --wait app
   # Nginx resolves the app address at startup; refresh it after app recreation.
   dc restart nginx
   echo 'Application rollback only. No database migration or restore executed.' ;;
 *) echo 'Actions: check build start status diagnose backup rollback' >&2; exit 1;;
esac
