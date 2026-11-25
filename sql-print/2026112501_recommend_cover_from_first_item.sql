-- 2026112501 列表页频道卡缺封面 → 回填该频道第一部电影的封面（手工执行，生产 MySQL 由用户跑）
--
-- 【重要更正】「列表页」(recommend/movie.json, dataType=5) 的频道卡并不在 vpn.recommend：
--   DataServiceImpl.getRecommendMoviePage → DataVideoServiceImpl.getVideoChannel
--   → VpnRecommandMapper 无关，走 VideoMapper.getChannel：
--     select * from vpn.videochannel_1 where enable=1 and channelType='movie' group by channel order by id desc
--   VO.img = videochannel_1.pic。所以本脚本改 vpn.videochannel_1.pic，不动 vpn.recommend。
--   探针证据（2026-11-25，GET /vvv/api/video/channel.json?channel=movie）：
--   52 行、36 行 img 空（id=122346「其他」param=ddys其他 等 ddys 系）；
--   非空 pic 一律是绝对 URL（如 https://i2.xiaoyakankan.com/9/d0/0mc7.jpg），
--   服务端对 pic 不做 baseurl 拼接（VideoController 里 vo.img 直接透传），所以写入必须是绝对 URL。
--
-- 「第一部」= 频道列表页默认口径：VideoMapper.getChannelItems
--   select * from vpn.videoitems_1 where channelType!='movie' and channel=? order by sortType desc, id desc
--   （DataVideoServiceImpl 第 151 行默认分支；带 keyword 才可能走 Asc 变体）。
--   因此取每组 channel 内 sortType desc, id desc 的第一条且 pic 非空的行。
--   items 行 channelType 若为 'movie' 则列表页本来就查不到它，不能拿来当封面——WHERE 里同样排除。
--
-- 执行顺序：① 备份表 ② 预览将被改的行 ③ UPDATE ④ 验证。出问题跑最后的回滚段。
--
-- 【风险须先库内核实】探针（2026-11-25）对 GET /vvv/api/video/channel/items.json?channel=ddys其他
--   等多变体均返回 errno 0 空列表——线上这批 ddys 频道可能压根没有条目行，或 videoitems_1 与
--   videochannel_1 不在同一库（先例见 2026112100_video_subtitle.sql 注释「两张表不同机器上
--   不保证同库」）。执行前先在目标库跑：
--     SELECT channel, COUNT(*) FROM vpn.videoitems_1
--     WHERE channel IN (SELECT channel FROM vpn.videochannel_1
--                       WHERE enable=1 AND channelType='movie' AND (pic IS NULL OR pic=''))
--     GROUP BY channel;
--   有行才继续；没行/不同库则本脚本只会空转（EXISTS 保护），需要跨库导出封面另议。

-- ① 备份（把当前全部空 pic 行落表，回滚就靠它）
CREATE TABLE IF NOT EXISTS vpn.videochannel_1_pic_bak_20261125 (
    id    INT PRIMARY KEY,
    channel VARCHAR(200),
    pic   VARCHAR(500)
);
INSERT INTO vpn.videochannel_1_pic_bak_20261125 (id, channel, pic)
SELECT id, channel, COALESCE(pic, '')
FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND (pic IS NULL OR pic = '');

-- ② 预览将被改的行与将要写入的封面（先跑这个肉眼确认，再 ③）
SELECT c.id, c.channel, c.name, COALESCE(c.pic,'') AS oldPic,
       COALESCE(NULLIF(SUBSTRING_INDEX(i.pic, '|', 1), ''),
                CONCAT(c.baseurl, SUBSTRING_INDEX(i.pic, '|', 1))) AS newPic
FROM vpn.videochannel_1 c
JOIN (
    SELECT x.channel, x.pic
    FROM vpn.videoitems_1 x
    WHERE x.channelType IS NULL OR x.channelType != 'movie'
    ORDER BY x.channel, x.sortType DESC, x.id DESC
) i ON i.channel = c.channel
WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '')
  AND COALESCE(NULLIF(SUBSTRING_INDEX(i.pic, '|', 1), ''), '') <> ''
  AND (SUBSTRING_INDEX(i.pic, '|', 1) LIKE 'http%'
       OR COALESCE(c.baseurl, '') <> '');
-- 注：内层派生表借「ORDER BY + 外层按 channel 取首行」是 MySQL 8 不保证的口径；
--   生产若为 8.0.14+，改用下面 ③ 里带窗口函数的版本（已在 ③ 注释给出）。

-- ③ 回填：空 pic ← 该频道列表页第一部有封面电影的 pic（相对路径按频道 baseurl 拼绝对）
-- MySQL 5.7 可跑版本（相关子查询天然按 getChannelItems 口径取第一条）：
UPDATE vpn.videochannel_1 c
SET c.pic = (
    SELECT COALESCE(
        CASE WHEN i.pic LIKE 'http%' THEN i.pic
             WHEN c.baseurl IS NOT NULL AND c.baseurl <> '' AND i.pic <> '' THEN CONCAT(c.baseurl, i.pic)
        END, '')
    FROM vpn.videoitems_1 i
    WHERE i.channel = c.channel
      AND (i.channelType IS NULL OR i.channelType != 'movie')
      AND COALESCE(i.pic, '') <> ''
    ORDER BY i.sortType DESC, i.id DESC
    LIMIT 1
)
WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '')
  AND EXISTS (
    SELECT 1 FROM vpn.videoitems_1 i
    WHERE i.channel = c.channel
      AND (i.channelType IS NULL OR i.channelType != 'movie')
      AND COALESCE(i.pic, '') <> ''
      AND (i.pic LIKE 'http%' OR COALESCE(c.baseurl, '') <> '')
  );

-- MySQL 8.0.14+ 更稳的等价版本（显式取组内第一行；二选一执行，别都跑）：
-- UPDATE vpn.videochannel_1 c
-- JOIN (
--     SELECT channel, pic FROM (
--         SELECT channel, pic,
--                ROW_NUMBER() OVER (PARTITION BY channel ORDER BY sortType DESC, id DESC) rn
--         FROM vpn.videoitems_1
--         WHERE (channelType IS NULL OR channelType != 'movie') AND COALESCE(pic,'') <> ''
--     ) t WHERE rn = 1
-- ) i ON i.channel = c.channel
-- SET c.pic = CASE WHEN i.pic LIKE 'http%' THEN i.pic ELSE CONCAT(c.baseurl, i.pic) END
-- WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '');

-- ④ 验证：空 pic 应只剩「该频道没有任何带封面条目」的行
SELECT COUNT(*) AS stillEmpty FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND (pic IS NULL OR pic = '');
-- 接口侧：GET /vvv/api/video/channel.json?channel=movie（同 recommend/movie.json）应看到原 36 空行出图。

-- 回滚（按备份表逐行恢复，含把 NULL 恢复成 NULL）
UPDATE vpn.videochannel_1 c
JOIN vpn.videochannel_1_pic_bak_20261125 b ON b.id = c.id
SET c.pic = NULLIF(b.pic, '');
