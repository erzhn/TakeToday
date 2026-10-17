# TakeToday

Сервис для покупки оставшейся к концу дня еды со скидкой. Backend: Java 17+, Spring Boot 3.5.6, Spring Security, PostgreSQL и Flyway. Интерфейс: HTML, CSS и JavaScript без отдельной сборки.

## Требования

- JDK 17 или новее; переменная `JAVA_HOME` должна указывать на JDK.
- Docker с Compose для локальных PostgreSQL и Mailpit либо собственная PostgreSQL.
- Доступ к интернету при первой сборке: Maven Wrapper скачивает Maven и зависимости. Отдельно устанавливать Maven не требуется.
- Node.js нужен только для необязательного отдельного frontend-сервера.

Все команды ниже выполняются из корня репозитория в PowerShell.

## Быстрый локальный запуск

### 1. Настройте базу

Создайте `.env` в корне проекта (файл исключён из Git). Можно начать с `.env.example`, но текущий пример содержит только `DB_USER`. Заполните:

```dotenv
DB_URL=jdbc:postgresql://localhost:5433/taketoday
DB_USER=taketoday
DB_PASSWORD=replace_with_local_password
PORT=8080
```

Замените пароль своим значением. Значения записывайте без кавычек, каждое на отдельной строке.

```powershell
docker compose -f compose.yaml up -d
docker compose -f compose.yaml ps
```

Используйте именно `-f compose.yaml`: этот файл запускает PostgreSQL 17 на порту **5433** и Mailpit. В репозитории также есть `docker-compose.yml` с PostgreSQL 16 на **5432**; это другой вариант конфигурации. Не смешивайте его порты с примером выше. База `taketoday` создаётся контейнером автоматически.

### 2. Запустите приложение

```powershell
.\start-backend.ps1
```

Скрипт читает настройки из `.env` и запускает Maven Wrapper. Если `MAIL_USERNAME` в `.env` не указан, письма уходят в локальный Mailpit из `compose.yaml` — отдельно задавать почтовые переменные не нужно.

Если порт 8080 занят (например, другим контейнером Docker), добавьте в `.env` строку `PORT=8081`.

