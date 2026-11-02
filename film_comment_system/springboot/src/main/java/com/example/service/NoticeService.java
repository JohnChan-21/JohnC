package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.Notice;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface NoticeService extends IService<Notice> {

    /**
     * 查询所有
     */
    List<Notice> list(Notice notice);
    
    /**
     * 分页查询
     */
    PageInfo<Notice> selectPage(Notice notice, Integer pageNum, Integer pageSize);

    /**
     * 删除
     */
    boolean removeById(Integer id);

}
