package com.example.controller;

import com.example.common.Result;
import com.example.entity.Category;
import com.example.service.CategoryService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    /**
     * 新增
     */
    @PostMapping("/add")
    public Result save(@RequestBody Category category) {
        categoryService.save(category);
        return Result.success();
    }

    /**
     * 更新
     */
    @PutMapping("/update")
    public Result updateById(@RequestBody Category category) {
        categoryService.updateById(category);
        return Result.success();
    }

    /**
     * 删除
     */
    @DeleteMapping("/delete/{id}")
    public Result removeById(@PathVariable Integer id) {
        categoryService.removeById(id);
        return Result.success();
    }

    /**
     * 查询所有
     */
    @GetMapping("/selectAll")
    public Result list(Category category) {
        List<Category> list = categoryService.list();
        return Result.success(list);
    }

    /**
     * 分页模糊查询
     */
    @GetMapping("/selectPage")
    public Result selectPage(Category category,
                             @RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageInfo<Category> pageInfo = categoryService.selectPage(category, pageNum, pageSize);
        return Result.success(pageInfo);
    }

}
