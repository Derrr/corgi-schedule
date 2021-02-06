package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.entity.CorgiStatistic;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
public class CorgiTopicTask {
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiLikeService corgiLikeService;


    @Async
    //@Scheduled(cron = "0 0 * * * *")
    @Scheduled(fixedRate = 3600 * 1000)
    public void run() {
        int page = 1;
        int size = 1000;
        do {
            List<String> activityIds = corgiToolService.getActivityIdsByTopic("24", page, size);
            if (CollectionUtils.isEmpty(activityIds)) {
                break;
            }
            for (String activityId : activityIds) {
                Long weight = corgiLikeService.countActivityLike(activityId);
                corgiToolService.updateActivityTopicWeight(activityId, weight.intValue());
            }
            page++;
        } while (true);

    }

}
