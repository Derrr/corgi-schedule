package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiFakeService;
import com.corgi.user.api.CorgiUserRecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiWeekTask {
    @Reference(retries = 1, timeout = 5000000)
    private CorgiFakeService corgiFakeService;
    @Autowired
    private TaskService taskService;


    @Async
    //@Scheduled(cron = "0 0 2 * * MON")
    @Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void refreshingFakeUserPool() {
        log.info("refreshing fake user pool...........");
        corgiFakeService.refreshFakeUser(10000);
    }

}
