package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.User;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 操作user相关数据接口
*/
public interface UserMapper extends BaseMapper<User> {

    /**
      * 新增
    */
    int insert(User user);

    /**
      * 删除
    */
    @Delete("delete from user where id = #{id}")
    int removeById(Integer id);

    /**
      * 修改
    */
    int updateById(User user);

    /**
      * 根据ID查询
    */
    @Select("select * from user where id = #{id}")
    User selectById(Integer id);

    /**
      * 查询所有
    */
    List<User> list(User user);

    @Select("select * from user where username = #{username}")
    User selectByUsername(String username);

}