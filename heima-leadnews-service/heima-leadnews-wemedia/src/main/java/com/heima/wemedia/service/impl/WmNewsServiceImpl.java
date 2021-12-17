package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    //查询文章
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {
        //1.参数校验
        dto.checkParam();
        //2.条件判断查询
        IPage page =new Page(dto.getPage(),dto.getSize());
        LambdaQueryWrapper<WmNews> querywrapper= Wrappers.lambdaQuery();
        //2.1状态判断

        if(dto.getStatus()!=null  ){
            querywrapper.eq(WmNews::getStatus,dto.getStatus());
        }
        //2.2 关键字模糊
        if(dto.getKeyword()!=null){
            querywrapper.like(WmNews::getTitle,dto.getKeyword());
        }
        //2.3 频道
        if(dto.getChannelId()!=null){
            querywrapper.eq(WmNews::getChannelId,dto.getChannelId());
        }
        //2.4 发布时间
        if(dto.getBeginPubdate()!=null && dto.getEndPubdate()!=null){
            querywrapper.between(WmNews::getPublishTime,dto.getBeginPubdate(),dto.getEndPubdate());
        }

        //2.5倒序展示
        querywrapper.orderByDesc(WmNews::getPublishTime);

        //2.6 用户id
        querywrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());


        //3.分页操作
        page=page(page,querywrapper);

        //4.返回数据
        ResponseResult result=new PageResponseResult(dto.getPage(),dto.getSize(), (int) page.getTotal());
        result.setData(page.getRecords());
        return result;
    }

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //判断内容是否为空
        if (dto.getContent()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        if (dto.getType()==-1){
            dto.setType(null);
        }
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto,wmNews);
        //封装images
        List<String> images = dto.getImages();
        //转成字符串
        if (images!=null&&images.size()>0){
            //利用工具把集合转成字符串,并用逗号分割
            String img = StringUtils.join(images, ",");
            wmNews.setImages(img);
        }
        //3.执行修改或者保存
        saveorUpdateNews(wmNews);
        //如果是草稿
        if (dto.getStatus()==0){
            //返回
            return ResponseResult.okResult(null);
        }
        //关联wm_news_marterial表 把内容中的图片抽出来
        List imageList= chouquImageList(wmNews);

        //4.1 内容中图片与文章的关系
        contentImageToNews(wmNews,imageList, (short) 0);

        //4.2 封面图片与文章的关系
        fengmianImageToNews(wmNews,dto,imageList,(short)1);

        //审核文章
        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
    /**
     * 抽取的第四个方法，关联封面图片与文章的关系，操作中间表
     *  如果封面类型是自动的话，从文章内容中图片进行操作
     *      如果文章内容图片个数大于1，小于3的话，则认为单图
     *      如果文章内容图片个数大于等于3的话，则认为多图
     *       如果文章内容图片个数，则认为是无图
     *
     * @param wmNews
     * @param type
     */
    private void fengmianImageToNews(WmNews wmNews,WmNewsDto dto,List imageList, short type) {
        //1.获取封面图片
        List<String> images = dto.getImages();

        //自动
        if(dto.getType()==null){
            if(imageList.size()>=1 && imageList.size()<3){
                //取出第一张图片作为封面
                images= (List<String>) imageList.stream().limit(1).collect(Collectors.toList());
                //单图设置类型
                wmNews.setType((short) 1);
            }else if(imageList.size()>=3){
                images= (List<String>) imageList.stream().limit(3).collect(Collectors.toList());
                //多图设置类型
                wmNews.setType((short) 3);
            }else{
                wmNews.setType((short) 0);
            }

            wmNews.setImages(StringUtils.join(images, ","));
            updateById(wmNews);
        }
        //否则，直接插入到中间表中
        saveNewsMaterial(wmNews, type, images);
    }

    /**
     * 第五个抽取方法
     * @param wmNews
     * @param type
     * @param images
     */
    private void saveNewsMaterial(WmNews wmNews, short type, List<String> images) {
        //1.判断imageList是否有值
        if (images != null && images.size() > 0) {
            //根据地址集合去查询素材表
            List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, images));
            //2.要从素材集合中只抽取出相对应的id集合
            List<Integer> materialIds = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

            /**
             * 参数1表示素材id集合
             * 参数2表示文章id
             * 参数3表示引用类型
             */
            wmNewsMaterialMapper.saveRelations(materialIds, wmNews.getId(), type);
        }
    }

    /**
     * 第三个抽取方法，关联内容中的图片与文章的关系，操作中间表
     * @param wmNews 文章实体
     * @param imageList  内容中抽取的图片集合,图片地址集合
     * @param type  引用类型，0表示内容引用 1表示封面引用
     */
    private void contentImageToNews(WmNews wmNews, List imageList, Short type) {

        saveNewsMaterial(wmNews, type, imageList);
    }

    /**
     * 第二个抽取方法，为了从文章内容中抽取图片集合
     * @param wmNews
     * @return
     */
    private List chouquImageList(WmNews wmNews) {

        List imageList=new ArrayList();

        //1.获取内容
        String content = wmNews.getContent();
        //2.转化list类型
        List<Map> list = JSON.parseArray(content, Map.class);
        for (Map map : list) {
            if(map.get("type").equals("image")){
                String value = (String) map.get("value");
                imageList.add(value);
            }
        }
        return imageList;

    }

    /**
     * 第一个抽取方法，修改或者保存
     * @param wmNews
     */
    private void saveorUpdateNews(WmNews wmNews) {
        //补全属性
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        //1.判断是否是包含id
        if(wmNews.getId()!=null){
            //修改业务
            //删除中间表
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery()
                    .eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            //修改文章表
            updateById(wmNews);
        }else{
            //新增
            save(wmNews);
        }
    }
}