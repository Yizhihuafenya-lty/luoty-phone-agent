import os
import sys

import paramiko

HOST = os.environ.get("SSH_HOST", "")
PORT = int(os.environ.get("SSH_PORT", "22"))
USER = os.environ.get("SSH_USER", "root")
KEY = os.environ.get("SSH_KEY", "")
TOKEN = os.environ.get("CF_TOKEN", "")
LOCAL_BIN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cloudflared-linux-amd64")


def run(client, command, timeout=300):
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    print(f"$ {command}\nEXIT={code}\n{out}{err}")
    return code


def main():
    if not TOKEN:
        print("CF_TOKEN missing")
        return 2
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, key_filename=KEY, timeout=20)
    try:
        sftp = client.open_sftp()
        print("uploading cloudflared ...")
        sftp.put(LOCAL_BIN, "/usr/local/bin/cloudflared")
        sftp.close()
        run(client, "chmod 755 /usr/local/bin/cloudflared && /usr/local/bin/cloudflared --version")
        if run(client, "test -f /etc/systemd/system/cloudflared.service && echo exists || echo missing") == 0:
            pass
        run(client, f"/usr/local/bin/cloudflared service install '{TOKEN}'")
        run(client, "systemctl enable --now cloudflared")
        run(client, "sleep 6; systemctl is-active cloudflared")
        run(client, "journalctl -u cloudflared -n 30 --no-pager | tail -30")
        run(client, "python3 - <<'PY'\nimport json, urllib.request\nprint(urllib.request.urlopen('http://127.0.0.1:18080/health', timeout=5).read().decode())\nPY")
        run(client, "cat /opt/luoty-feedback/admin_token")
    finally:
        client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
