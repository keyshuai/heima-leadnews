package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.common.redis.RedisCacheService;
import com.heima.model.common.constants.ScheduleConstants;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

@Slf4j
@Service
@Transactional //交给spring管理
public class TaskServiceImpl implements TaskService {


    @Autowired
    private RedisCacheService cacheService;
    @Override
    public long addTask(Task task) {
        //1.添加任务到数据库中
        boolean success=addTaskToDb(task);

        if (success){
            //2.添加任务到redis
            addTaskToCache(task);
        }

        return task.getTaskId();
    }

    /**
     * 把任务添加到redis中
     *
     * @param task
     */
    private void addTaskToCache(Task task) {

        String key=task.getTaskType()+"_"+task.getPriority();
        //获取5分钟毫秒值
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.MINUTE,5);
        long time = instance.getTimeInMillis();

        //如果任务时间小于等于当前时间存入list
        if (task.getExecuteTime()<=System.currentTimeMillis()){
            cacheService.lLeftPush(ScheduleConstants.TOPIC+key, JSON.toJSONString(task));
        }
        if (task.getExecuteTime()<=time){
            cacheService.zAdd(ScheduleConstants.FUTURE+key,JSON.toJSONString(task),task.getExecuteTime());
        }

    }

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    /**
     * 添加任务到数据库中
     *
     * @param task
     * @return
     */
    private boolean addTaskToDb(Task task) {
        boolean flag=false;

        try {
            //保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task,taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            //设置taskID
            task.setTaskId(taskinfo.getTaskId());

            //保存任务日志
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo,taskinfoLogs);
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);
            flag=true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 取消任务
     * @param taskId
     * @return
     */
    @Override
    public boolean cancelTask(long taskId) {

        boolean flag=false;
        //删除任务,更新日志
        Task task =updateDb(taskId,ScheduleConstants.EXECUTED);

        //删除redis中的数据
        if (task!=null){
            removeTaskFromCache(task);
            flag=true;
        }
        return false;
    }



    /**
     * 删除redis中的任务数据
     * @param task
     */
    private void removeTaskFromCache(Task task) {

        String key=task.getTaskType()+"_"+task.getPriority();

        if (task.getExecuteTime()<=System.currentTimeMillis()){
            cacheService.lRemove(ScheduleConstants.TOPIC+key,0,JSON.toJSONString(task));
        }else {
            cacheService.zRemove(ScheduleConstants.TOPIC+key,JSON.toJSONString(task));
        }



    }

    /**
     * 删除任务，更新任务日志状态
     * @param taskId
     * @param status
     * @return
     */

    private Task updateDb(long taskId, int status) {
        Task task =null;

        try {
            //删除任务
            taskinfoMapper.deleteById(taskId);

            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task=new Task();
            BeanUtils.copyProperties(taskinfoLogs,task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        } catch (BeansException e) {
            log.info("task cancel exception taskid={}",taskId);
        }

        return task;
    }


    /**
     * 按照类型和优先级拉取任务
     * @return
     */

    @Override
    public Task poll(int type, int priority) {
        Task task=null;

        try {
            String key= type+"_"+priority;
            String task_json=cacheService.lRightPop(ScheduleConstants.TOPIC+key);
            if (StringUtils.isNotBlank(task_json)){
                task=JSON.parseObject(task_json,Task.class);
                //更新数据
                updateDb(task.getTaskId(),ScheduleConstants.EXECUTED);
            }
        } catch (Exception e) {
            log.error("poll task exception");
        }

        return task;
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh(){

        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if(StringUtils.isNotBlank(token)){
            log.info("未来数据定时刷新---定时任务");

            //获取所有未来数据的集合key
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            for (String futureKey : futureKeys) {//future_100_50

                //获取当前数据的key  topic
                String topicKey = ScheduleConstants.TOPIC+futureKey.split(ScheduleConstants.FUTURE)[1];

                //按照key和分值查询符合条件的数据
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());

                //同步数据
                if(!tasks.isEmpty()){
                    cacheService.refreshWithPipeline(futureKey,topicKey,tasks);
                    log.info("成功的将"+futureKey+"刷新到了"+topicKey);
                }
            }
        }
    }

    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    public void reloadData(){
        clearCale();
        log.info("数据库数据同步到缓存");


    }

    private void clearCale() {
    }

}






















