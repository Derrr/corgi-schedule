package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiMatchService;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiFeedService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiFeed;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
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
import java.util.Date;
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
    @Reference
    private CorgiFeedService corgiFeedService;
    @Reference
    private CorgiMatchService corgiMatchService;
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
    @Scheduled(cron = "0 0 2 * * *")
//    @Scheduled(fixedRate = 24 * 3600 * 1000)
    public void runUser() {
        log.info("refreshing user...........");
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 5000;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            String nowDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            Long threshold = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L;
            Long threshold2 = System.currentTimeMillis() - 90 * 24 * 3600 * 1000L;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    if (userPosition.getUptime() == null) {
                        continue;
                    }
                    if (userPosition.getUptime() < threshold2) {
                        try {
                            Integer.valueOf(userPosition.getUserId());
                            CorgiFeed query = new CorgiFeed();
                            query.setUserId(userPosition.getUserId());
                            corgiFeedService.deleteFeed(query);
                        } catch (Exception e) {

                        }
                    }
                    List<Point> points = redisTemplate.opsForGeo().position("user", userPosition.getUserId());
                    if (CollectionUtils.isEmpty(points)) {
                        continue;
                    }
                    redisTemplate.opsForGeo().remove("user", userPosition.getUserId());
                    if (userPosition.getLng() == null || userPosition.getLng() > 180 || userPosition.getLng() < -180) {
                        continue;
                    }
                    if (userPosition.getLat() == null || userPosition.getLat() > 90 || userPosition.getLat() < -90) {
                        continue;
                    }
                    if (userPosition.getUptime() < threshold) {
                        UserDetail detail = corgiUserService.getUserDetailBasic(userPosition.getUserId());
                        if (detail == null) {
                            corgiMatchService.deleteUser(userPosition.getUserId());
                            continue;
                        }
                        if (!"influencer".equals(detail.getAvatarStatus())) {
                            String expire = corgiUserService.getUserVipExpire(userPosition.getUserId());
                            if (StringUtils.isEmpty(expire) || "-".equals(expire)) {
                                corgiMatchService.deleteUser(userPosition.getUserId());
                                continue;
                            }
                            if (expire.compareTo(nowDate) < 0) {
                                corgiMatchService.deleteUser(userPosition.getUserId());
                                continue;
                            }
                        }
                        corgiMatchService.updateUser(detail);
                    }
                    log.info("checkinginto ... " + userPosition.getUserId());
                    redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        log.info("end refreshing user...........");
    }


    @Async
    @Scheduled(cron = "0 0 5 * * *")
    public void runInfluencer() {
        log.info("refreshing Influencer...........");
        corgiUserRecommendService.initInfluencer();
    }
}
