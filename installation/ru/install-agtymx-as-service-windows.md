# Установка AGTY/MTX как Windows-сервиса

Инструкция настраивает Windows-сервис для уже собранного приложения AGTY/MTX.

TODO:
https://github.com/winsw/winsw/releases
<service>
  <id>org.agty.mtx</id>
  <name>AGTY/MTX</name>
  <description>AGTY/MTX Application Service</description>
  <executable>"C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\java.exe"</executable>
  <arguments>-jar "d:\org-agty-mtx\bin\org-agty-mtx-1.0.1.jar" --server.port=8083 --server.address=127.0.0.1 --spring.main.web-application-type=servlet</arguments>
  <logpath>d:\org-agty-mtx\logs</logpath>
  <logmode>rotate</logmode>
  <redirectstderrtolog>true</redirectstderrtolog>
  <redirectstdouttolog>true</redirectstdouttolog>
  <startmode>Automatic</startmode>
</service>

Предположения:
- Приложение уже собрано.
- Директория деплоя существует (пример): `C:\agty\org.agty.mtx\`
- Внутри уже есть:
  - `bin\` (файлы приложения)
  - `logs\` (файлы логов)

## 1) Создать скрипт запуска

Создайте `C:\agty\org.agty.mtx\org.agty.mtx.cmd`:

```bat
@echo off
setlocal

set "BASE_DIR=C:\agty\org.agty.mtx"
set "JAR_PATH=%BASE_DIR%\bin\org-agty-mtx-1.0.0.jar"
set "LOG_PATH=%BASE_DIR%\logs\org-agty-mtx-1.0.0.log"

cd /d "%BASE_DIR%"
"C:\Program Files\Java\jdk-17\bin\java.exe" -jar "%JAR_PATH%" >> "%LOG_PATH%" 2>&1
```

При необходимости скорректируйте путь к Java.

## 2) Создать сервис через `sc.exe`

Откройте терминал от имени администратора и выполните:

```bat
sc.exe create org.agty.mtx binPath= "cmd.exe /c C:\agty\org.agty.mtx\org.agty.mtx.cmd" start= auto DisplayName= "AGTY/MTX Service"
```

Задать политику перезапуска:

```bat
sc.exe failure org.agty.mtx reset= 86400 actions= restart/5000
```

## 3) Запустить сервис

```bat
sc.exe start org.agty.mtx
```

## 4) Проверить статус сервиса

```bat
sc.exe query org.agty.mtx
```

## 5) Просмотр логов

Логи в файле:

```bat
type C:\agty\org.agty.mtx\logs\org-agty-mtx-1.0.0.log
```

Live-режим в PowerShell:

```powershell
Get-Content C:\agty\org.agty.mtx\logs\org-agty-mtx-1.0.0.log -Wait
```

## Команды управления сервисом

Остановить:

```bat
sc.exe stop org.agty.mtx
```

Перезапустить:

```bat
sc.exe stop org.agty.mtx
sc.exe start org.agty.mtx
```

Отключить автозапуск:

```bat
sc.exe config org.agty.mtx start= demand
```

Включить автозапуск:

```bat
sc.exe config org.agty.mtx start= auto
```

Удалить сервис:

```bat
sc.exe delete org.agty.mtx
```
## Адрес в браузере

После запуска приложения/сервиса откройте:

```text
http://localhost:8080
```

Если меняли `server.port`, используйте тот же хост с вашим портом.

