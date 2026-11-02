package com.example.service.impl;

import cn.hutool.core.util.ObjectUtil;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.Account;
import com.example.entity.User;
import com.example.exception.CustomException;
import com.example.mapper.UserMapper;
import com.example.service.UserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务处理
 **/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

    /** BCrypt 单例：线程安全，避免重复 new */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 新增
     * @return 
     */
    @Override
    public boolean save(User user) {
        User dbUser = userMapper.selectByUsername(user.getUsername());
        if (ObjectUtil.isNotNull(dbUser)) {
            throw new CustomException("用户账号已存在");
        }
        if (ObjectUtil.isEmpty(user.getPassword())) {
            throw new CustomException("密码不能为空");
        }
        if (ObjectUtil.isEmpty(user.getName())) {
            user.setName(user.getUsername());
        }
        user.setRole("USER");
        // 密码 BCrypt 哈希入库，禁止明文存储
        user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        userMapper.insert(user);
        return true;
    }

    /**
     * 删除
     * @return 
     */
    @Override
    public boolean removeById(Integer id) {
        userMapper.removeById(id);
        return true;
    }

    /**
     * 修改
     * 密码改走 /updatePassword 专用接口；此处若携带密码，必须 BCrypt 哈希后入库，禁止明文落库；
     * 未携带（null 或空串）时置 null，XML 会跳过该字段，保留库中原密码。
     */
    @Override
    public boolean updateById(User user) {
        User dbUser = userMapper.selectById(user.getId());
        if (ObjectUtil.isNull(dbUser)) {
            throw new CustomException("用户不存在");
        }
        // 先判断本次是否携带了密码（供后面决定是否踢人），再决定落库值
        boolean passwordChanged = ObjectUtil.isNotEmpty(user.getPassword());
        user.setPassword(passwordChanged ? PASSWORD_ENCODER.encode(user.getPassword()) : null);
        boolean success = userMapper.updateById(user) > 0;
        // 管理员通过本接口重置了该用户密码 → 注销该账号全部会话，防止旧会话继续有效
        if (success && passwordChanged) {
            StpUtil.logout("USER:" + user.getId());
        }
        return success;
    }

    /**
     * 根据ID查询
     * @return 
     */
    @Override
    public User selectById(Integer id) {
        return userMapper.selectById(id);
    }

    /**
     * 查询所有
     */
    @Override
    public List<User> list(User user) {
        return userMapper.list(user);
    }

    /**
     * 分页查询
     */
    @Override
    public PageInfo<User> selectPage(User user, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = userMapper.list(user);
        return PageInfo.of(list);
    }

    /**
     * 登录
     */
    @Override
    public Account login(Account account) {
        Account dbUser = userMapper.selectByUsername(account.getUsername());
        // 用户不存在和密码错误统一返回相同消息，避免账号枚举
        if (ObjectUtil.isNull(dbUser)) {
            throw new CustomException("账号或密码错误");
        }
        if (!PASSWORD_ENCODER.matches(account.getPassword(), dbUser.getPassword())) {
            throw new CustomException("账号或密码错误");
        }
        return dbUser;
    }

    /**
     * 修改密码
     */
    @Override
    public void updatePassword(Account account) {
        User dbUser = userMapper.selectByUsername(account.getUsername());
        if (ObjectUtil.isNull(dbUser)) {
            throw new CustomException("原密码错误");
        }
        if (!PASSWORD_ENCODER.matches(account.getPassword(), dbUser.getPassword())) {
            throw new CustomException("原密码错误");
        }
        if (ObjectUtil.isEmpty(account.getNewPassword())) {
            throw new CustomException("新密码不能为空");
        }
        // 新密码 BCrypt 哈希后入库
        dbUser.setPassword(PASSWORD_ENCODER.encode(account.getNewPassword()));
        userMapper.updateById(dbUser);
    }

}