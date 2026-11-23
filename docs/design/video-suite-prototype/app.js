'use strict';
const $ = id => document.getElementById(id);
const icons = {
  play:'<path d="m9 5 11 7-11 7z" fill="currentColor" stroke="none"/>', pause:'<path d="M9 5v14m6-14v14"/>', back:'<path d="m14 6-6 6 6 6"/>', next:'<path d="m10 6 6 6-6 6"/>',
  rewind:'<path d="M8 4 4 8l4 4M4 8h9a6 6 0 1 1-5 9"/>', forward:'<path d="m16 4 4 4-4 4m4-4h-9a6 6 0 1 0 5 9"/>',
  full:'<path d="M9 4H4v5m11-5h5v5m-5 11h5v-5m-11 5H4v-5"/>', shrink:'<path d="M4 9h5V4m6 0v5h5m0 6h-5v5M9 20v-5H4"/>',
  lock:'<rect x="6" y="10" width="12" height="11" rx="2"/><path d="M9 10V7a3 3 0 0 1 6 0v3"/>', subtitles:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 14h4m2 0h4"/>', speed:'<path d="M4 16a8 8 0 1 1 16 0m-8 0 4-5"/>', chat:'<path d="M20 12a8 7 0 0 1-8 7H5l1-3a7 7 0 1 1 14-4Z"/><path d="M8 12h8"/>', more:'<path d="M5 12h.01m7 0h.01m7 0h.01" stroke-width="3"/>',
  heart:'<path d="M12 20 4.7 13a4.7 4.7 0 0 1 6.7-6.7l.6.6.6-.6a4.7 4.7 0 0 1 6.7 6.7Z"/>', cast:'<path d="M3 9V5h18v14h-8M3 12a7 7 0 0 1 7 7M3 16a3 3 0 0 1 3 3m-3 0h.01"/>',
  grid:'<path d="M4 4h6v6H4zm10 0h6v6h-6zM4 14h6v6H4zm10 0h6v6h-6z"/>', list:'<path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/>', close:'<path d="m6 6 12 12M18 6 6 18"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]}</svg>`;
const groups = [
  {id:'outside', name:'野外放映', type:'电影', image:'ridge', ratio:'.88', keys:['ridge','forest','snow','road','sea','sky']},
  {id:'late', name:'夜行电台', type:'夜色', image:'night', ratio:'1.32', keys:['night','city','sky']},
  {id:'water', name:'海边来信', type:'自定义', image:'sea', ratio:'1.25', keys:['sea','sky','road']},
  {id:'city', name:'城市侧写', type:'夜色', image:'city', ratio:'.82', keys:['city','night','road']},
  {id:'green', name:'森林来客', type:'自定义', image:'forest', ratio:'.96', keys:['forest','ridge','snow']},
  {id:'snow', name:'白色地平线', type:'电影', image:'snow', ratio:'1.22', keys:['snow','sky','ridge']}
];
const clips = Object.fromEntries([
  ['ridge','云层之上','去海拔更高的地方，听一会儿风。','.83'],['forest','树影慢一点','沿着没有路标的绿意往前走。','1.2'],['snow','白山回声','风过雪线，世界一下安静了。','.95'],['road','天亮就出发','把地图折起来，跟着地平线走。','1.12'],['night','午夜不打烊','把夜色留给还不想睡的人。','.84'],['city','最后一站','车窗外的灯，把街道拉成一条线。','1.2'],['sea','退潮之前','给自己留一小段看海的时间。','1.18'],['sky','蓝调之间','日落之后、天黑之前。','.86']
].map(([id,title,desc,ratio])=>[id,{id,title,desc,ratio}]));
const local = {id:'local',name:'本地视频',type:'本地样片',image:'road',keys:['road','ridge','sea']};
const seed = {ridge:4.2};
const recency = storage('recency',{ridge:1});
const watched = {...seed,...storage('watched',{})};
const likes = new Set(storage('likes',[]));
const state = {group:groups[0],clip:null,page:'channels',filter:'全部',videoFilter:'全部',poster:false,expanded:false,locked:false,rate:1,delay:0,cues:[],trackName:'',source:'off',danmaku:false,aspect:'contain'};
const scroll = {}, listPreferences = {};
const video = $('video'), screen = $('screen'), sheet = $('sheet');
let toastTimer, chromeTimer, pendingSeek = 0, lastSample = '';
function storage(key,fallback){try{return JSON.parse(localStorage.getItem(`screening-${key}`)) ?? fallback;}catch{return fallback;}}
function store(key,value){try{localStorage.setItem(`screening-${key}`,JSON.stringify(value));}catch{}}
function format(sec){const s=Math.max(0,Math.floor(Number(sec)||0));return `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;}
function announce(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,1900);}
const image = key => `media/${key}.jpg`;
function mountFilters(id, names, selected, select){const root=$(id);root.replaceChildren(...names.map(name=>{const b=document.createElement('button');b.textContent=name;b.setAttribute('aria-pressed',name===selected);b.onclick=()=>{select(name);[...root.children].forEach(n=>n.setAttribute('aria-pressed',n===b));};return b;}));}
function renderChannels(){
  const filtered=groups.filter(c=>state.filter==='全部'||c.type===state.filter);
  $('channel-count').textContent=`${filtered.length} 个频道`;
  $('channel-grid').innerHTML=[0,1].map(col=>`<section class="masonry-col">${filtered.filter((_,i)=>i%2===col).map(g=>`<button class="channel" data-group="${g.id}" style="--ratio:${g.ratio}"><img src="${image(g.image)}" alt=""><span><strong>${g.name}</strong><small>${g.keys.length} 个片段</small></span><span class="arrow">${icon('next')}</span></button>`).join('')}</section>`).join('');
}
function clipCard(key){const c=clips[key],t=watched[key]||0;return `<button class="video-card" data-clip="${key}" style="--ratio:${c.ratio}"><span class="thumb"><img src="${image(key)}" alt=""><b>0:12</b>${t?`<em style="width:${Math.min(100,t/12*100)}%"></em>`:''}</span><span><strong>${c.title}</strong><small>${t>=11.7?'已看完':t>0?`已看到 ${format(t)}`:'未观看'}</small></span></button>`;}
function renderLibrary(){
  const g=state.group;
  $('library-cover').querySelector('img').src=image(g.image);
  $('library-label').textContent=g.type;$('library-title').textContent=g.name;$('library-summary').textContent=`${g.keys.length} 个样片`;
  const resume=g.keys.find(key=>watched[key]>0&&watched[key]<11.7);
  $('resume-area').innerHTML=resume?`<button class="resume" data-clip="${resume}">${icon('play')}<span><strong>继续看《${clips[resume].title}》</strong><small>从 ${format(watched[resume])} 接着播</small></span><span class="resume-cta">续播</span></button>`:'';
  renderVideoGrid();
}
function renderVideoGrid(){
  const g=state.group,keys=g.keys.filter(key=>state.videoFilter!=='未观看'||!watched[key]);
  if(state.videoFilter==='最近看过')keys.sort((a,b)=>(recency[b]||0)-(recency[a]||0));
  $('video-grid').classList.toggle('poster',state.poster);
  $('layout').innerHTML=icon(state.poster?'list':'grid');$('layout').setAttribute('aria-pressed',state.poster);$('layout').setAttribute('aria-label',state.poster?'切换列表视图':'切换海报视图');
  $('video-grid').innerHTML=keys.length?keys.map(clipCard).join(''):'<p class="empty">这个频道没有未观看的样片。切回「全部」继续浏览。</p>';
}
function savePlayback(){if(state.clip&&video.currentTime>0&&!video.seeking){watched[state.clip]=video.ended?12:video.currentTime;store('watched',watched);recency[state.clip]=Date.now();store('recency',recency);}}
function stashPage(){
  if(state.page==='channels')scroll.channels=window.scrollY;
  if(state.page==='library'){scroll[state.group.id]=window.scrollY;listPreferences[state.group.id]={filter:state.videoFilter,poster:state.poster};}
}
let routeSerial=0;
function showPage(page,position=0){
  state.page=page;const seq=++routeSerial;
  for(const key of ['channels','library','player'])$(key).hidden=key!==page;
  requestAnimationFrame(()=>{if(seq!==routeSerial)return;const heading=$(page==='library'?'library-title':page==='player'?'player-title':'home-title');if(heading.tabIndex===-1&&page!=='channels')heading.focus({preventScroll:true});window.scrollTo(0,position);});
}
function go(group,key){const route=group?`#${group.id}${key?`/${key}`:''}`:'#channels';if(location.hash!==route)location.hash=route;}
function navigate(){
  const [groupId,clipId]=location.hash.slice(1).split('/');
  const group=[...groups,local].find(g=>g.id===groupId);
  const clip=group?.keys.includes(clipId)?clipId:null;
  if((state.page==='channels'&&!group)||(state.page==='player'&&state.group===group&&state.clip===clip)||(state.page==='library'&&state.group===group&&!clip))return;
  stashPage();
  if(state.page==='player'){savePlayback();video.pause();setExpanded(false);state.clip=null;if(sheet.open)sheet.close();}
  if(!group){showPage('channels',scroll.channels);return;}
  state.group=group;
  if(clip){openPlayer(clip);return;}
  $('player').hidden=true;
  const pref=listPreferences[group.id];state.videoFilter=pref?.filter??'全部';state.poster=pref?.poster??false;
  mountFilters('video-filters',['全部','未观看','最近看过'],state.videoFilter,name=>{state.videoFilter=name;renderVideoGrid();});
  renderLibrary();showPage('library',scroll[group.id]);
}
function updateLike(){const yes=likes.has(state.clip);$('favorite').setAttribute('aria-pressed',yes);$('favorite').innerHTML=`${icon('heart')}${yes?'已收藏':'收藏'}`;}
function openPlayer(key){
  const c=clips[key];state.clip=key;state.rate=1;state.delay=0;state.cues=[];state.trackName='';state.source='off';state.danmaku=false;state.aspect='contain';lastSample='';
  $('player-channel').textContent=state.group.name+' / 12 秒样片';$('player-title').textContent=c.title;$('expanded-title').textContent=c.title;$('player-desc').textContent=c.desc;
  $('queue').innerHTML=state.group.keys.filter(k=>k!==key).slice(0,4).map(clipCard).join('');
  video.poster=image(key);video.style.objectFit='contain';pendingSeek=watched[key]>0&&watched[key]<11.7?watched[key]:0;video.src=`media/${key}.mp4`;video.playbackRate=1;
  updateLike();$('progress').value=pendingSeek;sync();showPage('player');screen.classList.remove('quiet');
}
function setExpanded(on){
  state.expanded=on;state.locked=false;screen.classList.toggle('expanded',on);screen.classList.remove('quiet','locked');
  document.body.style.overflow=on?'hidden':'';$('unlock').hidden=true;$('lock').hidden=!on;
  $('fullscreen').innerHTML=icon(on?'shrink':'full');$('fullscreen').setAttribute('aria-label',on?'退出全屏':'全屏');
  (on?$('dock-slot'):$('tool-slot')).append($('dock'));burnChrome();
}
function burnChrome(){clearTimeout(chromeTimer);screen.classList.remove('quiet');if(state.expanded&&!video.paused&&!sheet.open&&!state.locked)chromeTimer=setTimeout(()=>{if(!screen.querySelector(':focus-visible'))screen.classList.add('quiet');},3500);}
function sync(){
  const duration=Number.isFinite(video.duration)?video.duration:12,t=video.currentTime||0;
  $('progress').max=duration;$('progress').value=t;$('clock').textContent=`${format(t)} / ${format(duration)}`;
  $('play').innerHTML=icon(video.paused?'play':'pause');$('play').setAttribute('aria-label',video.paused?'播放':'暂停');
  let cue='';if(state.source==='sample')cue=t-state.delay/1000>=1&&t-state.delay/1000<9?'风穿过山谷，天色渐亮。':'';
  if(state.source==='local')cue=state.cues.filter(c=>c.start<=t-state.delay/1000&&t-state.delay/1000<c.end).slice(0,3).map(c=>c.text).join('\n');
  if(cue!==lastSample||$('captions').textContent!==cue){$('captions').textContent=cue;lastSample=cue;}
  $('danmaku').hidden=!state.danmaku||t<2||t>=7;
}
async function togglePlay(){if(!state.clip||state.locked)return;if(!video.paused){video.pause();return;}try{await video.play();}catch{announce('无法播放样片，请重新打开。');}}
function seek(t){if(!Number.isFinite(video.duration))return;video.currentTime=Math.max(0,Math.min(video.duration,t));sync();}
function option(label,hint,action,{checked,disabled=false}={}){
  const b=document.createElement('button'),wrap=document.createElement('span'),name=document.createElement('strong');b.className='option';name.textContent=label;wrap.append(name);if(hint){const s=document.createElement('small');s.textContent=hint;wrap.append(s);}b.append(wrap);b.disabled=disabled;if(checked!==undefined)b.setAttribute('aria-pressed',checked);b.onclick=action;return b;
}
const toolLabels={subtitles:'字幕',speed:'倍速',chat:'弹幕',more:'更多',cast:'电视投屏'};
let panel = null;
function openPanel(name){panel=name;$('sheet-title').textContent=toolLabels[name];renderPanel();if(!sheet.open)sheet.showModal();burnChrome();}
function renderPanel(feedback=''){
  const root=$('sheet-content');root.replaceChildren();
  const note=text=>{const p=document.createElement('p');p.className='sheet-note';p.textContent=text;root.append(p);};
  const add=(...args)=>root.append(option(...args));
  if(panel==='subtitles'){
    note('在同一位置切换字幕来源。导入和渲染在浏览器本地完成。');
    const source=name=>{state.source=name;sync();renderPanel();};
    add('关闭字幕','不影响播放和弹幕',()=>source('off'),{checked:state.source==='off'});
    add('内嵌轨道','样片没有内嵌轨道',null,{disabled:true});
    if(state.trackName)add('本地文件',state.trackName,()=>source('local'),{checked:state.source==='local'});
    add(state.trackName?'换一个字幕文件':'本地文件',state.trackName?null:'导入 SRT 或 WebVTT',()=>$('subtitle-file').click());
    add('使用演示字幕','不需要文件，1 秒时开始显示',()=>source('sample'),{checked:state.source==='sample'});
    add('本机生成','交互原型未接语音识别',null,{disabled:true});
    add('在线实时','交互原型未连接在线服务',null,{disabled:true});
    const row=document.createElement('section');row.className='timing';row.innerHTML='<strong>字幕延时</strong><button aria-label="提前 500 毫秒">−</button><output aria-live="polite"></output><button aria-label="推迟 500 毫秒">+</button>';
    const out=row.querySelector('output'),change=n=>{state.delay=Math.max(-60000,Math.min(60000,state.delay+n));out.textContent=`${state.delay>0?'+':''}${state.delay} ms`;sync();};
    const [minus,plus]=row.querySelectorAll('button');minus.onclick=()=>change(-500);plus.onclick=()=>change(500);change(0);root.append(row);note('正值让字幕晚出现，负值让字幕早出现。');
    if(feedback){const p=document.createElement('p');p.className='sheet-feedback';p.setAttribute('role','status');p.textContent=feedback;root.append(p);}
  }else if(panel==='speed'){
    for(const rate of [.5,.75,1,1.25,1.5,2])add(`${rate}×`,rate===1?'正常速度':null,()=>{state.rate=rate;video.playbackRate=rate;sheet.close();renderTools();},{checked:state.rate===rate});
  }else if(panel==='chat'){
    add('关闭弹幕',null,()=>{state.danmaku=false;sync();renderPanel();},{checked:!state.danmaku});add('播放演示弹幕','样片 2–7 秒时显示，不影响字幕',()=>{state.danmaku=true;sync();renderPanel();},{checked:state.danmaku});
  }else if(panel==='more'){
    for(const [value,label] of [['contain','适应画面'],['cover','填满画面']])add(label,null,()=>{state.aspect=value;video.style.objectFit=value;renderPanel();},{checked:state.aspect===value});
  }else if(panel==='cast'){
    note('这里展示电视投屏的操作位置和设备页，不扫描网络，也不会向电视发送视频。');
    add('客厅电视','演示设备 · 未连接',()=>{const p=document.createElement('p');p.className='sheet-feedback';p.setAttribute('role','status');p.textContent='已选择演示设备，未发起真实投屏。';root.querySelector('.sheet-feedback')?.remove();root.append(p);});
  }
}
function parseTime(s){const fields=s.replace(',','.').split(':').map(Number);return fields.reduce((total,n)=>total*60+n,0);}
function parseCaptions(text){
  const cueLine=/^\s*((?:\d+:)?\d{2}:\d{2}[.,]\d{3})\s*-->\s*((?:\d+:)?\d{2}:\d{2}[.,]\d{3})(?:\s.*)?$/;
  return text.replace(/^﻿/,'').replace(/\r\n?/g,'\n').split(/\n[ \t]*\n+/).flatMap(block=>{
    const lines=block.split('\n');if(/^(WEBVTT|NOTE(?: |$)|STYLE$|REGION$)/.test(lines[0]))return [];
    const matches=lines.map(line=>line.match(cueLine)),i=matches.findIndex(Boolean);if(i<0)return [];
    const [,begin,end]=matches[i],start=parseTime(begin),finish=parseTime(end),content=lines.slice(i+1).join('\n').replace(/<[^>]*>/g,'').trim();
    return finish>start&&content?[{start,end:finish,text:content}]:[];
  }).sort((a,b)=>a.start-b.start);
}
function renderTools(){$('tools').innerHTML=Object.keys(toolLabels).slice(0,4).map(key=>`<button data-panel="${key}">${icon(key)}${toolLabels[key]}${key==='speed'&&state.rate!==1?` ${state.rate}×`:''}</button>`).join('');}
for(const b of document.querySelectorAll('[data-back]')){b.innerHTML=icon('back');b.onclick=()=>{if(state.locked)return;if(state.expanded){setExpanded(false);return;}go(b.dataset.back==='channels'?null:state.group);};}
$('channels-tab').onclick=()=>go(null);$('local-tab').onclick=()=>go(local);$('hero-play').innerHTML=`${icon('play')}看预告`;$('hero-play').onclick=()=>go(groups[0],'ridge');
$('layout').onclick=()=>{state.poster=!state.poster;renderVideoGrid();};
$('cast').innerHTML=`${icon('cast')}电视投屏`;$('cast').onclick=()=>openPanel('cast');
$('favorite').onclick=()=>{if(likes.has(state.clip))likes.delete(state.clip);else likes.add(state.clip);store('likes',[...likes]);updateLike();announce(likes.has(state.clip)?'已收藏':'已取消收藏');};
$('close-sheet').innerHTML=icon('close');$('close-sheet').onclick=()=>sheet.close();sheet.onclick=e=>{if(e.target===sheet)sheet.close();};sheet.addEventListener('close',()=>{panel=null;burnChrome();});
$('fullscreen').onclick=()=>setExpanded(!state.expanded);
$('fullscreen').innerHTML=icon('full');$('lock').innerHTML=icon('lock');
$('lock').onclick=()=>{state.locked=true;screen.classList.add('locked');$('unlock').hidden=false;$('unlock').focus();};
$('unlock').onclick=()=>{state.locked=false;screen.classList.remove('locked');$('unlock').hidden=true;$('lock').focus();burnChrome();};
$('play').onclick=togglePlay;$('rewind').innerHTML=icon('rewind');$('forward').innerHTML=icon('forward');$('rewind').onclick=()=>seek(video.currentTime-10);$('forward').onclick=()=>seek(video.currentTime+10);
$('progress').oninput=e=>{seek(Number(e.target.value));burnChrome();};
video.onclick=()=>{if(state.locked)return;if(video.paused){togglePlay();return;}if(screen.classList.contains('quiet'))burnChrome();else if(!video.paused)screen.classList.add('quiet');};
video.ondblclick=e=>{if(!state.locked)seek(video.currentTime+(e.offsetX<video.clientWidth/2?-10:10));};
video.onloadedmetadata=()=>{seek(pendingSeek);pendingSeek=0;};
for(const event of ['timeupdate','seeked','play','pause','ended'])video.addEventListener(event,()=>{sync();if(event==='ended')savePlayback();if(event==='play'||event==='pause')burnChrome();});
video.onerror=()=>{if(state.page==='player')announce('样片未能加载，请确认 media 文件夹与原型放在一起。');};
$('subtitle-file').onchange=async e=>{
  const file=e.target.files[0],key=state.clip;e.target.value='';if(!file)return;
  try{if(!/\.(srt|vtt)$/i.test(file.name)||file.size>2_000_000)throw Error();const cues=parseCaptions(await file.text());if(!cues.length||cues.length>10000)throw Error();if(state.clip!==key)return;state.cues=cues;state.trackName=file.name;state.source='local';state.delay=0;sync();if(panel==='subtitles')renderPanel('已导入字幕');}
  catch{if(panel==='subtitles')renderPanel('没能读取字幕。请选择有效的 SRT 或 WebVTT 文件（最多 2 MB）。');}
};
document.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.group)go(groups.find(g=>g.id===b.dataset.group));
  if(b.dataset.clip)go(state.group,b.dataset.clip);
  if(b.dataset.panel)openPanel(b.dataset.panel);
});
document.addEventListener('keydown',e=>{if(e.key!=='Escape'||sheet.open||state.page!=='player')return;if(state.locked)$('unlock').click();else if(state.expanded)setExpanded(false);});
window.addEventListener('hashchange',navigate);window.addEventListener('pagehide',savePlayback);
if('scrollRestoration' in history)history.scrollRestoration='manual';
mountFilters('channel-filters',['全部','电影','夜色','自定义'],state.filter,name=>{state.filter=name;renderChannels();});renderChannels();renderTools();navigate();
