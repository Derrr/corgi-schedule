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
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiBillboardTask {
    @Reference
    private CorgiBillboardService corgiBillboardService;

    @Async
    @Scheduled(fixedRate = 24 * 3600 * 1000)
    //@Scheduled(cron = "0 55 23 * * *")
    public void run() {
        log.info("adding billboard...........");
        List<String> userIds = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -7);
        String pastDate = sdf.format(calendar.getTime());
        List<UserProfile> pastUsers = corgiBillboardService.getPastBillboard(pastDate);
        for (UserProfile userProfile : pastUsers) {
            userIds.add(userProfile.getUserId());
        }

        UserDetail searchUser = new UserDetail();
        searchUser.setRole("1");
        List<UserProfile> userProfiles = corgiBillboardService.getPopularUser(searchUser, 72);
        int i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            i++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "fans1");
        }
        searchUser.setRole("0");
        userProfiles = corgiBillboardService.getPopularUser(searchUser, 74);
        for (UserProfile userProfile : userProfiles) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            i++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "fans0");
        }
        userProfiles = corgiBillboardService.getPassionUser(searchUser, 76);
        i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            i++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "follow");
            if (i >= 2) {
                break;
            }
        }
        userProfiles = corgiBillboardService.getActiveUser(searchUser, 80);
        i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            i++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "active");
            if (i >= 4) {
                break;
            }
        }
        if (userIds.size() < 10) {
            searchUser.setRole(null);
            userProfiles = corgiBillboardService.getPopularUser(searchUser, 80);
            for (UserProfile userProfile : userProfiles) {
                if (userIds.contains(userProfile.getUserId())) {
                    continue;
                }
                i++;
                userIds.add(userProfile.getUserId());
                corgiBillboardService.addBillboard(userProfile, date, "fanstotal");
                if (userIds.size() >= 10) {
                    break;
                }
            }
        }
    }
}
