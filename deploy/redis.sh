#!/bin/sh
set -eu
case "$REDIS_PASSWORD" in ''|*[!a-zA-Z0-9]*) echo 'Redis credential must be alphanumeric' >&2; exit 1;; esac
umask 077
printf 'bind 0.0.0.0\nprotected-mode yes\nrequirepass %s\nappendonly yes\nappendfsync everysec\nmaxmemory 192mb\nmaxmemory-policy noeviction\ndir /data\n' "$REDIS_PASSWORD" > /tmp/redis.conf
exec redis-server /tmp/redis.conf
