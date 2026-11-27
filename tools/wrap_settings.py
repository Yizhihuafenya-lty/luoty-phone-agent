import io
import pathlib
import shutil

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SettingsScreen.kt")
backup = path.with_suffix(".kt.bak")
if not backup.exists():
    shutil.copyfile(path, backup)

lines = path.read_text(encoding="utf-8").splitlines(keepends=False)

# 1-based inclusive ranges -> section
blocks = [
    (125, 169, "MODELS"),
    (170, 237, "SERVERS"),
    (238, 310, "MODELS"),
    (311, 345, "GENERATION"),
    (346, 379, "GENERATION"),
    (380, 436, "CONTEXT"),
    (437, 459, "PROMPT"),
    (460, 487, "WORKSPACE"),
    (488, 495, "ABOUT"),
]

inserts = []  # (position_index, [lines]) position = insert before this 0-based index
for start, end, name in blocks:
    inserts.append((start - 1, [f"            if (section == SettingsSection.{name}) {{"]))
    inserts.append((end, ["            }"]))

# 菜单在第一个卡片之前
inserts.append((
    124 - 1,
    [
        "            if (section == null) {",
        "                SettingsMenu(",
        "                    providerCount = providers.size,",
        "                    serverCount = servers.size,",
        "                    onSelect = { section = it },",
        "                )",
        "            }",
    ],
))

for index, payload in sorted(inserts, key=lambda item: item[0], reverse=True):
    lines[index:index] = payload

path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("inserted", len(inserts), "guards; total lines", len(lines))
