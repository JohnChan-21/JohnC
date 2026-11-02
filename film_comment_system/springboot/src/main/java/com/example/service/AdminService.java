package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.Account;
import com.example.entity.Admin;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * 管理员业务处理接口
 */
public interface AdminService extends IService<Admin> {

    /**
     * 新增
     */
    boolean save(Admin admin);

    /**
     * 查询所有
     */
    List<Admin> list(Admin admin);

    /**
     * 根据ID查询
     */
    Admin selectById(Integer id);

    /**
     * 更新
     */
    boolean updateById(Admin admin);

    /**
     * 分页查询
     */
    PageInfo<Admin> selectPage(Admin admin, Integer pageNum, Integer pageSize);

    /**
     * 登录
     */
    Account login(Account account);

    /**
     * 修改密码
     */
    void updatePassword(Account account);

}