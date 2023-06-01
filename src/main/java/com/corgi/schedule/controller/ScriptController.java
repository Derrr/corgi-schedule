package com.corgi.schedule.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.MatchRefresher;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.ActivityQuery;
import com.corgi.entity.CorgiPic;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.*;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("script")
public class ScriptController {
    @Autowired
    private MapService mapService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiBarService corgiBarService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference(retries = 1, timeout = 100000)
    private CorgiUserRecommendService corgiUserRecommendService;
    @Reference
    private CorgiSystemMessageService corgiSystemMessageService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiUserDateService corgiUserDateService;
    @Autowired
    private HxPushMessageService hxPushMessageService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private TaskService taskService;
    @Autowired
    private MQService mqService;
    @Autowired
    private FaceDetectedService faceDetectedService;
    @Autowired
    private AliyunGreenService aliyunGreenService;

    @GetMapping("init_station")
    public String initStation(@RequestParam("city") String city) {
        return mapService.initCity(city);
    }

    @GetMapping("refresh_match")
    public String refreshMatch(@RequestParam("userId") String userId) {
        MatchRefresher matchRefresher = new MatchRefresher();
        matchRefresher.setUserId(userId);
        rabbitTemplate.convertAndSend(CorgiQueueName.REFRESH_MATCH_QUEUE, matchRefresher);
        return "success";
    }

    @GetMapping("refresh_activity")
    public String refreshActivity() {
        corgiActivityService.refreshActivity();
        return "success";
    }

    @PostMapping("push_message")
    public String pushMessage(@RequestBody HashMap hashMap) {
        String userId = (String) hashMap.get("userId");
        String content = (String) hashMap.get("content");
        HashMap extra = (HashMap) hashMap.get("extra");
        SystemMessage systemMessage = new SystemMessage();
        systemMessage.setContent(content);
        log.info("extra: {} ", extra);
        if (extra == null) {
            extra = new HashMap();
        }
        hxPushMessageService.sendMessage(systemMessage, Arrays.asList(userId), extra);
        return "success";
    }

    @GetMapping("push_system_message")
    public String pushSystemMessage(@RequestParam("id") String id, @RequestParam("userId") String userId) {
        SystemMessage message = corgiSystemMessageService.getMessageDetail(id);
        HashMap extra = new HashMap();
        if ("907".equals(message.getType())) {
            extra.put("content", JSON.parse(message.getContent()));
            message.setContent(message.getTitle());
        }
        return hxPushMessageService.sendMessage(message, Arrays.asList("corgi" + userId), extra);
    }


    @PostMapping("push_mq_message")
    public String pushMQMessage(@RequestBody HashMap hashMap) {
        String userId = (String) hashMap.get("userId");
        String content = (String) hashMap.get("content");
        HashMap extra = (HashMap) hashMap.get("extra");
        String type = (String) hashMap.get("type");
        if (extra == null) {
            extra = new HashMap();
        }

        SystemMessage systemMessage = new SystemMessage();
        systemMessage.setContent(content);
        PushMessage pushMessage = new PushMessage();
        pushMessage.setSourceUserId("1");
        pushMessage.setTargetUserId(userId);
        pushMessage.setType(type);
        pushMessage.setMessage(content);
        pushMessage.setExtra(extra);
        mqService.sendMessage(pushMessage);
        return "success";
    }


    @GetMapping("add_user_trace")
    public String addUserTrace(@RequestParam("date") String date) {
        log.info("into add_user_trace...." + date);
        taskService.countUserTrace(date);
        return "success";
    }

