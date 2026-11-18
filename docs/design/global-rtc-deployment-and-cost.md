# 全球语音陪伴 RTC：部署拓扑、实施清单与成本

> 2026-11-17｜设计提案，未部署。用户分布全球，现有业务服务器在境外；具体云厂商、地域、流量分布和并发尚未提供。
> 本文不读取生产凭据、不购买资源、不部署或切换线路。价格为当日官方公开美元价，税费、折扣和实际账单另计。

## 1. 结论：先保留 Cloud 媒体，区域化我们自己的 Agent

**首选 A：LiveKit Cloud 全球媒体网络 + 自有区域化 Python Agent + 现有 testserverV 控制面。**

理由不是“托管一定快”，而是全球网络需要就近接入、运营商覆盖、TURN、媒体骨干和运维；当前没有证据表明迁移 SFU 能解决现有卡顿。我们可以保留自主业务与记忆系统，只购买媒体网络，不必同时购买 Agent 托管。

- 第一阶段在现有服务器所在地部署一组可观测、预热的 Agent，先测端到端分段延迟。
- 第二阶段按真实用户分布增设美洲／欧洲／亚太 Agent 池，独立显式 dispatch。不要一上来买齐三洲六节点。
- 自托管 SFU 作为独立 canary 与后续成本选项；ARTC 保留适配边界，不在本轮换 SDK。
- 如果团队不想维护 Agent 扩缩容，可选 B：Cloud 媒体 + Cloud Agent；费用与支持区域要分开判断。

### 撤回旧结论

“跨境就是卡顿根因”“最近只有东京／孟买”“国内 ECS 改动近零”“解决80%”“4–8周必然完成”均不是本项目实测结论。本次用户明确全球市场后，国内节点不是默认方案。Qwen 首包约1秒的旧说法也没有本项目统一测试证据，不作为选型依据。

## 2. 三种拓扑，三类不同成本

| 方案 | 媒体 SFU/TURN | Agent 计算 | 适用 |
|---|---|---|---|
| A 推荐 | Cloud全球mesh | 我们自己的VM/容器 | 保留Python业务，先控制费用和部署自由度 |
| B 全托管 | Cloud全球mesh | Cloud Agent部署 | 少运维，接受按Agent会话分钟及部署配额计费 |
| C 全自托管 | 我们的区域SFU/TURN | 我们的VM/容器 | 流量足够、数据边界要求或有RTC运维能力后评估 |

**Cloud媒体区域不等于Cloud Agent托管区域。** 当前官方列出的托管Agent区域是 us-east（Ashburn）、eu-central（Frankfurt）、ap-south（Mumbai）。不要把媒体边缘节点误当成可部署Agent的位置。自有Agent可以放在自己选定的境外云地域，再接Cloud媒体。

### A的拓扑

```text
全球 Android
  | HTTPS：登录/会话/令牌/历史
  v
API入口 -> testserverV + companion控制面 -> 会话路由表/业务DB
  |
  | 返回短时房间token、Cloud URL、session_id
  v
用户 <-> 就近Cloud媒体边缘 <== Cloud媒体网络 ==> Agent侧媒体边缘
                                                   |
                                      区域Python Agent池
                                       |      |       |
                                    ASR流    LLM流    TTS流
                                       \      |       /
                                       对应服务的真实区域

区域Agent -> 私有网络/鉴权服务API -> 业务、记忆、授权
```

相机在端侧，不进媒体轨；动作／语义数据走交互协议。桶直传和普通API不承载RTC媒体，不为“无CDN”而取消Cloud媒体服务：这两者不是同一个CDN概念。

### 控制面与媒体面拆开

- AppServer签发令牌、鉴权、角色／会话路由；不代理音频，不通过nginx把RTP转成HTTP。
- Agent注册控制连接是出站WebSocket，不需要公网入站业务端口；**音频仍需要独立WebRTC/ICE出站与回程规则**，不是只开WebSocket就够。
- 数据库查询、画像、工具调用也可能拖慢首字节；不把所有延迟记在LiveKit头上。
- 现有Java登录态在Redis的事实，不等于所有洲Agent都应该直连同一公网Redis。优先同区服务API验证／短期内部凭证；如保留Redis验证，使用私网/TLS、最小权限并计入跨区延迟。

