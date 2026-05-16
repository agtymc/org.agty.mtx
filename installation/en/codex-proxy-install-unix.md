# Codex Proxy Wrapper on Unix (Linux/macOS)

Goal: create a small launcher file in `/usr/local/bin/` (for example `codexp`) that always runs `codex` with proxy environment variables.

Then you can run it from anywhere in terminal:

```bash
codexp
```

And in this project config you can point directly to this wrapper path.

## 1) Create launcher file

Create file `/usr/local/bin/codexp`:

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

Notes:
- You can choose any file name instead of `codexp`.
- Replace proxy address/port with your own values.
- `PROXY_SOCKS` is declared for convenience if you later need SOCKS-based tools.

## 2) Make it executable

```bash
sudo chmod +x /usr/local/bin/codexp
```

## 3) Validate

```bash
which codexp
codexp --help
```

If output shows `/usr/local/bin/codexp`, wrapper is globally available.

## 4) Use in project config

In `config.ini` set launcher path:

```ini
codex.local.command_path=/usr/local/bin/codexp
```

Now the project will start Codex through this proxy wrapper.
## Browser URL

After the application/service is running, open:

```text
http://localhost:8080
```

If you changed `server.port`, use the same host with your custom port.