    @GetMapping("repair_video_cover")
    public String repairVideoCover() {
        CorgiActivity search = new CorgiActivity();
        search.setCoverUrl("Expires");
        search.setStatus(CorgiActivity.NOT_DELETED);
        for (int i = 1; i < 5; i++) {
            List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(search, 1, 100);
            if (CollectionUtils.isEmpty(activityList)) {
                break;
            }
            for (CorgiActivity activity : activityList) {
                log.info("updating:{} cover:{} ", activity.getId(), activity.getCoverUrl());
                corgiActivityService.updateByColumn(activity.getId(), "coverUrl", activity.getCoverUrl().split("\\?Expires")[0]);
            }
        }
        return "success";
    }

    @GetMapping("repair_birthday")
    public String repairBirthday() {
        for (int i = 0; i < 10; i++) {
            log.info("getting user id..." + i);
            UserDetail userDetail = corgiUserService.getUserDetail(i + "", "");
            if (userDetail != null && !StringUtils.isEmpty(userDetail.getBirthday())) {
                log.info("user id:{} birthday:{}", i, userDetail.getBirthday());
                String[] dates = userDetail.getBirthday().split("/");
                if (dates.length != 3) {
                    continue;
                }
                String year = dates[0];
                String month = dates[1];
                String day = dates[2];
                boolean update = false;
                if (month.length() == 1) {
                    month = "0" + month;
                    update = true;
                }
                if (day.length() == 1) {
                    day = "0" + day;
                    update = true;
                }
                if (update) {
                    UserDetail updateDetail = new UserDetail();
                    updateDetail.setUserId(i + "");
                    updateDetail.setBirthday(year + "/" + month + "/" + day);
                    log.info("updating user id:{} birthday:{}", i, updateDetail.getBirthday());
                    corgiUserService.updateDetail(updateDetail);
                }
            }

        }
        return "success";
    }

    @GetMapping("refresh_position")
    public String refreshPosition() {
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 1000;
        int noPeopleCount = 0;
        long maxUpdateTime = 0L;
        long minUpdateTime = Long.MAX_VALUE;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            log.info("page ={}, size={} ", page, userPositionList.size());
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    log.info("checking ... " + userPosition.getUserId() + " page = " + page);
                    List<Point> points = redisTemplate.opsForGeo().position("user", userPosition.getUserId());
                    redisTemplate.opsForGeo().remove("user", userPosition.getUserId());
                    if (userPosition.getLng() == null || userPosition.getLng() > 180 || userPosition.getLng() < -180) {
                        continue;
                    }
                    if (userPosition.getLat() == null || userPosition.getLat() > 90 || userPosition.getLat() < -90) {
                        continue;
                    }
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    if (userPosition.getUptime() == null) {
                        continue;
                    }
                    log.info("checking ... " + userPosition.getUserId());
                    if (CollectionUtils.isEmpty(points)) {
                        noPeopleCount++;
                        if (userPosition.getUptime() > maxUpdateTime) {
                            maxUpdateTime = userPosition.getUptime();
                        }
                        if (userPosition.getUptime() < minUpdateTime) {
                            minUpdateTime = userPosition.getUptime();
                        }
                        log.info("unexist userid: {} , uptime: {} ", userPosition.getUserId(), userPosition.getUptime());
                    } else {
                        Point point = points.get(0);
                        if (point == null) {
                            noPeopleCount++;
                            if (userPosition.getUptime() > maxUpdateTime) {
                                maxUpdateTime = userPosition.getUptime();
                            }
                            if (userPosition.getUptime() < minUpdateTime) {
                                minUpdateTime = userPosition.getUptime();
                            }
                            log.info("unexist userid: {} , uptime: {} ", userPosition.getUserId(), userPosition.getUptime());
                        } else if (point.getX() == 0 || point.getY() == 0) {
                            noPeopleCount++;
                            if (userPosition.getUptime() > maxUpdateTime) {
                                maxUpdateTime = userPosition.getUptime();
                            }
                            if (userPosition.getUptime() < minUpdateTime) {
                                minUpdateTime = userPosition.getUptime();
                            }
                            log.info("0-0 userid: {} , uptime: {} ", userPosition.getUserId(), userPosition.getUptime());
                        }
                    }
                    log.info("adding ... " + userPosition.getUserId());
                    redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        log.info("no: {} max: {} min:{}", noPeopleCount, maxUpdateTime, minUpdateTime);
        return "" + noPeopleCount;
    }

