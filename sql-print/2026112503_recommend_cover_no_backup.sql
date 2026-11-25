-- 2026112503 列表页封面回填【无备份·直接可用版】（用户点名 2026-11-25：「不需要备份，给我一个可用的sql」）
--
-- 口径与 2026112502 完全一致（服务端 VideoMapper.getChannelItems 走 choose/when：
-- 列表页真正显示的是 channelType='movie' 的条目；相对 pic 拼条目自身 baseurl），
-- 只去掉备份表和回滚段——改的是 videochannel_1.pic（封面），原值本来为空就是空，
-- 没有可丢的数据；要撤销就把改过的行 UPDATE 回 pic='' 即可（③ 预览里有行号）。
--
-- 顺序：① 有没有 channel 索引（没有先在线加）→ ② 逐频道点查预览（毫秒级）→
-- ③ 回填（只改预览里 firstPic 非 NULL 的行）→ ④ 验证。

-- ① 先看索引（只读）
SHOW INDEX FROM vpn.videoitems_1;
-- SHOW INDEX 里没有任何以 channel 打头的索引 → 先跑下面这条（INPLACE+LOCK=NONE 不挡读写；
-- 这个索引同时给线上频道列表接口提速）；有则跳过。
ALTER TABLE vpn.videoitems_1
    ADD INDEX idx_channel_sort (channel, sortType, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- ② 预览：每个空封面频道将取到的封面（先看结果再改）
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
-- firstPic 为 NULL 的频道＝该频道没有带封面的条目，不会也不会回填（数据事实：
-- 2026-11-22 探针 36 个 ddys 频道只有 ddys动作 有 875 条）。

-- ③ 回填（有 ① 的索引后每条点查是毫秒级；直接跑这一条）
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
WHERE c.enable = 1 AND c.channelType = 'movie' AND (c.pic IS NULL OR c.pic = '')
  AND EXISTS (SELECT 1 FROM vpn.videoitems_1 i
              WHERE i.channel = c.channel AND i.channelType = 'movie'
                AND COALESCE(i.pic, '') <> '');

-- ④ 验证
SELECT COUNT(*) AS stillEmpty FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND (pic IS NULL OR pic = '');
SELECT id, channel, pic FROM vpn.videochannel_1
WHERE enable = 1 AND channelType = 'movie' AND pic <> '' ORDER BY id DESC LIMIT 10;
-- 接口侧：GET /api/video/channel.json 应看到回填的频道卡出图。
