import os
import sys

import paramiko

HOST = os.environ.get("SSH_HOST", "")
PORT = int(os.environ.get("SSH_PORT", "22"))
USER = os.environ.get("SSH_USER", "root")
KEY = os.environ.get("SSH_KEY", "")
CF_TOKEN = os.environ.get("CF_TOKEN", "")

HERE = os.path.dirname(os.path.abspath(__file__))
FILES = ["app.py", "luoty-feedback.service", "install.sh"]


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, key_filename=KEY, timeout=20)
    try:
        sftp = client.open_sftp()
        remote_dir = "/opt/luoty-feedback-deploy"
        try:
            sftp.mkdir(remote_dir)
        except OSError:
            pass
        for name in FILES:
            local = os.path.join(HERE, "..", "server", "feedback", name)
            sftp.put(local, f"{remote_dir}/{name}")
            print("uploaded", name)
        sftp.close()

        command = f"cd {remote_dir} && bash install.sh '{CF_TOKEN}'"
        stdin, stdout, stderr = client.exec_command(command, timeout=600)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        print("EXIT", code)
        print(out)
        if err.strip():
            print("STDERR")
            print(err)
        return code
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
