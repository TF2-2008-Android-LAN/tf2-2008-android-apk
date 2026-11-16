from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import hashlib,struct,json,io

root=Path(__file__).resolve().parent
source=root/"missing_touch_icons_original"
names=["menu","shoot","shoot_alt","reload","jump","crouch"]
files={}
def mip_down(w,h,pix):
 nw=max(1,w//2);nh=max(1,h//2)
 out=bytearray(nw*nh*4)
 for y in range(nh):
  for x in range(nw):
   samples=[]
   for dy in (0,1):
    for dx in (0,1):
     src=((min(h-1,2*y+dy)*w)+min(w-1,2*x+dx))*4
     samples.append(pix[src:src+4])
   # Alpha-preserving 2x2 downsample in the same straight-alpha BGRA format
   dest=(y*nw+x)*4
   total_alpha=sum(p[3] for p in samples)
   for k in (0,1,2):
    out[dest+k]=(sum(p[k]*p[3] for p in samples)+total_alpha//2)//total_alpha if total_alpha else 255
   out[dest+3]=(total_alpha+2)//4
 return nw,nh,bytes(out)
def make_vtf(bgra):
 w=h=256
 assert len(bgra)==w*h*4
 versions=[]
 while True:
  versions.append((w,h,bgra))
  if w==h==1:break
  w,h,bgra=mip_down(w,h,bgra)
 header=bytearray(80)
 header[:4]=b"VTF\x00"
 struct.pack_into("<II",header,4,7,2)
 struct.pack_into("<I",header,12,80)
 struct.pack_into("<HH",header,16,256,256)
 struct.pack_into("<I",header,20,0x2100) # no lod and clamp
 struct.pack_into("<H",header,24,1)
 struct.pack_into("<3f",header,32,1.0,1.0,1.0)
 struct.pack_into("<f",header,48,1.0)
 struct.pack_into("<I",header,52,12) # BGRA8888
 header[56]=len(versions)
 struct.pack_into("<I",header,57,13) # DXT1 lowres
 header[61]=16;header[62]=8
 struct.pack_into("<H",header,63,1)
 # Low-res thumbnail: off-white constant color (DXT1), 8 blocks total.
 thumb=(struct.pack("<HHI",0xe73c,0xe73c,0))*8
 compressed=b"".join(pix for w,h,pix in reversed(versions))
 blob=bytes(header)+thumb+compressed
 assert blob[:4]==b"VTF\x00"
 assert struct.unpack_from("<HH",blob,16)==(256,256)
 assert struct.unpack_from("<I",blob,52)[0]==12
 assert blob[-256*256*4:]==versions[0][2]
 return blob
for name in names:
 src=source/(name+".bgra")
 raw=src.read_bytes()
 vt=make_vtf(raw)
 vmt=('"UnlitGeneric"\n{\n "$basetexture" "vgui/touch/'+name+
      '"\n "$translucent" "1"\n "$ignorez" "1"\n "$vertexcolor" "1"\n "$vertexalpha" "1"\n}\n').encode("ascii")
 folder="tf_port/materials/vgui/touch/"+name
 files[folder+".vtf"]=vt
 files[folder+".vmt"]=vmt
 print("ICON",name,"VTF",len(vt),"PNG", (source/(name+".png")).stat().st_size,
       "VTF_SHA256",hashlib.sha256(vt).hexdigest())
arch=root/"AndroidTouchSixIcons_v1_QA.zip"
with ZipFile(arch,"w",ZIP_DEFLATED,compresslevel=9) as z:
 for key,data in sorted(files.items()):z.writestr(key,data)
with ZipFile(arch) as z:
 for key,data in files.items():assert z.read(key)==data
out={
 "title":"Six original compatible Android touch icons",
 "count":len(files),
 "sha256":hashlib.sha256(arch.read_bytes()).hexdigest(),
 "size":arch.stat().st_size,
 "files":[{"path":key,"sha256":hashlib.sha256(data).hexdigest(),"bytes":len(data)}
          for key,data in sorted(files.items())]
}
(root/"AndroidTouchSixIcons_v1_QA_manifest.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
print("SUCCESS",arch,"BYTES",arch.stat().st_size,"SHA256",out["sha256"])
