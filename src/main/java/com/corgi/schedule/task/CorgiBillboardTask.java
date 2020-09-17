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
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Async
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    @Scheduled(cron = "0 0 20 * * *")
    public void run() {
        log.info("adding billboard...........");
        List<String> userIds = new ArrayList<>();
        userIds.add("8");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 3);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -7);
        String pastDate = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -23);
        String pastPopularDate = sdf.format(calendar.getTime());
        List<UserProfile> pastUsers = corgiBillboardService.getPastBillboard(pastDate);
        List<UserProfile> pastPopularUsers = corgiBillboardService.getPastBillboard(pastPopularDate);
        for (UserProfile userProfile : pastUsers) {
            userIds.add(userProfile.getUserId());
        }

        for (UserProfile userProfile : pastPopularUsers) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            userIds.add(userProfile.getUserId());
        }
        
        UserDetail searchUser = new UserDetail();
        searchUser.setRole("1");
        List<UserProfile> userProfiles = corgiBillboardService.getPopularUser(searchUser, 100);
        int total = 0;
        int i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (checkUser(userIds, userProfile.getUserId())) {
                continue;
            }

            i++;
            total++;
            log.info("fan1..." + userProfile.getUserId());
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "fans1");
            if (i >= 2) {
                break;
            }
        }
        searchUser.setRole("0");
        i = 0;
        userProfiles = corgiBillboardService.getPopularUser(searchUser, 100);
        for (UserProfile userProfile : userProfiles) {
            if (checkUser(userIds, userProfile.getUserId())) {
                continue;
            }
            i++;
            total++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "fans0");
            if (i >= 2) {
                break;
            }
        }
        userProfiles = corgiBillboardService.getPassionUser(searchUser, 100);
        i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (checkUser(userIds, userProfile.getUserId())) {
                continue;
            }
            i++;
            total++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "follow");
            if (i >= 2) {
                break;
            }
        }
        userProfiles = corgiBillboardService.getActiveUser(searchUser, 100);
        i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (checkUser(userIds, userProfile.getUserId())) {
                continue;
            }
            i++;
            total++;
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "active");
            if (i >= 4) {
                break;
            }
        }
        if (total < 10) {
            searchUser.setRole(null);
            userProfiles = corgiBillboardService.getPopularUser(searchUser, 100);
            for (UserProfile userProfile : userProfiles) {
                if (checkUser(userIds, userProfile.getUserId())) {
                    continue;
                }
                total++;
                userIds.add(userProfile.getUserId());
                corgiBillboardService.addBillboard(userProfile, date, "fanstotal");
                if (total >= 10) {
                    break;
                }
            }
        }
    }

    private boolean checkUser(List<String> userIds, String userId) {
        if (userIds.contains(userId)) {
            return true;
        }
        if (redisTemplate.hasKey("billboard_block_".concat(userId))) {
            return true;
        }
        if (userId.startsWith("B")) {
            return true;
        }
        return false;
    }
}
