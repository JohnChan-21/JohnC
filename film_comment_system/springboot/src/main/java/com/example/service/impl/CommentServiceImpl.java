package com.example.service.impl;

import cn.hutool.core.date.DateUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.common.HtmlSanitizer;
import com.example.common.LoginUserResolver;
import com.example.entity.Account;
import com.example.entity.Comment;
import com.example.exception.CustomException;
import com.example.mapper.CommentMapper;
import com.example.service.CommentService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService { 

    @Resource
    private CommentMapper commentMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private LoginUserResolver loginUserResolver;

    private static final String CACHE_FILM_DETAIL = "film:detail:";
    private static final String CACHE_FILM_RANKING = "film:ranking";
    private static final String CACHE_FILM_SCORE = "film:score:";
    private static final String CACHE_FILM_RECOMMEND = "film:recommend:";

    /**
     * 新增评论（新增后清除相关电影缓存，保证评分实时性）
     * @return 是否新增成功
     */
    @Override
    public boolean save(Comment comment) {
        //  取当前登录用户（含 id 与 role）
        Account currentUser = loginUserResolver.currentAccount();
        if (currentUser == null) {
            throw new CustomException("用户未登录");
        }
        
        //  塞入归属信息
        comment.setIssuerId(currentUser.getId());
        comment.setIssuerType(currentUser.getRole());

        // 内容净化：防存储型 XSS（长评走富文本白名单，短评/未知类型按纯文本转义）
        comment.setComment(sanitizeContent(comment.getType(), comment.getComment()));

        // 评分校验：防止绕过前端直接提交超范围分值（如 999999）刷榜
        comment.setScore(validateScore(comment.getScore()));

        comment.setTime(DateUtil.now());
        commentMapper.save(comment);
        // 清除该电影的评分、详情、推荐缓存
        Integer filmId = comment.getFilmId();
        if (filmId != null) {
            redisTemplate.delete(CACHE_FILM_SCORE + filmId);
            redisTemplate.delete(CACHE_FILM_DETAIL + filmId);
            redisTemplate.delete(CACHE_FILM_RECOMMEND + filmId);
        }
        // 清除排行榜缓存
        redisTemplate.delete(CACHE_FILM_RANKING);
        return true;
    }

    /**
     * 更新评论：归属校验——普通用户仅能改自己的评论，管理员可改任意评论；
     * 归属字段（issuerId/issuerType）与 filmId 以库中旧记录为准，禁止前端篡改。
     * 更新后清除相关电影缓存，保证评分实时性。
     * @return 是否更新成功
     */
    @Override
    public boolean updateById(Comment comment) {
        if (comment.getId() == null) {
            throw new CustomException("评论ID不能为空");
        }
        // 取当前登录用户（含 id 与 role）
        Account currentUser = loginUserResolver.currentAccount();
        if (currentUser == null) {
            throw new CustomException("用户未登录");
        }
        // 查出原评论做归属校验
        Comment dbComment = commentMapper.selectById(comment.getId());
        if (dbComment == null) {
            throw new CustomException("评论不存在");
        }
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        boolean isOwner = dbComment.getIssuerId() != null
                && dbComment.getIssuerId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new CustomException("无权修改他人评论");
        }
        // 防篡改：归属与所属电影不允许通过 update 修改，以库中记录为准
        comment.setIssuerId(dbComment.getIssuerId());
        comment.setIssuerType(dbComment.getIssuerType());
        comment.setFilmId(dbComment.getFilmId());
        // 内容净化：防存储型 XSS（长评走富文本白名单，短评/未知类型按纯文本转义）
        comment.setComment(sanitizeContent(comment.getType(), comment.getComment()));
        // 评分校验：防止绕过前端直接提交超范围分值（如 999999）刷榜
        comment.setScore(validateScore(comment.getScore()));
        // 时间以服务端为准：前端回传的是行内旧值，直接覆盖，编辑后即为最新时间
        comment.setTime(DateUtil.now());
        commentMapper.updateById(comment);
        // 清除该电影的评分、详情、推荐缓存
        Integer filmId = dbComment.getFilmId();
        if (filmId != null) {
            redisTemplate.delete(CACHE_FILM_SCORE + filmId);
            redisTemplate.delete(CACHE_FILM_DETAIL + filmId);
            redisTemplate.delete(CACHE_FILM_RECOMMEND + filmId);
        }
        // 清除排行榜缓存
        redisTemplate.delete(CACHE_FILM_RANKING);
        return true;
    }

    /**
     * 删除评论：归属校验——普通用户仅能删除自己的评论，管理员可删除任意评论
     * @return 是否删除成功
     */
    @Override
    public boolean removeById(Integer id) {
        // 取当前登录用户（含 id 与 role）
        Account currentUser = loginUserResolver.currentAccount();
        // 查出待删评论（顺便取 filmId 用于缓存清理，替代原先全量查询+过滤）
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new CustomException("评论不存在");
        }
        // 校验是否为本人评论
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        boolean isOwner = currentUser != null
                && comment.getIssuerId() != null
                && comment.getIssuerId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new CustomException("无权删除他人评论");
        }
        commentMapper.deleteById(id);
        if (comment.getFilmId() != null) {
            // 清除该电影的评分、详情、推荐缓存
            redisTemplate.delete(CACHE_FILM_SCORE + comment.getFilmId());
            redisTemplate.delete(CACHE_FILM_DETAIL + comment.getFilmId());
            redisTemplate.delete(CACHE_FILM_RECOMMEND + comment.getFilmId());
        }
        // 清除排行榜缓存
        redisTemplate.delete(CACHE_FILM_RANKING);
        return true;
    }

    /**
     * 查询所有（带电影名/发布者名，支持按电影名、类型等条件过滤）
     */
    @Override
    public List<Comment> list(Comment comment) {
        return commentMapper.list(comment);
    }

    /**
     * 分页模糊查询
     */
    @Override
    public PageInfo<Comment> selectPage(Comment comment, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Comment> list = commentMapper.list(comment);
        return PageInfo.of(list);
    }

    /**
     * 按评论类型净化内容：长评走富文本白名单，其余（短评/未知类型）一律按纯文本转义。
     * 类型由前端传入，因此未知类型必须回退到更严格的纯文本策略。
     */
    private String sanitizeContent(String type, String content) {
        return "长评".equals(type)
                ? HtmlSanitizer.cleanRichText(content)
                : HtmlSanitizer.cleanPlainText(content);
    }

    /**
     * 校验评分：前端 el-rate 满分 5、支持半星，因此分值必须落在 0~5。
     * 允许为空（视为未打分），但非空时超范围一律拒绝，防止绕过前端直接提交
     * 超大分值（如 999999）刷高电影评分与排行榜。
     */
    private Double validateScore(Double score) {
        if (score != null && (score < 0D || score > 5D)) {
            throw new CustomException("评分必须在 0~5 之间");
        }
        return score;
    }

}
