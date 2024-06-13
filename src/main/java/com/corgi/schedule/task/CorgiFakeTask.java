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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
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
    //private static final String commentActivity = "comment_activity_";

//    private Cache<String, List<String>> userListCache = CacheBuilder.newBuilder()
//            .expireAfterWrite(1L, TimeUnit.HOURS)
//            .initialCapacity(100)
//            .build();
//
//    private Cache<String, UserProfile> userDetailCache = CacheBuilder.newBuilder()
//            .initialCapacity(150000)
//            .build();
//
//    private Cache<String, HashMap<String, String>> activityCache = CacheBuilder.newBuilder()
//            .expireAfterWrite(10L, TimeUnit.MINUTES)
//            .initialCapacity(100)
//            .build();
//
//    private Cache<String, String> userDateCache = CacheBuilder.newBuilder()
//            .initialCapacity(150000)
//            .expireAfterWrite(10L, TimeUnit.MINUTES)
//            .build();

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
        corgiFakeService.updateFakeTime(userDetail.getUserId());
        log.info("running fake task...");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String c1 = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -2);
        String c3 = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, -4);
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
        List<CorgiVlogHot> manualHots = corgiVlogService.getHotVlog(hot, 1, 1000);
        hot.setCtime(c3);
        hot.setType(CorgiVlogHot.TYPE.AUTO);
        List<CorgiVlogHot> autoHots = corgiVlogService.getHotVlog(hot, 1, 1000);
//        if (!CollectionUtils.isEmpty(hotIds)) {
//            for (String id : hotIds) {
//                if (!onBoardActivityIds.contains(id)) {
//                    onBoardActivityIds.add(id);
//                }
//            }
//        }

        PaidBillboard billboardQuery = new PaidBillboard();
        billboardQuery.setDate(date);
        billboardQuery.setStatus(PaidBillboard.PASS);
        List<PaidBillboard> billboards = corgiBillboardService.queryPaidBillboard(billboardQuery, 1, 100);
        if (!CollectionUtils.isEmpty(billboards)) {
            for (PaidBillboard billboard : billboards) {
                if (!onBoardActivityIds.contains(billboard.getActivityId())) {
                    onBoardActivityIds.add(billboard.getActivityId());
                }
            }
        }
        SimpleDateFormat sdfActivity = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        List<CorgiActivity> onBoardActivity = corgiActivityService.getActivityByIds(onBoardActivityIds);
        for (CorgiActivity activity : onBoardActivity) {
            if (CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
                continue;
            }
            if ("check".equals(activity.getCheckStatus())) {
                continue;
            }
            if ("fail".equals(activity.getCheckStatus())) {
                continue;
            }
            if ("not_good".equals(activity.getCheckStatus())) {
                continue;
            }

            if (!StringUtils.isEmpty(activity.getId()) && Math.random() < 50.0 / DAY_MINUTE) {
                likeActivity(userDetail, activity.getId(), activity.getUserId());
            }
            if (Math.random() < 10.0 / DAY_MINUTE) {
                followUser(userDetail, activity.getUserId());
            }
//            String commentActivityId = redisTemplate.opsForValue().get(commentActivity + activity.getUserId());
//            if (StringUtils.isEmpty(commentActivityId) && !StringUtils.isEmpty(activity.getId())) {
//                redisTemplate.opsForValue().set(commentActivity + activity.getUserId(), activity.getId(), 30L, TimeUnit.DAYS);
//                commentActivityId = activity.getId();
//            }
//            if (!StringUtils.isEmpty(commentActivityId) && commentActivityId.equals(activity.getId())) {
//                try {
//                    if (Math.random() < 0.02 && sdfActivity.parse(activity.getCreateTime()).getTime() > System.currentTimeMillis() - 10000 * 60L) {
//                        commentActivity(userDetail, activity.getId());
//                    }
//                } catch (ParseException e) {
//                    e.printStackTrace();
//                }
//                if (Math.random() < 5.0 / DAY_MINUTE) {
//                    commentActivity(userDetail, activity.getId());
//                }
//            }
        }
        List<String> manualIds = new ArrayList<>();
        for (CorgiVlogHot vlogHot : manualHots) {
            if (!manualIds.contains(vlogHot.getActivityId())) {
                likeHot(vlogHot, userDetail, vlogHot.getExpectView() * 11 / 1000);
                manualIds.add(vlogHot.getActivityId());
            }
        }

        for (CorgiVlogHot vlogHot : autoHots) {
            if (vlogHot.getLikeCount() == null) {
                vlogHot.setLikeCount(0);
            }
            if (vlogHot.getLikeCount() > 30) {
                likeHot(vlogHot, userDetail, 35);
            } else {
                likeHot(vlogHot, userDetail, 5 + vlogHot.getLikeCount());
            }
        }

