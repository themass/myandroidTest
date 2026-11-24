-- 2026112402 声音馆歌词 mock 字幕（手工执行、不自动跑；生产 MySQL 由用户手跑）
--
-- 用途：给真机/联调点亮读链路（subtitle/items.json → item.json）。
-- 数据源：2026-11-25 线上探针抓的真实条目 soundItemId 2137–2150（「剑雨狼花」连载，
-- items.json 里均带 mp3 file）。每条目 3 条 cue、每条 2 秒（0-2/2-4/4-6s）。
-- 全部打 engine='mock'、genStatus='ready'；UNIQUE(soundItemId) 防重——已有点评/生成行会
-- 报 duplicate 跳过，不会覆盖。回滚=最后一条 DELETE（只删 engine='mock'）。
-- 注意：标题取自 mp3 文件名（items.json 的 title 为 null）；sourceFileHash=SHA2(file,256)
-- 与 SoundAudioUrl.sha256Hex 口径一致（小写 hex 由 SHA2() 给出，MySQL 默认小写）。

-- mock 字幕：每条目 3 条 cue，每条 2 秒（2026-11-25，等用户执行；回滚按 engine='mock'）

-- ① 备份

SELECT id, soundItemId, title, genStatus, engine, LENGTH(content) AS contentLen FROM vpn.sound_subtitle ORDER BY id;

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2137,'剑雨狼花15','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花15

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花15

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花15
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花15.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2139,'剑雨狼花16','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花16

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花16

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花16
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花16.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2141,'剑雨狼花14','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花14

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花14

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花14
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花14.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2142,'剑雨狼花12-2','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花12-2

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花12-2

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花12-2
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花12-2.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2143,'剑雨狼花13','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花13

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花13

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花13
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花13.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2144,'剑雨狼花12-1','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花12-1

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花12-1

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花12-1
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花12-1.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2145,'剑雨狼花11','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花11

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花11

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花11
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花11.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2146,'剑雨狼花9','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花9

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花9

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花9
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花9.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2147,'剑雨狼花10','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花10

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花10

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花10
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花10.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2148,'剑雨狼花8','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花8

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花8

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花8
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花8.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2149,'剑雨狼花6','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花6

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花6

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花6
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花6.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

INSERT INTO vpn.sound_subtitle (soundItemId,title,lang,format,content,sourceFileHash,genStatus,engine,enabled,createTime)
VALUES (2150,'剑雨狼花7','zh','srt','1
00:00:00,000 --> 00:00:02,000
测试字幕 第1条 剑雨狼花7

2
00:00:02,000 --> 00:00:04,000
测试字幕 第2条 剑雨狼花7

3
00:00:04,000 --> 00:00:06,000
测试字幕 第3条 剑雨狼花7
',SHA2('https://mmssxs1.com/有声连载/剑雨狼花7.mp3', 256),'ready','mock',1,UNIX_TIMESTAMP()*1000);

-- ③ 回滚

DELETE FROM vpn.sound_subtitle WHERE engine='mock';
