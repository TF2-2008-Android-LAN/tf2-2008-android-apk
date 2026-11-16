from pathlib import Path
from zipfile import ZipFile
import re,json,hashlib,struct
root=Path(__file__).resolve().parent
meta=json.loads((root/"TF2_2008_HUD_Toggle_Voices_Bots_v2_manifest.json").read_text(encoding="utf-8"))
zipf=root/"TF2_2008_HUD_Toggle_Voices_Bots_v2_QA.zip"
files={}
with ZipFile(zipf) as z:
 for row in meta["files"]:
  p=row["path"];raw=z.read(p);assert len(raw)==row["bytes"]
  assert hashlib.sha256(raw).hexdigest()==row["sha256"],p
  assert p not in files;files[p]=raw
 assert len(z.namelist())==len(files)==meta["total"]==108
cfg=lambda p:files["tf_port/cfg/"+p+".cfg"].decode("utf-8")
touch=files["tf_port/cfg/touch.cfg"].decode("utf-8")
buttonlist={}
for line in touch.splitlines():
 if not line.startswith("touch_addbutton "):continue
 match=re.match(r'touch_addbutton\s+"([^"]+)"\s+"([^"]*)"\s+"([^"]*)"\s+([\d\.-]+)\s+([\d\.-]+)\s+([\d\.-]+)\s+([\d\.-]+)\s+',line)
 assert match,line
 name,texture,command=match.group(1,2,3)
 assert name not in buttonlist,name
 buttonlist[name]=dict(tex=texture,cmd=command,rect=tuple(float(x) for x in match.group(4,5,6,7)))
assert len(buttonlist)==122,len(buttonlist)
for prefix,count in (("voice_tab_",3),("voice_",24),("bot_",10)):
 if prefix=="voice_":
  names=[x for x in buttonlist if re.fullmatch(r'voice_[zxc]_[0-7]',x)]
 elif prefix=="bot_":
  names=[x for x in buttonlist if x.startswith("bot_")]
 else:names=[x for x in buttonlist if x.startswith(prefix)]
 assert len(names)==count,(prefix,len(names),names)
assert buttonlist["duck"]["cmd"]=="tf2008_duck_toggle"
assert touch.count('alias tf2008_duck_on')==1
assert touch.count('alias tf2008_duck_off')==1
assert 'alias tf2008_duck_toggle "tf2008_duck_on"' in touch
assert buttonlist["aux_bots"]["cmd"]=="exec tf2008_bots_panel_open"
assert buttonlist["aux_voice"]["cmd"]=="exec tf2008_voice_panel_open"
# Every VTF uses 256x256, mipmaps and the matching VMT.
count=0
for p,b in files.items():
 if p.endswith(".vtf"):
  count+=1
  assert b[:4]==b"VTF\x00",p
  assert struct.unpack_from("<HH",b,16)==(256,256),p
  assert struct.unpack_from("<I",b,52)[0]==12,p
  assert b[56]>=9,p
  vmt=files.get(p[:-4]+".vmt")
  assert vmt and ('$basetexture' in vmt.decode()),p
assert count==39,count
# All 'exec tf2008_*' references used by the menus must exist.
ref=set()
for p,b in files.items():
 if p.endswith(".cfg"):
  ref.update(re.findall(r'\bexec\s+(tf2008_[a-z0-9_]+)',b.decode("utf-8"),re.I))
missing=sorted(x for x in ref if "tf_port/cfg/"+x+".cfg" not in files
               and x not in ("tf2008_touch_reset_class",))
# The six class-specific popup cfg files are shipped by the baseline overlay.
baseline=root.parents[1]/"android-app/assets/overlay.zip"
with ZipFile(baseline) as stock:
 stockpaths=set(stock.namelist())
 missing=[name for name in missing if
          "common/tf_port/cfg/"+name+".cfg" not in stockpaths]
assert not missing,missing
for n in range(17):
 text=cfg(f"tf2008_botquota_{n}")
 assert f"tf_bot_quota {n}" in text
 assert f'alias tf2008_bots_plus "exec tf2008_botquota_{min(n+1,16)}"' in text
 assert f'alias tf2008_bots_minus "exec tf2008_botquota_{max(n-1,0)}"' in text
 assert "exec tf2008_bot_difficulty_base" in text
assert all(line.strip().startswith("//") or line.strip()=="" for line in cfg("tf2008_bot_difficulty_base").splitlines())
for c,m in (("z",0),("x",1),("c",2)):
 for i in range(8):
  assert buttonlist[f"voice_{c}_{i}"]["cmd"]==f"voicemenu {m} {i}; exec tf2008_voice_panel_close"
# Stable safe movement, combat, and viewport button geometry.
for name in ("move","look","duck","attack","attack2","jump","reload","slot1","slot2","slot3","menu","score","changeclass"):
 assert name in buttonlist,name
print("STATIC_QA_PASS")
print("ZIP_SHA256",hashlib.sha256(zipf.read_bytes()).hexdigest())
print("FILES",len(files),"TOTAL_BUTTONS",len(buttonlist),"CUSTOM_VTF",count,
      "VOICE_COMMANDS",24,"BOT_QUOTA_LEVELS",17)
print("EXECS_RESOLVED",len(ref),"UNRESOLVED",len(missing))
print("DUCK_BUTTON",buttonlist["duck"])
print("BOT_LAUNCH_BUTTON",buttonlist["aux_bots"])
