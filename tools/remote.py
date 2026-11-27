import os
import sys

import paramiko

HOST = os.environ.get("SSH_HOST", "")
PORT = int(os.environ.get("SSH_PORT", "22"))
USER = os.environ.get("SSH_USER", "root")
PASS = os.environ.get("SSH_PASS", "")
KEY = os.environ.get("SSH_KEY", "")


def connect():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    kwargs = dict(
        hostname=HOST,
        port=PORT,
        username=USER,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
    )
    if KEY:
        kwargs["key_filename"] = KEY
    else:
        kwargs["password"] = PASS
    client.connect(**kwargs)
    return client


def run(client, command, timeout=120):
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "exec"
    client = connect()
    try:
        if mode == "exec":
            code, out, err = run(client, sys.argv[2])
            print(f"EXIT={code}")
            if out:
                print("--- STDOUT ---")
                print(out)
            if err:
                print("--- STDERR ---")
                print(err)
        elif mode == "put":
            local, remote = sys.argv[2], sys.argv[3]
            sftp = client.open_sftp()
            sftp.put(local, remote)
            sftp.close()
            print(f"UPLOADED {local} -> {remote}")
        elif mode == "script":
            script = sys.stdin.read()
            sftp = client.open_sftp()
            tmp = "/tmp/luoty_deploy.sh"
            with sftp.file(tmp, "w") as handle:
                handle.write(script)
            sftp.close()
            code, out, err = run(client, f"bash {tmp}", timeout=600)
            print(f"EXIT={code}")
            if out:
                print("--- STDOUT ---")
                print(out)
            if err:
                print("--- STDERR ---")
                print(err)
        else:
            print(f"unknown mode {mode}")
            sys.exit(2)
    finally:
        client.close()


if __name__ == "__main__":
    main()
