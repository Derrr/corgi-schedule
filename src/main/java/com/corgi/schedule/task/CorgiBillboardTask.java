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
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiBillboardTask {
    @Reference(retries = 1, timeout = 300000)
    private CorgiBillboardService corgiBillboardService;
    @Autowired
    private MQService mqService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Async
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    @Scheduled(cron = "0 0 10 * * *")
    public void run() {
        log.info("adding billboard...........");
        List<String> userIds = new ArrayList<>();
        userIds.add("7");
        userIds.add("8");
        userIds.add("9");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 3);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -30);
        String pastDate = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -60);
        String pastTimesDate = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -90);
        String pastPopularDate = sdf.format(calendar.getTime());
        List<UserProfile> pastUsers = corgiBillboardService.getPastBillboard(pastDate);
        List<UserProfile> pastTimesUsers = corgiBillboardService.getPastBillboard(pastTimesDate);
        List<UserProfile> pastPopularUsers = corgiBillboardService.getPastBillboard(pastPopularDate);
        for (UserProfile userProfile : pastUsers) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            userIds.add(userProfile.getUserId());
        }

        for (UserProfile userProfile : pastPopularUsers) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            userIds.add(userProfile.getUserId());
        }

        for (UserProfile userProfile : pastTimesUsers) {
            if (userIds.contains(userProfile.getUserId())) {
                continue;
            }
            Integer count = corgiBillboardService.countOnBoard(userProfile.getUserId());
            if (count != null && count > 6) {
                userIds.add(userProfile.getUserId());
            }
        }

        UserDetail searchUser = new UserDetail();
//        searchUser.setRole("1");
        List<UserProfile> userProfiles = corgiBillboardService.getPopularUser(searchUser, 500);
        int i = 0;
        for (UserProfile userProfile : userProfiles) {
            if (checkUser(userIds, userProfile.getUserId())) {
                continue;
            }

            i++;
            log.info("popular..." + userProfile.getUserId());
            userIds.add(userProfile.getUserId());
            corgiBillboardService.addBillboard(userProfile, date, "popular");
            if (i >= 10) {
                break;
            }
        }
//        searchUser.setRole("0");
//        i = 0;
//        userProfiles = corgiBillboardService.getPopularUser(searchUser, 500);
//        for (UserProfile userProfile : userProfiles) {
//            if (checkUser(userIds, userProfile.getUserId())) {
//                continue;
//            }
//            i++;
//            total++;
//            userIds.add(userProfile.getUserId());
//            corgiBillboardService.addBillboard(userProfile, date, "fans0");
//            if (i >= 2) {
//                break;
//            }
//        }
//        userProfiles = corgiBillboardService.getPassionUser(searchUser, 500);
//        i = 0;
//        for (UserProfile userProfile : userProfiles) {
//            if (checkUser(userIds, userProfile.getUserId())) {
//                continue;
//            }
//            i++;
//            total++;
//            userIds.add(userProfile.getUserId());
//            corgiBillboardService.addBillboard(userProfile, date, "follow");
//            if (i >= 2) {
//                break;
//            }
//        }
//        userProfiles = corgiBillboardService.getActiveUser(searchUser, 500);
//        i = 0;
//        for (UserProfile userProfile : userProfiles) {
//            if (checkUser(userIds, userProfile.getUserId())) {
//                continue;
//            }
//            i++;
//            total++;
//            userIds.add(userProfile.getUserId());
//            corgiBillboardService.addBillboard(userProfile, date, "active");
//            if (i >= 4) {
//                break;
//            }
//        }
//        if (total < 10) {
//            searchUser.setRole(null);
//            userProfiles = corgiBillboardService.getPopularUser(searchUser, 500);
//            for (UserProfile userProfile : userProfiles) {
//                if (checkUser(userIds, userProfile.getUserId())) {
//                    continue;
//                }
//                total++;
//                userIds.add(userProfile.getUserId());
//                corgiBillboardService.addBillboard(userProfile, date, "fanstotal");
//                if (total >= 10) {
//                    break;
//                }
//            }
//        }
    }

    @Async
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    @Scheduled(cron = "0 0 8 * * *")
    public void notice() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new Date());
        List<UserProfile> userProfiles = corgiBillboardService.getBillboard(date);
        for (UserProfile userProfile : userProfiles) {
            mqService.sendBillboardMessage(PushMessage.builder()
                    .targetUserId(userProfile.getUserId()).build());
        }
    }

    private boolean checkUser(List<String> userIds, String userId) {
        if (userId == null) {
            return true;
        }
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
