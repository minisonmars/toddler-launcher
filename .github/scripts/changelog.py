"""Release notes from CHANGELOG.md.

  notes             print the "## Next" section (the notes for the build being made)
  stamp VERSION     turn "## Next" into "## vVERSION" and start a new empty "## Next"
  section TAG       print the section for a tag, e.g. v0.1.12
  tags              list every tag that has a section
"""
import re
import sys

PATH = "CHANGELOG.md"
EMPTY = "- (nothing yet)"


def read():
    text = open(PATH, encoding="utf-8").read()
    parts = re.split(r"^## (.+)$", text, flags=re.M)
    head = parts[0]
    sections = [(parts[i].strip(), parts[i + 1].strip()) for i in range(1, len(parts), 2)]
    return head, sections


def write(head, sections):
    out = head.rstrip() + "\n\n" + "\n\n".join(f"## {t}\n\n{b}" for t, b in sections) + "\n"
    open(PATH, "w", encoding="utf-8").write(out)


def body_of(sections, title):
    for t, b in sections:
        if t == title:
            return b
    return ""


cmd = sys.argv[1]
head, sections = read()
if cmd == "notes":
    b = body_of(sections, "Next")
    print(b if b and b != EMPTY else "- Small fixes and improvements")
elif cmd == "stamp":
    v = "v" + sys.argv[2]
    b = body_of(sections, "Next")
    rest = [(t, x) for t, x in sections if t != "Next"]
    new = [("Next", EMPTY)]
    if b and b != EMPTY:
        new.append((v, b))
    write(head, new + rest)
elif cmd == "section":
    print(body_of(sections, sys.argv[2]))
elif cmd == "tags":
    for t, _ in sections:
        if t.startswith("v"):
            print(t)
