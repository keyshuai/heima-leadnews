package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import org.checkerframework.checker.units.qual.A;

public interface ArticleHomeService extends IService<ApArticle> {
    //1为加载更多  2为加载最新
    ResponseResult load (Short type , ArticleHomeDto dto);
}
