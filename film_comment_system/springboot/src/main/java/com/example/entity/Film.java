package com.example.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

/**
 * 电影
 */
@Data
public class Film {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 电影名称 */
    private String name;
    /** 封面 */
    private String cover;
    /** 电影描述 */
    private String descr;
    /** 电影年份 */
    private String year;
    /** 导演 */
    private String director;
    /** 演员 */
    private String actors;
    /** 分类ID */
    private Integer categoryId;
    /** 国家 */
    private String country;
    /** 语言 */
    private String language;
    /** 发布日期 */
    private String date;
    /** 电影时长 */
    private String duration;
    /** IMDB评分 */
    private String imdb;
    /** 分类名称 */
    private String categoryName;
    // 多少条评论
    private Integer commentNum;
    // 电影评分
    private Double score;

}
