package com.heima.article.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ArticleHomeMapper;
import com.heima.article.service.ArticleHomeService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.jsoup.helper.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * @Transactional 实质是使用了 JDBC 的事务来进行事务控制的
 * @Transactional 基于 Spring 的动态代理的机制
 * 就需要AOP拦截及事务的处理，可能影响系统性能。
 */
@Transactional
@Service
@Slf4j
public class ArticleHomeServiceImpl extends ServiceImpl<ArticleHomeMapper, ApArticle>implements ArticleHomeService {

    // 单页最大加载的数字
    private  final static short MAX_PAGE_SIZE = 50;

    @Autowired
    private ArticleHomeMapper articleHomeMapper;

    @Override
    public ResponseResult load(Short type, ArticleHomeDto dto) {
        //校验参数
        Integer size = dto.getSize();
        if (size ==null || size ==0){
            size =10;
        }
        //分页校验
        size =Math.min(size,MAX_PAGE_SIZE);
        dto.setSize(size);

        //类型校验-->type
        if (!type.equals(ArticleConstants.LOADTYPE_LOAD_MORE)&&!type.equals(ArticleConstants.LOADTYPE_LOAD_NEW)){
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }
        //频道校验  StringUtils.isEmpty 中间判断空格
        if (StringUtils.isEmpty(dto.getTag())){
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        //时间校验
        if (dto.getMaxBehotTime() ==null)dto.setMaxBehotTime(new Date());
        if (dto.getMinBehotTime()==null)dto.setMinBehotTime(new Date());


        //查询数据
        List<ApArticle> apArticles = articleHomeMapper.loadArticleList(dto, type);
        return ResponseResult.okResult(apArticles);
    }
}
