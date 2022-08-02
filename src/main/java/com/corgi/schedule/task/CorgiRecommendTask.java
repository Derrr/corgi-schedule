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
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private StringRedisTemplate redisTemplate;
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
    //@Scheduled(cron = "0 0 4 * * *")
    @Scheduled(fixedRate = 24 * 3600 * 1000)
    public void runUser() {
        log.info("refreshing user...........");
        List<Point> points = redisTemplate.opsForGeo().position("user", "42746");
        log.info("point:{}",points);
        redisTemplate.opsForGeo().remove("user", "42746");
        points = redisTemplate.opsForGeo().position("user", "42746");
        log.info("point:{}",points);
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius("user", new Circle(new Point(116.410145, 39.966783), new Distance(10, Metrics.KILOMETERS)), RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().limit(2).sortAscending());
        log.info("geo:{}",geoResults.getContent());

//        List<UserPosition> userPositionList;
//        int page = 1;
//        int pageSize = 5000;
//        do {
//            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
//            page++;
//            //log.info("page ={}, size={} ", page, userPositionList.size());
//            Long threshold = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L;
//            if (userPositionList != null) {
//                for (UserPosition userPosition : userPositionList) {
//                    //log.info("checking ... " + userPosition.getUserId() + " page = " + page);
//                    List<Point> points = redisTemplate.opsForGeo().position("user", userPosition.getUserId());
//                    if (CollectionUtils.isEmpty(points)) {
//                        continue;
//                    }
//                    redisTemplate.opsForGeo().remove("user", userPosition.getUserId());
//                    if (userPosition.getLng() == null || userPosition.getLng() > 180 || userPosition.getLng() < -180) {
//                        continue;
//                    }
//                    if (userPosition.getLat() == null || userPosition.getLat() > 90 || userPosition.getLat() < -90) {
//                        continue;
//                    }
//                    if (StringUtils.isEmpty(userPosition.getUserId())) {
//                        continue;
//                    }
//                    if (userPosition.getUptime() == null) {
//                        continue;
//                    }
//                    UserDetail detail = corgiUserService.getUserDetailBasic(userPosition.getUserId());
//                    if (detail == null) {
//                        continue;
//                    }
//                    if (!"influencer".equals(detail.getAvatarStatus())) {
//                        String expire = corgiUserService.getUserVipExpire(userPosition.getUserId());
//                        if (StringUtils.isEmpty(expire) || "-".equals(expire)) {
//                            if (userPosition.getUptime() < threshold) {
//                                continue;
//                            }
//                        }
//                    }
//                    log.info("checkinginto ... " + userPosition.getUserId());
//                    redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
//                }
//            }
//        } while (!CollectionUtils.isEmpty(userPositionList));
//        log.info("end refreshing user...........");
    }


    @Async
    @Scheduled(cron = "0 0 5 * * *")
    public void runInfluencer() {
        log.info("refreshing Influencer...........");
        corgiUserRecommendService.initInfluencer();
    }
}
