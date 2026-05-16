# AGTY/MTX (`org-agty-mtx`)

Создан при помощи Codex; не претендует на проект, который заменит все LLM-чаты. Распространяется AS-IS.

Веб-приложение чата на Spring Boot с пользовательскими аккаунтами, группировкой диалогов, потоковой генерацией ответов и поддержкой нескольких backend-моделей:
- локальные модели через Ollama,
- локальный Codex CLI (`codex:local`): требуется учетная запись, а при запуске через proxy нужно учесть соответствующие настройки (см. инструкции по установке),
- Codex через OpenAI API (`codex:api`): интеграция пока не завершена полностью.

## Технологии

- Java 17
- Spring Boot 3.1.5 (Web, WebFlux, Thymeleaf, Security, JPA)
- SQLite (`sqlite-jdbc`) + Hibernate dialect
- Frontend: Vanilla JS + CSS + Thymeleaf templates

## Основные возможности

### 1) Аутентификация и сессии

- Регистрация и вход по логину/паролю.
- Выход из аккаунта.
- Просмотр текущей авторизации (`/api/auth/me`).
- Профиль пользователя: имя и email.
- Смена пароля.
- Длительная серверная сессия:
  - timeout `30d`,
  - persistent sessions,
  - хранение сессий в `./sessions`.

### 2) Чаты и группы

- Дерево «Группы и чаты» в боковом меню.
- Автосоздание дефолтной группы и первого чата для нового пользователя.
- CRUD по группам:
  - создать, переименовать, удалить,
  - перетасовка порядка групп,
  - защита от удаления последней группы,
  - перенос чатов при удалении группы.
- CRUD по чатам:
  - создать, переименовать, удалить,
  - перенос между группами,
  - переупорядочивание чатов внутри группы.
- Сворачивание/разворачивание групп с сохранением состояния на сервере.

### 3) Сообщения и генерация

- Обычная отправка сообщения (sync).
- Потоковая генерация ответа (stream/SSE-подобный поток чанков).
- Остановка активной генерации (кнопка стоп + backend endpoint).
- Автогенерация названия нового чата по первому сообщению.
- Поддержка `temperature` в запросах к модели.
- Приватный чат:
  - не сохраняется в БД,
  - поддерживает потоковую генерацию по истории в памяти клиента.

### 4) Поддерживаемые backend-модели

- **Ollama**: список локальных моделей, sync/stream запросы, usage-метрики.
- **Codex Local (CLI)**:
  - модель-алиас (по умолчанию `codex:local`),
  - запуск внешней команды (`codexp exec ...`),
  - режим `resume --last` при `keep_session=true`,
  - fallback на новый запуск при ошибке resume,
  - опциональный debug-лог.
- **Codex API (OpenAI)**:
  - модель-алиас (по умолчанию `codex:api`),
  - запросы к `/chat/completions` (sync/stream),
  - поддержка прокси,
  - API ключ из `config.ini` или `OPENAI_API_KEY`.

### 5) Каталог моделей

- Загрузка моделей при старте приложения.
- Обновление списка моделей по кнопке (`loading...` в UI во время обновления).
- Слияние списка Ollama + включенных Codex-алиасов.
- Выбор модели в форме отправки.
- Сохранение выбранной модели в пользовательских настройках.

### 6) UI/UX

- Темная/светлая тема.
- Настройки интерфейса на пользователя:
  - размер шрифта чата,
  - размер шрифта меню,
  - ширина sidebar,
  - сторона кнопок навигации по ответам (left/right),
  - выбранная модель.
- Ограниченный внутренний контейнер чата.
- Адаптивная прокрутка:
  - авто-скролл во время стрима,
  - отключение авто-скролла при ручной прокрутке,
  - навигационные кнопки вверх/вниз по ответам.
- Кнопки копирования:
  - копирование сообщения,
  - копирование блоков кода.

### 7) Статистика и токены

