import pathlib

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SettingsScreen.kt")
lines = path.read_text(encoding="utf-8").splitlines()
out = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()
    if stripped == 'title = "多模态（可发图片）",' and i > 0 and lines[i - 1].strip() == "SwitchRow(":
        # 回退掉已经写出的 "SwitchRow(" 行
        if out and out[-1].strip() == "SwitchRow(":
            out.pop()
        while i < len(lines) and lines[i].strip() != ")":
            i += 1
        i += 1
        continue
    if stripped == 'label = "上下文长度（tokens）",' and i > 0 and lines[i - 1].strip() == "NumberField(":
        block = "\n".join(lines[i:i + 6])
        if "draft.contextWindow" in block:
            if out and out[-1].strip() == "NumberField(":
                out.pop()
            while i < len(lines) and lines[i].strip() != ")":
                i += 1
            i += 1
            continue
    out.append(line)
    i += 1

path.write_text("\n".join(out) + "\n", encoding="utf-8")
print("lines now", len(out))
