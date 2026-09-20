#!/usr/bin/env python3
"""Compile actual ROI helpers with small type fixtures, not a kernel/Android build."""
import argparse
import re
import subprocess
import tempfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True, type=Path)
parser.add_argument('--modules', required=True, type=Path)
args = parser.parse_args()
core = args.display.resolve() / 'sdm/libs/core'
kernel = args.modules.resolve() / 'qcom/opensource/display-drivers/msm'

required = (
    core / 'mondrian_pu.h',
    core / 'mondrian_pu_geometry.h',
    core / 'drm/hw_device_drm.cpp',
    kernel / 'sde/sde_crtc.c',
)
missing = [str(path) for path in required if not path.is_file()]
if missing:
    raise SystemExit(
        'Partial Update production sources are missing. Apply '
        'device/xiaomi/mondrian/display-settings/apply-partial-update-display-patch.sh '
        'and the required kernel commits first:\n  ' + '\n  '.join(missing)
    )

def function(source, name):
    start = source.index(name + '(')
    start = source.rfind('\n', 0, start) + 1
    first = source.index('{', start)
    depth = 1
    end = first + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

fixtures = r'''
#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <map>
#include <vector>
#include <algorithm>
#include <bitset>
#define UINT32(x) static_cast<uint32_t>(x)
namespace sdm {
struct HWPanelInfo {
 bool is_primary_panel=true, partial_update=true;
 int mode=1, left_align=720, width_align=720, min_roi_width=720;
 int top_align=32, height_align=32, min_roi_height=32;
 char panel_name[256]="xiaomi 42 02 0a cmd mode dsc dsi panel";
};
constexpr int kModeCommand=1;
struct HWMixerAttributes { uint32_t width=1080, height=2400, split_left=540, dest_scaler_blocks_used=2; };
struct HWDisplayAttributes { uint32_t x_pixels=1440, y_pixels=3200; bool is_device_split=true; };
struct LayerRect { float left, top, right, bottom;
 LayerRect(float l=0,float t=0,float r=0,float b=0):left(l),top(t),right(r),bottom(b){} };
struct DRMRect { uint32_t left=0, top=0, right=0, bottom=0; };
struct ScaleData { struct { bool scale=true, detail_enhance=false; } enable;
 uint32_t dst_width=1440, dst_height=288; };
struct HWDestScaleInfo { ScaleData scale_data; LayerRect panel_roi={0,96,1440,384};
 uint32_t mixer_width=540,mixer_height=2400; bool scale_update=false; };
constexpr int kUpdateResources=0;
struct HWLayersInfo { std::vector<LayerRect> left_frame_roi, right_frame_roi;
 std::bitset<8> updates_mask;
 std::map<uint32_t,HWDestScaleInfo*> dest_scale_info_map; };
struct Debug { static int value;
 static int GetProperty(const char*, int* v) { *v=value; return 0; } };
int Debug::value=2;
}
'''
hwc_source = (core / 'drm/hw_device_drm.cpp').read_text()
hwc_helpers = '\n'.join(function(hwc_source, n) for n in ('GetConnectorROI', 'ValidateM11aROI'))
hwc_test = r'''
int main() {
 using namespace sdm;
 using namespace sdm::mondrian_pu;
 int step=0, minimum=0;
 assert(SourceAlignment(720,1080,1440,&step) && step==540);
 assert(SourceAlignment(32,2400,3200,&step) && step==24);
 assert(SourceMinimum(32,2400,3200,24,&minimum) && minimum==24);
 assert(!SourceAlignment(0,1080,1440,&step));
 assert(!SourceAlignment(32,0,3200,&step));
 assert(!SourceAlignment(32,65536,3200,&step));
 uint64_t cases=0;
 for (uint32_t source : {720u,1080u,1152u,1440u,1600u,1920u,2400u,2560u,3200u}) {
  const uint32_t panel=source<=1440 ? 1440 : 3200;
  const uint32_t alignment=panel==1440 ? 720 : 32;
  assert(SourceAlignment(alignment,source,panel,&step));
  for (uint32_t a=0; a<source; ++a) {
   for (uint32_t b=a+1; b<=source; ++b) {
    uint32_t left=0,right=0;
    const bool expected=(uint64_t(a)*panel)%source==0 &&
       (uint64_t(b)*panel)%source==0 &&
       ((uint64_t(a)*panel)/source)%alignment==0 &&
       ((uint64_t(b-a)*panel)/source)%alignment==0;
    const bool ok=ProjectAxis(a,b,source,panel,alignment,alignment,alignment,&left,&right);
    assert(ok==expected);
    if (ok) { assert(uint64_t(left)*source==uint64_t(a)*panel);
      assert(uint64_t(right)*source==uint64_t(b)*panel); }
    ++cases;
   }
  }
 }
 HWPanelInfo panel, mapped;
 HWMixerAttributes mixer;
 HWDisplayAttributes display;
 assert(PlannerPanelInfo(panel,mixer,display,&mapped));
 assert(mapped.left_align==720 && mapped.width_align==720 &&
        mapped.top_align==32 && mapped.height_align==32);
 HWMixerAttributes native_mixer{1440,3200};
 assert(PlannerPanelInfo(panel,native_mixer,display,&mapped));
 assert(mapped.left_align==720 && mapped.width_align==720 &&
        mapped.top_align==32 && mapped.height_align==32);
 Debug::value=0;
 assert(PlannerPanelInfo(panel,mixer,display,&mapped) && mapped.partial_update);
 HWDisplayAttributes invalid_display{1080,2400};
 assert(!PlannerPanelInfo(panel,mixer,invalid_display,&mapped) && !mapped.partial_update);
 HWPanelInfo other=panel; std::strcpy(other.panel_name,"other panel");
 assert(!PlannerPanelInfo(other,mixer,display,&mapped) && mapped.partial_update);
 Debug::value=1; assert(Mode(panel)==2);
 Debug::value=99; assert(Mode(panel)==2); assert(Mode(other)==-1);
 Debug::value=0; assert(Mode(panel)==0);
 HWLayersInfo layers;
 layers.left_frame_roi.push_back({0,72,1080,288});
 HWDestScaleInfo left,right;
 layers.dest_scale_info_map[0]=&left;
 assert(ValidateM11aROI(layers,mixer,display,panel));
 left.scale_data.dst_height=3200;
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 left.scale_data.dst_height=288;
 left.panel_roi.bottom=416;
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 left.panel_roi.bottom=384;
 layers.left_frame_roi[0].top=73;
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 layers.left_frame_roi[0].top=std::numeric_limits<float>::quiet_NaN();
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 layers.left_frame_roi[0]={540,72,1080,288};
 layers.dest_scale_info_map.clear(); layers.dest_scale_info_map[1]=&right;
 right.panel_roi={720,96,1440,384}; right.scale_data.dst_width=720;
 assert(ValidateM11aROI(layers,mixer,display,panel));
 right.panel_roi.left=0; assert(!ValidateM11aROI(layers,mixer,display,panel));
 right.panel_roi.left=720;
 layers.left_frame_roi[0]={0,72,1080,288};
 left.panel_roi={0,96,720,384}; left.scale_data.dst_width=720;
 layers.dest_scale_info_map[0]=&left;
 assert(ValidateM11aROI(layers,mixer,display,panel));
 layers.dest_scale_info_map[0]=nullptr;
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 assert(ValidateM11aROI(layers,mixer,display,other));
 layers.dest_scale_info_map[0]=&left;
 layers.left_frame_roi[0]={0,0,1080,2400};
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 left.scale_data.dst_height=right.scale_data.dst_height=3200;
 left.panel_roi=right.panel_roi={}; // Full resource plans can omit panel_roi.
 assert(ValidateM11aROI(layers,mixer,display,panel));
 left.scale_data.enable.detail_enhance=true; // DE remains valid for full frames.
 assert(ValidateM11aROI(layers,mixer,display,panel));
 layers.dest_scale_info_map.erase(0);
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 layers.dest_scale_info_map.clear();
 assert(!ValidateM11aROI(layers,mixer,display,panel));
 layers.left_frame_roi[0]={0,0,1440,3200};
 assert(ValidateM11aROI(layers,native_mixer,display,panel));
 panel.partial_update=false; panel.width_align=panel.height_align=0;
 assert(ValidateM11aROI(layers,native_mixer,display,panel));
 layers.left_frame_roi[0]={0,0,720,32};
 assert(!ValidateM11aROI(layers,native_mixer,display,panel));
 std::printf("HWC: %llu projection cases and scaler-plan regressions passed\n",
             (unsigned long long)cases);
}
'''
kernel_source = (kernel / 'sde/sde_crtc.c').read_text()
kernel_helpers = '\n'.join(function(kernel_source, n) for n in (
 '_sde_crtc_scaled_axis_valid','_sde_crtc_validate_m11a_scaled_roi'))
