import pathlib
import re

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SettingsScreen.kt")
lines = path.read_text(encoding="utf-8").splitlines()
STR = re.compile(r'"(?:\\.|[^"\\])*"')


def delta(line):
    clean = STR.sub('""', line)
    return clean.count("{") - clean.count("}")


def match_block(start):
    depth = 0
    index = start
    while index < len(lines):
        depth += delta(lines[index])
        if depth == 0 and index > start:
            return index
        if depth == 0 and index == start and "{" in lines[index]:
            return index
        index += 1
    raise RuntimeError("unmatched")


cards = {}
i = 0
while i < len(lines):
    stripped = lines[i].strip()
    if stripped.startswith('SettingsCard("'):
        title = stripped.split('"')[1]
        end = match_block(i)
        cards[title] = lines[i:end + 1]
        i = end + 1
        continue
    i += 1

column_start = next(
    idx for idx, line in enumerate(lines)
    if line.strip() == "Column("
    and any("verticalScroll(rememberScrollState())" in lines[j] for j in range(idx, min(idx + 8, len(lines))))
)

close = None
for idx in range(column_start + 5, len(lines)):
    if lines[idx].strip() == "}" and lines[idx].startswith("        }"):
        window = "\n".join(lines[idx + 1: idx + 5])
        if "showServerDialog" in window:
            close = idx
            break
if close is None:
    raise SystemExit("column close not found")

footer = None
for idx in range(column_start, close):
    if lines[idx].strip().startswith("HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)"):
        end = idx
        while end < close and "Spacer(Modifier.height(12.dp))" not in lines[end]:
            end += 1
        footer = lines[idx:end + 1]
        break
footer = footer or []


def render(block):
    return ["    " + line if line.strip() else line for line in block]


groups = [
    ("MODELS", ["模型提供方", "接口"]),
    ("SERVERS", ["服务器"]),
    ("GENERATION", ["生成参数", "行为"]),
    ("CONTEXT", ["权限与上下文"]),
    ("PROMPT", ["系统提示词"]),
    ("WORKSPACE", ["工作区"]),
]

content = [
    "            if (section == null) {",
    "                SettingsMenu(",
    "                    providerCount = providers.size,",
    "                    serverCount = servers.size,",
    "                    onSelect = { section = it },",
    "                )",
    "            }",
]
for name, titles in groups:
    content.append(f"            if (section == SettingsSection.{name}) {{")
    for title in titles:
        content.extend(render(cards[title]))
    content.append("            }")
content.append("            if (section == SettingsSection.ABOUT) {")
content.extend(render(footer))
content.append("            }")

block = [
    "        Column(",
    "            Modifier",
    "                .weight(1f)",
    "                .verticalScroll(rememberScrollState())",
    "                .padding(horizontal = 14.dp, vertical = 8.dp),",
    "            verticalArrangement = Arrangement.spacedBy(12.dp),",
    "        ) {",
] + content + [
    "        }",
]

new_lines = lines[:column_start] + block + lines[close + 1:]
path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
print("fixed column, cards:", list(cards))
