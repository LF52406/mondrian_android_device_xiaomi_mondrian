#!/usr/bin/env python3
"""Optional, hash-pinned AArch64 execution of ApplyPanelRestriction.

Requires unicorn, pyelftools and c++filt. This calls the actual vendor method;
its imported public rect helpers are small host equivalents. It does not run
resource allocation, libscalar, QSEED, Android or the panel. Offsets belong only
to the verified blob, so a different hash is rejected before loading it.
"""
import argparse, hashlib, struct, math, subprocess
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_CODE
from unicorn.arm64_const import *
from elftools.elf.elffile import ELFFile
from pathlib import Path
class Planner:
 def __init__(self, library):
  self.u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u=self.u
  u.mem_map(0,0x200000);u.mem_map(0x20000000,0x200000)
  with open(library,'rb') as f:
   e=ELFFile(f)
   for seg in e.iter_segments():
    if seg['p_type']=='PT_LOAD':u.mem_write(seg['p_vaddr'],seg.data())
   syms=list(e.get_section_by_name('.dynsym').iter_symbols())
   names=subprocess.run(['c++filt'],input='\n'.join(s.name for s in syms),text=True,capture_output=True,check=True).stdout.splitlines()
   self.sym={n:s['st_value'] for n,s in zip(names,syms)}
   self.plt={}
   for j,r in enumerate(e.get_section_by_name('.rela.plt').iter_relocations()):
    self.plt[e.get_section_by_name('.plt')['sh_addr']+32+j*16]=names[r['r_info_sym']]
  u.mem_write(0x134020,struct.pack('<Q',0x20100000))
  self.obj=0x20000000;self.lr=0x20001000;self.rr=self.lr+16
  self.write(self.obj,'Q',self.sym['vtable for sdm::PartialUpdateSrcSplit']+16)
  u.reg_write(UC_ARM64_REG_TPIDR_EL0,0x20110000)
  u.hook_add(UC_HOOK_CODE,self.hook)
 def write(self,p,fmt,*v):self.u.mem_write(p,struct.pack('<'+fmt,*v))
 def rect(self,p):return struct.unpack('<4f',self.u.mem_read(p,16))
 def ret_rect(self,r):
  for reg,x in zip([UC_ARM64_REG_S0,UC_ARM64_REG_S1,UC_ARM64_REG_S2,UC_ARM64_REG_S3],r):self.u.reg_write(reg,struct.unpack('<I',struct.pack('<f',x))[0])
 def hook(self,u,pc,size,data):
  n=self.plt.get(pc)
  if n is None:return
  if self.sym.get(n):u.reg_write(UC_ARM64_REG_PC,self.sym[n]);return
  a=[u.reg_read(r) for r in [UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2,UC_ARM64_REG_X3,UC_ARM64_REG_X4]]
  valid=lambda r:r[2]>r[0] and r[3]>r[1]
  if n.startswith('sdm::IsValid('):u.reg_write(UC_ARM64_REG_X0,int(valid(self.rect(a[0]))))
  elif n.startswith('sdm::Union(') or n.startswith('sdm::Intersection('):
   r,s=self.rect(a[0]),self.rect(a[1])
   if 'Union' in n:
    if not valid(r):t=s
    elif not valid(s):t=r
    else:t=(min(r[0],s[0]),min(r[1],s[1]),max(r[2],s[2]),max(r[3],s[3]))
   else:
    t=(max(r[0],s[0]),max(r[1],s[1]),min(r[2],s[2]),min(r[3],s[3]))
    if not valid(r) or not valid(s) or not valid(t):t=(0,0,0,0)
   self.ret_rect(t)
  elif n.startswith('sdm::MapRect('):
   s,d,r=[self.rect(p) for p in a[:3]]
   if valid(s) and valid(d) and valid(r):
    t=tuple(math.floor(d[j%2]+(d[2+j%2]-d[j%2])/(s[2+j%2]-s[j%2])*(r[j]-int(s[j%2]))) for j in range(4))
    self.write(a[3],'4f',*t)
  elif n.startswith('sdm::AdjustSize('):
   minimum,lo,hi=a[:3];start=struct.unpack('<i',u.mem_read(a[3],4))[0];end=struct.unpack('<i',u.mem_read(a[4],4))[0]
   correction=minimum-(end-start);start-=correction>>1;end+=(correction>>1)+(correction&1)
   if start<lo:start=lo;end=start+minimum
   elif end>hi:end=hi;start=end-minimum
   self.write(a[3],'i',start);self.write(a[4],'i',end)
  elif n.startswith('sdm::Log('):pass
  else:raise RuntimeError('Unexpected import '+n)
  u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
 def run(self,w,h,align,left,right,split=True):
  self.write(self.obj+0x27c,'6I',align[0],align[0],align[1],align[1],align[0],align[1])
  self.write(self.obj+0x470,'6I',w,h,w//2,1,0,2)
  self.write(self.obj+0x488,'2I',1440,3200)
  self.write(self.obj+0x4ac,'B',split)
  self.write(self.lr,'4f',*left);self.write(self.rr,'4f',*right)
  u=self.u;u.reg_write(UC_ARM64_REG_SP,0x200f0000);u.reg_write(UC_ARM64_REG_LR,0x1f0000)
  u.reg_write(UC_ARM64_REG_X0,self.obj);u.reg_write(UC_ARM64_REG_X1,self.lr);u.reg_write(UC_ARM64_REG_X2,self.rr)
  u.emu_start(self.sym['sdm::PartialUpdateImpl::ApplyPanelRestriction(sdm::LayerRect*, sdm::LayerRect*)'],0x1f0000,count=50000)
  assert u.reg_read(UC_ARM64_REG_PC)==0x1f0000
  return self.rect(self.lr),self.rect(self.rr)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--library',required=True,type=Path)
 args=parser.parse_args()
 expected='7a088c44d8cdd52bdc1f6a3cbc28b6d71fafc0e310352a9464c2670aa8b41d79'
 actual=hashlib.sha256(args.library.read_bytes()).hexdigest()
 if actual!=expected:raise SystemExit('Unsupported vendor ABI/hash: '+actual)
 p=Planner(args.library)
 zero=(0,0,0,0)
 bad=p.run(1080,2400,(540,24),(40,73,80,86),zero)
 good=p.run(1080,2400,(720,32),(40,73,80,86),zero)
 assert bad[0]==(0,54,405,90) and good[0]==(0,48,540,96)
 cases=0
 for w,h in [(1080,2400),(1440,3200)]:
  for top,bottom in [(0,1),(1,2),(73,86),(h//2,h//2+1),(h-2,h-1),(0,h)]:
   for left,right in [((40,top,80,bottom),zero),
                      (zero,(w-80,top,w-40,bottom)),
                      ((w//2-20,top,w//2,bottom),(w//2,top,w//2+20,bottom))]:
    output=p.run(w,h,(720,32),left,right)
    for original,roi in zip((left,right),output):
     if original==zero:continue
     assert roi[0]<=original[0] and roi[1]<=original[1]
     assert roi[2]>=original[2] and roi[3]>=original[3]
     physical=[roi[i]*(1440/w if i%2==0 else 3200/h) for i in range(4)]
     assert all(v.is_integer() for v in physical)
     assert physical[0]%720==physical[2]%720==0
     assert physical[1]%32==physical[3]%32==0
    cases+=1
 print('Vendor ApplyPanelRestriction:',cases,'edge/half/crossing cases passed; old FHD double-scale reproduced')
 print('Library SHA256:',actual)
