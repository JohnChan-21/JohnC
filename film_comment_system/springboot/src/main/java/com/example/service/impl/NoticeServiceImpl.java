package com.example.service.impl;

import cn.hutool.core.date.DateUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.Notice;
import com.example.mapper.NoticeMapper;
import com.example.service.NoticeService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService  {

    @Resource
    private NoticeMapper noticeMapper;

    /**
     * 新增
     * @return 是否新增成功
     */
    @Override
    public boolean save(Notice notice) {
        notice.setTime(DateUtil.now());  // 获取当前的最新的时间的字符串  设置到notice对象中去
        noticeMapper.save(notice);
        return true;
    }

    /**
     * 更新
     */
    @Override
    public boolean updateById(Notice notice) {
        noticeMapper.updateById(notice);
        return true;
    }

    /**
     * 删除
     */
    @Override
    public boolean removeById(Integer id) {
        noticeMapper.removeById(id);
        return true;
    }

    /**
     * 查询所有（按标题模糊查询）
     */
    public List<Notice> list(Notice notice) {
        return noticeMapper.list(notice);
    }

    /**
     * 分页查询（按标题模糊查询）
     */
    public PageInfo<Notice> selectPage(Notice notice, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Notice> list = noticeMapper.list(notice);
        return PageInfo.of(list);
    }

}
