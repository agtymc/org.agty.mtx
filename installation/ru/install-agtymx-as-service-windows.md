# Установка AGTY/MTX как Windows-сервиса

Эта инструкция настраивает Windows-сервис для уже собранного приложения AGTY/MTX.

## Предварительные условия

- Приложение уже собрано.
- Выбран свободный порт (в примере используется `8080`).
- Указан путь к Java: `C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\java.exe`.
- Существует директория деплоя (пример): `C:\agty\org.agty.mtx\`.
- В директории деплоя уже есть `bin\` (файлы приложения) и `logs\` (файлы логов).

## Установка через WinSW

1. Скачайте WinSW из репозитория: https://github.com/winsw/winsw/releases. В разделе `Assets` выберите `WinSW-x64.exe` или файл под вашу версию ОС.
2. Поместите скачанный файл в `C:\agty\org.agty.mtx\`.
3. Переименуйте файл в `org.agty.mtx.service.exe`.
4. Создайте конфигурацию `org.agty.mtx.service.xml`. Обратите внимание: путь к Java указан в кавычках.
```xml
<service>
   <id>org.agty.mtx</id>
   <name>AGTY/MTX</name>
   <description>AGTY/MTX Application Service</description>
   <executable>"C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\java.exe"</executable>
   <arguments>-jar "C:\agty\org.agty.mtx\bin\org-agty-mtx-1.0.0.jar" --server.port=8080 --server.address=127.0.0.1 --spring.main.web-application-type=servlet</arguments>
   <logpath>C:\agty\org.agty.mtx\logs</logpath>
   <logmode>rotate</logmode>
   <redirectstderrtolog>true</redirectstderrtolog>
   <redirectstdouttolog>true</redirectstdouttolog>
   <startmode>Automatic</startmode>
</service>
```
5. Откройте `cmd` или PowerShell.
6. Перейдите в директорию `C:\agty\org.agty.mtx`.
7. Установите сервис:

```bat
org.agty.mtx.service.exe install
```

## Управление сервисом

Запуск:

```bat
org.agty.mtx.service.exe start
```

Остановка:

```bat
org.agty.mtx.service.exe stop
```

Перезапуск:

```bat
org.agty.mtx.service.exe restart
```

Статус:

```bat
org.agty.mtx.service.exe status
```

Удаление:

```bat
org.agty.mtx.service.exe uninstall
```

Если загружаете обновление версии, обязательно меняйте номер версии в конфиге и перезапускайте сервис.

## Адрес в браузере

После запуска приложения/сервиса откройте:

```text
http://localhost:8080
```

Если меняли `server.port`, используйте тот же хост с вашим портом.
