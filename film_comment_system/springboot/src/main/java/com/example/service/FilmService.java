package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.Film;
import com.github.pagehelper.PageInfo;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public interface FilmService extends IService<Film> {
    /**
     * 查询所有
     */
    List<Film> list(Film film);
    /**
     * 分页查询
     */
    PageInfo<Film> selectPage(Film film, Integer pageNum, Integer pageSize);
    /**
     * 查询排行榜
     */
    List<Film> selectRanking();
    /**
     * 查询随机推荐电影
     */
    List<Film> selectRecommend(Integer id);
    /**
     * 根据ID查询电影详情（带 Redis 缓存）
     * 防缓存穿透双层防护：布隆过滤器 + 缓存空对象
     */
    Film selectById(Integer id);
}
