package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.Account;
import com.example.entity.User;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务处理
 **/
@Service
public interface UserService extends IService<User> {
    /**
     * 新增
     */
    boolean save(User user);
    /**
     * 删除
     */
    boolean removeById(Integer id);
    /**
     * 修改
     */
    boolean updateById(User user);
    /**
     * 根据ID查询
     */
    User selectById(Integer id);
    /**
     * 查询所有
     */
    List<User> list(User user);
    /**
     * 分页查询
     */
    PageInfo<User> selectPage(User user, Integer pageNum, Integer pageSize);
    /**
     * 登录
     */
    Account login(Account account);
    /**
     * 修改密码
     */
    void updatePassword(Account account);

    
}