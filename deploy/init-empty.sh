#!/bin/sh
set -eu
. /opt/spa/database-policy.sh
count=$(MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse 'SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()')
test "$count" = 0 || { echo 'Schema initialization requires an empty database' >&2; exit 1; }
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" < /opt/spa/schema.sql
# Privilege changes are a separate explicit operator action, never automatic here.