    @GetMapping("clear_keys")
    public String clearKeys() {
        corgiUserMatchService.clearMatch();
        return "success";
    }

    @GetMapping("init_bar_city")
    public String initBarCity() {
        List<BarProfile> barProfiles = corgiBarService.getBarListByCity(null, null, null);
        for (BarProfile bar : barProfiles) {
            String[] cityArr = mapService.getCity(bar.getLat(), bar.getLng()).split("-");
            bar.setCity(cityArr[cityArr.length - 1]);
            corgiBarService.updateBarProfile(bar);
        }
        return "success";
    }

    @GetMapping("clear_business_activity")
    public String clearBusinessActivity() {
        CorgiActivity query = new CorgiActivity();
        query.setCategory(CorgiActivity.CAT_BUSINESS);
        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(query, 1, 2000);
        for (CorgiActivity business : corgiActivities) {
            log.info("business .. {} ", business);
            BarProfile barProfile = corgiBarService.getBarProfile(business.getUserId());
            log.info(" bar ... {} ", barProfile);
            if (barProfile == null) {
                corgiActivityService.removeActivity(business.getId());
            }
        }
        return "success";
    }

    @GetMapping("init_business_city")
    public String initBusinessCity() {
        CorgiActivity query = new CorgiActivity();
        query.setCategory(CorgiActivity.CAT_BUSINESS);
        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(query, 1, 1000);
        for (CorgiActivity business : corgiActivities) {
            log.info("business .. {} ", business);
            if (StringUtils.isEmpty(business.getCity())) {
                BarProfile barProfile = corgiBarService.getBarProfile(business.getUserId());
                log.info(" bar ... {} ", barProfile);
                if (barProfile != null && !StringUtils.isEmpty(barProfile.getCity())) {
                    business.setCity(barProfile.getCity());
                    corgiActivityService.updateCorgiActivity(business);
                }
            }
        }
        return "success";
    }

    @GetMapping("count_activity_publish")
    public String countActivity() {
        String dateMonthSeven = "2020/07/";
        String dateMonthSeven1 = "2020-07-";
        String dateMonthEight = "2020/08/";
        String dateMonthEight1 = "2020-08-";
        for (int i = 28; i <= 31; i++) {
            String date = dateMonthSeven + i;
            long activity = corgiActivityService.countPublishActivity(date);
            corgiStatisticService.addCount(CorgiStatistic.ACTIVITY, dateMonthSeven1 + i, activity);
        }
        for (int i = 1; i <= 9; i++) {
            String date = dateMonthEight + "0" + i;
            long activity = corgiActivityService.countPublishActivity(date);
            corgiStatisticService.addCount(CorgiStatistic.ACTIVITY, dateMonthEight1 + "0" + i, activity);
        }
        for (int i = 10; i <= 18; i++) {
            String date = dateMonthEight + i;
            long activity = corgiActivityService.countPublishActivity(date);
            corgiStatisticService.addCount(CorgiStatistic.ACTIVITY, dateMonthEight1 + i, activity);
        }
        return "success";
    }

    @GetMapping("check_avatar")
    public String checkAvatar(@RequestParam("avatar") String avatar) {
        CorgiPic pic = new CorgiPic();
        pic.setPicUrl(avatar);
        faceDetectedService.checkFace(pic, "1");
        return "success";
    }

