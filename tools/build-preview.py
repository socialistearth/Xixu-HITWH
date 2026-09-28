#!/usr/bin/env python3
"""Create an offline, single-file UI preview from the exact Android assets."""
from pathlib import Path
import hashlib,base64
root=Path(__file__).resolve().parent.parent
assets=root/'app/src/main/assets'
html=(assets/'index.html').read_text()
hashes=[]
for name in ['core.js','startup.js','news.js','app.js']:
    source=(assets/name).read_text()
    assert '</script' not in source.lower()
    digest=base64.b64encode(hashlib.sha256(source.encode()).digest()).decode()
    hashes.append("'sha256-"+digest+"'")
    html=html.replace('<script src="'+name+'"></script>','<script>'+source+'</script>')
html=html.replace('<link rel="stylesheet" href="style.css">','<style>'+(assets/'style.css').read_text()+'</style>')
html=html.replace("script-src 'self';","script-src "+' '.join(hashes)+';')
html=html.replace('<title>汐序 · HITWH</title>','<title>汐序 · 安卓应用交互预览（示例数据）</title>')
(root/'preview.html').write_text(html)
print('Built preview.html; uses only synthetic data and no external resources.')