overfetch = re.search(r'#define SDE_DS_OVERFETCH_SIZE (\d+)',
                     (kernel / 'sde/sde_hw_ds.h').read_text()).group(1)
kernel_fixture = r'''
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <algorithm>
using u32=uint32_t;
using u64=uint64_t;
#define BIT(x) (1u<<(x))
#define max_t(type,a,b) std::max<type>(a,b)
#define min_t(type,a,b) std::min<type>(a,b)
#define SDE_MAX_DS_COUNT 2
#define SDE_DRM_DESTSCALER_ENABLE 1
#define SDE_DRM_DESTSCALER_PU_ENABLE 8
struct drm_clip_rect { uint16_t x1,y1,x2,y2; };
struct msm_roi_list { uint32_t num_rects; drm_clip_rect roi[4]; };
struct msm_roi_alignment { uint32_t xstart_pix_align=720, width_pix_align=720,
 ystart_pix_align=32,height_pix_align=32,min_width=720,min_height=32; };
struct msm_roi_caps { bool scaled_pu=true; msm_roi_alignment align; };
struct sde_rect { uint16_t x,y,w,h; };
struct sde_hw_ds_cfg { uint32_t idx=0, flags=9, lm_width=540,lm_height=2400;
 struct { bool enable=true; struct {bool enable=false;} de;
 uint32_t dst_width=720,dst_height=288,src_width[1]={540},src_height[1]={216}; } scl3_cfg;
};
struct sde_crtc_state {
 uint32_t num_ds_enabled=2,num_ds=2,num_connectors=1;
 msm_roi_list user_roi_list={1,{{0,72,1080,288}}};
 sde_rect lm_bounds[2]={{0,0,540,2400},{540,0,540,2400}};
 struct { struct {uint32_t hdisplay=1440,vdisplay=3200;} adjusted_mode;} base;
 sde_hw_ds_cfg ds_cfg[2];
};
struct sde_crtc {uint32_t num_mixers=2;};
'''
kernel_test=r'''
int main() {
 sde_crtc crtc; sde_crtc_state state; msm_roi_caps caps;
 state.ds_cfg[1].idx=1;
 msm_roi_list out={1,{{0,96,1440,384}}};
 auto valid=[&]{return _sde_crtc_validate_m11a_scaled_roi(&crtc,&state,&caps,&out);};
 assert(valid());
 caps.scaled_pu=false; assert(!valid()); caps.scaled_pu=true;
 state.ds_cfg[0].scl3_cfg.dst_height=3200; assert(!valid());
 state.ds_cfg[0].scl3_cfg.dst_height=288;
 out.roi[0].y2=416; assert(!valid()); out.roi[0].y2=384;
 state.ds_cfg[1].idx=0; assert(!valid()); state.ds_cfg[1].idx=1;
 state.ds_cfg[0].flags=1; assert(!valid()); state.ds_cfg[0].flags=9;
 state.ds_cfg[0].scl3_cfg.src_height[0]=2400; assert(!valid());
 state.ds_cfg[0].scl3_cfg.src_height[0]=216;
 state.ds_cfg[0].scl3_cfg.de.enable=true; assert(!valid());
 state.ds_cfg[0].scl3_cfg.de.enable=false;
 state.ds_cfg[0].scl3_cfg.src_width[0]=540+SDE_DS_OVERFETCH_SIZE; assert(valid());
 state.ds_cfg[0].scl3_cfg.src_width[0]++; assert(!valid());
 state.ds_cfg[0].scl3_cfg.src_width[0]=540;
 state.num_ds=state.num_ds_enabled=1; state.ds_cfg[0].idx=1;
 state.user_roi_list.roi[0].x1=540; out.roi[0].x1=720;
 assert(valid());
 state.ds_cfg[0].idx=0; assert(!valid());
 assert(!_sde_crtc_scaled_axis_valid(0,24,0,32,0,3200,32,32,32));
 assert(!_sde_crtc_scaled_axis_valid(0,0,0,32,2400,3200,32,32,32));
 assert(_sde_crtc_scaled_axis_valid(2376,2400,3168,3200,2400,3200,32,32,32));
 std::puts("Kernel: exact mapping, stale scaler, right-only and bounds regressions passed");
}
'''
prepare_source = (core / 'display_base.cpp').read_text()
retry_start = prepare_source.index('  bool retried_full_frame = false;')
retry_end = prepare_source.index('  while (true) {', retry_start)
retry_block = prepare_source[retry_start:retry_end]
retry_fixture = r'''
#define DLOGW(...) ((void)0)
constexpr int kErrorNone=0, kErrorNeedsValidate=1, kErrorNeedsLutRegen=2;
struct RetryStack { bool needs_validate=false; };
struct RetryLayers { sdm::HWLayersInfo info; };
struct RetryManager {
 RetryStack *stack;
 int stops=0, starts=0, result=0;
 bool disabled=false;
 void PostPrepare(int, RetryLayers*) { ++stops; }
 void ControlPartialUpdate(int, bool enable) { disabled=!enable; }
 void GenerateROI(int, RetryLayers *layers) {
   layers->info.left_frame_roi={{0,0,1080,2400}};
 }
 int PrePrepare(int, RetryLayers*) {
   ++starts; assert(stack->needs_validate); return result;
 }
};
void checkRetry(bool partial, bool m11a, int prepare_error) {
 using namespace sdm;
 HWPanelInfo hw_panel_info_;
 if (!m11a) std::strcpy(hw_panel_info_.panel_name,"another panel");
 HWMixerAttributes mixer_attributes_;
 RetryStack stack; RetryStack *layer_stack=&stack;
 RetryLayers disp_layer_stack_;
 disp_layer_stack_.info.left_frame_roi={{0,72,1080,288}};
 if (!partial) disp_layer_stack_.info.left_frame_roi={{0,0,1080,2400}};
 RetryManager manager{&stack}; manager.result=prepare_error;
 RetryManager *comp_manager_=&manager;
 int display_comp_ctx_=0, error=-1;
 bool mondrian_pu_failed_=false, pu_pending_=true;
'''
retry_test = r'''
 bool eligible=partial && m11a;
 bool ready=retry_full_frame();
 assert(ready==(eligible && prepare_error>=0));
 assert(manager.starts==(eligible ? 1 : 0));
 assert(manager.stops==manager.starts);
 assert(manager.disabled==eligible && stack.needs_validate==eligible);
 assert(mondrian_pu_failed_==eligible && pu_pending_!=eligible);
 assert(retry_prepare_failed==(eligible && prepare_error<0));
 assert(!retry_full_frame());
 assert(manager.starts==(eligible ? 1 : 0));
}
int main() {
 for (bool partial : {false,true}) for (bool m11a : {false,true})
   for (int result : {0,1,2,-1}) checkRetry(partial,m11a,result);
 std::puts("Retry: forced revalidation, fatal prepare and single-attempt regressions passed");
}
'''
strategy_source = (core / 'strategy.cpp').read_text()
strategy_methods = function(strategy_source, 'Strategy::GenerateROI') + '\n' + function(
    strategy_source[strategy_source.index('void Strategy::GenerateROI()'):], 'Strategy::GenerateROI')