## 3. 区域选址与路由

### 3.1 初始候选而非采购清单

| 用户群 | 自有Agent候选 | 要测的模型端点 |
|---|---|---|
| 美洲 | 北美东部；西海岸用户多再比较西部 | 实际ASR/LLM/TTS地域，不根据域名猜 |
| 欧洲 | Frankfurt或邻近EU区域 | EU可用端点；记忆与日志是否出EU另审 |
| 南亚 | Mumbai或Singapore实测比较 | 同语言模型质量、流式稳定性 |
| 东南亚/东亚/大洋洲 | Singapore或Tokyo候选 | 不假设Mumbai托管Agent对东亚最快 |
| 南美/中东/非洲 | 先使用测得最佳现有池 | 有足够占比与明显劣化再开新池 |

国家不等于最佳网络路径；运营商、VPN和模型服务位置都会改变结果。

### 3.2 路由算法

1. 先筛选法域／数据驻留允许区域，后考虑健康、容量和延迟。
2. IP地理位置只作初始提示；使用区域探测与真实会话统计更新权重。HTTPS探针不是RTP质量证明。
3. 对候选区域计算：用户媒体路径 + Agent到ASR/TTS/LLM实际路径 + 队列／冷启动 + 记忆读取。不能把全部RTT直接相加当精确首音延迟。
4. 服务端选择区域并写入session route；整个通话保持固定，不因短暂RTT抖动反复迁移。
5. 区域Agent使用不同dispatch name，如 companion-prod-eu / companion-prod-us / companion-prod-ap。API明确指定，避免多地域同名worker偶然抢单。
6. dispatch按session幂等；本地维护唯一约束和结果对账，不假设调用超时就是未创建。重复连接不额外生成第二个Agent。

Cloud的媒体就近路由与我们的Agent选择是两个独立决策。Cloud默认有地理亲和，但显式区域名称更容易验证与控制。若启用数据驻留，不能为了低延迟偷偷跨允许区域。

### 3.3 建连流程

- 用户请求创建会话：鉴权、额度、幂等键、选择区域。
- 控制面生成不可猜房间名，绑定用户/角色/交互epoch/Agent版本。
- 创建房间及显式dispatch；避免同时启用自动dispatch和token dispatch造成双Agent。
- 返回限制room/identity/source的短时token；不把API secret发客户端。当前无视频需求应禁止发布摄像头来源，兼容具体SDK授权能力验证后上线。
- 用户和Agent分别连接Cloud；状态区分用户入会、Agent入会、麦克风发布、首包到达、首音可播放。
- 超时释放空房间与孤立Agent，返回明确错误；挂断停止Agent会话，不只把UI音量归零继续计费。

## 4. 先定位“卡”在哪

每回合关联session_id/turn_id/speech_id，不记录token和原始人脸数据。

| 阶段 | 指标与证据 |
|---|---|
| 登录/创建会话 | DNS、TLS、API响应、鉴权/DB耗时 |
| RTC建连 | connect至Connected、选中ICE候选类型/协议、地区、重连次数 |
| 麦克风 | 权限/轨发布成功、本地PCM变化、Agent收到音频 |
| 上行语音 | RTT、jitter、丢包、concealment、Agent收帧间隔 |
| 轮次判定 | 用户停止说话至end-of-turn提交，避免VAD等太久 |
| ASR | 首partial、final时间 |
| LLM | 首token、排队、每秒token、工具调用 |
| TTS | 首音频块、合成速率、流中断 |
| 下行播放 | 音频发送到客户端收帧与真实出声分别测；蓝牙单列 |

跨设备时间不能直接相减：客户端阶段用本地单调时钟，服务端用trace，跨端需校时误差或端到端测量。PCM sink回调不是实际扬声器播放时钟。

验收目标初值：会话建立p95≤3s、有效语音结束至首音p50≤1.2s/p95≤2.5s、正常网络重连成功率≥99%。这些是待校准产品目标，不是现状或厂商保证。按国家/运营商/设备/候选路径分桶，不拿全球平均数遮蔽某地区失败。

## 5. A方案执行步骤

### 5.1 环境隔离与版本清单

