package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.TaskService;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiFakeService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Autowired
    private MQService mqService;

    private static final Double DAY_MINUTE = 13 * 60.0;


    @Async
    @Scheduled(cron = "0 0/1 9-22 * * *")
    public void run() {
        log.info("creating fake");
        int page = 1;
        int pageSize = 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        UserDetail userDetail = null;
        for (int i = 0; i < 100; i++) {
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
        do {
            List<UserProfile> profiles = corgiUserService.getBasicUserDetailByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(profiles)) {
                break;
            }
            for (UserProfile profile : profiles) {
                if (userDetail.getUserId().equals(profile.getUserId())) {
                    continue;
                }
                if ("influencer".equals(profile.getAvatarStatus())) {
                    influencer(profile, userDetail);
                } else if (date.compareTo(profile.getCreateTime()) > 0) {
                    oldCorgier(profile, userDetail);
                } else {
                    newCorgier(profile, userDetail);
                }
            }
        } while (true);

    }

    private void newCorgier(UserProfile profile, UserDetail userDetail) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), sdf.format(new Date()));
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = 10 / (30 * DAY_MINUTE);
        double likeChance = 0.0;
        if (hasFace) {
            followChance = 20 / (30 * DAY_MINUTE);
        }
        if (hasBoard) {
            followChance += 100 / DAY_MINUTE;
        }
        if (!StringUtils.isEmpty(activityId)) {
            likeChance = 20 / DAY_MINUTE;
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -7);
            if (sdf.format(calendar.getTime()).compareTo(profile.getCreateTime()) < 0) {
                followChance += 10 / DAY_MINUTE;
            }
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }

    }

    private void oldCorgier(UserProfile profile, UserDetail userDetail) {
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
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), today);
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = 0.0;
        double likeChance = 0.0;

        if (today.compareTo("2021-02-24") < 0 || today.compareTo(finalTime) < 0) {
            if (hasFace) {
                followChance = 20 / (30 * DAY_MINUTE);
            } else {
                followChance = 10 / (30 * DAY_MINUTE);
            }
            if (!StringUtils.isEmpty(corgiFakeService.getLastActivity(profile.getUserId(), profile.getCreateTime()))) {
                followChance += 30 / (30 * DAY_MINUTE);
            }
        }

        if (profile.getTime() != null && System.currentTimeMillis() - 24 * 1000 * 3600 > profile.getTime()) {
            if (hasFace) {
                followChance += 2 / DAY_MINUTE;
            } else {
                followChance += 1 / DAY_MINUTE;
            }
        }
        if (hasBoard) {
            followChance += 100 / DAY_MINUTE;
        }
        if (!StringUtils.isEmpty(activityId)) {
            likeChance = 15 / DAY_MINUTE;
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }
    }

    private void influencer(UserProfile profile, UserDetail userDetail) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), today);
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = 0.0;
        double likeChance = 0.0;
        Integer fakeFollower = corgiFakeService.countFakeFollower(profile.getUserId());
        if (fakeFollower < 700) {
            followChance = 100 / (30 * DAY_MINUTE);
            if (hasFace) {
                followChance += 200 / (30 * DAY_MINUTE);
            }
            if (!StringUtils.isEmpty(corgiFakeService.getLastActivity(profile.getUserId(), profile.getCreateTime()))) {
                followChance += 400 / (30 * DAY_MINUTE);
            }
        }
        if (hasBoard) {
            followChance += 100 / DAY_MINUTE;
        }

        if (!StringUtils.isEmpty(activityId)) {
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

}