    @GetMapping("init_date")
    public String initDate() {
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 1000;
        HashMap<String, String> dateTypeMap = new HashMap<>();
        List<DateType> dateTypes = corgiToolService.getDateTypes();
        for (DateType dateType : dateTypes) {
            dateTypeMap.put(dateType.getType(), dateType.getContent());
        }
        CorgiActivity query = new CorgiActivity();
        query.setCategory(CorgiActivity.CAT_ACTIVITY);
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    log.info("check user ... " + userPosition.getUserId());
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    String userId = userPosition.getUserId();
                    UserDetail userDetail = corgiUserService.getUserDetailBasic(userId);
                    if (userDetail != null) {
                        query.setUserId(userId);
                        CorgiDate corgiDate = new CorgiDate();
                        corgiDate.setUserId(userId);
                        query.setStatus(CorgiActivity.CREATED);
                        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(query, 1, 10);
                        if (!CollectionUtils.isEmpty(corgiActivities)
                                && corgiActivities.get(0) != null) {
                            CorgiActivity corgiActivity = corgiActivities.get(0);
                            String type = corgiActivity.getActivityType();
                            if (dateTypeMap.get(type) != null) {
                                corgiDate.setType(type);
                                corgiDate.setDetail(corgiActivity.getContent());
                                corgiUserDateService.addDate(corgiDate);
                            } else {
                                type = "不限";
                                corgiDate.setType(type);
                                corgiDate.setDetail(dateTypeMap.get(type));
                                corgiUserDateService.addDate(corgiDate);
                            }
                        } else {
                            query.setStatus(CorgiActivity.ENDED);
                            corgiActivities = corgiActivityService.searchCorgiActivity(query, 1, 10);
                            if (!CollectionUtils.isEmpty(corgiActivities)
                                    && corgiActivities.get(0) != null) {
                                CorgiActivity corgiActivity = corgiActivities.get(0);
                                String type = corgiActivity.getActivityType();
                                if (dateTypeMap.get(type) != null) {
                                    corgiDate.setType(type);
                                    corgiDate.setDetail(dateTypeMap.get(type));
                                    corgiUserDateService.addDate(corgiDate);
                                } else {
                                    type = "不限";
                                    corgiDate.setType(type);
                                    corgiDate.setDetail(dateTypeMap.get(type));
                                    corgiUserDateService.addDate(corgiDate);
                                }
                            } else if ("influencer".equals(userDetail.getAvatarStatus())) {
                                String type = "不限";
                                corgiDate.setType(type);
                                corgiDate.setDetail(dateTypeMap.get(type));
                                corgiUserDateService.addDate(corgiDate);
                            }
                        }
                    }
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        return "success";
    }

    @GetMapping("init_avatar")
    public String initAvatar() {
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 1000;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    log.info("check user ... " + userPosition.getUserId());
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    redisTemplate.opsForGeo().remove("user", userPosition.getUserId());
                    String userId = userPosition.getUserId();
                    UserDetail userDetail = corgiUserService.getUserDetail(userId, null);
                    if (userDetail != null && StringUtils.isEmpty(userDetail.getAvatarCheckStatus())) {
                        List<UserPic> userPics = corgiPicService.getUserPic(userId);
                        //List<CheckPic> checkPics = corgiPicService.getCheckPicBySourceId(CheckPic.AVATAR, userId);
                        if (!CollectionUtils.isEmpty(userPics)
                                && userPics.get(0) != null
                                && !CorgiPic.NEED_CHECK.equals(userPics.get(0).getStatus())) {
                            UserPic userPic = userPics.get(0);
                            userPic.setStatus(CorgiPic.NORMAL);
                            faceDetectedService.checkFace(userPic, userPic.getUserId());
                            log.info("check result ... " + userPic.getStatus());
                            UserDetail updateDetail = new UserDetail();
                            updateDetail.setAvatar(userPic.getPicUrl());
                            updateDetail.setAvatarDataId(userPic.getDataId());
                            updateDetail.setAvatarCheckStatus(userPic.getStatus());
                            updateDetail.setUserId(userId);
                            corgiUserService.updateDetail(updateDetail);
                            if (userPosition.getLng() == null || userPosition.getLng() > 180 || userPosition.getLng() < -180) {
                                continue;
                            }
                            if (userPosition.getLat() == null || userPosition.getLat() > 90 || userPosition.getLat() < -90) {
                                continue;
                            }
                            redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
                        }
                    }
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        return "success";
    }

