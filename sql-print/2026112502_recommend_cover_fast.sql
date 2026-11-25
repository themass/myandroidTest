-- 2026112502 列表页封面回填【快速版·修正】——替换 2026112501 的 ③ 段
--
-- 【修正了什么（2026-11-25 二次校对）】
-- 旧版 ④⑤ 把条目过滤成 `channelType != 'movie'`——方向反了。
-- 服务端 VideoMapper.getChannelItems 是 choose/when：
--   app 点电影频道卡进列表页 → getOneChannel 命中 videochannel_1 行（channelType='movie'）
--   → 走 when 分支：WHERE channelType='movie' AND channel=#{channel}
--     ORDER BY sortType DESC, id DESC
-- `!= 'movie'` 只是频道行查不到时的 otherwise 兜底。所以列表页真正显示的就是
-- channelType='movie' 的条目（2026-11-22 探针：ddys动作 875 条即此批），
-- 旧口径会把它们全部排除，预览全 NULL、回填不了任何东西。
-- 另：服务端给列表行拼相对 pic 用的是【条目自己的 baseurl】（VO 的 baseurl=VideoPo.baseurl，
-- 不是频道表的），所以相对路径要拼 i.baseurl，不是 c.baseurl。
--
-- 2026112501 慢的根因：③ 用相关子查询 + EXISTS，36 个空封面频道各扫两趟
-- videoitems_1（线上大表）。连接断开＝语句超时被掐，MySQL 回滚整个事务，
-- 数据库没有被改，可以放心重来。
--
-- 正确顺序：先确认有没有 channel 索引（没有就在线加，顺便给线上接口提速）→
-- 再逐频道点查（走索引，毫秒级）→ 最后按主键逐行 UPDATE（每行一个极短事务，
-- 断线也不会回滚掉已改的行）。

-- ① 有没有索引 / 表多大（先跑这个，只看结果不写数据）
SHOW INDEX FROM vpn.videoitems_1;
SELECT table_name, table_rows, engine
FROM information_schema.tables
WHERE table_schema = 'vpn' AND table_name IN ('videoitems_1', 'videochannel_1');
-- 若 SHOW INDEX 里没有任何以 channel 打头的索引 → 跑 ②；有则直接跳到 ③。

-- ② 在线补索引（InnoDB；INPLACE + LOCK=NONE 不挡读写，只是要跑一会儿）。
-- 这个索引同时给线上「频道列表页」接口提速——items.json 就是按 channel 过滤的。
-- 大表建索引期间别断线：建议在 screen/tmux 里跑，或用客户端后台执行。
ALTER TABLE vpn.videoitems_1
    ADD INDEX idx_channel_sort (channel, sortType, id),
    ALGORITHM = INPLACE, LOCK = NONE;
-- （若之前已建过 idx_channel_sort，不用换：④ 的 channelType='movie' 过滤很便宜，
--   走 channel 前缀 + 倒序扫即可。）

-- ③ 备份（照 2026112501 的 ①，很轻，只落空 pic 行）
CREATE TABLE IF NOT EXISTS vpn.videochannel_1_pic_bak_20261125 (
    id      INT PRIMARY KEY,
    channel VARCHAR(200),
    pic     VARCHAR(500)
);
INSERT INTO vpn.videochannel_1_pic_bak_20261125 (id, channel, pic)
SELECT id, channel, COALESCE(pic, '')
FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND (pic IS NULL OR pic = '');

-- ④ 逐频道点查封面（走 ② 的索引，每频道一次 LIMIT 1；先看结果）
-- 口径与列表页 when 分支完全一致：channelType='movie'（频道行本身是 movie，
-- getChannelItems 就按它过滤）+ order by sortType desc, id desc。
SELECT c.id, c.channel, c.name,
       (SELECT i.pic
          FROM vpn.videoitems_1 i
         WHERE i.channel = c.channel
           AND i.channelType = 'movie'
           AND COALESCE(i.pic, '') <> ''
         ORDER BY i.sortType DESC, i.id DESC
         LIMIT 1) AS firstPic,
       (SELECT i.baseurl
          FROM vpn.videoitems_1 i
         WHERE i.channel = c.channel
           AND i.channelType = 'movie'
           AND COALESCE(i.pic, '') <> ''
         ORDER BY i.sortType DESC, i.id DESC
         LIMIT 1) AS firstPicBase
FROM vpn.videochannel_1 c
WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '')
ORDER BY c.id;
-- firstPic 为 NULL 的频道＝该频道没有带封面的条目（跑脚本也不会有封面，数据事实）。
-- 2026-11-22 探针：36 个 ddys 频道里只有 ddys动作 有 875 条，其余 35 个 0 条——
-- 预期只有 ddys动作（以及任何真有条目的频道）会被回填，这不是脚本漏了。

-- ⑤ 回填（把 ④ 里 firstPic 非 NULL 的频道按主键逐行改；绝对 URL 直接用，
--    相对路径拼【条目自己的 baseurl】——与列表页拼法一致）
-- 逐行版（最稳，断了不丢已改的）——把 <id> 换成 ④ 的 c.id：
UPDATE vpn.videochannel_1 c
SET c.pic = CONCAT(
        CASE WHEN (SELECT i.pic FROM vpn.videoitems_1 i
                    WHERE i.channel = c.channel AND i.channelType = 'movie'
                      AND COALESCE(i.pic, '') <> ''
                    ORDER BY i.sortType DESC, i.id DESC LIMIT 1) LIKE 'http%'
             THEN ''
             ELSE COALESCE((SELECT i.baseurl FROM vpn.videoitems_1 i
                    WHERE i.channel = c.channel AND i.channelType = 'movie'
                      AND COALESCE(i.pic, '') <> ''
                    ORDER BY i.sortType DESC, i.id DESC LIMIT 1), '')
        END,
        (SELECT i.pic FROM vpn.videoitems_1 i
          WHERE i.channel = c.channel AND i.channelType = 'movie'
            AND COALESCE(i.pic, '') <> ''
          ORDER BY i.sortType DESC, i.id DESC LIMIT 1))
WHERE c.id = <④里的 c.id>;
-- 一条搞定版（有索引后点查是毫秒级；④ 已看过结果，直接跑也行）：
-- 上面 UPDATE 去掉 `WHERE c.id = ...`，换成：
--   WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '')
--     AND EXISTS (SELECT 1 FROM vpn.videoitems_1 i
--                 WHERE i.channel = c.channel AND i.channelType = 'movie'
--                   AND COALESCE(i.pic,'') <> '' LIMIT 1)

-- ⑥ 验证
SELECT COUNT(*) AS stillEmpty FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND (pic IS NULL OR pic = '');
SELECT id, channel, pic FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND pic <> '' ORDER BY id DESC LIMIT 10;
-- 接口侧：GET /api/video/channel.json?channel=movie 应看到回填的行出图。

-- 回滚（照 2026112501）
UPDATE vpn.videochannel_1 c
JOIN vpn.videochannel_1_pic_bak_20261125 b ON b.id = c.id
SET c.pic = NULLIF(b.pic, '');
-- ② 加的索引不用删：它是线上接口本来就该有的。
