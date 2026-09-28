#!/usr/bin/env python3
"""Package distributable sources; never confuse core.js with a numeric core dump."""
from pathlib import Path
import re
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 2:
    raise SystemExit('Usage: python3 tools/package-source.py /path/to/source.zip')
output = Path(sys.argv[1]).resolve()
excluded_dirs = {'.git', '.gradle', '.idea', 'build', 'node_modules', '__pycache__',
                 '.venv', 'venv', 'private', 'signing'}
excluded_suffixes = {'.apk', '.aab', '.idsig', '.p12', '.jks', '.keystore', '.pem',
                     '.key', '.sqlite', '.sqlite3', '.db', '.pyc', '.log'}
for required in ('app/src/main/assets/core.js', 'app/src/main/assets/index.html',
                 'gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'README.md'):
    if not (root / required).is_file():
        raise SystemExit('Missing required source: ' + required)
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root)
        if (not path.is_file() or path.is_symlink() or path.resolve() == output
                or any(part in excluded_dirs for part in rel.parts)
                or path.suffix in excluded_suffixes
                or path.name in {'local.properties', 'password.txt', '.DS_Store'}
                or path.name.startswith(('.env', 'hs_err_'))
                or re.fullmatch(r'core(?:\.\d+)?', path.name)):
            continue
        archive.write(path, 'Xixu-HITWH/' + rel.as_posix())
with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise SystemExit('Source archive failed integrity check')
    if 'Xixu-HITWH/app/src/main/assets/core.js' not in archive.namelist():
        raise SystemExit('Source archive is incomplete')
print(output)
