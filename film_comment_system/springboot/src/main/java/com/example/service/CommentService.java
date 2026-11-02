package com.example.service;

import com.example.entity.Comment;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

@Service
public interface CommentService extends IService<Comment> {

    /**
     * 删除评论：归属校验——普通用户仅能删除自己的评论，管理员可删除任意评论
     * @return 是否删除成功
     */
    boolean removeById(Integer id);

    /**
     * 查询所有（带电影名/发布者名，支持按电影名、类型等条件过滤）
     */
    List<Comment> list(Comment comment);

    /**
     * 分页模糊查询
     */
    PageInfo<Comment> selectPage(Comment comment, Integer pageNum, Integer pageSize);


}