lifecycle_fixture = r'''
#define FLOAT(x) static_cast<float>(x)
namespace sdm {
constexpr int kErrorNone=0;
struct PUConstraints { bool enable=true; };
struct DispLayerStack { HWLayersInfo info; };
struct Planner {
 int calls=0, start_error=0, generate_error=0;
 bool enabled=true;
 HWDestScaleInfo scale;
 int Start(const PUConstraints &c) { enabled=c.enable; return start_error; }
 int GenerateROI(DispLayerStack *s) {
  ++calls;
  s->info.left_frame_roi={{540,72,1080,288}};
  s->info.dest_scale_info_map[1]=&scale;
  return generate_error;
 }
};
struct Strategy {
 DispLayerStack *disp_layer_stack_=nullptr;
 HWPanelInfo hw_panel_info_;
 HWMixerAttributes mixer_attributes_;
 HWDisplayAttributes display_attributes_;
 struct { bool is_src_split=true; } hw_resource_info_;
 Planner *partial_update_intf_=nullptr;
 bool pu_frame_enabled_=true, pu_start_success_=false;
 void GenerateROI(DispLayerStack*,const PUConstraints&);
 void GenerateROI();
};
struct ClientLock { explicit ClientLock(int) {} };
struct DisplayBase {
 int disp_mutex_=0, mondrian_pu_mode_=2;
 bool validated_=true, needs_validate_=false;
 HWPanelInfo hw_panel_info_;
 bool IsValidated();
};
'''
lifecycle_test = r'''
} // namespace sdm
int main() {
 using namespace sdm;
 Planner planner; Strategy strategy; DispLayerStack stack;
 strategy.partial_update_intf_=&planner;
 auto full=[&] { assert(stack.info.left_frame_roi.size()==1);
   auto r=stack.info.left_frame_roi[0];
   assert(r.left==0 && r.top==0 && r.right==1080 && r.bottom==2400);
   assert(stack.info.dest_scale_info_map.empty()); };
 for (int i=0; i<100; ++i) {
  Debug::value=2; strategy.GenerateROI(&stack,{});
  assert(planner.enabled && stack.info.dest_scale_info_map.at(1)==&planner.scale);
  int before=planner.calls;
  Debug::value=0; strategy.GenerateROI(&stack,{});
  assert(!planner.enabled && planner.calls==before+1); full();
 }
 Debug::value=2; strategy.GenerateROI(&stack,{false}); full();
 planner.generate_error=-1; strategy.GenerateROI(&stack,{}); full();
 int before=planner.calls;
 planner.start_error=-1; strategy.GenerateROI(&stack,{}); full();
 assert(planner.calls==before);
 strategy.partial_update_intf_=nullptr; strategy.GenerateROI(&stack,{}); full();
 DisplayBase display;
 assert(display.IsValidated());
 Debug::value=0; assert(!display.IsValidated());
 assert(display.mondrian_pu_mode_==2); // Observation does not consume transition.
 display.mondrian_pu_mode_=0; assert(display.IsValidated());
 Debug::value=2; assert(!display.IsValidated());
 std::strcpy(display.hw_panel_info_.panel_name,"another panel");
 assert(display.IsValidated());
 std::puts("Lifecycle: 100 ON/OFF cycles, disabled Start, failed planning, stale DS cleanup, HWC skip gate passed");
}
'''

