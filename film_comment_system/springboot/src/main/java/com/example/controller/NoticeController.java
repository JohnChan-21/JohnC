package com.example.controller;

import com.example.common.Result;
import com.example.entity.Notice;
import com.example.service.NoticeService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    /**
     * 新增
     */
    @PostMapping("/add")
    public Result save(@RequestBody Notice notice) {
        noticeService.save(notice);
        return Result.success();
    }

    /**
     * 更新
     */
    @PutMapping("/update")
    public Result updateById(@RequestBody Notice notice) {
        noticeService.updateById(notice);
        return Result.success();
    }

    /**
     * 删除
     */
    @DeleteMapping("/delete/{id}")
    public Result removeById(@PathVariable Integer id) {
        noticeService.removeById(id);
        return Result.success();
    }

    /**
     * 查询所有
     */
    @GetMapping("/selectAll")
    public Result list(Notice notice) {
        List<Notice> list = noticeService.list(notice);
        return Result.success(list);
    }

    /**
     * 分页模糊查询
     */
    @GetMapping("/selectPage")
    public Result selectPage(Notice notice,
                             @RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageInfo<Notice> pageInfo = noticeService.selectPage(notice, pageNum, pageSize);
        return Result.success(pageInfo);
    }

}
