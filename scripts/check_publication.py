"""Scan Git-tracked files without displaying secret values. Run before publishing."""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "API token": re.compile(r"\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b"),
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "machine-specific path": re.compile(r"(?i)\b[A-Z]:[\\/](?:Users|项目)[\\/]"),
    "personal email": re.compile(r"[A-Za-z0-9._%+-]+@(?:qq|163|126|gmail)\.com", re.I),
    "student/identity number": re.compile(r"(?<![\w])\d{12,20}(?![\w])"),
}
FORBIDDEN_SUFFIXES = {".zip", ".7z", ".rar", ".docx", ".pdf", ".xlsx", ".csv", ".class", ".jar", ".pem", ".key"}


def main():
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode("utf-8").split("\0")
    failures = []
    count = 0
    for name in filter(None, paths):
        path = ROOT / name
        count += 1
        if name.startswith("docs/screenshots/") and path.suffix.lower() == ".png":
            if not path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
                failures.append(f"{name}: invalid PNG screenshot")
            continue
        if (path.suffix.lower() in FORBIDDEN_SUFFIXES
                or any(part in {"node_modules", "target", "dist", ".idea", ".vscode"} for part in path.parts)
                or path.name.startswith((".env", "application-local"))):
            failures.append(f"{name}: private/generated file")
            continue
        try:
            content = path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            failures.append(f"{name}: unexpected binary file")
            continue
        for number, line in enumerate(content.splitlines(), 1):
            for label, pattern in PATTERNS.items():
                if pattern.search(line):
                    failures.append(f"{name}:{number}: {label}")
    if failures:
        print("Publication check failed (values redacted):\n" + "\n".join(failures))
        return 1
    print(f"Publication check passed: {count} tracked files scanned. Manual review is still required.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
