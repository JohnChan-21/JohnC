package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 管理员
*/
@Data
public class Admin extends Account {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 用户名 */
    private String username;
    /**
     * 密码：WRITE_ONLY 表示反序列化能读，序列化（响应）不输出
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    /** 姓名 */
    private String name;
    /** 头像 */
    private String avatar;
    /** 角色标识 */
    private String role;

}