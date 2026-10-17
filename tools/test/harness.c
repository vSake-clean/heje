// Headless test harness around pce-go (the PC Engine core used by Cardputer Game Station).
// Usage: harness rom.pce < script
// script lines: "<frames> <buttons>" (buttons: U D L R A B S(elect) T(run) or -), "shot <file.ppm>", "ram <hexaddr> <len>"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "pce-go.h"
#include "pce.h"
#include "h6280.h"
void pce_run(void);
uint64_t esp_timer_get_time(void){return 0;}
void vTaskDelay(int x){(void)x;}
void ets_delay_us(uint32_t x){(void)x;}
uint32_t esp_get_free_heap_size(void){return 0;}
static uint8_t *fbbase,*fb; static int fw,fh,allocH=-1;
static uint8_t joy;
uint8_t *osd_gfx_framebuffer(int w,int h){
  if(w<=0||h<=0) return fb;
  fw=w; fh=h;
  if(!fbbase){ allocH=h; fbbase=calloc(XBUF_WIDTH*(h+2),1); fb=fbbase+16; fprintf(stderr,"[fb alloc %dx%d]\n",w,h);}
  if(h>allocH){fprintf(stderr,"!! FB OVERFLOW h=%d alloc=%d\n",h,allocH); exit(3);}
  return fb;
}
void osd_input_read(uint8_t j[8]){ j[0]=joy; }
void osd_vsync(void){}
static void shot(const char*fn){
  FILE*f=fopen(fn,"wb"); fprintf(f,"P6\n%d %d\n255\n",fw,fh);
  for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){uint8_t i=fb[y*XBUF_WIDTH+x];
    uint8_t r=(i&0x1C)<<3,g=(i&0xE0),b=(i&3)<<6; if(i==255)r=g=b=255; fputc(r,f);fputc(g,f);fputc(b,f);}
  fclose(f);
}
int main(int c,char**v){
  if(InitPCE(22050,false))return 1;
  if(LoadFile(v[1]))return 2;
  char line[256]; long frame=0;
  while(fgets(line,sizeof line,stdin)){
    char a[256],b[256]; int n=sscanf(line,"%255s %255s",a,b); if(n<1||a[0]=='#')continue;
    if(!strcmp(a,"shot")){shot(b);fprintf(stderr,"shot %s @%ld (%dx%d)\n",b,frame,fw,fh);continue;}
    if(!strcmp(a,"cpu")){printf("PC=%04x VDW=%d HDR=%04x CR=%04x MWR=%04x sw=%d sh=%d\n",CPU.PC,IO_VDC_REG[13].W,IO_VDC_REG[11].W,IO_VDC_REG[5].W,IO_VDC_REG[9].W,PCE.VDC.screen_width,PCE.VDC.screen_height);continue;}
    if(!strcmp(a,"poke")){int ad,val; sscanf(line,"poke %x %x",&ad,&val); PCE.RAM[(ad-0x2000)&0x1fff]=val; continue;}
    if(!strcmp(a,"ram")){int ad,len; sscanf(line,"ram %x %d",&ad,&len); printf("RAM %04x:",ad);for(int i=0;i<len;i++)printf(" %02x",PCE.RAM[(ad-0x2000+i)&0x1fff]);printf("\n");continue;}
    int frames=atoi(a); joy=0; if(n>1)for(char*p=b;*p;p++)switch(*p){case 'U':joy|=JOY_UP;break;case 'D':joy|=JOY_DOWN;break;case 'L':joy|=JOY_LEFT;break;case 'R':joy|=JOY_RIGHT;break;case 'A':joy|=JOY_A;break;case 'B':joy|=JOY_B;break;case 'S':joy|=JOY_SELECT;break;case 'T':joy|=JOY_RUN;break;}
    for(int i=0;i<frames;i++){osd_input_read(PCE.Joypad.regs);pce_run();frame++;}
  }
  return 0;
}