scaler_fixture = r'''
namespace sdm {
using DisplayError=int;
constexpr int kErrorNone=0, kErrorNotSupported=-1;
constexpr int SDE_DRM_DESTSCALER_ENABLE=1, SDE_DRM_DESTSCALER_SCALE_UPDATE=2,
 SDE_DRM_DESTSCALER_ENHANCER_UPDATE=4, SDE_DRM_DESTSCALER_PU_ENABLE=8;
struct SDEScaler { struct { bool enable=false;
 struct { bool enable=false; } de; uint32_t dst_width=0,dst_height=0; } scaler_v2; };
struct sde_drm_dest_scaler_cfg { uint32_t index=0,flags=0,lm_width=0,lm_height=0;
 uint64_t scaler_cfg=0; };
struct DSData { uint32_t num_dest_scaler=0; sde_drm_dest_scaler_cfg ds_cfg[2]; };
struct HWScale {
 void SetScaler(const ScaleData &in, SDEScaler *out) {
  out->scaler_v2.enable=in.enable.scale;
  out->scaler_v2.de.enable=in.enable.detail_enhance;
  out->scaler_v2.dst_width=in.dst_width; out->scaler_v2.dst_height=in.dst_height;
 }
};
enum class DRMOps { CRTC_SET_DEST_SCALER_CONFIG };
struct Atomic {
 int calls=0; DSData data;
 void Perform(DRMOps,int,uint64_t p) { ++calls; data=*reinterpret_cast<DSData*>(p); }
};
struct HWPeripheralDRM {
 HWScale scaler; HWScale *hw_scale_=&scaler;
 HWPanelInfo hw_panel_info_;
 uint32_t dest_scaler_blocks_used_=2;
 std::vector<SDEScaler> scalar_data_{2};
 DSData sde_dest_scalar_data_;
 struct Cache { SDEScaler scalar_data; uint32_t flags=0; };
 std::vector<Cache> dest_scalar_cache_{2};
 Atomic atomic; Atomic *drm_atomic_intf_=&atomic;
 struct { int crtc_id=1; } token_;
 bool needs_ds_update_=false;
 DisplayError SetDestScalarData(const HWLayersInfo&);
};
'''
scaler_test = r'''
}
int main() {
 using namespace sdm;
 HWPeripheralDRM hw; HWLayersInfo layers; HWDestScaleInfo left,right;
 layers.updates_mask.set(kUpdateResources);
 layers.dest_scale_info_map[1]=&right;
 assert(hw.SetDestScalarData(layers)==kErrorNone);
 assert(hw.atomic.data.num_dest_scaler==1 && hw.atomic.data.ds_cfg[0].index==1);
 assert(hw.atomic.data.ds_cfg[0].flags & SDE_DRM_DESTSCALER_SCALE_UPDATE);
 left.scale_data.enable.scale=false; layers.dest_scale_info_map[0]=&left;
 assert(hw.SetDestScalarData(layers)==kErrorNone);
 assert(hw.atomic.data.num_dest_scaler==1 && hw.atomic.data.ds_cfg[0].index==1);
 left.scale_data.enable.scale=true;
 assert(hw.SetDestScalarData(layers)==kErrorNone);
 assert(hw.atomic.data.num_dest_scaler==2 && hw.atomic.data.ds_cfg[0].index==0);
 right.mixer_height=3200;
 assert(hw.SetDestScalarData(layers)==kErrorNone);
 assert(hw.atomic.data.ds_cfg[1].lm_height==3200);
 int before=hw.atomic.calls;
 layers.updates_mask.reset();
 assert(hw.SetDestScalarData(layers)==kErrorNone && hw.atomic.calls==before);
 layers.updates_mask.set(); layers.dest_scale_info_map[1]=nullptr;
 assert(hw.SetDestScalarData(layers)==kErrorNotSupported && hw.atomic.calls==before);
 layers.dest_scale_info_map.erase(1); layers.dest_scale_info_map[2]=&right;
 assert(hw.SetDestScalarData(layers)==kErrorNotSupported && hw.atomic.calls==before);
 layers.dest_scale_info_map.clear();
 assert(hw.SetDestScalarData(layers)==kErrorNone && hw.atomic.calls==before+1);
 assert(hw.atomic.data.num_dest_scaler==2);
 for (auto &cfg:hw.atomic.data.ds_cfg) {
  assert(cfg.flags==SDE_DRM_DESTSCALER_SCALE_UPDATE);
  assert(!hw.scalar_data_[cfg.index].scaler_v2.enable);
 }
 std::puts("DS submission: sparse right index, disabled entry, LM change, buffer-only, invalid plan and native disable passed");
}
'''

