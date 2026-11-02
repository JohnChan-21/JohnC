package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 分类
 */
@Data
public class Category {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 分类名称 */
    private String name;
}
