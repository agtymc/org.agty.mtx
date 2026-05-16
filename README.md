# AGTY/MTX

Created with Codex; it does not aim to be a project that replaces all LLM chat apps. Distributed AS-IS.

A Spring Boot chat web application with user accounts, grouped conversations, streaming responses, and multiple model backends:
- local models via Ollama,
- local Codex CLI (`codex:local`): an account is required, and if you run through a proxy, configure it accordingly (see the installation instructions).
- Codex via OpenAI API (`codex:api`): not fully completed yet.

## Tech Stack

- Java 17
- Spring Boot 3.1.5 (Web, WebFlux, Thymeleaf, Security, JPA)
- SQLite (`sqlite-jdbc`) + Hibernate dialect
- Frontend: Vanilla JS + CSS + Thymeleaf templates

## Core Features

### 1) Authentication and Sessions

- Register and login with username/password.
- Logout.
- Current auth status endpoint (`/api/auth/me`).
- User profile: display name and email.
- Password change.
- Long-lived server session:
  - timeout `30d`,
  - persistent sessions,
  - session storage in `./sessions`.

### 2) Chats and Groups

- "Groups and Chats" tree in the sidebar.
- Automatic default group and first chat creation for new users.
- Group CRUD:
  - create, rename, delete,
  - reorder groups,
  - protection from deleting the last group,
  - move chats when deleting a group.
- Chat CRUD:
  - create, rename, delete,
  - move between groups,
  - reorder chats inside a group.
- Group collapse/expand state persisted on the server.

### 3) Messaging and Generation

- Regular message send (sync).
- Streaming responses (chunked stream).
- Stop active generation (stop button + backend endpoint).
- Auto-title generation for new chats from the first user message.
- `temperature` support for model requests.
- Private chat mode:
  - not stored in the database,
  - supports streaming with in-memory history on the client side.

### 4) Supported Model Backends

- **Ollama**: local model list, sync/stream requests, usage metrics.
- **Codex Local (CLI)**:
  - model alias (default `codex:local`),
  - external command execution (`codexp exec ...`),
  - `resume --last` mode when `keep_session=true`,
  - fallback to fresh execution if resume fails,
  - optional debug logging.
- **Codex API (OpenAI)**:
  - model alias (default `codex:api`),
  - `/chat/completions` requests (sync/stream),
  - proxy support,
  - API key from `config.ini` or `OPENAI_API_KEY`.

### 5) Model Catalog

- Model loading on application startup.
- Manual model list refresh (`loading...` state in UI while refreshing).
- Merged list of Ollama models + enabled Codex aliases.
- Model selection in the send form.
- Selected model persisted in user settings.

### 6) UI/UX

- Dark/light theme.
- Per-user UI settings:
  - chat font size,
  - menu font size,
  - sidebar width,
  - answer navigation side (left/right),
  - selected model.
- Constrained inner chat container.
- Adaptive scrolling:
  - auto-scroll during streaming,
  - auto-scroll disable on manual user scroll,
  - up/down navigation buttons for assistant answers.
- Copy actions:
  - copy full message,
  - copy code blocks.

### 7) Statistics and Tokens

- Current chat token counters: input/output/total.
- "Statistics" section by model:
  - reply count,
  - prompt/completion/total tokens,
  - average tokens per reply,
  - output tokens/sec,
  - summed and average duration metrics,
  - done reasons,
  - first/last usage timestamps.
- Overall usage summary across all user models.

### 8) Additional Pages

- `/about`
- `/contacts`
- `/downloads`

### 9) Print Mode

- Dedicated print mode: chat text and source domain link only.

## Configuration

### `application.yml`

Default key settings:
- `server.port: 8080`
- SQLite: `jdbc:sqlite:./chat-app.db...`
- `spring.jpa.hibernate.ddl-auto: update`
- sessions: `30d`, `persistent: true`, `store-dir: ./sessions`
- Ollama API: `http://localhost:11434`

### `config.ini`

Copy `config.ini-sample` to `config.ini` and configure the required blocks:

- `codex.local.*` for local CLI mode
- `codex.api.*` for API mode (including proxy)

Example:

```ini
codex.local.enable=true
codex.local.command_path=/usr/local/bin/codexp
codex.local.model_name=codex:local

codex.api.enable=true
codex.api.api_key=YOUR_KEY
codex.api.openai_model=gpt-5.5
```

## Run

### Requirements

- JDK 17+
- Maven Wrapper (already included)
- (optional) local Ollama
- (optional) `codexp` for `codex:local`

### Commands

```bash
./mvnw -q -DskipTests compile
./mvnw spring-boot:run
```

Open: `http://localhost:8080`

## Installation Guides

Application/service installation guides are located in the `installation/` directory:

- Unix service (EN): `installation/en/install-agtymx-as-service.md`
- Unix service (RU): `installation/ru/install-agtymx-as-service.md`
- Windows service (EN): `installation/en/install-agtymx-as-service-windows.md`
- Windows service (RU): `installation/ru/install-agtymx-as-service-windows.md`
- Codex proxy wrapper (EN): `installation/en/codex-proxy-install-unix.md`, `installation/en/codex-proxy-install-windows.md`
- Codex proxy wrapper (RU): `installation/ru/codex-proxy-install-unix.md`, `installation/ru/codex-proxy-install-windows.md`

## Main API Endpoints

- Auth:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
  - `GET/PUT /api/auth/profile`
  - `POST /api/auth/change-password`
- Models and bootstrap:
  - `GET /api/bootstrap`
  - `GET /api/models`
  - `POST /api/models/refresh`
- Settings and stats:
  - `GET/PUT /api/settings`
  - `GET /api/stats/models`
- Groups:
  - `GET/POST /api/groups`
  - `PUT/DELETE /api/groups/{groupId}`
  - `PATCH /api/groups/reorder`
  - `PUT /api/groups/collapsed`
- Chats:
  - `GET/POST /api/chats`
  - `GET/PUT/DELETE /api/chats/{chatId}`
  - `PATCH /api/chats/{chatId}/move`
  - `PATCH /api/chats/reorder`
  - `DELETE /api/chats/{chatId}/group`
- Messages:
  - `POST /api/chats/{chatId}/messages`
  - `POST /api/chats/{chatId}/messages/stream`
  - `POST /api/chats/{chatId}/messages/stop`
  - `POST /api/private/messages/stream`

## Security

- Spring Security with session-based auth.
- `/api/**` is restricted to authenticated users (except `/api/auth/**`).
- Passwords are hashed with BCrypt.

## Repository Notes

- Local DB files (`chat-app.db*`) and session files should stay in `.gitignore`.
- `scripts/update-project-context.sh` updates `files/project-context.md` from local Codex session history.
