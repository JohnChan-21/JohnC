package com.example.service.impl;

import cn.hutool.core.util.ObjectUtil;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.Account;
import com.example.entity.Admin;
import com.example.exception.CustomException;
import com.example.mapper.AdminMapper;
import com.example.service.AdminService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员业务处理实现类
 */
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {

    @Resource
    private AdminMapper adminMapper;

    /** BCrypt 单例：线程安全，避免重复 new */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 新增
     * @return 
     */
    @Override
    public boolean save(Admin admin) {
        Admin dbAdmin = adminMapper.selectByUsername(admin.getUsername());
        if (ObjectUtil.isNotNull(dbAdmin)) {
            throw new CustomException("管理员账号已存在");
        }
        if (ObjectUtil.isEmpty(admin.getPassword())) {
            throw new CustomException("密码不能为空");
        }
        if (ObjectUtil.isEmpty(admin.getName())) {
            admin.setName(admin.getUsername());
        }
        admin.setRole("ADMIN");
        // 密码 BCrypt 哈希入库，禁止明文存储
        admin.setPassword(PASSWORD_ENCODER.encode(admin.getPassword()));
        adminMapper.insert(admin);
        return true;
    }

    /**
     * 查询所有
     */
    @Override
    public List<Admin> list(Admin admin) {
        return adminMapper.list(admin);
    }

    /**
     * 根据ID查询
     */
    @Override
    public Admin selectById(Integer id) {
        return adminMapper.selectById(id);
    }

    /**
     * 分页查询
     */
    @Override
    public PageInfo<Admin> selectPage(Admin admin, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Admin> list = adminMapper.list(admin);
        return PageInfo.of(list);
    }

    /**
     * 登录
     */
    @Override
    public Account login(Account account) {
        Account dbAdmin = adminMapper.selectByUsername(account.getUsername());
        // 用户不存在和密码错误统一返回相同消息，避免账号枚举
        if (ObjectUtil.isNull(dbAdmin)) {
            throw new CustomException("账号或密码错误");
        }
        if (!PASSWORD_ENCODER.matches(account.getPassword(), dbAdmin.getPassword())) {
            throw new CustomException("账号或密码错误");
        }
        return dbAdmin;
    }

    /**
     * 更新
     * 密码改走 /updatePassword 专用接口；此处若携带密码，必须 BCrypt 哈希后入库，禁止明文落库；
     * 未携带（null 或空串）时置 null，XML 会跳过该字段，保留库中原密码。
     * @return 
     */
    @Override
    public boolean updateById(Admin admin) {
        Admin dbAdmin = adminMapper.selectById(admin.getId());
        if (ObjectUtil.isNull(dbAdmin)) {
            throw new CustomException("管理员不存在");
        }
        // 先判断本次是否携带了密码（供后面决定是否踢人），再决定落库值
        boolean passwordChanged = ObjectUtil.isNotEmpty(admin.getPassword());
        admin.setPassword(passwordChanged ? PASSWORD_ENCODER.encode(admin.getPassword()) : null);
        boolean success = adminMapper.updateById(admin) > 0;
        // 通过本接口重置了该管理员密码 → 注销该账号全部会话，防止旧会话继续有效
        if (success && passwordChanged) {
            StpUtil.logout("ADMIN:" + admin.getId());
        }
        return success;
    }

    /**
     * 修改密码
     */
    @Override
    public void updatePassword(Account account) {
        Admin dbAdmin = adminMapper.selectByUsername(account.getUsername());
        if (ObjectUtil.isNull(dbAdmin)) {
            throw new CustomException("原密码错误");
        }
        if (!PASSWORD_ENCODER.matches(account.getPassword(), dbAdmin.getPassword())) {
            throw new CustomException("原密码错误");
        }
        if (ObjectUtil.isEmpty(account.getNewPassword())) {
            throw new CustomException("新密码不能为空");
        }
        // 新密码 BCrypt 哈希后入库
        dbAdmin.setPassword(PASSWORD_ENCODER.encode(account.getNewPassword()));
        adminMapper.updateById(dbAdmin);
    }

}
