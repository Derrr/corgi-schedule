package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.entity.CorgiStatistic;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author tairanliu
 */
@Component
public class CorgiStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiToolService corgiToolService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");

    @Scheduled(cron = "0 50 23 * * *")
    public void run() {
        Date date = new Date();
        String today = dau_sdf.format(date);
        String activityToday = activity_sdf.format(date);

        Long time = System.currentTimeMillis();
        long zero = time / (1000 * 3600 * 24) * (1000 * 3600 * 24) - TimeZone.getDefault().getRawOffset();

        long dau = corgiUserService.countActiveUser(zero, zero + 1000 * 3600 * 24);
        corgiToolService.addCount(CorgiStatistic.DAU, today, dau);

        long register = corgiUserService.countRegisterUser(today);
        corgiToolService.addCount(CorgiStatistic.REGISTER, today, register);

        long activity = corgiActivityService.countPublishActivity(activityToday);
        corgiToolService.addCount(CorgiStatistic.ACTIVITY, today, activity);
    }
}
