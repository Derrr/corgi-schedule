package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiActivityCheckTask {
    @Reference
    private CorgiActivityService corgiActivityService;
    @Autowired
    private MQService mqService;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private SimpleDateFormat m_sdf = new SimpleDateFormat("HH点mm分");

    @Async
    @Scheduled(cron = "0 * * * * *")
    public void run() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        String endDate = sdf.format(calendar.getTime());
        CorgiActivity query = new CorgiActivity();
        query.setStatus(CorgiActivity.CREATED);
        query.setSignUpTime(endDate);
        int page = 1;
        int pageSize = 100;
        List<CorgiActivity> activityList;
        do {
            activityList = corgiActivityService.searchCorgiActivity(query, page, pageSize);
            page++;
            if (activityList != null) {
                for (CorgiActivity activity : activityList) {
                    String time = "";
                    try {
                        time = m_sdf.format(sdf.parse(activity.getSignUpTime()));
                    } catch (ParseException e) {
                        log.error(e.getMessage(), e);
                    }
                    PushMessage message = PushMessage.builder()
                            .sourceUserId(activity.getId())
                            .type(PushMessage.ACTIVITY_DUEL)
                            .message("哈喽～不要忘了我们的约会，" + time + "，我们在 " + activity.getAddress() + " 不见不散。马上就要开始了，准备出发吧！（此条信息由Corgi运营小哥哥暖心提供）")
                            .build();
                    mqService.sendMessage(message);
                }
            }
        } while (!CollectionUtils.isEmpty(activityList));

    }

}
