package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Notice;

import java.util.List;

public interface NoticeMapper extends BaseMapper<Notice> {

    void save(Notice notice);

    int updateById(Notice notice);

    void removeById(Integer id);

    List<Notice> list(Notice notice);

}
