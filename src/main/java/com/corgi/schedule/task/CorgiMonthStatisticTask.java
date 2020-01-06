package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.entity.CorgiStatistic;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author tairanliu
 */
@Component
public class CorgiMonthStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiToolService corgiToolService;

    private static SimpleDateFormat mau_sdf = new SimpleDateFormat("yyyy-MM");

    @Scheduled(cron = "0 5 0 1 * *")
    public void run() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String month = mau_sdf.format(calendar.getTime());

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Long time = System.currentTimeMillis();
        long zero = time / (1000 * 3600 * 24) * (1000 * 3600 * 24) - TimeZone.getDefault().getRawOffset();

        long mau = corgiUserService.countActiveUser(calendar.getTimeInMillis(), zero);
        corgiToolService.addCount(CorgiStatistic.MAU, month, mau);
    }
}
