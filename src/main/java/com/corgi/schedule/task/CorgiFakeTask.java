package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.ActivityQuery;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Component
@Slf4j
@EnableAsync
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
    @Reference
    private CorgiVisitService corgiVisitService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserDateService corgiUserDateService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    private static final Double DAY_MINUTE = 14 * 60.0 * 10;

    private static final String LAST_ACTIVITY = "last_activity";

    private static final String activityKey = "recent_activity";
    private static final String activityAllKey = "all_activity";
    private static final String creatorKey = "activity_creator";
    private static final String influencerKey = "is_influencer";
    private static final String publishDate = "publish_date_";

    private Cache<String, List<String>> userListCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1L, TimeUnit.HOURS)
            .initialCapacity(100)
            .build();

    private Cache<String, UserProfile> userDetailCache = CacheBuilder.newBuilder()
            .initialCapacity(150000)
            .build();

    private Cache<String, HashMap<String, String>> activityCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10L, TimeUnit.MINUTES)
            .initialCapacity(100)
            .build();

    private Cache<String, String> userDateCache = CacheBuilder.newBuilder()
            .initialCapacity(150000)
            .expireAfterWrite(10L, TimeUnit.MINUTES)
            .build();

    //@Async(value = "asyncExecutor")
    @Scheduled(cron = "0/6 * 9-22 * * *")
    //@Scheduled(fixedRate = 6000)
    public void runFakeTask() {
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
        log.info("running fake task...");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String c1 = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -2);
        String c3 = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -4);
        String c7 = sdf.format(calendar.getTime());
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        List<String> users = this.getOnBoardUsers();
        for (String userId : users) {
            if (Math.random() < 1.0 / DAY_MINUTE) {
                followUser(userDetail, userId);
            }
        }

        String date = sdf1.format(new Date());
        List<String> onBoardActivityIds = corgiBillboardService.getActivityBillboard(date);
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setStatus(CorgiVlogHot.STATUS.OPEN);
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        hot.setCtime(c1);
        List<String> hotIds = corgiVlogService.getHotVlog(hot, 1, 100).stream().map(h -> h.getActivityId()).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(hotIds)) {
            for (String id : hotIds) {
                if (!onBoardActivityIds.contains(id)) {
                    onBoardActivityIds.add(id);
                }
            }
        }
        List<CorgiActivity> onBoardActivity = corgiActivityService.getActivityByIds(onBoardActivityIds);
        for (CorgiActivity activity : onBoardActivity) {
            if (!StringUtils.isEmpty(activity.getId()) && Math.random() < 50.0 / DAY_MINUTE) {
                likeActivity(userDetail, activity.getId(), activity.getUserId());
            }
            if (Math.random() < 10.0 / DAY_MINUTE) {
                followUser(userDetail, activity.getUserId());
            }
        }


        List<String> activityIds = redisTemplate.opsForList().range(activityKey, 0, -1);
        if (CollectionUtils.isEmpty(activityIds)) {
            redisTemplate.delete(activityAllKey);
            ActivityQuery query = new ActivityQuery();
            query.setPageSize(5000);
            List<CorgiActivity> corgiActivities = corgiUserActivityService.queryActivity(query);
            for (CorgiActivity corgiActivity : corgiActivities) {
                String activityId = corgiActivity.getId();
                if (c1.compareTo(corgiActivity.getCreateTime()) > 0) {
                    redisTemplate.opsForList().rightPush(activityKey, activityId);
                } else if (c3.compareTo(corgiActivity.getCreateTime()) > 0) {
                    redisTemplate.opsForList().rightPush(activityAllKey, activityId);
                } else {
                    break;
                }
                String key = creatorKey + activityId;
                if (!redisTemplate.hasKey(key)) {
                    redisTemplate.opsForValue().set(key, corgiActivity.getUserId(), 3L, TimeUnit.DAYS);
                }
            }
            redisTemplate.expire(activityKey, 10l, TimeUnit.MINUTES);
            activityIds = redisTemplate.opsForList().range(activityKey, 0, -1);
        }
        List<String> creatorIds = new ArrayList<>();
        for (String activityId : activityIds) {

            String userId = redisTemplate.opsForValue().get(creatorKey + activityId);
            if (!creatorIds.contains(userId)) {
                creatorIds.add(userId);
                String datesKey = publishDate.concat(userId);
                String dateCountStr = redisTemplate.opsForValue().get(datesKey);
                Integer dateCount;
                if (StringUtils.isEmpty(dateCountStr)) {
                    ActivityQuery query = new ActivityQuery();
                    query.setUserId(userId);
                    query.setEndTime(c3);
                    dateCount = corgiUserActivityService.countActivityDate(query);
                    if (dateCount >= 3) {
                        query.setEndTime(c7);
                        dateCount = corgiUserActivityService.countActivityDate(query);
                    }

                    redisTemplate.opsForValue().set(datesKey, dateCount + "", 10l, TimeUnit.MINUTES);
                } else {
                    dateCount = Integer.valueOf(dateCountStr);
                }
                if (dateCount >= 7 && Math.random() < 5.0 / DAY_MINUTE) {
                    followUser(userDetail, userId);
                }
            }

            List<String> topics = corgiToolService.getActivityTopic(activityId);
            if (topics.contains("57") && Math.random() < 20.0 / DAY_MINUTE) {
                likeActivity(userDetail, activityId, userId);
            }

            Double likeChance = this.countLikeChance(activityId, userId, userDetail);
            if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance / DAY_MINUTE) {
                likeActivity(userDetail, activityId, userId);
            }
        }

        List<String> allActivityIds = redisTemplate.opsForList().range(activityAllKey, 0, -1);
        for (String activityId : allActivityIds) {
            String userId = redisTemplate.opsForValue().get(creatorKey + activityId);
            if (!creatorIds.contains(userId)) {
                creatorIds.add(userId);
            }
            Double likeChance = this.countAllLikeChance(activityId, userId);
            if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance / DAY_MINUTE) {
                likeActivity(userDetail, activityId, userId);
            }
        }

        ActivityComment queryComment = new ActivityComment();
        queryComment.setCtime(c1);
        List<ActivityComment> activityComments = corgiCommentService.listComment(queryComment, 100);
        List<String> commentUserIds = new ArrayList<>();
        for (ActivityComment comment : activityComments) {
            String commentUserId = comment.getCommentUserId();
            String activityId = comment.getActivityId();
            String userId = comment.getUserId();
            if (userId.equals(commentUserId)) {
                continue;
            }
            String influencerUserKey = influencerKey + userId;
            String avatarStatus = redisTemplate.opsForValue().get(influencerUserKey);
            if (StringUtils.isEmpty(avatarStatus)) {
                UserDetail commentUser = corgiUserService.getUserDetailBasic(commentUserId);
                if (commentUser == null) {
                    continue;
                }
                avatarStatus = commentUser.getAvatarStatus();
                if (avatarStatus == null) {
                    avatarStatus = "";
                }
                redisTemplate.opsForValue().set(influencerUserKey, avatarStatus, 20l, TimeUnit.HOURS);
            }
            double likeChance = 5;

            if ("influencer".equals(avatarStatus)) {
                likeChance = 10;
            }
            if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance / DAY_MINUTE) {
                likeActivity(userDetail, activityId, userId);
            }
            if (!commentUserIds.contains(commentUserId)) {
                commentUserIds.add(commentUserId);
                if (Math.random() < 5.0 / DAY_MINUTE) {
                    followUser(userDetail, commentUserId);
                }
            }
        }


    }

    private List<String> getOnBoardUsers() {
        String key = "on_board";
        if (!redisTemplate.hasKey(key)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -7);
            UserDetail userDetail = new UserDetail();
            userDetail.setAvatarCheckStatus(UserDetail.VERIFIED);
            userDetail.setCtime(sdf.format(calendar.getTime()));
            List<UserProfile> newUsers = corgiUserService.searchUsers(userDetail, null, 1, 10000);
            List<String> users = newUsers.stream().map(u -> u.getUserId()).collect(Collectors.toList());
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
//            Calendar calendar = Calendar.getInstance();
//            List<UserProfile> onBoardUsers = corgiBillboardService.getBillboard(sdf.format(calendar.getTime()));
//            List<String> users = onBoardUsers.stream().map(u -> u.getUserId()).collect(Collectors.toList());
            redisTemplate.opsForList().rightPushAll(key, users);
            redisTemplate.expire(key, 1l, TimeUnit.HOURS);
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    private Double countAllLikeChance(String activityId, String userId) {
        String likeChanceKey = "activity_chance_like_" + activityId;
        String chanceStr = redisTemplate.opsForValue().get(likeChanceKey);
        String datesKey = publishDate.concat(userId);
        String dateCountStr = redisTemplate.opsForValue().get(datesKey);
        if (StringUtils.isEmpty(chanceStr)) {
            Double likeChance = 0.0;
            String likeKey = "activity_like_" + activityId;
            Integer likeCount = Integer.valueOf(redisTemplate.opsForValue().get(likeKey));
            if (likeCount >= 12) {
                likeChance += 70.0 / 3.0;
            } else if (likeCount >= 6) {
                likeChance += 50.0 / 3.0;
            }
            if (!StringUtils.isEmpty(dateCountStr)) {
                Integer dateCount = Integer.valueOf(dateCountStr);
                if (dateCount >= 7) {
                    likeChance += 20;
                } else if (dateCount >= 3) {
                    likeChance += 10.0;
                }
            }
            redisTemplate.opsForValue().set(likeChanceKey, likeChance + "", 1l, TimeUnit.HOURS);
            return likeChance;
        } else {
            return Double.valueOf(chanceStr);
        }
    }

    private Double countLikeChance(String activityId, String userId, UserDetail userDetail1) {
        String likeChanceKey = "activity_chance_like_" + activityId;
        String chanceStr = redisTemplate.opsForValue().get(likeChanceKey);
        if (StringUtils.isEmpty(chanceStr)) {
            double likeChance = 20;
            String influencerUserKey = influencerKey + userId;
            String avatarStatus = redisTemplate.opsForValue().get(influencerUserKey);
            if (StringUtils.isEmpty(avatarStatus)) {
                UserDetail userDetail = corgiUserService.getUserDetailBasic(userId);
                if (userDetail == null) {
                    redisTemplate.opsForValue().set(likeChanceKey, "0.0", 3l, TimeUnit.DAYS);
                    return 0.0;
                }
                avatarStatus = userDetail.getAvatarStatus();
                if (avatarStatus == null) {
                    avatarStatus = "";
                }
                redisTemplate.opsForValue().set(influencerUserKey, avatarStatus, 20l, TimeUnit.HOURS);
            }
            if ("influencer".equals(avatarStatus)) {
                likeChance += 10;
            }
            Long likeCount = corgiLikeService.countActivityLike(activityId);
            String likeKey = "activity_like_" + activityId;
            redisTemplate.opsForValue().set(likeKey, likeCount + "", 3L, TimeUnit.DAYS);
            likeChance += countAllLikeChance(activityId, userId);
            redisTemplate.opsForValue().set(likeChanceKey, likeChance + "", 10l, TimeUnit.MINUTES);
            return likeChance;
        } else {
            String likeKey = "activity_like_" + activityId;
            String likeCountStr = redisTemplate.opsForValue().get(likeKey);
            if (!StringUtils.isEmpty(likeCountStr)) {
                Long likeCount = Long.valueOf(likeCountStr);
                if (likeCount >= 12 && Math.random() < 15.0 / DAY_MINUTE) {
                    followUser(userDetail1, userId);
                } else if (likeCount >= 6 && Math.random() < 10.0 / DAY_MINUTE) {
                    followUser(userDetail1, userId);
                }
            }

            return Double.valueOf(chanceStr);
        }
    }

    //@Async(value = "asyncExecutor")
    //@Scheduled(cron = "0/6 * 9-22 * * *")
    public void runUserFakeTask() {
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
        calendar.add(Calendar.DATE, -3);
        String date = sdf.format(calendar.getTime());
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByUserIds(Arrays.asList("254215", "254188", "804", "423", "2058"), CorgiActivity.CAT_IMAGE, 1, 1000);
        Double likeChance = 25.0 / DAY_MINUTE;
        for (CorgiActivity corgiActivity : corgiActivities) {
            if (!StringUtils.isEmpty(corgiActivity.getId()) && Math.random() < likeChance) {
                likeActivity(userDetail, corgiActivity.getId(), corgiActivity.getUserId());
            }
        }

//        List<UserProfile> onBoardUsers = corgiBillboardService.getBillboard(sdf.format(new Date()));
//        do {
//            List<UserProfile> profiles = this.getBasicUserDetailByPage(page, pageSize);
//            page++;
//            if (CollectionUtils.isEmpty(profiles)) {
//                break;
//            }
//            for (UserProfile profile : profiles) {
//                if (userDetail.getUserId().equals(profile.getUserId())) {
//                    continue;
//                }
//                if ("influencer".equals(profile.getAvatarStatus())) {
//                    influencer(profile, userDetail, hasOnBoard(onBoardUsers, profile));
//                } else if (date.compareTo(profile.getCreateTime()) > 0) {
//                    oldCorgier(profile, userDetail, hasOnBoard(onBoardUsers, profile));
//                } else {
//                    newCorgier(profile, userDetail, hasOnBoard(onBoardUsers, profile));
//                }
//            }
//        } while (true);
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

    private void newCorgier(UserProfile profile, UserDetail userDetail, Boolean hasBoard) {
        boolean hasFace = UserDetail.VERIFIED.equals(profile.getAvatarCheckStatus());

        String dateStatus = getDateStatus(profile.getUserId());
        boolean hasDate = CorgiDate.OPEN.equals(dateStatus);

        String activityId = getLastActivity(profile.getUserId());
        double followChance = 0.0;
        if (hasDate) {
            followChance += 10.0 / (3.0 * DAY_MINUTE);
        }
        double likeChance = 0.0;
        if (hasFace) {
            followChance += 5.0 / (3.0 * DAY_MINUTE);
        }
        if (hasBoard) {
            followChance += 50.0 / DAY_MINUTE;
        }
        if (!"-1".equals(activityId)) {
            likeChance = 20.0 / DAY_MINUTE;
            if (activityId.contains("#")) {
                activityId = activityId.replaceAll("#", "");
                followChance += 10.0 / (3.0 * DAY_MINUTE);
                likeChance += 30.0 / DAY_MINUTE;
            }
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }

    }

    private void oldCorgier(UserProfile profile, UserDetail userDetail, Boolean hasBoard) {
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());


//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
//        String today = sdf.format(new Date());
//        String finalTime;
//        try {
//            sdf.parse(profile.getCreateTime());
//            Calendar calendar = sdf.getCalendar();
//            calendar.add(Calendar.DATE, 60);
//            finalTime = sdf.format(calendar.getTime());
//        } catch (ParseException e) {
//            e.printStackTrace();
//            return;
//        }
        String activityId = getLastActivity(profile.getUserId());
        double followChance = 0.0;
        double likeChance = 0.0;

//        if (today.compareTo(finalTime) < 0) {
//            if (hasFace) {
//                followChance = 20.0 / (30.0 * DAY_MINUTE);
//            } else {
//                followChance = 10.0 / (30.0 * DAY_MINUTE);
//            }
//        }
//        if (!"-1".equals(activityId)) {
//            followChance += 1 / DAY_MINUTE;
//        }
//        if (profile.getTime() != null && System.currentTimeMillis() - 24 * 1000 * 3600 > profile.getTime()) {
//            if (hasFace) {
//                followChance += 1.0 / DAY_MINUTE;
//            }
//        }
        if (hasBoard) {
            followChance += 50.0 / DAY_MINUTE;
        }
        if (!"-1".equals(activityId)) {
            if (activityId.contains("#")) {
                activityId = activityId.replaceAll("#", "");
            }
            likeChance += 10.0 / DAY_MINUTE;
            String dateStatus = getDateStatus(profile.getUserId());
            if (CorgiDate.OPEN.equals(dateStatus)) {
                likeChance += 5.0 / DAY_MINUTE;
            }
            if (hasFace) {
                likeChance *= 1.5;
            }
        }

        if (Math.random() < followChance) {
            followUser(userDetail, profile.getUserId());
        }
        if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance) {
            likeActivity(userDetail, activityId, profile.getUserId());
        }
    }

    private void influencer(UserProfile profile, UserDetail userDetail, Boolean hasBoard) {
        boolean hasFace = !UserDetail.NO_FACE.equals(profile.getAvatarCheckStatus());
        String activityId = getLastActivity(profile.getUserId());
        double followChance = 0.0;
        double likeChance = 0.0;
//        followChance = 100 / (30 * DAY_MINUTE);
//        if (hasFace) {
//            followChance += 200 / (30 * DAY_MINUTE);
//        }
        if (!"-1".equals(activityId)) {
            if (activityId.contains("#")) {
                activityId = activityId.replaceAll("#", "");
                followChance += 10.0 / DAY_MINUTE;
            }
            likeChance += 20.0 / DAY_MINUTE;
            String dateStatus = getDateStatus(profile.getUserId());
            if (CorgiDate.OPEN.equals(dateStatus)) {
                likeChance += 10.0 / DAY_MINUTE;
            }
            if (hasFace) {
                likeChance *= 1.5;
            }
        }

        if (hasBoard) {
            followChance += 50.0 / DAY_MINUTE;
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
            corgiVisitService.visit(userDetail.getUserId(), followId);
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
                        .message(PushMessage.USER_LIKE)
                        .extra(extra)
                        .build());
            }
        }
    }

    private String getDateStatus(String userId) {
        String dateStatus = userDateCache.getIfPresent(userId);
        if (StringUtils.isEmpty(dateStatus)) {
            CorgiDate date = corgiUserDateService.getDateByUserId(userId);
            dateStatus = date.getStatus();
            userDateCache.put(userId, date.getStatus());
        }
        return dateStatus;
    }

    private String getLastActivity(String userId) {
        HashMap<String, String> activityMap = activityCache.getIfPresent(LAST_ACTIVITY);
        if (activityMap == null) {
            activityMap = new HashMap<>();
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -1);
            String lastDay = sdf2.format(calendar.getTime());

            List<CorgiActivity> corgiActivities = corgiFakeService.getActivityByDate(lastDay);
            for (CorgiActivity activity : corgiActivities) {
                activityMap.put(activity.getUserId(), activity.getId());
            }

            calendar.add(Calendar.DATE, -1);
            String lastTwoDay = sdf2.format(calendar.getTime());
            corgiActivities = corgiFakeService.getHotActivityByDate(lastTwoDay, 1);
            for (CorgiActivity activity : corgiActivities) {
                activityMap.put(activity.getUserId(), "#" + activity.getId());
            }
        }
        activityCache.put(LAST_ACTIVITY, activityMap);
        String activityId = activityMap.get(userId);
        if (StringUtils.isEmpty(activityId)) {
            activityId = "-1";
        }
        return activityId;
    }

    private List<UserProfile> getBasicUserDetailByPage(Integer page, Integer pageSize) {
        List<UserProfile> result = new ArrayList<>();
        String userListKey = "user_list" + page;
        List<String> userIds = userListCache.getIfPresent(userListKey);
        if (!CollectionUtils.isEmpty(userIds)) {
            for (String userId : userIds) {
                UserProfile userProfile = userDetailCache.getIfPresent(userId);
                if (userProfile != null) {
                    result.add(userProfile);
                }
            }
        } else {
            userIds = new ArrayList<>();
            result = corgiUserService.getBasicUserDetailByPage(page, pageSize);
            if (CollectionUtils.isEmpty(result)) {
                userListCache.put(userListKey, Arrays.asList("empty"));
            } else {
                for (UserProfile userProfile : result) {
                    userIds.add(userProfile.getUserId());
                    userDetailCache.put(userProfile.getUserId(), userProfile);
                }
                userListCache.put(userListKey, userIds);
            }
        }
        return result;
    }
}
