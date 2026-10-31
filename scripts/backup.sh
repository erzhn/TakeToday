#!/usr/bin/env sh
# Резервная копия TakeToday (п. 5.3.1 ТЗ) для Linux/macOS: база из compose.yaml + загруженные файлы.
#   ./scripts/backup.sh            — копия в backups/, хранится 14 последних
#   KEEP=30 ./scripts/backup.sh
# Расписание (cron, каждый день в 03:00):
#   0 3 * * * cd /path/to/TakeToday && ./scripts/backup.sh >> backups/backup.log 2>&1
set -eu
cd "$(dirname "$0")/.."
KEEP="${KEEP:-14}"
DB_USER="$(grep -E '^DB_USER=' .env 2>/dev/null | cut -d= -f2- || true)"; DB_USER="${DB_USER:-taketoday}"
UPLOAD_DIR="$(grep -E '^UPLOAD_DIR=' .env 2>/dev/null | cut -d= -f2- || true)"; UPLOAD_DIR="${UPLOAD_DIR:-uploads}"
STAMP="$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p backups

docker compose exec -T postgres pg_dump -U "$DB_USER" -d taketoday --format=custom > "backups/taketoday-$STAMP.dump"
[ "$(wc -c < "backups/taketoday-$STAMP.dump")" -gt 1024 ] || { echo "Дамп подозрительно маленький" >&2; exit 1; }
if [ -d "$UPLOAD_DIR" ]; then tar -czf "backups/uploads-$STAMP.tar.gz" -C "$UPLOAD_DIR" .; fi

ls -1t backups/taketoday-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f
ls -1t backups/uploads-*.tar.gz 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f
echo "Готово: backups/taketoday-$STAMP.dump"
echo "Восстановление: docker compose exec -T postgres pg_restore -U $DB_USER -d taketoday --clean --if-exists --no-owner --single-transaction < backups/taketoday-$STAMP.dump"
