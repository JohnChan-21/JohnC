package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 角色用户父类
 */
@Data
public class Account {
    /** ID */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 用户名 */
    private String username;
    /** 名称 */
    private String name;
    /**
     * 密码：WRITE_ONLY 表示反序列化（接收请求）能读，序列化（响应给前端）不输出
     * 防止 /login、/user/selectAll 等接口在响应里回显密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    /** 角色标识 */
    private String role;
    /**
     * 新密码：仅用于改密请求的传输字段，数据库没有这一列。
     * 必须标记 exist = false，否则 MyBatis-Plus 会把它当成实体列，
     * 凡是未在 XML/注解里自定义语句、走 MP 生成 SQL 的地方
     * （如 AdminMapper 的 selectById、insert）都会带上 new_password 并报 Unknown column。
     */
    @TableField(exist = false)
    private String newPassword;
    /** 头像 */
    private String avatar;

}
