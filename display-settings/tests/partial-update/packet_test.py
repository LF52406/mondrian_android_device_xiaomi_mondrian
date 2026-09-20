#!/usr/bin/env python3
"""Exercise stock DSC packetization and DSI stream programming, with register fixtures."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser=argparse.ArgumentParser()
parser.add_argument('--modules',required=True,type=Path)
args=parser.parse_args()
root=args.modules/'qcom/opensource/display-drivers/msm'

def function(path,name):
    source=(root/path).read_text()
    start=source.index(name+'(')
    start=source.rfind('\n',0,start)+1
    end=source.index('{',start)+1
    depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}')
        end+=1
    return source[start:end]

fixture=r'''
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cerrno>
#include <cstddef>
using u16=uint16_t; using u32=uint32_t;
#define DIV_ROUND_UP(a,b) (((a)+(b)-1)/(b))
#define BIT(x) (1u<<(x))
#define DSC_BPP(c) ((c).bits_per_pixel>>4)
#define SDE_ERROR(...) ((void)0)
#define DSI_CTRL_HW_DBG(...) ((void)0)
#define SDE_EVT32(...) ((void)0)
#define container_of(p,t,m) reinterpret_cast<t*>(reinterpret_cast<char*>(p)-offsetof(t,m))
struct drm_dsc_config { int slice_width=720,slice_height=32,bits_per_component=10,bits_per_pixel=128; };
struct msm_display_dsc_info {
 drm_dsc_config config; int slice_per_pkt=2,slice_last_group_size=0,det_thresh_flatness=0,
 eol_byte_num=0,pclk_per_line=0,bytes_in_slice=0,bytes_per_pkt=0,pkt_per_line=0,
 dsc_4hsmerge_padding=0,dsc_4hsmerge_alignment=0;
};
struct msm_display_vdc_info { int slice_width=0,bytes_per_pkt=0,pkt_per_line=0,eol_byte_num=0,bytes_in_slice=0; };
struct dsi_mode_info { int h_active=1440,v_active=3200; msm_display_dsc_info *dsc=nullptr; msm_display_vdc_info *vdc=nullptr; };
struct dsi_host_common_cfg { int dst_format=0; };
struct dsi_rect { u32 x,y,w,h; };
enum {
 DSI_COMMAND_MODE_MDP_CTRL2, DSI_COMMAND_COMPRESSION_MODE_CTRL,
 DSI_COMMAND_COMPRESSION_MODE_CTRL2, DSI_HS_TIMER_CTRL,
 DSI_COMMAND_MODE_MDP_STREAM0_CTRL, DSI_COMMAND_MODE_MDP_STREAM1_CTRL,
 DSI_COMMAND_MODE_MDP_STREAM0_TOTAL, DSI_COMMAND_MODE_MDP_STREAM1_TOTAL,
 DSI_COMMAND_MODE_NULL_INSERTION_CTRL
};
struct dsi_ctrl_hw { bool widebus_support=false,null_insertion_enabled=false; u32 regs[9]={}; };
struct dsi_ctrl { dsi_ctrl_hw hw; bool max_hs_timer_supported=true; };
#define DSI_R32(c,r) ((c)->regs[r])
#define DSI_W32(c,r,v) ((c)->regs[r]=(v))
int dsi_pixel_format_to_bpp(int) {return 24;}
bool dsi_dsc_compression_enabled(dsi_mode_info *m) {return m->dsc;}
bool dsi_vdc_compression_enabled(dsi_mode_info *m) {return m->vdc;}
bool dsi_compression_enabled(dsi_mode_info *m) {return m->dsc || m->vdc;}
void sde_vdc_intf_prog_params(msm_display_vdc_info*,int) {assert(false);}
'''

test=r'''
int main() {
 msm_display_dsc_info dsc; dsi_mode_info mode; mode.dsc=&dsc;
 dsi_host_common_cfg cfg;
 for (bool wide : {false,true}) for (u32 width : {720u,1440u})
  for (u32 height : {32u,64u,960u,3200u}) {
   dsi_ctrl ctrl; ctrl.hw.widebus_support=wide;
   dsi_rect roi={width==720 ? 720u : 0u,0,width,height};
   assert(!sde_dsc_populate_dsc_private_params(&dsc,width));
   assert(dsc.slice_per_pkt==2 && dsc.pkt_per_line==1 && dsc.bytes_per_pkt==int(width));
   assert(dsc.bytes_in_slice==720 && dsc.pclk_per_line==int(width/3));
   dsi_ctrl_hw_cmn_setup_cmd_stream(&ctrl.hw,&mode,&cfg,0,&roi);
   u32 total=ctrl.hw.regs[DSI_COMMAND_MODE_MDP_STREAM0_TOTAL];
   u32 stream=ctrl.hw.regs[DSI_COMMAND_MODE_MDP_STREAM0_CTRL];
   assert((total>>16)==height && (total&65535)==width/(wide ? 6 : 3));
   assert((stream>>16)==width+1);
   assert(((ctrl.hw.regs[DSI_COMMAND_COMPRESSION_MODE_CTRL]>>6)&3)==0);
   assert(ctrl.hw.regs[DSI_COMMAND_COMPRESSION_MODE_CTRL2]==720);
   assert(dsc.slice_per_pkt==2);
  }
 puts("Stock DSC/DSI: half/full width, 32..3200 lines, both bus widths and unchanged slice_per_pkt=2 passed");
}
'''
source='#include <initializer_list>\n'+fixture+function('sde_dsc_helper.c','sde_dsc_populate_dsc_private_params')+function('dsi/dsi_ctrl_hw_cmn.c','dsi_ctrl_hw_cmn_setup_cmd_stream')+test
with tempfile.TemporaryDirectory(prefix='m11a-packets-') as name:
    src,binary=Path(name)/'test.cpp',Path(name)/'test'
    src.write_text(source)
    subprocess.run(['c++','-std=c++17','-Wall','-Wextra','-Werror','-Wno-sign-compare','-fsanitize=undefined',str(src),'-o',str(binary)],check=True)
    subprocess.run([str(binary)],check=True)
