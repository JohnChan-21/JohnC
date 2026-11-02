package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Comment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    /**
     * 新增
     */
    void save(Comment comment);

    /**
     * 修改
     * 注意：必须在此显式声明。若省略，会解析到 BaseMapper.updateById(@Param("et") T entity)，
     * 参数被包装成 Map（[et, param1]），与 CommentMapper.xml 中的裸属性名 #{filmId} 不匹配，
     * 运行期将抛 BindingException。
     */
    int updateById(Comment comment);

    /**
     * 删除
     */
    int removeById(Integer id);

    /**
     * 分页模糊查询
     */
    List<Comment> list(Comment comment);

    @Select("select count(*) from comment where film_id = #{filmId}")
    int selectTotal(Integer filmId);

    @Select("select sum(score) from comment where film_id = #{filmId}")
    double selectSum(Integer filmId);

}
