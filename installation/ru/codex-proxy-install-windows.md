# Обертка Codex через Proxy на Windows

Цель: создать launcher-файл (например `codexp.cmd`), который запускает `codex` с прокси-переменными, а затем указать путь к нему в конфиге проекта.

## Вариант A: обертка `cmd` (рекомендуется)

### 1) Создать launcher-файл

Создайте `C:\Tools\codexp.cmd` со следующим содержимым:

```bat
@echo off
set "PROXY_HTTP=http://127.0.0.1:9997"
set "PROXY_SOCKS=socks5://127.0.0.1:9997"

set "HTTP_PROXY=%PROXY_HTTP%"
set "HTTPS_PROXY=%PROXY_HTTP%"

set "WS_PROXY=%PROXY_HTTP%"
set "WSS_PROXY=%PROXY_HTTP%"

codex --sandbox danger-full-access %*
```

Примечания:
- Можно выбрать любое имя/путь файла.
- Адрес и порт прокси замените на свои.

### 2) Добавить папку в `PATH`

Добавьте `C:\Tools` в системный или пользовательский `PATH`.

После перезапуска терминала:

```bat
where codexp
codexp --help
```

## Вариант B: обертка PowerShell

Создайте `C:\Tools\codexp.ps1`:

```powershell
$env:PROXY_HTTP = "http://127.0.0.1:9997"
$env:PROXY_SOCKS = "socks5://127.0.0.1:9997"

$env:HTTP_PROXY = $env:PROXY_HTTP
$env:HTTPS_PROXY = $env:PROXY_HTTP

$env:WS_PROXY = $env:PROXY_HTTP
$env:WSS_PROXY = $env:PROXY_HTTP

codex --sandbox danger-full-access @args
```

При необходимости скорректируйте execution policy для локальных скриптов.

## Использование в конфиге проекта

В `config.ini` явно укажите путь к wrapper-файлу:

```ini
codex.local.command_path=C:/Tools/codexp.cmd
```

Теперь проект будет запускать Codex через proxy через этот launcher.
## Адрес в браузере

После запуска приложения/сервиса откройте:

```text
http://localhost:8080
```

Если меняли `server.port`, используйте тот же хост с вашим портом.

