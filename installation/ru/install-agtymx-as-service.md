# Установка AGTY/MTX как systemd-сервиса (Unix)

Инструкция основана на примере в `/home/agarty/scripts/org.agty.mtx/`.

Предположения:
- Приложение уже собрано.
- Директория деплоя уже существует, например: `/home/agarty/scripts/org.agty.mtx/`.
- Внутри уже есть:
  - `bin/` (файлы приложения)
  - `logs/` (файлы логов)
  - скрипт запуска (пример: `org.agty.mtx.sh`)

Автоматически создаваемые файлы описывать и настраивать не нужно.

## 1) Создать/обновить скрипт запуска

Пример скрипта запуска:

```bash
cat >/home/agarty/scripts/org.agty.mtx/org.agty.mtx.sh <<'SCRIPT'
APP=org.agty.mtx-1.0.0
WORKDIR=/home/agarty/scripts/org.agty.mtx/
BINDIR=/home/agarty/scripts/org.agty.mtx/bin/
LOGFILE=/home/agarty/scripts/org.agty.mtx/logs/${APP}.log
PORT=8083
HOST=127.0.0.1

cd ${WORKDIR}
/usr/bin/cat /dev/null > ${LOGFILE}
/usr/bin/java -Xmx1024m -jar ${BINDIR}${APP}.jar --server.port=${PORT} --server.host=${HOST} >> ${LOGFILE}
SCRIPT
```

Сделать исполняемым:

```bash
chmod +x /home/agarty/scripts/org.agty.mtx/org.agty.mtx.sh
```

## 2) Создать файл systemd-сервиса

Создайте файл `/etc/systemd/system/org.agty.mtx.service`:

```bash
sudo tee /etc/systemd/system/org.agty.mtx.service >/dev/null <<'SERVICE'
[Unit]
Description=AGTY/MTX Service
After=network.target

[Service]
User=agarty
WorkingDirectory=/home/agarty/scripts/org.agty.mtx
ExecStart=/home/agarty/scripts/org.agty.mtx/org.agty.mtx.sh

SuccessExitStatus=143
TimeoutStopSec=10
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE
```

## 3) Перечитать конфигурацию systemd

```bash
sudo systemctl daemon-reload
```

## 4) Включить автозапуск при старте ОС

```bash
sudo systemctl enable org.agty.mtx.service
```

## 5) Запустить сервис

```bash
sudo systemctl start org.agty.mtx.service
```

## 6) Проверить статус

```bash
sudo systemctl status org.agty.mtx.service --no-pager
```

## 7) Просмотр логов

Логи приложения (файл):

```bash
tail -f /home/agarty/scripts/org.agty.mtx/logs/org-agty-mtx-1.0.0.log
```

Логи systemd journal:

```bash
journalctl -u org.agty.mtx.service -f
```

## Команды управления сервисом

Перезапуск:

```bash
sudo systemctl restart org.agty.mtx.service
```

Остановка:

```bash
sudo systemctl stop org.agty.mtx.service
```

Отключить автозапуск:

```bash
sudo systemctl disable org.agty.mtx.service
```
## Адрес в браузере

После запуска приложения/сервиса откройте:

```text
http://localhost:8080
```

Если меняли `server.port`, используйте тот же хост с вашим портом.

