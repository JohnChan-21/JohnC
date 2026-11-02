-- ====================================================================
-- 线上种子数据修正脚本（部署后执行一次即可）
-- 作用：把导入的演示数据中写死的 http://localhost:9090 图片地址
--       替换为真实站点域名，否则线上电影海报 / 头像全部 404。
--
-- 使用方法（在部署机上执行）：
--   1) 先备份：mysqldump -u root -p film_comment_system > backup.sql
--   2) 修改下方 @new_base 为你的真实域名（结尾不要带斜杠）
--   3) 执行：mysql -u john123456 -p film_comment_system < fix-seed-urls.sql
--
-- 说明：
--   - 只改 URL 前缀；原始文件需部署到后端 files 上传目录，
--     且 Nginx 需将 /files/download/** 反代到后端（同源部署时天然满足）。
-- ====================================================================

USE film_comment_system;

SET @new_base = 'https://your-domain.com';   -- ★ 改成你的真实站点域名（结尾不带斜杠）

-- 可选：先预览将被替换的行数，确认范围后再执行
-- SELECT COUNT(*) FROM film  WHERE cover  LIKE 'http://localhost:9090%';
-- SELECT COUNT(*) FROM admin WHERE avatar LIKE 'http://localhost:9090%';
-- SELECT COUNT(*) FROM `user` WHERE avatar LIKE 'http://localhost:9090%';

-- 电影海报
UPDATE film
SET cover = REPLACE(cover, 'http://localhost:9090', @new_base)
WHERE cover LIKE 'http://localhost:9090%';

-- 管理员头像
UPDATE admin
SET avatar = REPLACE(avatar, 'http://localhost:9090', @new_base)
WHERE avatar LIKE 'http://localhost:9090%';

-- 普通用户头像
UPDATE `user`
SET avatar = REPLACE(avatar, 'http://localhost:9090', @new_base)
WHERE avatar LIKE 'http://localhost:9090%';

-- --------------------------------------------------------------------
-- 必做：重置全部账号口令（决策：演示账号保留、评论保留，仅改密）
--   种子数据里的 bcrypt 哈希随 dump 公开传播，等同已知密码，上线前必须重置。
--   在部署机上用 Apache htpasswd 生成 bcrypt 哈希（明文自己定）：
--     htpasswd -bnBC 10 "" '你的新密码' | tr -d ':\n'
--   把输出（$2y$ 开头，需把 $2y$ 改成 $2a$）填入下面各条：
--
-- ① 主管理员 admin(id=1)：
-- UPDATE admin SET password = '<bcrypt哈希>' WHERE id = 1;
--
-- ② 演示管理员 John(id=2)：
-- UPDATE admin SET password = '<bcrypt哈希>' WHERE id = 2;
--
-- ③ 7 个普通演示用户（ccc/bbb/aaa/acc/ddd/user/user2），统一新密码便于演示：
-- UPDATE `user` SET password = '<bcrypt哈希>' WHERE id IN (1,2,3,4,5,12,14);
--
-- 提示：直接 SQL 改密不会踢已登录会话；上线初期无已登录会话可忽略，
--       日常改密请走系统「修改密码」接口（会踢全部会话）。
-- --------------------------------------------------------------------