with tempfile.TemporaryDirectory(prefix='mondrian-pu-tests-') as name:
    tmp=Path(name)
    (tmp/'private').mkdir(); (tmp/'utils').mkdir()
    (tmp/'private/hw_info_types.h').write_text('#pragma once\n')
    (tmp/'utils/debug.h').write_text('#pragma once\n')
    sources={
      'hwc':fixtures+'\n#include "mondrian_pu.h"\nnamespace sdm {\n'+hwc_helpers+'\n}\n'+hwc_test,
      'kernel':kernel_fixture+f'\n#define SDE_DS_OVERFETCH_SIZE {overfetch}\n'+kernel_helpers+kernel_test,
      'retry':fixtures+'\n#include "mondrian_pu.h"\n'+retry_fixture+retry_block+retry_test,
      'lifecycle': fixtures+'\n#include "mondrian_pu.h"\n'+lifecycle_fixture+strategy_methods+
                   function(prepare_source, 'DisplayBase::IsValidated')+lifecycle_test,
      'scaler': fixtures+'\n#include "mondrian_pu.h"\n'+scaler_fixture+function(
          (core/'drm/hw_peripheral_drm.cpp').read_text(), 'HWPeripheralDRM::SetDestScalarData')+scaler_test,
    }
    for key,source in sources.items():
        src=tmp/(key+'.cpp'); binary=tmp/key; src.write_text(source)
        subprocess.run(['c++','-std=c++17','-Wall','-Wextra','-Werror','-Wno-sign-compare',
                        '-O2','-fsanitize=undefined','-I'+str(tmp),'-I'+str(core),
                        str(src),'-o',str(binary)],check=True)
        subprocess.run([str(binary)],check=True)
print('These host tests do not validate Android ABI, SELinux compilation, QSEED hardware or the panel.')
