package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiTimeStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Autowired
    private MQService mqService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");
    private static SimpleDateFormat hour_sdf = new SimpleDateFormat("HH");

    @Async
    @Scheduled(fixedRate = 1000 * 24 * 3600)
    public void run() {
        int page = 1;
        while (true) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -90);
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
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

//        Calendar calendar = Calendar.getInstance();
//        String date = dau_sdf.format(calendar.getTime());
//        long time = calendar.getTimeInMillis();
//
//        int hour = calendar.get(Calendar.HOUR_OF_DAY);
//        String endHour = hour_sdf.format(calendar.getTime());
//
//
//        calendar.add(Calendar.HOUR_OF_DAY, -3);
//        String beginHour = hour_sdf.format(calendar.getTime());
//        if (hour == 0) {
//            date = dau_sdf.format(calendar.getTime());
//        }
//        String key = beginHour + "-" + endHour;
//
//        long count = corgiUserService.countActiveUser(calendar.getTimeInMillis(), time);
//        corgiStatisticService.updateMap(CorgiStatistic.ACTIVE, date, key, count);

    }

}
