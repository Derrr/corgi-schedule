package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.ActivityQuery;
import com.corgi.entity.CorgiTopic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiBillboardTask {
    @Reference(retries = 1, timeout = 300000)
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiBlacklistService corgiBlacklistService;
    @Reference
    private CorgiToolService corgiToolService;
    @Autowired
    private MQService mqService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Async
    @Scheduled(cron = "0 0 10 * * *")
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    public void run2() {
        log.info("adding activity billboard...........");
        List<String> userIds = Lists.newArrayList("7", "8", "9");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 3);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
        calendar.add(Calendar.DATE, -6);
        String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime());
        //calendar.add(Calendar.DATE, -57);
        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());

        ActivityQuery query = new ActivityQuery();
        query.setStartTime(startTime);
        List<CorgiActivity> corgiActivities = corgiBillboardService.getPopularActivity(query, 5000);
        ActivityBillboard billboard = new ActivityBillboard();
        billboard.setDate(startDate);
        List<ActivityBillboard> allOnboardActivity = corgiBillboardService.getAllActivityBillboard(billboard);
        for (ActivityBillboard billboard1 : allOnboardActivity) {
            CorgiActivity activity = corgiActivityFeedService.getActivityById(billboard1.getActivityId());
            if (activity != null) {
                userIds.add(activity.getUserId());
            }
        }

        calendar.add(Calendar.DATE, -1);
        String lastTime = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
        ActivityBillboard billboardQuery = new ActivityBillboard();
        billboardQuery.setDate(lastTime);
        billboardQuery.setCtime(date);
        log.info("activity size:{} ", corgiActivities.size());
        int total = 0;
        for (CorgiActivity activity : corgiActivities) {
            if (StringUtils.isEmpty(activity.getUserId()) || userIds.contains(activity.getUserId())) {
                continue;
            }
            if (StringUtils.isEmpty(activity.getId())) {
                continue;
            }
            CorgiReport reportQuery = new CorgiReport();
            reportQuery.setAccuseId(activity.getUserId());
            reportQuery.setReportStatus("normal");
            Integer count = corgiBlacklistService.countReport(reportQuery);
            if (count > 0) {
                userIds.add(activity.getUserId());
                continue;
            }
            reportQuery.setReportStatus("darkroom");
            count = corgiBlacklistService.countReport(reportQuery);
            if (count > 0) {
                userIds.add(activity.getUserId());
                continue;
            }
            total++;
            if (total > 10) {
                break;
            }
            userIds.add(activity.getUserId());
            ActivityBillboard activityBillboard = new ActivityBillboard();
            activityBillboard.setActivityId(activity.getId());
            activityBillboard.setDate(date);
            activityBillboard.setUserId(activity.getUserId());
            activityBillboard.setCount(activity.getLikeCount().intValue());
            activityBillboard.setOrder(99);
            log.info("add billboard:{} ", activityBillboard);
            corgiBillboardService.addActivityBillboard(activityBillboard);
        }
    }

    @Async
    //@Scheduled(cron = "0 12 3 * * *")
    @Scheduled(fixedRate = 24 * 3600 * 1000)
    public void runTopic() {
        log.info("adding topic billboard...........");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime());

        List<CorgiTopic> topics = corgiToolService.searchTopic(null, "release");
        for (CorgiTopic topic : topics) {
            ActivityQuery query = new ActivityQuery();
            query.setStartTime(startTime);
            query.setTopic(topic.getTopicId());
            List<CorgiActivity> corgiActivities = corgiBillboardService.getPopularActivity(query, 30);
            int total = 0;
            List<TopicBillboard> topicBillboards = new ArrayList<>();
            for (CorgiActivity activity : corgiActivities) {
                if (StringUtils.isEmpty(activity.getUserId())) {
                    continue;
                }
                if (StringUtils.isEmpty(activity.getId())) {
                    continue;
                }
                if (activity.getLikeCount() < 4) {
                    break;
                }
                CorgiReport reportQuery = new CorgiReport();
                reportQuery.setAccuseId(activity.getUserId());
                reportQuery.setReportStatus("normal");
                Integer count = corgiBlacklistService.countReport(reportQuery);
                if (count > 0) {
                    continue;
                }
                reportQuery.setReportStatus("darkroom");
                count = corgiBlacklistService.countReport(reportQuery);
                if (count > 0) {
                    continue;
                }
                total++;
                if (total > 21) {
                    break;
                }
                TopicBillboard topicBillboard = new TopicBillboard();
                topicBillboard.setActivityId(activity.getId());
                topicBillboard.setTopic(topic.getTopicId());
                topicBillboard.setOrder(total);
                topicBillboards.add(topicBillboard);
                if (total == 1) {
                    redisTemplate.opsForValue().set(TopicBillboard.PREFIX.concat(topic.getTopicId()), activity.getId(), 7L, TimeUnit.DAYS);
                }
            }
            if (topicBillboards.size() > 0) {
                TopicBillboard topicQuery = new TopicBillboard();
                topicQuery.setTopic(topic.getTopicId());
                corgiBillboardService.deleteTopicBillboard(topicQuery);
                for (TopicBillboard topicBillboard : topicBillboards) {
                    corgiBillboardService.addTopicBillboard(topicBillboard);
                }
            }
        }
    }
}
