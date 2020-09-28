package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.schedule.service.HxPushMessageService;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.SystemMessage;
import com.corgi.user.entity.UserLogin;
import com.corgi.user.entity.UserProfile;
import com.corgi.user.entity.UserSignUp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiActivityEndCheckTask {
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiPicService corgiPicService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private HxPushMessageService hxPushMessageService;


    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private SimpleDateFormat m_sdf = new SimpleDateFormat("HH点mm分");

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String endDate = sdf.format(calendar.getTime());
        CorgiActivity query = new CorgiActivity();
        query.setStatus(CorgiActivity.CREATED);
        calendar.add(Calendar.HOUR, -1);
        query.setCurrentTime(sdf.format(calendar.getTime()));
        query.setSignUpTime(endDate);
        int page = 1;
        int pageSize = 100;
        List<CorgiActivity> activityList;
        do {
            activityList = corgiActivityService.searchCorgiActivity(query, page, pageSize);
            page++;
            if (activityList != null) {
                for (CorgiActivity activity : activityList) {
                    List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activity.getId(), null, UserSignUp.AGREE + "");
                    List<String> userIds = new ArrayList<>();
                    userIds.add(activity.getUserId());
                    for (UserProfile userProfile : userProfiles) {
                        userIds.add(userProfile.getUserId());
                    }
                    sendMessage(activity, userIds);
                }
            }
        } while (!CollectionUtils.isEmpty(activityList));

    }

    private void sendMessage(CorgiActivity activity, List<String> userIds) {
        String activityId = activity.getId();
        String key = "activity_end_check_sent_" + activityId;
        if (redisTemplate.hasKey(key)) {
            return;
        }
        SystemMessage systemMessage = new SystemMessage();
        systemMessage.setContent("活动结束了，快分享美好瞬间吧！");
        HashMap extra = new HashMap();
        extra.put("type", "902");
        extra.put("activityId", activityId);
        extra.put("aId", activityId);
        extra.put("title", activity.getTitle());
        extra.put("desc", activity.getContent());
        List<ActivityPic> pics = corgiPicService.getActivityPic(activityId);
        if (!CollectionUtils.isEmpty(pics) && pics.get(0) != null) {
            extra.put("picUrl", activity.getPics().get(0).getPicUrl());
        }
        hxPushMessageService.sendMessage(systemMessage, userIds, extra);
        redisTemplate.opsForValue().set(key, System.currentTimeMillis(), 2, TimeUnit.HOURS);
    }
}
