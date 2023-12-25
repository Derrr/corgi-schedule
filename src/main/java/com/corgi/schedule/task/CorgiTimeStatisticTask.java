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
    private static List<String> groupOrder = Arrays.asList("匀称", "肉壮", "肌肉", "偏胖", "精壮", "偏瘦");

    @Async
    @Scheduled(cron = "0 0 0/4 * * *")
    //@Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runPreferGroup() {
        log.info("into prefer group.....");
        int page = 1;
//        HashMap<String, Double> result = corgiUserRecommendService.getGroupCor("all");
//        if (!CollectionUtils.isEmpty(result)) {
//            Double total = result.values().stream().reduce((m, n) -> m + n).get();
//            Double totalCount = corgiUserRecommendService.getGroupWeight(0, "");
//            if (total != 0) {
//                for (String key : groupOrder) {
//                    Double value = result.get(key) == null ? 0.0 : result.get(key) / total;
//                    Integer groupCount = (int) (Math.round(value * totalCount));
//                    Double weight = corgiUserRecommendService.getGroupWeight(groupCount, key);
//                    if (weight == null) {
//                        log.info("wrong weight:" + key);
//                        weight = 1.0;
//                    }
//                    redisTemplate.opsForHash().put("group_weight", key, weight + "");
//                    redisTemplate.opsForHash().put("group_count", key, groupCount + "");
//
//                    String incrementKey = "group_weight_" + key;
//                    redisTemplate.delete(incrementKey);
//                    redisTemplate.opsForValue().increment(incrementKey);
//                    redisTemplate.expire(incrementKey, 3l, TimeUnit.HOURS);
//                }
//            }
//            redisTemplate.expire("group_weight", 12l, TimeUnit.HOURS);
//            redisTemplate.expire("group_count", 12l, TimeUnit.HOURS);
//        }

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
    @Scheduled(cron = "0 0 2/4 * * *")
    //@Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runGroup() {
        int page = 1;
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
