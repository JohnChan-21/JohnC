package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.Category;
import com.github.pagehelper.PageInfo;

import org.springframework.stereotype.Service;

@Service
public interface CategoryService extends IService<Category> {

    /**
      * 分页查询
    */
    PageInfo<Category> selectPage(Category category, Integer pageNum, Integer pageSize);

}