- staging与prod独立Cloud项目、API密钥及worker；禁止本地开发worker接生产任务。
- 锁定Android 2.29.0、Python agents/rtc/server版本、容器digest及模型配置。最新文档里的API不自动等于锁定版本可用。
- 输出不含秘密的部署清单：区域、实例规格、worker名、镜像digest、数据库法域、模型端点区域、并发阈值。
- 使用Secret Manager/私有文件注入密钥，不能把.env/生产yml内容贴进文档。

### 5.2 单区基线

1. 保留当前业务地域和模型配置，部署至少两个预热Agent副本（两个故障域才有AZ级冗余）。
2. CPU/内存可先从每worker 4vCPU/8GiB开始压测。官方10–25并发是一般建议，不是本项目承诺；初期只按每worker 10个活跃job预算。
3. load阈值先0.7，扩容触发约0.5，并结合活跃job、队列、内存和模型配额，避免CPU低但外部服务被限流。
4. 进程预热VAD等必要模型，镜像构建阶段下载允许的权重；不要让每个新通话首次下载。
5. 健康端点仅私网开放；liveness与readiness分开，业务模型不可用时不再接新任务。
6. 优雅排空：先停止接单，已有通话结束再停；长陪伴会话需要最长会话时长与排空上限，不能用固定10分钟强杀所有长通话。

### 5.3 第二、三区

- 在相同版本、模型质量下，只增加一个区域池做5%新会话灰度。
- 比较同国家历史基线、不同运营商以及模型端点；不是让同一用户同时开两路付费通话。
- 地域达到样本量、质量不退化后再提高到25/50/100%；留快速回退区域配置。
- 区域故障只把新会话送到允许的备用区；活跃房间不能承诺无损切换Agent。

### 5.4 数据与记忆

初期可以单主写库，但跨洲每次工具调用都会增加RTT；先加载小型角色/画像快照，异步非关键写入，不任意缓存跨账号数据。不要把强一致用户事实换成多主最终一致而无冲突方案。

若承诺EU驻留，需要一起约束Agent、数据库、日志、对象桶及模型供应商。Cloud EU媒体pinning不覆盖所有外部API；Cloud inference地域限制也不自动适用于自有Agent。GDPR不是“选Frankfurt”就完成，跨境合同和法律判断另行审查。

## 6. C方案：全自托管怎么搭

### 6.1 不是复制Cloud全球mesh

官方开源自托管是single-home：**一个房间必须放在一个SFU节点上**。多个节点加Redis可以扩展房间数量，但不能把同一房间自动变成全球分布式媒体mesh。

1人+1Agent很适合区域单房间：让用户选到区域SFU、Agent与SFU同区，避免Agent跨洲。纯DNS就近不能解决一个已存在房间在远端的问题。

推荐按区域独立cluster/独立Redis与凭据，控制面返回区域URL；跨区会话路由放业务层。不要默认跨洲共用一个Redis总线。官方也支持共享Redis+regionaware选择器，但跨洲控制面延迟、分区故障和房间单节点限制仍在，本项目不把它作为初期默认。

```text
Global API -> session router
              |-- us: signaling LB -> SFU us-a/us-b -> private Redis-us
              |        TURN L4 LB / public IP         Agent-us pool
              |-- eu: signaling LB -> SFU eu-a/eu-b -> private Redis-eu
              |        TURN L4 LB / public IP         Agent-eu pool
              `-- ap: signaling LB -> SFU ap-a/ap-b -> private Redis-ap
                       TURN L4 LB / public IP         Agent-ap pool
