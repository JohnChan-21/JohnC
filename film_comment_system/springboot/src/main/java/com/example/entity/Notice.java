package com.example.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

/**
 * 通知
 */
@Data
public class Notice {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 通知标题 */
    private String title;
    /** 通知内容 */
    private String content;
    /** 通知发布时间 */
    private String time;
}
