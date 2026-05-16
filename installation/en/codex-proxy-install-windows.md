# Codex Proxy Wrapper on Windows

Goal: create a launcher file (for example `codexp.cmd`) that runs `codex` with proxy variables, and then point project config to that launcher path.

## Option A: `cmd` wrapper (recommended)

### 1) Create launcher file

Create `C:\Tools\codexp.cmd` with content:

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

Notes:
- You can choose any file name/path.
- Replace proxy host/port with your own values.

### 2) Add wrapper folder to `PATH`

Add `C:\Tools` to system/user `PATH`.

After reopening terminal:

```bat
where codexp
codexp --help
```

## Option B: PowerShell wrapper

Create `C:\Tools\codexp.ps1`:

```powershell
$env:PROXY_HTTP = "http://127.0.0.1:9997"
$env:PROXY_SOCKS = "socks5://127.0.0.1:9997"

$env:HTTP_PROXY = $env:PROXY_HTTP
$env:HTTPS_PROXY = $env:PROXY_HTTP

$env:WS_PROXY = $env:PROXY_HTTP
$env:WSS_PROXY = $env:PROXY_HTTP

codex --sandbox danger-full-access @args
```

If needed, adjust execution policy for local scripts.

## Use in project config

In `config.ini` set wrapper path explicitly:

```ini
codex.local.command_path=C:/Tools/codexp.cmd
```

Now the project will launch Codex through proxy via this wrapper.
## Browser URL

After the application/service is running, open:

```text
http://localhost:8080
```

If you changed `server.port`, use the same host with your custom port.

