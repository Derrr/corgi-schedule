package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.common.messages.TraceFollow;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MapService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@Component
public class CorgiStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference
    private CorgiToolService corgiToolService;
    @Autowired
    private MapService mapService;
    @Autowired
    private TaskService taskService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");

    @Async
    @Scheduled(cron = "0 59 23 * * *")
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    public void run() {
        Calendar calendar = Calendar.getInstance();
        Long time = calendar.getTimeInMillis();
        Date date = calendar.getTime();
        String today = dau_sdf.format(date);
        String activityToday = activity_sdf.format(date);
        long zero = time / (1000 * 3600 * 24) * (1000 * 3600 * 24) - TimeZone.getDefault().getRawOffset();

        long dau = corgiUserService.countActiveUser(zero, zero + 1000 * 3600 * 24);
        corgiStatisticService.addCount(CorgiStatistic.DAU, today, dau);

        long register = corgiUserService.countRegisterUser(today);
        corgiStatisticService.addCount(CorgiStatistic.REGISTER, today, register);

        long activity = corgiActivityService.countPublishActivity(activityToday);
        corgiStatisticService.addCount(CorgiStatistic.ACTIVITY, today, activity);

        countSilentUser(zero, today);
        countUserRole(today);
        countUserGroup(today);
        countUserPreferGroup(today);
        Calendar tmp = Calendar.getInstance();
        tmp.setTimeInMillis(time);
        countUserAge(tmp, today);
        countActivityType(today);
        //countUserCity(today);

        tmp = Calendar.getInstance();
        tmp.setTimeInMillis(time);
        countUserStay(calendar, today, zero);

        //countUserTrace(today);
    }

    public void countUserTrace(String date) {
        taskService.countUserTrace(date);
    }

    void countUserStay(Calendar calendar, String date, long zero) {
        for (int i = 1; i <= 90; i++) {
            calendar.add(Calendar.DATE, -1);
            String registerDate = dau_sdf.format(calendar.getTime());
            long count = corgiUserService.countUserStay(zero, registerDate);
            corgiStatisticService.addUserStay(date, registerDate, i + "", count);
        }
    }

    void countActivityType(String date) {
        List<HashMap> hashMapList = corgiActivityService.groupByActivity("activityType", "0", "9");
        if (!CollectionUtils.isEmpty(hashMapList)) {
            for (HashMap hashMap : hashMapList) {
                try {
                    log.info(hashMap.toString());
                    if (hashMap.get("_id") != null) {
                        corgiStatisticService.addList(CorgiStatistic.ACTIVITY_TYPE, date, (String) hashMap.get("_id"), Long.valueOf(hashMap.get("count").toString()));
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    void countSilentUser(Long time, String date) {
        long count;
        long dayTime = 1000 * 3600 * 24L;
        long todayTime = time + dayTime;

        long time3 = todayTime - dayTime * 3;
        count = corgiUserService.countActiveUser(0, time3);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "3days", count);

        long time7 = todayTime - dayTime * 7;
        count = corgiUserService.countActiveUser(0, time7);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "7days", count);

        long time14 = todayTime - dayTime * 14;
        count = corgiUserService.countActiveUser(0, time14);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "14days", count);

        long time30 = todayTime - dayTime * 30;
        count = corgiUserService.countActiveUser(0, time30);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "30days", count);

        long time90 = todayTime - dayTime * 90;
        count = corgiUserService.countActiveUser(0, time90);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "90days", count);

        long time180 = todayTime - dayTime * 180;
        count = corgiUserService.countActiveUser(0, time180);
        corgiStatisticService.updateMap(CorgiStatistic.SILENT, date, "180days", count);

    }

    void countUserAge(Calendar calendar, String date) {
        long count;

        calendar.add(Calendar.YEAR, -18);
        String date18 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date18, "9");
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "-18", count);

        calendar.add(Calendar.YEAR, -7);
        String date25 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date25, date18);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "18-25", count);

        calendar.add(Calendar.YEAR, -5);
        String date30 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date30, date25);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "25-30", count);

        calendar.add(Calendar.YEAR, -5);
        String date35 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date35, date30);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "30-35", count);

        calendar.add(Calendar.YEAR, -5);
        String date40 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date40, date35);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "35-40", count);

        calendar.add(Calendar.YEAR, -5);
        String date45 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date45, date40);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "40-45", count);

        calendar.add(Calendar.YEAR, -5);
        String date50 = activity_sdf.format(calendar.getTime());
        count = corgiUserService.countBirthday(date50, date45);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "45-50", count);

        count = corgiUserService.countBirthday("", date50);
        corgiStatisticService.updateMap(CorgiStatistic.AGE, date, "50+", count);
    }

    void countUserPreferGroup(String date) {
        long count = corgiUserService.countPreferGroup("偏胖");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "pig", count);

        count = corgiUserService.countPreferGroup("肉壮");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "bear", count);

        count = corgiUserService.countPreferGroup("肌肉");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "baboon", count);

        count = corgiUserService.countPreferGroup("精壮");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "wolf", count);

        count = corgiUserService.countPreferGroup("匀称");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "dog", count);

        count = corgiUserService.countPreferGroup("偏瘦");
        corgiStatisticService.updateMap(CorgiStatistic.PREFER_GROUP, date, "monkey", count);

    }

    void countUserGroup(String date) {
        UserDetail userDetail = new UserDetail();

        userDetail.setGroup("偏胖");
        long count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "pig", count);

        userDetail.setGroup("肉壮");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "bear", count);

        userDetail.setGroup("肌肉");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "baboon", count);

        userDetail.setGroup("精壮");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "wolf", count);

        userDetail.setGroup("匀称");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "dog", count);

        userDetail.setGroup("偏瘦");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.GROUP, date, "monkey", count);

    }

    void countUserRole(String date) {
        UserDetail userDetail = new UserDetail();

        userDetail.setRole("1");
        long count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.ROLE, date, "top", count);

        userDetail.setRole("0.5+");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.ROLE, date, "verstop", count);

        userDetail.setRole("0.5");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.ROLE, date, "vers", count);

        userDetail.setRole("0.5-");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.ROLE, date, "versbottom", count);

        userDetail.setRole("0");
        count = corgiUserService.countUsers(userDetail);
        corgiStatisticService.updateMap(CorgiStatistic.ROLE, date, "bottom", count);

    }
}
