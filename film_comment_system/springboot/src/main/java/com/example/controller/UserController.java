package com.example.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.common.LoginUserResolver;
import com.example.common.Result;
import com.example.entity.Account;
import com.example.entity.User;
import com.example.exception.CustomException;
import com.example.service.UserService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户前端操作接口
 **/
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private LoginUserResolver loginUserResolver;

    /**
     * 新增
     */
    @SaCheckRole("ADMIN")
    @PostMapping("/add")
    public Result save(@RequestBody User user) {
        userService.save(user);
        return Result.success();
    }

    /**
     * 删除
     */
    @SaCheckRole("ADMIN")
    @DeleteMapping("/delete/{id}")
    public Result removeById(@PathVariable Integer id) {
        userService.removeById(id);
        return Result.success();
    }

    /**
     * 修改：仅本人或管理员可改；普通用户禁止改 role/password，防提权
     */
    @PutMapping("/update")
    public Result updateById(@RequestBody User user) {
        Account currentUser = loginUserResolver.currentAccount();
        if (currentUser == null) {
            throw new CustomException("登录已过期，请重新登录");
        }
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        if (!isAdmin) {
            // 必须是本人
            if (user.getId() == null || !user.getId().equals(currentUser.getId())) {
                throw new CustomException("无权修改他人信息");
            }
            // 普通用户禁止改角色和密码（密码走 updatePassword 专用接口）
            user.setRole(null);
            user.setPassword(null);
        }
        userService.updateById(user);
        return Result.success();
    }

    /**
     * 根据ID查询
     */
    @SaCheckRole("ADMIN")
    @GetMapping("/selectById/{id}")
    public Result selectById(@PathVariable Integer id) {
        User user = userService.selectById(id);
        return Result.success(user);
    }

    /**
     * 查询所有
     */
    @SaCheckRole("ADMIN")
    @GetMapping("/selectAll")
    public Result list(User user) {
        List<User> list = userService.list(user);
        return Result.success(list);
    }

    /**
     * 分页查询
     */
    @SaCheckRole("ADMIN")
    @GetMapping("/selectPage")
    public Result selectPage(User user,
                             @RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageInfo<User> page = userService.selectPage(user, pageNum, pageSize);
        return Result.success(page);
    }

}