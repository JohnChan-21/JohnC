package com.example.common;

import cn.dev33.satoken.stp.StpUtil;
import com.example.entity.Account;
import com.example.mapper.AdminMapper;
import com.example.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 登录用户实时解析器。
 * 系统把「角色」编码进登录标识（形如 "ADMIN:1" / "USER:1"），并在登录时把账号信息存进了 Session。
 * 若鉴权直接读 Session 中的快照，数据库改角色后旧会话仍保留原权限（降权不即时）。
 * 这里统一改为按登录标识实时回查数据库，取得「当前」角色，供鉴权与归属判断使用。
 */
@Component
public class LoginUserResolver {

    @Resource
    private AdminMapper adminMapper;
    @Resource
    private UserMapper userMapper;

    /**
     * 依据登录标识（角色:ID）实时回查数据库，返回当前角色。
     * 账号已不存在或标识非法时返回 null（调用方按无权限处理）。
     */
    public String resolveRole(Object loginId) {
        Account account = resolveAccount(loginId);
        return account == null ? null : account.getRole();
    }

    /**
     * 当前登录用户的实时角色；未登录或账号已删除返回 null。
     */
    public String currentRole() {
        return resolveRole(StpUtil.getLoginIdDefaultNull());
    }

    /**
     * 当前登录用户的实时账号信息（角色、名称等以数据库为准）；未登录或账号已删除返回 null。
     */
    public Account currentAccount() {
        return resolveAccount(StpUtil.getLoginIdDefaultNull());
    }

    /**
     * 按登录标识前缀定位账号所属表（admin / user），再按 ID 回查数据库。
     */
    private Account resolveAccount(Object loginId) {
        if (loginId == null) {
            return null;
        }
        String s = loginId.toString();
        int idx = s.indexOf(':');
        if (idx <= 0 || idx == s.length() - 1) {
            return null;
        }
        Integer id;
        try {
            id = Integer.valueOf(s.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        String prefix = s.substring(0, idx);
        if ("ADMIN".equals(prefix)) {
            return adminMapper.selectById(id);
        }
        if ("USER".equals(prefix)) {
            return userMapper.selectById(id);
        }
        return null;
    }
}