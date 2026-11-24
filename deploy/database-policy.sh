#!/bin/sh
set -eu
case "${SPA_DEPLOYMENT_MODE:-}:$MYSQL_DATABASE" in
 staging:spa_staging|production:spa_production) ;;
 acceptance:spa_acceptance_*) suffix=${MYSQL_DATABASE#spa_acceptance_}; case "$suffix" in ''|*[!0-9]*) exit 1;; esac; test "${#suffix}" -eq 14 ;;
 *) echo 'Database identity policy rejected target' >&2; exit 1;;
esac
case "$MYSQL_USER" in ''|*[!a-zA-Z0-9_]*) exit 1;; esac
actual=$(MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse 'SELECT DATABASE()')
test "$actual" = "$MYSQL_DATABASE"
