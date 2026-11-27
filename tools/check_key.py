import base64
import io
import pathlib

import paramiko

path = pathlib.Path.home() / ".ssh" / "luoty_feedback_ed25519"
raw = path.read_bytes()
print("bytes:", len(raw))
text = raw.decode("utf-8", "replace")
lines = [line for line in text.strip().splitlines()]
print("lines:", len(lines), "lengths:", [len(line) for line in lines])
b64 = "".join(line for line in lines if not line.startswith("-----"))
try:
    blob = base64.b64decode(b64, validate=True)
    print("blob:", len(blob), "magic:", blob[:15])
except Exception as error:  # noqa: BLE001
    print("base64 error:", error)
try:
    key = paramiko.Ed25519Key.from_private_key(io.StringIO(text))
    print("paramiko ok:", key.get_base64()[:36])
except Exception as error:  # noqa: BLE001
    print("paramiko error:", type(error).__name__, error)
