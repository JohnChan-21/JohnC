package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 评论
 */
@Data
public class Comment {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 电影ID */
    private Integer filmId;
    /** 评分 */
    private Double score;
    /** 评论内容 */
    private String comment;
    /** 评论人ID */
    private Integer issuerId;
    /** 评论人类型 */
    private String issuerType;
    /** 评论时间 */
    private String time;
    /** 评论类型 */
    private String type;
    /** 电影名称 */
    private String filmName;
    /** 评论人名称 */
    private String issuerName;

}
