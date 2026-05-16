# Install AGTY/MTX as a systemd Service (Unix)

This guide is based on the example in `/home/agarty/scripts/org.agty.mtx/`.

Assumptions:
- The application is already built.
- Deployment directory already exists, for example: `/home/agarty/scripts/org.agty.mtx/`.
- Inside it you already have:
  - `bin/` (application files)
  - `logs/` (log files)
  - launcher script (example: `org.agty.mtx.sh`)

Do not describe or manage auto-generated files.

## 1) Create/update launcher script

Example launcher file:

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

Make it executable:

```bash
chmod +x /home/agarty/scripts/org.agty.mtx/org.agty.mtx.sh
```

## 2) Create systemd service file

Create file `/etc/systemd/system/org.agty.mtx.service`:

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

## 3) Reload systemd

```bash
sudo systemctl daemon-reload
```

## 4) Enable auto-start on boot

```bash
sudo systemctl enable org.agty.mtx.service
```

## 5) Start service

```bash
sudo systemctl start org.agty.mtx.service
```

## 6) Check status

```bash
sudo systemctl status org.agty.mtx.service --no-pager
```

## 7) View logs

Application file logs:

```bash
tail -f /home/agarty/scripts/org.agty.mtx/logs/org-agty-mtx-1.0.0.log
```

Systemd journal logs:

```bash
journalctl -u org.agty.mtx.service -f
```

## Service management commands

Restart:

```bash
sudo systemctl restart org.agty.mtx.service
```

Stop:

```bash
sudo systemctl stop org.agty.mtx.service
```

Disable auto-start:

```bash
sudo systemctl disable org.agty.mtx.service
```