    @GetMapping("init_recommend")
    public String initRecommend() {
        RecommendCalculater calculater = new RecommendCalculater();
        calculater.setUserId("8");
        log.info("recommend..." + calculater.getUserId());
        mqService.sendCalculater(calculater);
        //taskService.calculateRecommend();
        //taskService.calculateRecommendActivity();
        return "success";
    }

    @GetMapping("init_influencer_billboard")
    public String initInfluencerBillboard() {
        corgiUserRecommendService.initInfluencer();
        return "success";
    }

    @GetMapping("init_user_pic")
    public String initUserPic() {
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 1000;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    log.info("check user ... " + userPosition.getUserId());
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    String userId = userPosition.getUserId();
                    UserDetail userDetail = corgiUserService.getUserDetail(userId, null);
                    if (userDetail != null && !StringUtils.isEmpty(userDetail.getAvatarCheckStatus()) && !StringUtils.isEmpty(userDetail.getAvatar())) {
                        List<UserPic> userPics = corgiPicService.getUserPic(userId);
                        if (CollectionUtils.isEmpty(userPics)
                                && !CorgiPic.NEED_CHECK.equals(userDetail.getAvatarCheckStatus())) {
                            UserDetail queryDetail = new UserDetail();
                            queryDetail.setUserId(userId);
                            UserProfile userProfile = corgiUserService.searchUsers(queryDetail, null, 1, 1).get(0);
                            UserPosition position = corgiUserService.getUserPosition(userId);
                            log.info("register time: {} version: {} ", userProfile.getCreateTime(), position.getVersion());
                            UserPic userPic = new UserPic();
                            userPic.setStatus(UserPic.NORMAL);
                            userPic.setPicUrl(userDetail.getAvatar());
                            userPic.setUserId(userId);
                            corgiPicService.addUserPic(userPic);
                        }
                    }
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        return "success";
    }

