# Install AGTY/MTX as a Windows Service

This guide configures a Windows service for an already built AGTY/MTX application.

## Prerequisites

- The application is already built.
- A free port is selected (the example uses `8080`).
- The Java path is set: `C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\java.exe`.
- Deployment directory exists (example): `C:\agty\org.agty.mtx\`.
- The deployment directory already contains `bin\` (application files) and `logs\` (log files).

## Install with WinSW

1. Download WinSW from: https://github.com/winsw/winsw/releases. In `Assets`, choose `WinSW-x64.exe` or a binary matching your OS.
2. Place the downloaded file into `C:\agty\org.agty.mtx\`.
3. Rename it to `org.agty.mtx.service.exe`.
4. Create `org.agty.mtx.service.xml`. Note: the Java path is wrapped in quotes.
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
5. Open `cmd` or PowerShell.
6. Go to `C:\agty\org.agty.mtx`.
7. Install the service:

```bat
org.agty.mtx.service.exe install
```

## Service management

Start:

```bat
org.agty.mtx.service.exe start
```

Stop:

```bat
org.agty.mtx.service.exe stop
```

Restart:

```bat
org.agty.mtx.service.exe restart
```

Status:

```bat
org.agty.mtx.service.exe status
```

Uninstall:

```bat
org.agty.mtx.service.exe uninstall
```

If you deploy a new application version, update the JAR version in the XML config and restart the service.

## Browser URL

After the application/service is running, open:

```text
http://localhost:8080
```

If you changed `server.port`, use the same host with your custom port.
