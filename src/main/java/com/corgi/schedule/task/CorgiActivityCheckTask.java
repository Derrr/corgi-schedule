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
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserLogin;
import com.corgi.user.entity.UserProfile;
import com.corgi.user.entity.UserSignUp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiActivityCheckTask {
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private MQService mqService;
    @Autowired
    private RedisTemplate redisTemplate;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private SimpleDateFormat m_sdf = new SimpleDateFormat("HH点mm分");

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
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", "LTAI4Fwu7uCt6ijGW7rLWbqQ", "Kxl0JLGjrIyeSc94ZZKGiUTUCSZLx3");
        IAcsClient client = new DefaultAcsClient(profile);
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
                    List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activity.getId(), null, UserSignUp.AGREE + "");
                    UserLogin userLogin = corgiUserService.getUserLogin(activity.getUserId());
                    sendMessage(client, activity, activity.getUserId(), userLogin.getTelNo(), time);
                    for (UserProfile userProfile : userProfiles) {
                        if (userProfile.getTelNo() != null) {
                            sendMessage(client, activity, userProfile.getUserId(), userProfile.getTelNo(), time);
                        }
                    }

                }
            }
        } while (!CollectionUtils.isEmpty(activityList));

    }

    private void sendMessage(IAcsClient client, CorgiActivity activity, String userId, String telNo, String time) {
        String key = "activity_check_sent_" + activity.getId() + "_" + userId;
        if (!redisTemplate.hasKey(key)) {
            String sign = "SMS_187225471";
            if (telNo.contains("-")) {
                sign = "SMS_188550914";
            }
            CommonRequest request = new CommonRequest();
            request.setMethod(MethodType.POST);
            request.setDomain("dysmsapi.aliyuncs.com");
            request.setVersion("2017-05-25");
            request.setAction("SendSms");
            request.putQueryParameter("RegionId", "cn-hangzhou");
            request.putQueryParameter("PhoneNumbers", telNo.replaceAll("-", ""));
            request.putQueryParameter("SignName", "Corgi");
            request.putQueryParameter("TemplateCode", sign);
            request.putQueryParameter("TemplateParam", "{\"time\":\"" + time + "\",\"name\":\"" + activity.getAddress() + "\"}");
            try {
                CommonResponse response = client.getCommonResponse(request);
                log.info(response.getData());
                redisTemplate.opsForValue().set(key, System.currentTimeMillis(), 2, TimeUnit.HOURS);
            } catch (ServerException e) {
                log.error(e.getMessage(), e);
            } catch (ClientException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
