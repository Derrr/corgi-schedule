package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
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
public class CorgiFakeTask {
    @Reference
    private CorgiFakeService corgiFakeService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference(retries = 1, timeout = 300000)
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    private static final Double DAY_MINUTE = 14 * 60.0;

    private static final String LAST_ACTIVITY = "last_activity_";


    @Async(value = "asyncExecutor")
    @Scheduled(cron = "0 0/1 9-22 * * *")
    public void run() {
        log.info("creating fake");
        Long start = System.currentTimeMillis();
        int page = 1;
        int pageSize = 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        UserDetail userDetail = null;
        for (int i = 0; i < 10; i++) {
            String userId = corgiFakeService.selectFakeUser();
            userDetail = corgiUserService.getUserDetailBasic(userId);
            if (userDetail != null) {
                break;
            }
        }
        if (userDetail == null) {
            return;
        }

        corgiFakeService.updateFakeTime(userDetail.getUserId());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -30);
        String date = sdf.format(calendar.getTime());
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String lastDay = sdf2.format(calendar.getTime());

        List<UserProfile> onBoardUsers = corgiBillboardService.getBillboard(sdf.format(new Date()));
        do {
            List<UserProfile> profiles = this.getBasicUserDetailByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(profiles)) {
                break;
            }
            for (UserProfile profile : profiles) {
                if (userDetail.getUserId().equals(profile.getUserId())) {
                    continue;
                }
                if ("influencer".equals(profile.getAvatarStatus())) {
                    influencer(profile, userDetail, hasOnBoard(onBoardUsers, profile), lastDay);
                } else if (date.compareTo(profile.getCreateTime()) > 0) {
                    oldCorgier(profile, userDetail, hasOnBoard(onBoardUsers, profile), lastDay);
                } else {
                    newCorgier(profile, userDetail, hasOnBoard(onBoardUsers, profile), lastDay);
                }
            }
        } while (true);
        log.info("fake task take: {}", (System.currentTimeMillis() - start) / 1000);
    }

    private Boolean hasOnBoard(List<UserProfile> billboardUsers, UserProfile user) {
        if (billboardUsers.size() == 0) {
            return false;
        }
        for (int i = 0; i < billboardUsers.size(); i++) {
            UserProfile bUser = billboardUsers.get(i);
            if (bUser.getUserId().equals(user.getUserId())) {
                billboardUsers.remove(i);
                return true;
            }
        }
        return false;
    }

    private void newCorgier(UserProfile profile, UserDetail userDetail, Boolean hasBoard, String lastDay) {
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = getLastActivity(profile.getUserId(), lastDay);
        double followChance = 10.0 / (30.0 * DAY_MINUTE);
        double likeChance = 0.0;
        if (hasFace) {
            followChance = 20.0 / (30.0 * DAY_MINUTE);
        }
        if (hasBoard) {
            followChance += 100.0 / DAY_MINUTE;
        }
        if (!"-1".equals(activityId)) {
            likeChance = 20.0 / DAY_MINUTE;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -7);
            if (sdf.format(calendar.getTime()).compareTo(profile.getCreateTime()) < 0) {
                followChance += 10.0 / DAY_MINUTE;
            }
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }

    }

    private void oldCorgier(UserProfile profile, UserDetail userDetail, Boolean hasBoard, String lastDay) {
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        String finalTime;
        try {
            sdf.parse(profile.getCreateTime());
            Calendar calendar = sdf.getCalendar();
            calendar.add(Calendar.DATE, 60);
            finalTime = sdf.format(calendar.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }
        String activityId = getLastActivity(profile.getUserId(), lastDay);
        double followChance = 0.0;
        double likeChance = 0.0;

        if (today.compareTo("2021-02-24") < 0 || today.compareTo(finalTime) < 0) {
            if (hasFace) {
                followChance = 20.0 / (30.0 * DAY_MINUTE);
            } else {
                followChance = 10.0 / (30.0 * DAY_MINUTE);
            }
        }
        if (!"-1".equals(activityId)) {
            followChance += 30.0 / (30.0 * DAY_MINUTE);
        }
        if (profile.getTime() != null && System.currentTimeMillis() - 24 * 1000 * 3600 > profile.getTime()) {
            if (hasFace) {
                followChance += 1.0 / DAY_MINUTE;
            }
        }
        if (hasBoard) {
            followChance += 100.0 / DAY_MINUTE;
        }
        if (!"-1".equals(activityId)) {
            likeChance = 15.0 / DAY_MINUTE;
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }
    }

    private void influencer(UserProfile profile, UserDetail userDetail, Boolean hasBoard, String lastDay) {
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = getLastActivity(profile.getUserId(), lastDay);
        double followChance = 0.0;
        double likeChance = 0.0;
        followChance = 100 / (30 * DAY_MINUTE);
        if (hasFace) {
            followChance += 200 / (30 * DAY_MINUTE);
        }
        if (!"-1".equals(activityId)) {
            followChance += 400 / (30 * DAY_MINUTE);
        }

        if (hasBoard) {
            followChance += 100 / DAY_MINUTE;
        }

        if (!"-1".equals(activityId)) {
            likeChance = 40 / DAY_MINUTE;
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }
    }

    private void followUser(UserDetail userDetail, String followId) {
        if (corgiFakeService.addFakeFollower(userDetail.getUserId(), followId)) {
            HashMap extra = new HashMap();
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.FOLLOW)
                    .sourceUserId(userDetail.getUserId())
                    .targetUserId(followId)
                    .extra(extra)
                    .build());
        }
    }

    private void likeActivity(UserDetail userDetail, String activityId, String activityCreator) {
        ActivityLike activityLike = new ActivityLike();
        activityLike.setActivityId(activityId);
        activityLike.setLikeUserId(userDetail.getUserId());
        activityLike.setUserId(activityCreator);
        activityLike.setLikeUserAvatar(userDetail.getAvatar());
        activityLike.setLikeUserName(userDetail.getNickname());
        if (corgiFakeService.addFakeLike(activityLike)) {
            CorgiVlog corgiVlog = new CorgiVlog();
            corgiVlog.setActivityId(activityLike.getActivityId());
            corgiVlog.setLikeCount(1);
            corgiVlogService.addVlogCount(corgiVlog);
            HashMap extra = new HashMap();
            extra.put("activityId", activityLike.getActivityId());
            extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
            if (!activityLike.getUserId().equals(activityLike.getLikeUserId())) {
                mqService.sendMessage(PushMessage.builder()
                        .type(PushMessage.DEFAULT)
                        .sourceUserId(activityLike.getLikeUserId())
                        .targetUserId(activityLike.getUserId())
                        .message(PushMessage.NEW_MESSAGE)
                        .extra(extra)
                        .build());
            }
        }
    }

    private String getLastActivity(String userId, String lastDay) {
        if (!redisTemplate.hasKey(LAST_ACTIVITY)) {
            List<CorgiActivity> corgiActivities = corgiFakeService.getActivityByDate(lastDay);
            for (CorgiActivity activity : corgiActivities) {
                redisTemplate.opsForHash().put(LAST_ACTIVITY, userId, activity.getId());
            }
            redisTemplate.expire(LAST_ACTIVITY, 1L, TimeUnit.HOURS);
        }
        String activityId = (String) redisTemplate.opsForHash().get(LAST_ACTIVITY, userId);
        if (StringUtils.isEmpty(activityId)) {
            activityId = "-1";
        }
        return activityId;
    }

    private List<UserProfile> getBasicUserDetailByPage(Integer page, Integer pageSize) {
        List<UserProfile> result = new ArrayList<>();
        String userListKey = "user_list" + page;
        String userDetailKey = "user_detail";
        if (redisTemplate.hasKey(userListKey)) {
            List<String> userIds = redisTemplate.opsForList().range(userListKey, 0, pageSize);
            for (String userId : userIds) {
                String detailJson = (String) redisTemplate.opsForHash().get(userDetailKey, userId);
                if (!StringUtils.isEmpty(detailJson)) {
                    result.add(JSON.parseObject(detailJson, UserProfile.class));
                }
            }
        } else {
            result = corgiUserService.getBasicUserDetailByPage(page, pageSize);
            if (CollectionUtils.isEmpty(result)) {
                redisTemplate.opsForList().rightPush(userListKey, "empty");
            } else {
                for (UserProfile userProfile : result) {
                    redisTemplate.opsForList().rightPush(userListKey, userProfile.getUserId());
                    redisTemplate.opsForHash().put(userDetailKey, userProfile.getUserId(), JSON.toJSONString(userProfile));
                }
            }
            redisTemplate.expire(userListKey, 1L, TimeUnit.HOURS);
        }
        return result;
    }
}