//        List<String> activityIds = redisTemplate.opsForList().range(activityKey, 0, -1);
//        if (CollectionUtils.isEmpty(activityIds)) {
//            redisTemplate.delete(activityAllKey);
//            ActivityQuery query = new ActivityQuery();
//            query.setCategory(CorgiActivity.CAT_IMAGE);
//            query.setPageSize(5000);
//            List<CorgiActivity> corgiActivities = corgiUserActivityService.queryActivity(query);
//            for (CorgiActivity corgiActivity : corgiActivities) {
//                String activityId = corgiActivity.getId();
//                String category = corgiActivity.getCategory();
//                if (!CorgiActivity.CAT_IMAGE.equals(category) && !CorgiActivity.CAT_VIDEO.equals(category) && !CorgiActivity.CAT_TEXT.equals(category)) {
//                    continue;
//                }
//                if ("check".equals(corgiActivity.getCheckStatus())) {
//                    continue;
//                }
//                if ("fail".equals(corgiActivity.getCheckStatus())) {
//                    continue;
//                }
//                if ("not_good".equals(corgiActivity.getCheckStatus())) {
//                    continue;
//                }
//                if (c1.compareTo(corgiActivity.getCreateTime()) < 0) {
//                    redisTemplate.opsForList().rightPush(activityKey, activityId);
//                } else if (c3.compareTo(corgiActivity.getCreateTime()) < 0) {
//                    redisTemplate.opsForList().rightPush(activityAllKey, activityId);
//                } else {
//                    break;
//                }
//                String key = creatorKey + activityId;
//                if (!redisTemplate.hasKey(key)) {
//                    redisTemplate.opsForValue().set(key, corgiActivity.getUserId(), 3L, TimeUnit.DAYS);
//                }
//            }
//            redisTemplate.expire(activityKey, 10l, TimeUnit.MINUTES);
//            activityIds = redisTemplate.opsForList().range(activityKey, 0, -1);
//        }
//
//        List<String> creatorIds = new ArrayList<>();
//        for (String activityId : activityIds) {
//
//            String userId = redisTemplate.opsForValue().get(creatorKey + activityId);
//            if (StringUtils.isEmpty(userId)) {
//                continue;
//            }
//            if (!creatorIds.contains(userId)) {
//                creatorIds.add(userId);
//                String datesKey = publishDate.concat(userId);
//                String dateCountStr = redisTemplate.opsForValue().get(datesKey);
//                Integer dateCount;
//                if (StringUtils.isEmpty(dateCountStr)) {
//                    ActivityQuery query = new ActivityQuery();
//                    query.setUserId(userId);
//                    query.setEndTime(c3);
//                    dateCount = corgiUserActivityService.countActivityDate(query);
//                    if (dateCount >= 3) {
//                        query.setEndTime(c7);
//                        dateCount = corgiUserActivityService.countActivityDate(query);
//                    }
//
//                    redisTemplate.opsForValue().set(datesKey, dateCount + "", 10l, TimeUnit.MINUTES);
//                } else {
//                    dateCount = Integer.valueOf(dateCountStr);
//                }
//                if (dateCount >= 7 && Math.random() < 5.0 / DAY_MINUTE) {
//                    followUser(userDetail, userId);
//                }
//            }
//
////            List<String> topics = corgiToolService.getActivityTopic(activityId);
////            if (topics.contains("57") && Math.random() < 20.0 / DAY_MINUTE) {
////                likeActivity(userDetail, activityId, userId);
////            }
//            Double likeChance = this.countLikeChance(activityId, userId, userDetail);
//            String influencerUserKey = influencerKey + userId;
//            String avatarStatus = redisTemplate.opsForValue().get(influencerUserKey);
//            if (avatarStatus == null) {
//                UserDetail createUser = corgiUserService.getUserDetailBasic(userId);
//                if (createUser != null) {
//                    avatarStatus = createUser.getAvatarStatus();
//                    if (avatarStatus == null) {
//                        avatarStatus = "";
//                    }
//                    redisTemplate.opsForValue().set(influencerUserKey, avatarStatus, 20l, TimeUnit.HOURS);
//                }
//            }
//
//            if ("influencer".equals(avatarStatus)) {
//                likeChance += 15;
//            }
//            if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance / DAY_MINUTE) {
//                likeActivity(userDetail, activityId, userId);
//            }
//        }
//
//        List<String> allActivityIds = redisTemplate.opsForList().range(activityAllKey, 0, -1);
//        for (String activityId : allActivityIds) {
//            String userId = redisTemplate.opsForValue().get(creatorKey + activityId);
//            if (!creatorIds.contains(userId)) {
//                creatorIds.add(userId);
//            }
//            Double likeChance = this.countAllLikeChance(activityId, userId);
//            CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
//            if (CorgiActivity.CAT_ACTIVITY.equals(activity.getCategory())) {
//                log.info(activityId);
//            }
//            if (!StringUtils.isEmpty(activityId) && Math.random() < likeChance / DAY_MINUTE) {
//                likeActivity(userDetail, activityId, userId);
//            }
//        }
//        if (allActivityIds.size() > 0) {
//            String commentActivityId = allActivityIds.get(new Random().nextInt(allActivityIds.size()) + 1);
//            if (!StringUtils.isEmpty(commentActivityId) && Math.random() < 300 / DAY_MINUTE) {
//                commentActivity(userDetail, commentActivityId);
//            }
//        }

        ActivityComment queryComment = new ActivityComment();
        queryComment.setCtime(c1);
        List<ActivityComment> activityComments = corgiCommentService.listComment(queryComment, 100);
        List<String> commentUserIds = new ArrayList<>();
        for (ActivityComment comment : activityComments) {
            if ("fake".equals(comment.getStatus())) {
                continue;
            }
            String commentUserId = comment.getCommentUserId();
            String activityId = comment.getActivityId();
            String userId = comment.getUserId();
            if (userId == null || userId.equals(commentUserId)) {
                continue;
            }
            String influencerUserKey = influencerKey + userId;
            String avatarStatus = redisTemplate.opsForValue().get(influencerUserKey);
            if (avatarStatus == null) {
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
                CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
                if (!CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
                    likeActivity(userDetail, activityId, userId);
                }
            }
            if (!commentUserIds.contains(commentUserId)) {
                commentUserIds.add(commentUserId);
                if (Math.random() < 5.0 / DAY_MINUTE) {
                    followUser(userDetail, commentUserId);
                }
            }
        }

    }

    private void likeHot(CorgiVlogHot vlogHot, UserDetail userDetail, Integer likeCount) {
        String statusKey = "activity_check_status" + vlogHot.getActivityId();
        String creatorKey = "activity_creator" + vlogHot.getActivityId();

        String checkStatus = redisTemplate.opsForValue().get(statusKey);
        String creator = redisTemplate.opsForValue().get(creatorKey);
        if (StringUtils.isEmpty(checkStatus) || StringUtils.isEmpty(creator)) {
            CorgiActivity activity = corgiActivityFeedService.getActivityById(vlogHot.getActivityId());
            checkStatus = activity.getCheckStatus();
            creator = activity.getUserId();
            if (StringUtils.isEmpty(checkStatus) || StringUtils.isEmpty(creator)) {
                return;
            }
            if (!"check".equals(activity.getCheckStatus())) {
                redisTemplate.opsForValue().set(statusKey, activity.getCheckStatus(), 3l, TimeUnit.DAYS);
            }
            redisTemplate.opsForValue().set(creator, creator, 3l, TimeUnit.DAYS);
        }
        if ("fail".equals(checkStatus) || "check".equals(checkStatus) || "not_good".equals(checkStatus)) {
            return;
        }
        if (Math.random() < likeCount / DAY_MINUTE) {
            likeActivity(userDetail, vlogHot.getActivityId(), creator);
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
            redisTemplate.opsForList().rightPushAll(key, users);
            redisTemplate.expire(key, 1l, TimeUnit.HOURS);
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    private Double countAllLikeChance(String activityId, String userId) {
        if (StringUtils.isEmpty(userId)) {
            return 0.0;
        }
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

    private void commentActivity(UserDetail userDetail, String activity) {
        ActivityComment activityComment = new ActivityComment();
        activityComment.setActivityId(activity);
        activityComment.setContent(corgiFakeService.getFakeComment());
        activityComment.setCommentUserId(userDetail.getUserId());
        activityComment.setStatus("fake");
        activityComment.setParentCommentId("0");
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activity));
        if (CollectionUtils.isEmpty(activityList)) {
            return;
        }
        activityComment.setUserId(activityList.get(0).getUserId());

        activityComment = corgiCommentService.addActivityComment(activityComment);
        HashMap extra = new HashMap();
        extra.put("activityId", activityComment.getActivityId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        if (!activityComment.getUserId().equals(activityComment.getCommentUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityComment.getCommentUserId())
                    .targetUserId(activityComment.getUserId())
                    .message(PushMessage.USER_COMMENT)
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
                        .message(PushMessage.USER_LIKE)
                        .extra(extra)
                        .build());
            }
        }
    }
}
