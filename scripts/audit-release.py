"""Static release scan only: no database, container, index or filesystem mutation."""
from pathlib import Path
import subprocess,re,json
root=Path(__file__).resolve().parents[1]
paths=set(subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().split('\0')+subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=root).decode().split('\0'))-{''}
issues=[];count=0
for name in sorted(paths):
 p=root/name
 if not p.is_file():continue
 count+=1
 if name.startswith(('target/','backups/','docs/','db/verification/')):issues.append({'file':name,'kind':'excluded-path'})
 try:text=p.read_bytes().decode('utf-8-sig')
 except UnicodeDecodeError:issues.append({'file':name,'kind':'non-utf8'});continue
 if p.suffix=='.sh' and (b'\r' in p.read_bytes() or p.read_bytes().startswith(b'\xef\xbb\xbf')):issues.append({'file':name,'kind':'shell-not-utf8-lf'})
 for line_no,line in enumerate(text.splitlines(),1):
  if re.search(r'AKIA[0-9A-Z]{16}|LTAI[A-Za-z0-9]{16,}|sk-[A-Za-z0-9_-]{24,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',line):issues.append({'file':name,'line':line_no,'kind':'secret-candidate'})
  if line.rstrip(' \t')!=line:issues.append({'file':name,'line':line_no,'kind':'trailing-whitespace'})
print(json.dumps({'files':count,'issues':issues},indent=2))
raise SystemExit(1 if issues else 0)
