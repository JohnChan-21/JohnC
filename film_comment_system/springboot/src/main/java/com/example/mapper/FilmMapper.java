package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Film;

import java.util.List;

public interface FilmMapper extends BaseMapper<Film> {

    int save(Film film);

    int updateById(Film film);

    int removeById(Integer id);

    List<Film> list(Film film);

    Film selectById(Integer id);

    /**
     * 查询全部电影 ID，用于布隆过滤器预热
     */
    List<Integer> selectAllIds();
}
