package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.Film;
import com.example.mapper.CommentMapper;
import com.example.mapper.FilmMapper;
import com.example.service.FilmService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.hash.BloomFilter;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FilmServiceImpl extends ServiceImpl<FilmMapper, Film> implements FilmService {

    @Resource
    private FilmMapper filmMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private BloomFilter<Integer> filmIdBloomFilter;

    private static final String CACHE_FILM_DETAIL = "film:detail:";
    private static final String CACHE_FILM_RANKING = "film:ranking";
    private static final String CACHE_FILM_RECOMMEND = "film:recommend:";
    private static final String CACHE_FILM_SCORE = "film:score:";

    /**
     * 新增电影
     * @return 是否新增成功
     */
    @Override
    public boolean save(Film film) {
        filmMapper.save(film);
        // 同步加入布隆过滤器，保证后续可被查询命中
        filmIdBloomFilter.put(film.getId());
        // 清除排行榜缓存
        redisTemplate.delete(CACHE_FILM_RANKING);
        return true;
    }

    /**
     * 更新电影
     * @return 是否更新成功
     */
    @Override
    public boolean updateById(Film film) {
        filmMapper.updateById(film);
        // 清除该电影的缓存
        redisTemplate.delete(CACHE_FILM_DETAIL + film.getId());
        redisTemplate.delete(CACHE_FILM_RANKING);
        redisTemplate.delete(CACHE_FILM_RECOMMEND + film.getId());
        return true;
    }

    /**
     * 删除
     */
    public boolean removeById(Integer id) {
        filmMapper.removeById(id);
        // 清除该电影的所有缓存
        redisTemplate.delete(CACHE_FILM_DETAIL + id);
        redisTemplate.delete(CACHE_FILM_RANKING);
        redisTemplate.delete(CACHE_FILM_RECOMMEND + id);
        redisTemplate.delete(CACHE_FILM_SCORE + id);
        return true;
    }

    /**
     * 查询所有
     */
    @Override
    public List<Film> list(Film film) {
        return filmMapper.list(film);
    }

    /**
     * 分页查询
     */
    @Override
    public PageInfo<Film> selectPage(Film film, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Film> list = filmMapper.list(film);
        for (Film f : list) {
            this.setScore(f);
        }
        return PageInfo.of(list);
    }

    /**
     * 根据ID查询电影详情（带 Redis 缓存）
     * 防缓存穿透双层防护：布隆过滤器 + 缓存空对象
     */
    @Override
    public Film selectById(Integer id) {
        // 1. 布隆过滤器前置拦截：ID 一定不存在则直接返回，避免穿透到缓存/数据库
        if (!filmIdBloomFilter.mightContain(id)) {
            return null;
        }
        // 2. 先查 Redis 缓存
        String cacheKey = CACHE_FILM_DETAIL + id;
        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (SerializationException e) {
            cached = null;
        }
        // 命中真实电影对象才返回；不能用 (Film) 强转，否则命中空对象标记时会抛 ClassCastException
        if (cached instanceof Film cachedFilm) {
            return cachedFilm;
        }
        // 命中空对象标记（"_EMPTY_"）：该电影此前查无（多为已删除或布隆误判），直接返回 null
        if ("_EMPTY_".equals(cached)) {
            return null;
        }
        // 3. 缓存未命中，查询数据库
        Film film = filmMapper.selectById(id);
        if (film != null) {
            this.setScore(film);
            // 4. 写入 Redis 缓存，过期时间 30 分钟 + 随机 0~10 分钟
            int baseSecond = 30 * 60;
            int randomSecond = new Random().nextInt(10 * 60);
            int realTTL = baseSecond + randomSecond;
            redisTemplate.opsForValue().set(cacheKey, film, realTTL, TimeUnit.SECONDS);
        }else{
            // 5. 缓存空对象，防缓存穿透，短过期时间（兜底：布隆误判时仍可拦截）
            redisTemplate.opsForValue().set(cacheKey, "_EMPTY_", 5, TimeUnit.MINUTES);
        }
        return film;
    }

    /**
     * 随机推荐电影（带 Redis 缓存，5 分钟过期保证随机性）
     */
    @Override
    public List<Film> selectRecommend(Integer filmId) {
        // 1. 先查 Redis 缓存
        String cacheKey = CACHE_FILM_RECOMMEND + filmId;
        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (SerializationException e) {
            cached = null;
        }
        if (cached != null) {
            return (List<Film>) cached;
        }
        // 2. 缓存未命中，查询数据库
        List<Film> films = filmMapper.list(null);
        films = films.stream().filter(film -> !film.getId().equals(filmId)).collect(Collectors.toList());
        Collections.shuffle(films);
        List<Film> filmList = films.stream().limit(3).collect(Collectors.toList());
        for (Film film : filmList) {
            this.setScore(film);
        }
        // 3. 写入 Redis 缓存，过期时间 5 分钟 + 随机 0~10 分钟
        int baseSecond = 5 * 60;
        int randomSecond = new Random().nextInt(10 * 60);
        int realTTL = baseSecond + randomSecond;
        redisTemplate.opsForValue().set(cacheKey, filmList, realTTL, TimeUnit.SECONDS);
        return filmList;
    }

    /**
     * 设置电影评分和评论数（带 Redis 缓存）
     */
    private void setScore(Film film) {
        // 1. 先从缓存读取评分数据，捕获序列化异常
        if (film == null || film.getId() == null) {
            return;
        }
        String scoreKey = CACHE_FILM_SCORE + film.getId();
        Object cachedScore = null;
        try {
            cachedScore = redisTemplate.opsForValue().get(scoreKey);
        } catch (SerializationException e) {
            cachedScore = null;
        }
        if (cachedScore instanceof Object[] arr) {
            film.setCommentNum((Integer) arr[0]);
            film.setScore((Double) arr[1]);
            return;
        }
        // 2. 缓存未命中，查询数据库，查询评论数和评分
        int total = commentMapper.selectTotal(film.getId());
        film.setCommentNum(total);
        if (total == 0) {
            film.setScore(0D);
        } else {
            double sum = commentMapper.selectSum(film.getId());
            BigDecimal score = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            film.setScore(score.doubleValue());
        }
        // 3. 缓存评分数据，过期时间 10 分钟 + 随机 0~10 分钟
        int baseSecond = 10 * 60;
        int randomSecond = new Random().nextInt(10 * 60);
        int realTTL = baseSecond + randomSecond;
        redisTemplate.opsForValue().set(scoreKey, new Object[]{total, film.getScore()}, realTTL, TimeUnit.SECONDS);
    }

    /**
     * 排行前5的电影数据（带 Redis 缓存，30 分钟过期）
     */
    @Override
    public List<Film> selectRanking() {
        // 1. 先查Redis缓存，捕获序列化异常
        String cacheKey = CACHE_FILM_RANKING;
        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (SerializationException e) {
            // Redis脏数据、格式不兼容，直接置空走数据库刷新
            cached = null;
        }
        if (cached != null) {
            return (List<Film>) cached;
        }
        // 2. 缓存未命中，查询数据库，查询所有电影，设置评分和评论数
        List<Film> filmList = filmMapper.list(null);
        for (Film film : filmList) {
            setScore(film);
        }
        List<Film> ranking = filmList.stream()
                .sorted((f1, f2) -> f2.getScore().compareTo(f1.getScore()))
                .limit(300)
                .toList();
        // 3. 写入缓存，过期时间 30 分钟 + 随机 0~10 分钟
        int baseSecond = 30 * 60;
        int randomSecond = new Random().nextInt(10 * 60);
        int realTTL = baseSecond + randomSecond;
        redisTemplate.opsForValue().set(cacheKey, ranking, realTTL, TimeUnit.SECONDS);
        return ranking;
    }

}
