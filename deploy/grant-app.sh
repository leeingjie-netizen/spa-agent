#!/bin/sh
set -eu
test "${SPA_APPROVE_PRIVILEGE_CHANGES:-}" = YES || { echo 'Explicit privilege approval required' >&2; exit 1; }
. /opt/spa/database-policy.sh
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" <<SQL
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$MYSQL_USER'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USER'@'%';
SQL
