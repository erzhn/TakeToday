# Резервные копии (п. 5.3.1 ТЗ)

Сведения об оплаченных и выданных заказах не теряются при сбое сайта: всё пишется в PostgreSQL в транзакциях,
а незавершённые возвраты доводит фоновая задача `RefundJob`. От потери самой базы (сломался диск, удалили том Docker)
защищают резервные копии.

## Что копируется

| Что | Файл |
| --- | --- |
| База `taketoday` целиком (пользователи, заведения, предложения, заказы, платежи, возвраты, журнал) | `backups/taketoday-ГГГГ-ММ-ДД_чч-мм-сс.dump` (формат `pg_dump --format=custom`) |
| Фото наборов и логотипы заведений (`UPLOAD_DIR`) | `backups/uploads-ГГГГ-ММ-ДД_чч-мм-сс.zip` |

Хранятся 14 последних копий (`-Keep 30` — больше). Папка `backups/` не попадает в git.

## Сделать копию (Windows)

```powershell
docker compose up -d          # база должна быть запущена
.\scripts\backup.ps1
```

Сайт останавливать не нужно — `pg_dump` делает согласованный снимок работающей базы.

**Каждый день автоматически** — Планировщик заданий Windows:

```powershell
schtasks /Create /TN "TakeToday backup" /SC DAILY /ST 03:00 /TR "powershell -NoProfile -ExecutionPolicy Bypass -File \"D:\study\5th sem\TKR\TakeToday\scripts\backup.ps1\""
```

Linux/macOS — `./scripts/backup.sh`, расписание через cron (пример в начале скрипта).

Лучше копировать папку `backups/` ещё и на другой диск или в облако: копия на том же диске не спасёт, если сломается диск.

## Восстановить

1. Остановите сайт (Ctrl+C в окне `start-backend.ps1`).
2. Восстановите базу (и при необходимости фото):

   ```powershell
   .\scripts\restore.ps1 -File backups\taketoday-2026-12-20_03-00-00.dump -Uploads backups\uploads-2026-12-20_03-00-00.zip
   ```

   Скрипт спросит подтверждение. Восстановление идёт одной транзакцией: если что-то пошло не так, база остаётся как была.
3. Запустите сайт `.\start-backend.ps1`. Если копия старее кода, Flyway сам применит новые миграции.

## Проверка копии

Раз в месяц стоит восстановить копию в отдельную базу и открыть сайт — копия, которую ни разу не восстанавливали,
может оказаться непригодной:

```powershell
docker compose exec postgres createdb -U taketoday taketoday_check
docker compose cp backups\taketoday-....dump postgres:/tmp/check.dump
docker compose exec postgres pg_restore -U taketoday -d taketoday_check --no-owner /tmp/check.dump
docker compose exec postgres psql -U taketoday -d taketoday_check -c "SELECT status, COUNT(*) FROM reservation_orders GROUP BY status"
docker compose exec postgres dropdb -U taketoday taketoday_check
```
