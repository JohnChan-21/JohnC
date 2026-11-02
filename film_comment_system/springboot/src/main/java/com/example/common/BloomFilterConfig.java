package com.example.common;

import com.example.mapper.FilmMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 布隆过滤器配置
 * 用于防止缓存穿透：查询前先判断 ID 是否「可能存在」，
 * 不存在则直接返回，避免无效请求打到 Redis / 数据库。
 * <p>
 * 容量 100 万、误判率 0.01%，实际电影数据量远小于此值，
 * 内存占用约 1.8MB，单机足够。
 */
@Configuration
public class BloomFilterConfig {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterConfig.class);

    /** 预期插入元素数量 */
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    /** 误判率（0.01%） */
    private static final double FPP = 0.0001;

    @Resource
    private FilmMapper filmMapper;

    @Bean
    public BloomFilter<Integer> filmIdBloomFilter() {
        Funnel<Integer> funnel = Funnels.integerFunnel();
        return BloomFilter.create(funnel, EXPECTED_INSERTIONS, FPP);
    }

    /**
     * 应用启动后预热：加载全部电影 ID 到布隆过滤器
     */
    @Bean
    public ApplicationRunner bloomFilterWarmupRunner(BloomFilter<Integer> filmIdBloomFilter) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                List<Integer> ids = filmMapper.selectAllIds();
                for (Integer id : ids) {
                    filmIdBloomFilter.put(id);
                }
                log.info("布隆过滤器预热完成，共加载 {} 条电影 ID", ids.size());
            }
        };
    }
}