    @GetMapping("init_user_pic_with_detail")
    public String initUserPicDetail() {
        List<UserProfile> userPositionList;
        int page = 1;
        int pageSize = 1000;
        do {
            userPositionList = corgiUserService.getBasicUserDetailByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserProfile userPosition : userPositionList) {
                    log.info("check user ... " + userPosition.getUserId());
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    String userId = userPosition.getUserId();
                    UserDetail userDetail = corgiUserService.getUserDetail(userId, null);
                    if (userDetail != null && !StringUtils.isEmpty(userDetail.getAvatarCheckStatus()) && !StringUtils.isEmpty(userDetail.getAvatar())) {
                        List<UserPic> userPics = corgiPicService.getUserPic(userId);
                        if (CollectionUtils.isEmpty(userPics)
                                && !CorgiPic.NEED_CHECK.equals(userDetail.getAvatarCheckStatus())) {
                            UserDetail queryDetail = new UserDetail();
                            queryDetail.setUserId(userId);
                            UserPic userPic = new UserPic();
                            userPic.setStatus(UserPic.NORMAL);
                            userPic.setPicUrl(userDetail.getAvatar());
                            userPic.setUserId(userId);
                            corgiPicService.addUserPic(userPic);
                        }
                    }
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        return "success";
    }

    @GetMapping("init_hot_activity")
    public String initHotActivity() {
        int page = 1;
        int size = 1000;
        List<String> activityList = new ArrayList<>();
        boolean shouldContinue = true;
        CorgiVlogHot queryHot = new CorgiVlogHot();
        queryHot.setStatus(CorgiVlogHot.STATUS.OPEN);
        queryHot.setType(CorgiVlogHot.TYPE.AUTO);
        do {
            List<ActivityLike> likeList = corgiLikeService.getLikeByPage(page, size);
            log.info("like size:{} ", likeList.size());
            if (CollectionUtils.isEmpty(likeList)) {
                break;
            }
            for (ActivityLike like : likeList) {
                String activityId = like.getActivityId();
                if (activityList.contains(activityId)) {
                    continue;
                }
                activityList.add(activityId);
                CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
                if (!CorgiActivity.CAT_IMAGE.equals(activity.getCategory())
                        && !CorgiActivity.CAT_VIDEO.equals(activity.getCategory())
                        && !CorgiActivity.CAT_TEXT.equals(activity.getCategory())) {
                    continue;
                }

                Long totalCount = corgiLikeService.countActivityLike(activityId);
                corgiActivityService.updateByColumn(activityId, "likeCount", totalCount + "");

                Integer likeCount = corgiLikeService.countRealActivityLike(activityId);
                if (likeCount < 5) {
                    continue;
                }
                queryHot.setActivityId(activityId);
                List<CorgiVlogHot> tmpList = corgiVlogService.getHotVlog(queryHot, 1, 1);
                if (tmpList.size() > 0) {
                    CorgiVlogHot hot = tmpList.get(0);
                    if (likeCount * 10 > hot.getExpectView()) {
                        CorgiVlogHot updateHot = new CorgiVlogHot();
                        updateHot.setId(hot.getId());
                        updateHot.setLikeCount(likeCount);
                        updateHot.setExpectView(likeCount * 10);
                        corgiVlogService.updateHotVlog(updateHot);
                    }
                } else {
                    CorgiVlogHot addHot = new CorgiVlogHot();
                    addHot.setActivityId(activityId);
                    addHot.setExpectView(likeCount * 10);
                    addHot.setLikeCount(likeCount);
                    addHot.setType(CorgiVlogHot.TYPE.AUTO);
                    corgiVlogService.addHotVlog(addHot);
                }
                queryHot.setType(CorgiVlogHot.TYPE.MANUAL);
                List<CorgiVlogHot> manualList = corgiVlogService.getHotVlog(queryHot, 1, 10);
                if (CollectionUtils.isEmpty(manualList)) {
                    for (CorgiVlogHot hot : manualList) {
                        CorgiVlogHot updateHot = new CorgiVlogHot();
                        updateHot.setId(hot.getId());
                        updateHot.setLikeCount(likeCount);
                        corgiVlogService.updateHotVlog(updateHot);
                    }
                }
                queryHot.setType(CorgiVlogHot.TYPE.AUTO);
            }
            page++;
        } while (shouldContinue);
        return "success";
    }

    @GetMapping("refresh_activity_pic")
    public String refreshActivityPic() {
        ActivityQuery query = new ActivityQuery();
        query.setPageSize(1000);
        String activityId = "";
        while (true) {
            query.setActivityId(activityId);
            List<CorgiActivity> activityList = corgiUserActivityService.queryActivity(query);
            if (CollectionUtils.isEmpty(activityList)) {
                break;
            }
            for (CorgiActivity corgiActivity : activityList) {
                log.info("checking...{} category:{}", corgiActivity.getId(), corgiActivity.getCategory());
                activityId = corgiActivity.getId();
                if (!CorgiActivity.CAT_IMAGE.equals(corgiActivity.getCategory()) && !CorgiActivity.CAT_PAYING.equals(corgiActivity.getCategory())) {
                    continue;
                }
                List<ActivityPic> pics = corgiPicService.getActivityPic(corgiActivity.getId());
                if (CollectionUtils.isEmpty(pics)) {
                    continue;
                }
                if (!aliyunGreenService.checkPic(pics, "crazy_check")) {
                    corgiActivityService.updateByColumn(corgiActivity.getId(), "strictStatus", "check");
                }
            }
        }
        return "success";
    }
}
