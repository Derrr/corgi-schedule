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
        int pageSize = 100;
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
        Integer fakeFollowerCount = 0;
        Integer fakeLikeCount = 0;
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), profile.getCreateTime());
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = 0.0;
        double likeChance = 0.0;
        if (hasBoard && hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 300;
            followChance = fakeFollowerCount / DAY_MINUTE;

            fakeLikeCount = 50;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 100;
            followChance = fakeFollowerCount / (5 * DAY_MINUTE);

            fakeLikeCount = 30;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasBoard && hasFace) {
            fakeFollowerCount = 100;
            followChance = fakeFollowerCount / DAY_MINUTE;
        } else if (!StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 50;
            followChance = fakeFollowerCount / DAY_MINUTE;

            fakeLikeCount = 10;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace) {
            fakeFollowerCount = 50;
            followChance = fakeFollowerCount / DAY_MINUTE;
        }

        if (Math.random() < followChance && corgiFakeService.countFakeFollower(profile.getUserId()) < fakeFollowerCount) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance && corgiFakeService.countFakeLike(activityId) < fakeLikeCount) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }

    }

    private void oldCorgier(UserProfile profile, UserDetail userDetail) {
        Integer fakeFollowerCount = 10;
        Integer fakeLikeCount = 0;
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String publishTime;
        try {
            sdf.parse(profile.getCreateTime());
            Calendar calendar = sdf.getCalendar();
            calendar.add(Calendar.DATE, 30);
            publishTime = sdf.format(calendar.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), publishTime);
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = fakeFollowerCount / (2 * DAY_MINUTE);
        double likeChance = 0.0;
        if (hasBoard && hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 400;
            followChance = fakeFollowerCount / DAY_MINUTE;

            fakeLikeCount = 50;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 300;
            followChance = fakeFollowerCount / (30 * DAY_MINUTE);

            fakeLikeCount = 30;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasBoard && hasFace) {
            fakeFollowerCount = 200;
            followChance = fakeFollowerCount / DAY_MINUTE;
        } else if (!StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 150;
            followChance = fakeFollowerCount / (30 * DAY_MINUTE);

            fakeLikeCount = 10;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace) {
            fakeFollowerCount = 100;
            followChance = fakeFollowerCount / (30 * DAY_MINUTE);
        }

        if (Math.random() < followChance && corgiFakeService.countFakeFollower(profile.getUserId()) < fakeFollowerCount) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance && corgiFakeService.countFakeLike(activityId) < fakeLikeCount) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }
    }

    private void influencer(UserProfile profile, UserDetail userDetail) {
        Integer fakeFollowerCount = 0;
        Integer fakeLikeCount = 0;
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = corgiFakeService.getLastActivity(profile.getUserId(), profile.getFollowTime());
        boolean hasBoard = corgiBillboardService.countOnBoard(profile.getUserId()) > 0;
        double followChance = 0.0;
        double likeChance = 0.0;
        Integer countFollow = corgiFakeService.countFakeFollower(profile.getUserId());
        if (hasBoard && hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 10300;
            followChance = 10000 / (30 * DAY_MINUTE);
            if (countFollow < 300) {
                followChance += 300 / DAY_MINUTE;
            }

            fakeLikeCount = 100;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace && !StringUtils.isEmpty(activityId)) {
            fakeFollowerCount = 10000;
            followChance = fakeFollowerCount / (30 * DAY_MINUTE);

            fakeLikeCount = 50;
            likeChance = fakeLikeCount / DAY_MINUTE;
        } else if (hasFace) {
            fakeFollowerCount = 1000;
            followChance = fakeFollowerCount / (30 * DAY_MINUTE);
        }

        if (countFollow < fakeFollowerCount
                && Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance && corgiFakeService.countFakeLike(activityId) < fakeLikeCount) {
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