```

### 6.2 网络和端口表

| 服务 | 入口 | 规则 |
|---|---|---|
| API/WSS | TCP443 -> TLS/L7 LB ->7880 | WSS升级、长连接超时正确；管理API由应用鉴权隔离，不因端口内部就认为所有HTTP方法安全 |
| 直接媒体UDP | 每SFU公网IP，50000–60000/UDP **或** mux 7882/UDP | 选一种并验证advertised IP；普通HTTP反代不能转发RTP |
| ICE TCP | 7881/TCP直达节点 | UDP被禁时备用，不通过HTTP LB |
| TURN TLS | 独立turn域名，公网443/TCP -> L4 ->5349 | 证书、TLS终止层、后端路由按官方生成配置核对 |
| TURN UDP | 3478/UDP，可另评估443/UDP | 不与直接UDP mux混淆；NAT与返回规则验证 |
| Redis/DB | 私网TLS/安全组 | 不暴露公网；Redis与登录态Redis职责分离 |
| metrics/worker health | 私网 | 监控认证，禁止公网裸暴露 |

TURN/TLS443和WSS443不能在同IP上被两个无协调进程抢占。可以用独立公网IP/四层LB；单VM验证使用官方Caddy/Compose生成方案，不能把两份“监听443”的教程直接拼起来。域名示例仅占位：rtc-us.example.com、turn-us.example.com。

媒体域名默认DNS-only，不经普通CDN橙云代理；除非另购买并验证支持UDP/TCP实时媒体的专用服务。启用HTTPS不代表所有严格企业网络必通，实际TURN认证与relay路径必须测试。

### 6.3 搭建顺序与交付物

1. 确认地域/固定公网地址/域名/预算，用IaC记录网络、安全组和主机。环境隔离。
2. 在新空目录运行官方livekit/generate，审查输出Caddy/Compose/LiveKit/Redis配置；不直接在生产服务器运行未知脚本。
3. 使用受信CA证书，设置真正的公网候选地址，固定镜像版本，不用latest自动升级。
4. 区域先部署staging单SFU；验证UDP、禁UDP后的TCP、TURN/TLS-only三种路径。
5. 加第二SFU、区域Redis HA、信令LB和TURN L4入口；不能把staging单VM称为生产HA。
6. Agent接该区域URL并显式注册，AppServer用同区域secret签房间token；日志遮蔽JWT。
7. 跑授权用户的模拟语音负载，逐步增加并发，测CPU/NIC/丢包/音频质量；不做对公网目标的压测。
8. 5%新会话灰度，失败回退Cloud；收集完整账单后再决定扩区。

### 6.4 故障语义

SFU宕机并不会因为Redis存在就无损转移正在转发的音频。客户端先走SDK重连；若房间/Agent状态丢失，控制面重建新attempt，新的token/epoch，加载已提交历史，不假装补回没录下的半句话。

Agent任务故障也需要业务恢复，不依赖“dispatch会自动重试”来保证进行中的语音job迁移。room_attempt与logical_session分离；一个会话只允许一个有效attempt，避免回退后两路Agent同时说话和重复记忆写入。

## 7. LiveKit官方价格（2026-11-17查证）

| 项目 | Build | Ship | Scale |
|---|---:|---:|---:|
| 月费起点 | $0 | $50 | $500 |
| WebRTC participant分钟包含量 | 5,000 | 150,000 | 1,500,000 |
| 超额participant分钟 | 无此处生产报价 | $0.0005 | $0.0004 |
| 下行流量包含量 | 50GB | 250GB | 3TB |
| 超额下行 | 以套餐限制为准 | $0.12/GB | $0.10/GB |
| Cloud Agent分钟包含量 | 1,000 | 5,000 | 50,000 |
| 超额Cloud Agent分钟 | 以套餐限制为准 | $0.01 | $0.01 |
| Cloud Agent部署数 | 1 | 2 | 4 |
| Agent并发 | 5 | 20 | 起始50，可申请至600 |

媒体连接并发是另一指标：Build100、Ship1000、Scale5000。Ship的20是托管Agent会话配额，不是把自有Agent池的容量等同为20；自有方案仍须确认项目整体配额/调度限制。三洲托管Agent部署数至少3，Ship默认只有2个，不能只看分钟算钱。

**单价换算**：Ship超额participant分钟$0.0005 = 每1,000 participant分钟$0.50；Cloud Agent超额$0.01 = 每1,000 Agent分钟$10，相差20倍，但购买的是不同服务。

### 7.1 计费口径

- M = 每月真实通话时长（room分钟），不是活跃用户数，也不是只在说话的分钟。
- 1用户+1自有Agent接Cloud：按 **P≈2M participant分钟** 预算。官方价格表明确self-hosted agents计入WebRTC participant分钟。
- 1用户+1 Cloud-hosted Agent：用户participant分钟按P≈M，Agent另计A≈M。上线用账单核实实际身份分类，不盲套2倍。
- 多设备、重连重叠、额外机器人会改变P；没有说话但保持连接通常仍计时。
- 媒体/Agent按秒计量，每session最低10秒；流量最小增量0.01GB并逐资源取整后汇总，因此大量短会话不能只用总M精确推账。
- 自有Agent没有Cloud计算托管分钟费，但VM和其向Cloud发送的出口费用归我们；使用Cloud inference、turn detection、observability仍可能独立收费。

### 7.2 公式

Ship：

`50 + max(P-150000,0)*0.0005 + max(D-250,0)*0.12 + max(A-5000,0)*0.01`

Scale：

`500 + max(P-1500000,0)*0.0004 + max(D-3000,0)*0.10 + max(A-50000,0)*0.01`

A仅用于Cloud-hosted Agent；自有Agent设A=0。D为账单实际GB。此处把3TB按3000GB计算，采购前核对账单单位。各免费量独立不能互相折算。

**不包含**：ASR/LLM/TTS、VM、存储、录音、日志、额外turn detection、自有云出口及税。无电话接入不加SIP费；无录像不加视频egress费。

### 7.3 三档算例

为了可比，假设每分钟总Cloud下行 **2MB（十进制）**，D=0.002M GB。这是模型输入，不是实测音频码率；包括对用户及自有Agent的下行时，流量要按真实总量记。采用Opus、静音抑制和包开销后会不同。

| room分钟/月 | 自有Agent+Cloud Ship | 自有Agent+Cloud Scale | Cloud Agent+Ship | Cloud Agent+Scale |
|---:|---:|---:|---:|---:|
| 10,000 | $50 | $500 | $100 | $500 |
| 100,000 | $75 | $500 | $1,000 | $1,000 |
| 1,000,000 | $1,185 | $700 | $10,635 | $10,000 |

表为上述公式的**基础设施子集估算**，不表示全包报价。自有Agent列还要加worker VM及运维；托管列须同时满足Agent并发与部署数，月分钟低也可能因三洲部署需要Scale。

自有Agent在D=2MB/min这个假设下，Ship与Scale约在60.9万room分钟/月交叉；不是普遍固定阈值。全托管在10万分钟示例恰好相同，也不能仅凭相同价格选Ship，因为能力配额不同。

月分钟不能代替峰值：30天约43,200分钟。100,000分钟平均约2.31并发，若峰均比5则约11.6；1,000,000分钟平均23.15、峰值约115.7。峰均比是假设，需用真实日活时段与最长会话确认。

## 8. 自托管TCO：不编造云厂商报价

下列仅为**预算假设**，不是从AWS/其他厂商查得的当前价格。用户未指定云厂商、地域、折扣和现有空余容量，不能给出准确采购金额。

| 假设资源 | 单区月度输入示例 |
|---|---:|
| 2台SFU（跨AZ） | 2×$80=$160 |
| 区域Redis HA | $60 |
| WSS/TURN LB、公网IP及基础监控 | $80 |
| 2台Agent worker | 2×$80=$160 |
| 单区固定小计 | $460 |
| 三地区固定小计 | $1,380 |

同样三地区Agent在A方案也约$480/月（同假设）。所以比较A与C时，不能一边把Agent算进去另一边漏掉。

例如100,000room分钟：A Ship媒体$75 + 假设三洲Agent$480 = $555（加VM出口/监控等）；C假设固定$1,380（加出口/跨AZ/备份等）。不能据此宣称C一定贵，因为真实VM报价、现有资源、流量和运维投入都未确定。

100万分钟时每区两台worker不一定撑得住峰值；按每worker安全10并发且单区需容忍一台故障，区域峰值需要额外冗余。容量必须逐区算，不能直接复用固定$1,380估算。

### 8.1 全成本公式

`C = SFU + TURN + Agent计算 + Redis/LB/IP + 公网出口 + 跨AZ/跨区流量 + 监控/备份 + ASR/LLM/TTS + 运维工时 + 故障成本`

全自托管并不消除模型费用。Cloud媒体单价不是“每分钟AI总价”，不应拿它和阿里某个包含模型的套餐直接比。

模型成本独立记账：

`ASR按实际连接秒数/音频量 + LLM输入输出token(含缓存口径) + TTS字符/音频量`

不假设用户说话与AI说话各占一半，也不把“静音不说话”自动当成模型连接不计费。先从本项目100个真实授权回合抽样计算，不为了估价触发新模型调用。

### 8.2 做决定需要的表格

运维填写：各候选区SFU和Agent实例小时价、固定IP、LB、Redis、包含流量、超额出口、跨AZ价格、承诺折扣、带宽限制和DDoS防护边界；工程填写：每区安全并发、峰值、TTS/ASR/LLM消耗、排空时间；产品填写：月分钟、最长会话、国家比例、法域要求。

只有TCO和质量都过关才切，不以“开源免费”取代计算。

## 9. 故障回退与ARTC边界

- provider、region、room_attempt、凭据、版本是会话路由的一部分，不是客户端随意换factory就能跨供应商续聊。
- 新会话回退可由服务端改路由；活跃会话切供应商需要新token、重建音轨/数据流、取消旧speech、去重历史，用户可能听到停顿。
- 不并行发布两路麦克风或让两Agent同时回复；未获用户同意不偷偷录音用于补偿。
- ARTC评估必须覆盖Python桥接/进程隔离、全球覆盖、数据通道限制、SDK授权、音频时钟、语言模型和价格同口径；旧草案的32KB/1KB/价格不能视为本次已验证。

## 10. 可执行工作包与上线门槛

| 工作包 | 交付 | 门槛/回退 |
|---|---|---|
| T1 基线 | 分阶段trace、RTC统计、国家/运营商分桶报告 | 至少覆盖主要用户群、UDP与TURN场景；没有证据不迁SFU |
| T2 单区Agent规范化 | 锁版本镜像、预热、readiness、并发/排空、秘钥管理 | 重复dispatch不双Agent；挂断真停止计费任务 |
| T3 路由控制面 | 区域目录、session唯一attempt、显式dispatch、健康/容量选择 | staging故障演练、旧客户端兼容 |
| T4 新区试点 | 一新区部署、5%灰度、质量与成本对照 | 首音/失败率不退化；可配置回原区 |
| T5 自托管canary（可选） | 新隔离SFU/TURN/Redis网络、IaC与手册 | UDP/TCP/TURN-only、AZ/节点故障、证书续期均验收 |
| T6 成本复核 | 用实际账单替换模型假设 | 不遗漏worker、模型、流量取整与日志 |

这些是实施拆分，不是已批准采购或承诺工期。可先用T1+T2解决现有体验；明确瓶颈前不把所有组件同时换掉。

待用户补充但不阻塞方案成稿：现有云厂商/具体区域、前三用户国家及占比、月room分钟和峰值并发、模型供应商地域、是否有EU驻留承诺、可接受月预算与运维人力。

## 11. 文档衔接与来源

- [四主题调研汇总](companion-four-topic-research-summary.md)
- [共享图片直传与TOS桶设计](shared-media-upload-and-storage.md)：无CDN不等于公开桶；桶地域也须按全球市场重新确认，不默认沿用北京桶。
- [双向交互协议与端侧感知](companion-interaction-protocol-and-perception.md)

官方来源（2026-11-17读取）：

1. 价格：https://livekit.com/pricing
2. 计量/最低10秒/流量取整：https://docs.livekit.io/deploy/admin/billing/
3. Cloud与开源single-home/mesh差异：https://docs.livekit.io/transport/self-hosting/
4. 自托管分布式与regionaware：https://docs.livekit.io/transport/self-hosting/distributed/
5. 端口：https://docs.livekit.io/transport/self-hosting/ports-firewall/
6. TLS/TURN：https://docs.livekit.io/transport/self-hosting/deployment/
7. VM配置生成：https://docs.livekit.io/transport/self-hosting/vm/
8. 自有Agent部署、规格建议与排空：https://docs.livekit.io/deploy/custom/deployments/
9. 显式dispatch：https://docs.livekit.io/agents/server/agent-dispatch/
10. Agent区域：https://docs.livekit.io/deploy/admin/regions/agent-deployment/
11. 媒体/Agent区域区别：https://docs.livekit.io/deploy/admin/regions/endpoints/
12. 多层地域与隐私边界：https://docs.livekit.io/deploy/admin/regions/data-residency/

官方get_pricing_info与公开pricing页交叉核对了上述套餐金额。本文未查询用户Cloud后台/账单，未实测全球运营商路径，未核验任何云VM实时报价。所有容量、TCO假设与验收目标不得对外宣称已经达到。
