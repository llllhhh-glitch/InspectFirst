#!/usr/bin/env python3
"""Write a source-only ZIP outside the project tree."""
from pathlib import Path
import argparse,zipfile,os
root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('output',type=Path)
a=parser.parse_args();out=a.output.resolve()
if out==root or root in out.parents:
    raise SystemExit('Choose an output path outside the project folder.')
allowed={'backend','frontend','database','deploy'}
rootfiles={'README.md','.gitignore','.dockerignore','.env.example','compose.yaml'}
excluded={'.local','.git','node_modules','target','dist','recovery-backups','__pycache__','.idea'}
files=[]
for directory,dirs,names in os.walk(root):
    dirs[:]=[d for d in dirs if d not in excluded and not (Path(directory)/d).is_symlink()]
    for name in names:
        f=Path(directory)/name;rel=f.relative_to(root)
        if f.is_symlink() or not f.is_file():continue
        if rel.parts[0] not in allowed and str(rel) not in rootfiles:continue
        if f.name=='.DS_Store' or f.name.startswith('.env') and f.name!='.env.example':continue
        if f.suffix.lower() in {'.docx','.pdf','.log','.pyc','.zip','.iml'}:continue
        files.append((f,rel))
out.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for f,rel in sorted(files):z.write(f,Path('InspectFirst')/rel)
print(f'Created {out.name}: {len(files)} files, {out.stat().st_size:,} bytes.')
