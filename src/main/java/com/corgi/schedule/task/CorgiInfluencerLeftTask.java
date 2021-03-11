package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.PushMessage;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiInfluencerLeftTask {
    @Reference(retries = 1, timeout = 300000)
    private CorgiUserRecommendService corgiUserRecommendService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiToolService corgiToolService;
    @Autowired
    private MQService mqService;
    @Autowired
    private TaskService taskService;


    @Async
    @Scheduled(cron = "0 0 10 25 * *")
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    public void run() {
        List<UserProfile> influencers = corgiUserRecommendService.getInfluencerByCity(null, "全国", 1000);
        UserDetail userDetail = new UserDetail();
        userDetail.setAvatarStatus("");
        Long threshold = System.currentTimeMillis() - 30 * 24 * 1000 * 3600;
        for (UserProfile userProfile : influencers) {
            UserPosition userPosition = corgiUserService.getUserPosition(userProfile.getUserId());
            if (userPosition != null && userPosition.getUptime() < threshold) {
                log.error(userPosition.getUserId() + ":" + userPosition.getUptime() + "<" + threshold);
                userDetail.setUserId(userProfile.getUserId());
                corgiUserService.updateDetail(userDetail);
                corgiToolService.countUserNumber(userProfile.getNickname());
                mqService.sendInfluencerLeftMessage(PushMessage.builder().targetUserId(userProfile.getUserId()).build());
            }
        }
    }
}
