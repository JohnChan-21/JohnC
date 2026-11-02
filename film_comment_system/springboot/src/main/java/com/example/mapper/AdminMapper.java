package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Admin;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 操作admin相关数据接口
*/
public interface AdminMapper extends BaseMapper<Admin> {

    /**
      * 新增
    */
    int insert(Admin admin);

    /**
      * 修改
      * 必须声明为 Admin（而非 Account），才能重写 Mapper<Admin>#updateById，
      * 否则会变成重载，参数被 MyBatis 包成 ParamMap，XML 里的 #{username} 等裸属性名将无法解析
    */
    int updateById(Admin admin);

    /**
      * 查询所有
    */
    List<Admin> list(Admin admin);

    /**
      * 根据ID查询
    */
    Admin selectById(Integer id);

    /**
      * 根据用户名查询
    */
    @Select("select * from admin where username = #{username}")
    Admin selectByUsername(String username);
}