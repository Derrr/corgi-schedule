package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.PushMessage;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiOrderService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiOrder;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
    private MQService mqService;
    @Autowired
    private TaskService taskService;


    @Async
    @Scheduled(cron = "0 25 4 * * *")
    @Scheduled(fixedRate = 24 * 3600 * 1000)
    public void run() {
        List<UserProfile> influencers = corgiUserRecommendService.getInfluencerByCity(null, "全国", 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (UserProfile userProfile : influencers) {
            String vipExpire = corgiUserService.getUserVipExpire(userProfile.getUserId());
            if (!"-".equals(vipExpire)) {
                try {
                    Date expire = sdf.parse(vipExpire);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(expire);
                    calendar.add(Calendar.DATE, 1);
                    CorgiOrder order = CorgiOrder.builder()
                            .userId(userProfile.getUserId())
                            .status(CorgiOrder.STATUS.SUCCESS)
                            .build();
                    corgiOrderService.subscribe(order, null, "1", sdf.format(calendar.getTime()));
                } catch (ParseException e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
}
