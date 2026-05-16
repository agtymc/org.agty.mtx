# Install AGTY/MTX as a Windows Service

This guide configures a Windows service for an already built AGTY/MTX application.

Assumptions:
- The application is already built.
- Deployment directory exists (example): `C:\agty\org.agty.mtx\`
- You already have:
  - `bin\` (application files)
  - `logs\` (log files)

## 1) Create launcher script

Create `C:\agty\org.agty.mtx\org.agty.mtx.cmd`:

```bat
@echo off
setlocal

set "BASE_DIR=C:\agty\org.agty.mtx"
set "JAR_PATH=%BASE_DIR%\bin\org-agty-mtx-1.0.0.jar"
set "LOG_PATH=%BASE_DIR%\logs\org-agty-mtx-1.0.0.log"

cd /d "%BASE_DIR%"
"C:\Program Files\Java\jdk-17\bin\java.exe" -jar "%JAR_PATH%" >> "%LOG_PATH%" 2>&1
```

Adjust Java path if needed.

## 2) Install service with `sc.exe`

Open terminal as Administrator and run:

```bat
sc.exe create org.agty.mtx binPath= "cmd.exe /c C:\agty\org.agty.mtx\org.agty.mtx.cmd" start= auto DisplayName= "AGTY/MTX Service"
```

Set restart policy:

```bat
sc.exe failure org.agty.mtx reset= 86400 actions= restart/5000
```

## 3) Start service

```bat
sc.exe start org.agty.mtx
```

## 4) Check service status

```bat
sc.exe query org.agty.mtx
```

## 5) View logs

File logs:

```bat
type C:\agty\org.agty.mtx\logs\org-agty-mtx-1.0.0.log
```

Live tail in PowerShell:

```powershell
Get-Content C:\agty\org.agty.mtx\logs\org-agty-mtx-1.0.0.log -Wait
```

## Service management commands

Stop:

```bat
sc.exe stop org.agty.mtx
```

Restart:

```bat
sc.exe stop org.agty.mtx
sc.exe start org.agty.mtx
```

Disable auto-start:

```bat
sc.exe config org.agty.mtx start= demand
```

Enable auto-start:

```bat
sc.exe config org.agty.mtx start= auto
```

Delete service:

```bat
sc.exe delete org.agty.mtx
```
