package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.api.CorgiMatchService;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.*;
import com.corgi.user.entity.CorgiFeed;
import com.corgi.user.entity.UserExtra;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiCleanUserTask {
    @Reference(retries = 1, timeout = 300000)
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiExtraService corgiExtraService;
    @Reference
    private CorgiMatchService corgiMatchService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiFeedService corgiFeedService;

//    @Async
//    @Scheduled(fixedRate = 24 * 3600 * 1000)
//    public void run1() {
//        List<String> userExtras = corgiExtraService.getExtraUserIds(1, 5000);
//        for (String userId : userExtras) {
//            corgiMatchService.updateUser(corgiUserService.getUserDetailBasic(userId));
//        }
//    }

    @Async
    //@Scheduled(cron = "0 0 4 * * *")
    @Scheduled(fixedRate = 24 * 3600 * 1000)
    public void run() {
        log.info("cleaning user...........");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<String> userIds = corgiUserService.getUnregisterUsers(sdf.format(calendar.getTime()));
        if (userIds.size() > 1000) {
            log.error(" too many unregister users:{}! ", userIds.size());
            return;
        }
        for (String userId : userIds) {
            if (userId != null) {
                try {
                    corgiUserService.deleteUser(userId);
                    corgiActivityService.deleteUserActivity(userId);
                    CorgiFeed query = new CorgiFeed();
                    query.setUserId(userId);
                    corgiFeedService.deleteFeed(query);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        calendar.add(Calendar.DATE, -7);
        corgiUserMatchService.clearMatchByDate(sdf.format(calendar.getTime()));
        corgiUserMatchService.clearMatchViewByDate(sdf.format(new Date()));
    }
}
