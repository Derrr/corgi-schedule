package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.entity.CorgiStatistic;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * @author tairanliu
 */
@Component
public class CorgiHourStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");
    private static SimpleDateFormat hour_sdf = new SimpleDateFormat("HH");

    @Async
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        Calendar calendar = Calendar.getInstance();
        String date = dau_sdf.format(calendar.getTime());

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String endHour = hour_sdf.format(calendar.getTime());
        String endDate = activity_sdf.format(calendar.getTime()) + " " + endHour;


        calendar.add(Calendar.HOUR_OF_DAY, -1);
        String beginHour = hour_sdf.format(calendar.getTime());
        String beginDate = activity_sdf.format(calendar.getTime()) + " " + beginHour;
        if (hour == 0) {
            date = dau_sdf.format(calendar.getTime());
        }

        long count = corgiActivityService.countRangePublishActivity(beginDate, endDate);
        corgiStatisticService.addList(CorgiStatistic.PUBLISH, date, beginHour, count);

    }

}
