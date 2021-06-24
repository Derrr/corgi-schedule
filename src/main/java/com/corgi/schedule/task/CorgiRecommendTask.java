package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiRecommendTask {
    @Reference(retries = 1, timeout = 100000)
    private CorgiUserRecommendService corgiUserRecommendService;
    @Autowired
    private MQService mqService;
    @Autowired
    private TaskService taskService;


    @Async
    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        log.info("refreshing recommend...........");
        taskService.calculateRecommend();
        taskService.calculateRecommendActivity();
        taskService.clearFeed();
    }

    @Async
    @Scheduled(cron = "0 0 5 * * *")
    public void runInfluencer() {
        log.info("refreshing Influencer...........");
        corgiUserRecommendService.initInfluencer();
    }
}
