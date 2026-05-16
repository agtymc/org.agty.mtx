# Обертка Codex через Proxy на Unix (Linux/macOS)

Цель: создать небольшой launcher-файл в `/usr/local/bin/` (например `codexp`), который всегда запускает `codex` с переменными прокси.

После этого запуск доступен из любого места в терминале:

```bash
codexp
```

И в конфиге проекта можно указать путь напрямую к этому wrapper-файлу.

## 1) Создать launcher-файл

Создайте файл `/usr/local/bin/codexp`:

```bash
sudo tee /usr/local/bin/codexp >/dev/null <<'SCRIPT'
#!/usr/bin/env bash
PROXY_HTTP="http://127.0.0.1:9997"
PROXY_SOCKS="socks5://127.0.0.1:9997"

export HTTP_PROXY="$PROXY_HTTP"
export HTTPS_PROXY="$PROXY_HTTP"

export WS_PROXY="$PROXY_HTTP"
export WSS_PROXY="$PROXY_HTTP"

codex --sandbox danger-full-access "$@"
SCRIPT
```

Примечания:
- Вместо `codexp` можно использовать любое имя файла.
- Адрес и порт прокси замените на свои.
- `PROXY_SOCKS` объявлен на будущее, если понадобится SOCKS для других инструментов.

## 2) Дать права на запуск

```bash
sudo chmod +x /usr/local/bin/codexp
```

## 3) Проверка

```bash
which codexp
codexp --help
```

Если вывод показывает `/usr/local/bin/codexp`, wrapper установлен глобально.

## 4) Использование в конфиге проекта

В `config.ini` укажите путь к launcher-файлу:

```ini
codex.local.command_path=/usr/local/bin/codexp
```

Теперь проект будет запускать Codex через этот proxy-wrapper.
## Адрес в браузере

После запуска приложения/сервиса откройте:

```text
http://localhost:8080
```

Если меняли `server.port`, используйте тот же хост с вашим портом.

