package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.mapper.ArticleHomeMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.article.service.ArticleHomeService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.pojos.WmNews;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle>  implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;
    /**
     * 保存和修改文章内容+文章配置+文章信息
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {
        if(dto.getContent()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApArticle apArticle=null;
        //如果没有id,则表示新增
        if(dto.getId()==null){
            //1.保存文章信息表
            apArticle=new ApArticle();
            BeanUtils.copyProperties(dto,apArticle);
            apArticleMapper.insert(apArticle);
            //2.保存文章配置
            ApArticleConfig apArticleConfig=new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            //3.保存文章内容
            ApArticleContent apArticleContent=new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }else{//修改文章+内容
            //1.先根据id去查询文章，在修改
            apArticle = apArticleMapper.selectById(dto.getId());
            if(apArticle!=null){
                apArticleMapper.updateById(apArticle);
                //先查文章内容，在修改
                ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery()
                        .eq(ApArticleContent::getArticleId, apArticle.getId()));
                apArticleContent.setContent(dto.getContent());
                apArticleContentMapper.updateById(apArticleContent);
            }
        }
        //异步调用 生成静态文件上传到minio中
        articleFreemarkerService.buildArticleToMinIO(apArticle,dto.getContent());
        //4.返回文章id
        ResponseResult responseResult=ResponseResult.okResult(apArticle==null?"":apArticle.getId());
        return responseResult;
    }


}




