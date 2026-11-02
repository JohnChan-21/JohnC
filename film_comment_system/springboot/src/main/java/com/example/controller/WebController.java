package com.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.common.LoginUserResolver;
import com.example.common.RateLimiter;
import com.example.common.Result;
import com.example.entity.Account;
import com.example.entity.User;
import com.example.exception.CustomException;
import com.example.service.AdminService;
import com.example.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;


@RestController
public class WebController {

    /** 登录限流：同一来源每分钟最多 20 次，防暴力撞库 */
    private static final long LOGIN_LIMIT_PER_MINUTE = 20L;
    /** 注册限流：同一来源每分钟最多 5 次，防批量注册 */
    private static final long REGISTER_LIMIT_PER_MINUTE = 5L;

    @Resource
    private AdminService adminService;
    @Resource
    private UserService userService;
    @Resource
    private LoginUserResolver loginUserResolver;
    @Resource
    private RateLimiter rateLimiter;

    /**
     * 默认请求接口
     */
    @GetMapping("/")
    public Result hello() {
        return Result.success();
    }

    /**
     * 登录（使用 Sa-Token 鉴权）
     */
    @PostMapping("/login")
    public Result login(@RequestBody Account account, HttpServletRequest request, HttpServletResponse response) {
        // 登录限流：按来源 IP 计数，防暴力撞库（超过阈值直接拒绝，不进入密码比对）
        if (!rateLimiter.tryAcquire("login:ip:" + clientIp(request), LOGIN_LIMIT_PER_MINUTE, 60)) {
            throw new CustomException("登录尝试过于频繁，请稍后再试");
        }
        Account ac = null;
        if ("ADMIN".equals(account.getRole())) {
            ac = adminService.login(account);
        } else if ("USER".equals(account.getRole())) {
            ac = userService.login(account);
        } else {
            throw new CustomException("角色错误");
        }
        // Sa-Token 登录：以用户 ID 作为账号标识；token 由 Sa-Token 写入 http-only Cookie
        StpUtil.login(ac.getRole() + ":" + ac.getId());
        // 将用户信息存入 Session（Redis 持久化）
        StpUtil.getSession().set("loginUser", ac);
        // 响应体不回传 token，前端不落盘，仅依赖 http-only Cookie 鉴权
        return Result.success(ac);
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result register(@RequestBody User user, HttpServletRequest request) {
        // 注册限流：按来源 IP 计数，防批量注册
        if (!rateLimiter.tryAcquire("register:ip:" + clientIp(request), REGISTER_LIMIT_PER_MINUTE, 60)) {
            throw new CustomException("注册过于频繁，请稍后再试");
        }
        userService.save(user);
        return Result.success();
    }

    /**
     * 获取客户端来源 IP：优先取反向代理设置的 X-Real-IP（Nginx 用 $remote_addr 覆盖，不可伪造），
     * 否则回退到直连地址。用于按来源限流。
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 修改密码：以当前登录用户身份修改，不信前端传入的 username/role
     */
    @PutMapping("/updatePassword")
    public Result updatePassword(@RequestBody Account account) {
        Account currentUser = loginUserResolver.currentAccount();
        if (currentUser == null) {
            throw new CustomException("登录已过期，请重新登录");
        }
        // 强制覆盖：当前登录用户的 username 与 role 才可信
        account.setUsername(currentUser.getUsername());
        account.setRole(currentUser.getRole());
        if ("ADMIN".equals(account.getRole())) {
            adminService.updatePassword(account);
        } else if ("USER".equals(account.getRole())) {
            userService.updatePassword(account);
        } else {
            throw new CustomException("角色错误");
        }
        // 修改密码后，注销该登录标识的全部会话（含其它设备/浏览器的旧登录），
        // 防止旧会话在改密后仍持有有效凭证继续使用
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId != null) {
            StpUtil.logout(loginId);
        }
        return Result.success();
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/currentUser")
    public Result currentUser() {
        if (StpUtil.isLogin()) {
            Account ac = (Account) StpUtil.getSession().get("loginUser");
            return Result.success(ac);
        }
        return Result.error("未登录");
    }

}
