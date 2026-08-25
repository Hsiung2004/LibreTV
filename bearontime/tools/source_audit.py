from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
PKG=ROOT/'app/src/main/java/com/bearfamily/app/bearontime'
errors=[]
forbidden=[r'com\.zdworks',r'zdclock',r'熊愛雅婷',r'Bear House Voice Time',r'com\.bearhouse']
for p in ROOT.rglob('*'):
    if p.is_file() and p.suffix.lower() in {'.java','.xml','.kts','.properties','.md','.txt','.json'}:
        text=p.read_text(encoding='utf-8',errors='ignore')
        for pat in forbidden:
            if re.search(pat,text,re.I): errors.append(f'legacy text: {p.relative_to(ROOT)} -> {pat}')
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
for name in re.findall(r'android:name="\.([A-Za-z0-9_]+)"',manifest):
    if name.endswith(('Activity','Service','Receiver','App')) and not (PKG/f'{name}.java').is_file():
        errors.append(f'manifest class missing: {name}')
for p in PKG.glob('*.java'):
    text=p.read_text(encoding='utf-8')
    if 'package com.bearfamily.app.bearontime;' not in text: errors.append(f'package mismatch: {p.name}')
    if 'findViewById(' in text: errors.append(f'unsafe old-view lookup found: {p.name}')
    if '.isBlank()' in text: errors.append(f'Java11-only String.isBlank found: {p.name}')
print('BearOnTime GitHub CI source audit')
print('Java files:',len(list(PKG.glob('*.java'))))
if errors:
    print('FAIL')
    [print('-',e) for e in errors]
    sys.exit(1)
print('PASS')
