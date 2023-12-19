package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiTimeStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserRecommendService corgiUserRecommendService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");
    private static SimpleDateFormat hour_sdf = new SimpleDateFormat("HH");

    @Async
    //@Scheduled(cron = "0 0 0/4 * * *")
    //@Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runPreferGroup() {
        log.info("into prefer group.....");
        int page = 1;
        while (true) {
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
            log.info("into prefer group.....page" + page);
            if (CollectionUtils.isEmpty(userPositionList)) {
                break;
            }
            page++;
            for (UserPosition userPosition : userPositionList) {
                RecommendCalculater recommendCalculater = new RecommendCalculater();
                recommendCalculater.setUserId(userPosition.getUserId());
                mqService.sendPreferGroup(recommendCalculater);
            }
        }

    }

    @Async
    //@Scheduled(cron = "0 0 2/4 * * *")
    @Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runGroup() {
        int page = 1;
        HashMap<String, Double> result = corgiUserRecommendService.getGroupCor("all");
        if (!CollectionUtils.isEmpty(result)) {
            Double total = result.values().stream().reduce((m, n) -> m + n).get();
            if (total != 0) {
                for (String key : result.keySet()) {
                    String value = "1.0";
                    try {
                        value = Math.pow(result.get(key) / total, 0.8) + "";
                    } catch (Exception e) {
                        log.info(e.getMessage(), e);
                    }
                    log.info("group:" + key + " value:" + value);
                    redisTemplate.opsForHash().put("group_weight", key, value);
                }
            }
            redisTemplate.expire("group_weight", 12l, TimeUnit.HOURS);
        }
        while (true) {
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
            if (CollectionUtils.isEmpty(userPositionList)) {
                break;
            }
            page++;
            for (UserPosition userPosition : userPositionList) {
                RecommendCalculater recommendCalculater = new RecommendCalculater();
                recommendCalculater.setUserId(userPosition.getUserId());
                mqService.sendGroup(recommendCalculater);
            }
        }
    }

}
