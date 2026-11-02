package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Category;
import com.github.pagehelper.PageInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 查询所有
    */
    List<Category> list(Category category);

    /**
     * 分页查询
    */
    PageInfo<Category> selectPage(Category category, Integer pageNum, Integer pageSize);
}