- Отображение токенов текущего чата: input/output/total.
- Раздел «Статистика» по моделям:
  - число ответов,
  - prompt/completion/total tokens,
  - средние токены на ответ,
  - output tokens/sec,
  - суммы и средние по duration-метрикам,
  - done reasons,
  - первое/последнее использование.
- Сводная статистика по всем моделям пользователя.

### 8) Дополнительные страницы

- `/about`
- `/contacts`
- `/downloads`

### 9) Печать

- Специальный print-режим: в печати остается текст чата и ссылка на домен-источник.

## Конфигурация

### `application.yml`

Ключевые параметры по умолчанию:
- `server.port: 8080`
- SQLite: `jdbc:sqlite:./chat-app.db...`
- `spring.jpa.hibernate.ddl-auto: update`
- сессии: `30d`, `persistent: true`, `store-dir: ./sessions`
- Ollama API: `http://localhost:11434`

### `config.ini`

Скопируйте `config.ini-sample` в `config.ini` и настройте нужные блоки:

- `codex.local.*` для локального CLI режима
- `codex.api.*` для API режима (включая прокси)

Пример:

```ini
codex.local.enable=true
codex.local.command_path=/usr/local/bin/codexp
codex.local.model_name=codex:local

codex.api.enable=true
codex.api.api_key=YOUR_KEY
codex.api.openai_model=gpt-5.5
```

## Запуск

### Требования

- JDK 17+
- Maven Wrapper (уже в проекте)
- (опционально) локальный Ollama
- (опционально) `codexp` для режима `codex:local`

### Команды

```bash
./mvnw -q -DskipTests compile
./mvnw spring-boot:run
```

Открыть: `http://localhost:8080`

## Инструкции по установке

Инструкции по установке приложения и сервиса находятся в директории `installation/`:

- Unix service (EN): `installation/en/install-agtymx-as-service.md`
- Unix service (RU): `installation/ru/install-agtymx-as-service.md`
- Windows service (EN): `installation/en/install-agtymx-as-service-windows.md`
- Windows service (RU): `installation/ru/install-agtymx-as-service-windows.md`
- Codex proxy wrapper (EN): `installation/en/codex-proxy-install-unix.md`, `installation/en/codex-proxy-install-windows.md`
- Codex proxy wrapper (RU): `installation/ru/codex-proxy-install-unix.md`, `installation/ru/codex-proxy-install-windows.md`

## Основные API-эндпоинты

- Auth:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
  - `GET/PUT /api/auth/profile`
  - `POST /api/auth/change-password`
- Модели и bootstrap:
  - `GET /api/bootstrap`
  - `GET /api/models`
  - `POST /api/models/refresh`
- Настройки и статистика:
  - `GET/PUT /api/settings`
  - `GET /api/stats/models`
- Группы:
  - `GET/POST /api/groups`
  - `PUT/DELETE /api/groups/{groupId}`
  - `PATCH /api/groups/reorder`
  - `PUT /api/groups/collapsed`
- Чаты:
  - `GET/POST /api/chats`
  - `GET/PUT/DELETE /api/chats/{chatId}`
  - `PATCH /api/chats/{chatId}/move`
  - `PATCH /api/chats/reorder`
  - `DELETE /api/chats/{chatId}/group`
- Сообщения:
  - `POST /api/chats/{chatId}/messages`
  - `POST /api/chats/{chatId}/messages/stream`
  - `POST /api/chats/{chatId}/messages/stop`
  - `POST /api/private/messages/stream`

## Безопасность

- Spring Security с сессионной auth.
- Доступ к `/api/**` только для авторизованных пользователей (кроме `/api/auth/**`).
- Пароли хэшируются через BCrypt.

## Примечания по репозиторию

- Локальная БД (`chat-app.db*`) и сессии должны быть в `.gitignore`.
- Скрипт `scripts/update-project-context.sh` обновляет `files/project-context.md` по локальной истории сессий Codex.