Запуск без скрипта (например, если PowerShell запрещает `.ps1`):

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { Set-Item "env:$($Matches[1])" $Matches[2] } }
$env:MAIL_USERNAME = "noreply@taketoday.local"; $env:MAIL_PASSWORD = "local-unused"
$env:SPRING_MAIL_HOST = "localhost"; $env:SPRING_MAIL_PORT = "1025"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = "false"; $env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "false"
.\mvnw.cmd spring-boot:run
```

После сообщения об успешном старте:

- Сайт: http://localhost:8080 — доступен без входа.
- Письма с кодами: http://localhost:8025 — интерфейс Mailpit.
- PostgreSQL: `localhost:5433`, база `taketoday`, учётные данные из `.env`.

Mailpit принимает письма для любых адресов локально. При регистрации можно использовать, например, `customer@example.com`, а код взять в Mailpit. Внешняя доставка писем при этой настройке не выполняется.

Миграции из `src/main/resources/db/migration` применяются автоматически при запуске. Hibernate проверяет схему (`ddl-auto: validate`).

### 3. Проверьте сценарий входа

Откройте сайт без регистрации, затем создайте аккаунт покупателя или представителя заведения. Для сотрудника заполните данные заведения. Введите шестизначный код из Mailpit. При повторном входе потребуются пароль и новый код из письма.

Код действует 10 минут, повторная отправка доступна через 60 секунд. После пяти неверных кодов нужно начать вход заново.

## Запуск в IntelliJ IDEA

1. Откройте `pom.xml` как Maven-проект и выберите JDK 17+.
2. Выполните Reload All Maven Projects.
3. Создайте Spring Boot/Application-конфигурацию с главным классом **`com.example.taketoday.TakeTodayApplication`**.
4. В Environment variables укажите значения `DB_URL`, `DB_USER`, `DB_PASSWORD` из примера и все почтовые переменные из раздела быстрого запуска. При необходимости добавьте `ADMIN_EMAIL` и `ADMIN_PASSWORD`.
5. Запустите приложение и откройте http://localhost:8080.

IDEA не получает переменные из другого окна PowerShell и не читает `.env` автоматически. В Maven-плагине основной класс уже указан явно.

## Доступ и роли

| Раздел | Доступ |
| --- | --- |
| `/`, `/index.html`, `/styles.css`, `/app.js`, `/css/**`, `/js/**`, `/uploads/**` | Всем, включая гостей |
| `/api/public/**` | Всем |
| `/api/auth/**` | Доступны до входа; отдельные операции проверяют данные и код |
| `/api/customer/**`, `/api/orders/**` | Только `CUSTOMER` |
| `/profile` | Любой вошедший пользователь |
| `/staff`, `/staff/**`, `/api/staff/**` | Только `STAFF` — сотрудник/продавец |
| `/admin`, `/admin/**`, `/api/admin/**` | Только `ADMIN` |
| Прочие маршруты | Требуют авторизации |

Проверка прав выполняется сервером. Гость получает `401` для защищённого раздела, вошедший пользователь с другой ролью — `403`. Для POST-запросов дополнительно требуется CSRF-токен. Публичный доступ к маршруту не означает, что любой запрос к нему будет принят без валидации.

Сейчас сведения о роли и доступном разделе отображаются на главной после входа. Реализованы API `/api/customer/home`, `/api/staff/home`, `/api/admin/home`. Бронирование и оплата пока не реализованы.

Страницы этапа 1 (Thymeleaf, по макету Figma), ссылки на них появляются в меню главной после входа:

| Адрес | Что на странице |
| --- | --- |
| `/profile` | Личный кабинет: имя, телефон, смена пароля. После смены пароля текущая сессия остаётся, остальные завершаются |
| `/staff` | Кабинет заведения: карточка заведения и часы работы на сегодня |
| `/staff/venue` | Сведения о заведении: название, категория, описание, адрес, район, контакты, логотип (JPG/PNG до 2 МБ), график по дням |
| `/admin` | Обзор админ-панели (число пользователей и заведений). Остальные разделы — этап 5 |

Сотрудник видит и меняет только своё заведение: оно ищется по `owner_id` вошедшего пользователя. Логотипы сохраняются в папку `UPLOAD_DIR` (по умолчанию `uploads` в корне проекта, папка исключена из Git).

Сессии хранятся через cookie `JSESSIONID`. Блокировка аккаунта и смена версии авторизации отзывают доступ; сброс пароля отзывает прежние сессии.

### Создание администратора

До запуска добавьте в `.env` (при запуске из IDEA — в Environment variables):

```dotenv
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=replace_with_strong_password
```

Пароль: от 10 символов, не более 72 байт UTF-8. При старте приложение создаст администратора, если такого аккаунта ещё нет. Используйте адрес, который не занят покупателем или сотрудником. Смена `ADMIN_PASSWORD` не обновляет пароль уже существующего администратора. Публичная регистрация с ролью `ADMIN` запрещена. Администратор также входит с кодом из письма.

## Другие варианты запуска

### Собственная PostgreSQL

Создайте пользователя и базу `taketoday`, затем укажите их в `DB_URL`, `DB_USER`, `DB_PASSWORD`. Пользователь должен иметь права на создание таблиц и применение миграций. Пример URL: `jdbc:postgresql://localhost:5432/taketoday`. Docker для базы в этом варианте не нужен. Почту настройте отдельно.

### Реальная почта

Вместо блока Mailpit задайте `MAIL_USERNAME` и `MAIL_PASSWORD` для Gmail (пароль приложения), убрав переопределения `SPRING_MAIL_*`. Текущий `application.yml` использует `smtp.gmail.com:587`, SMTP AUTH и STARTTLS; отправителем служит `MAIL_USERNAME`. Для другого провайдера переопределите соответствующие `SPRING_MAIL_*`. Переменные `SMTP_*` и `MAIL_FROM` в текущей конфигурации не подключены к этим свойствам.

### Отдельный frontend-сервер

Для обычного запуска Node.js не требуется: Spring Boot раздаёт сайт на 8080. Если нужен отдельный сервер, запустите backend, затем в другом окне:

```powershell
.\start-frontend.ps1
```

Откройте http://localhost:5173. Сервер `frontend/server.mjs` читает те же файлы из `src/main/resources/static` и проксирует `/api/` на `http://localhost:8080`. Для другого backend задайте `API_URL`, для другого frontend-порта — `FRONTEND_PORT`. Используйте один и тот же адрес сайта в рамках сессии.

## Проверки

Проверка главной страницы, ресурсов и ограничений ролей без PostgreSQL и SMTP:

```powershell
.\mvnw.cmd -Dtest=HomePageTests test
```

Если Mockito не может автоматически подключить Java-агент, используйте явное подключение (версия соответствует текущей зависимости проекта):

```powershell
.\mvnw.cmd -Dtest=HomePageTests "-DargLine=-javaagent:${env:USERPROFILE}/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" test
```

При нестандартном Maven local repository скорректируйте путь.

Полный набор тестов (H2 добавлен в `pom.xml` с областью `test`, PostgreSQL и SMTP не нужны):

```powershell
.\mvnw.cmd test
```

Тесты этапа 1 лежат в `src/test/java/com/example/taketoday/stage1`: личный кабинет, сведения о заведении, доступ по ролям и правила проверки полей.

Проверка синтаксиса JavaScript при установленном Node.js:

```powershell
node --check src/main/resources/static/app.js
node --check src/main/resources/static/js/app.js
node --check frontend/server.mjs
```

Коллекция API: [postman/TakeToday.postman_collection.json](postman/TakeToday.postman_collection.json). Сценарии: [postman/README.md](postman/README.md). Для настройки запуска ориентируйтесь на этот README: старые инструкции Postman о `.env` и SMTP требуют поправок, описанных выше.

## Структура и последние изменения

- `src/main/java/com/example/taketoday/auth` — авторизация и регистрация.
- `src/main/java/com/example/taketoday/common`, `profile`, `venue`, `admin` — личный кабинет, сведения о заведении и админ-панель (этап 1).
- `src/main/resources/templates`, `static/css/app.css`, `static/js/app.js` — страницы Thymeleaf этапа 1 и их стили.
- `src/main/resources/static/fonts`, `static/css/fonts.css` — шрифты и иконки лежат в проекте, без Google Fonts (как добавить иконку — `docs/fonts.md`).
- `/orders`, `/staff/pickup`, `/staff/orders` — пока страницы «Раздел появится на этапе N» (`ComingSoonController`), чтобы ссылки в меню не вели на 404.
- `src/main/resources/static/index.html`, `styles.css`, `app.js` — общий источник интерфейса для Spring Boot и отдельного Node-сервера. Ранее файлы находились в `frontend`; перенос устраняет 404 из-за отсутствия ресурсов в сборке.
- `src/main/resources/db/migration` — миграции Flyway.
- `frontend/server.mjs` — необязательный сервер интерфейса и API-прокси.
- `src/test/java/com/example/taketoday/HomePageTests.java` — проверки публичной главной и защищённых разделов.
- `docs/SPEC.md` — общее рабочее ТЗ команды.
- `pom.xml` — явно выбран основной класс `com.example.taketoday.TakeTodayApplication`.
- Главная и её ресурсы открыты гостям; маршруты продавца и администратора защищены проверкой ролей.

## Диагностика

| Симптом | Что проверить |
| --- | --- |
| Whitelabel `401` на главной | Правильный класс запуска, актуальность кода и полный перезапуск приложения |
| Whitelabel `404` на главной | После Maven-сборки должен существовать `target/classes/static/index.html`; обновите Maven-проект, пересоберите и перезапустите |
| Ошибка подключения к БД | Для `compose.yaml` используйте порт 5433; проверьте `docker compose -f compose.yaml ps` и данные `.env` |
| Ошибка пароля PostgreSQL после изменения `.env` | Пароль контейнера задаётся при первой инициализации volume; изменение `.env` не меняет пароль существующей БД |
| Ошибка `${MAIL_USERNAME}` / `${MAIL_PASSWORD}` | Задайте обе переменные в окружении запуска; одного `.env` для них недостаточно |
| `503` при отправке кода | Доступность SMTP, Mailpit и почтовые настройки текущего процесса |
| `403` при POST | Получите новый токен через `/api/auth/csrf` и передайте его вместе с cookie сессии |
| `401` после подтверждения кода | Cookie `JSESSIONID`, единый адрес `localhost`, актуальность сессии |
| `429` | Пауза между письмами 60 секунд и лимит попыток в 15-минутном окне |
| PowerShell запрещает `.ps1` | Используйте разрешённую в вашей среде политику либо задайте переменные вручную и выполните `.\mvnw.cmd spring-boot:run` |

Остановка приложения: `Ctrl+C`. Остановка контейнеров с сохранением данных:

```powershell
docker compose -f compose.yaml down
```
