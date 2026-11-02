package com.example.common;

import cn.dev33.satoken.stp.StpInterface;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限/角色认证接口实现
 * 定义如何获取当前登录用户的角色和权限
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private LoginUserResolver loginUserResolver;

    /**
     * 返回当前登录用户所拥有的角色集合。
     * 角色按登录标识实时回查数据库，避免登录时的角色「快照」导致降权/提权不即时生效。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        String role = loginUserResolver.resolveRole(loginId);
        if (role != null) {
            roles.add(role);
        }
        return roles;
    }

    /**
     * 返回当前登录用户所拥有的权限集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 本系统基于角色控制，暂不细化到权限级别
        return new ArrayList<>();
    }
}