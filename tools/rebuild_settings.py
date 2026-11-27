import pathlib
import re

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SettingsScreen.kt")
lines = path.read_text(encoding="utf-8").splitlines()

STR = re.compile(r'"(?:\\.|[^"\\])*"')


def depth_delta(line: str) -> int:
    clean = STR.sub('""', line)
    return clean.count("{") - clean.count("}")


def match_block(start: int) -> int:
    depth = 0
    index = start
    while index < len(lines):
        depth += depth_delta(lines[index])
        if depth == 0 and index > start:
            return index
        if depth == 0 and index == start and "{" in lines[index]:
            return index
        index += 1
    raise RuntimeError(f"unmatched block at {start + 1}")


# 1. 收集卡片块（原始文件中）
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

print("cards:", list(cards))

# 2. 找 Column 内容区
column_start = None
for index, line in enumerate(lines):
    if line.strip() == "Column(" and any(
        "verticalScroll(rememberScrollState())" in lines[j] for j in range(index, min(index + 8, len(lines)))
    ):
        column_start = index
        break
if column_start is None:
    raise SystemExit("column not found")
column_end = match_block(column_start)

# 3. 找页脚（工作区卡片之后的 HorizontalDivider 到 Spacer(12.dp)）
footer = None
for index in range(column_start, column_end):
    if lines[index].strip().startswith("HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)"):
        end = index
        while end < column_end and "Spacer(Modifier.height(12.dp))" not in lines[end]:
            end += 1
        footer = lines[index:end + 1]
        break
if footer is None:
    footer = []


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

new_lines = lines[:column_start + 1] + content + lines[column_end:]
path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
print("rebuilt settings content, lines:", len(new_lines))
