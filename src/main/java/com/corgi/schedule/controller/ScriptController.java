package com.corgi.schedule.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.MatchRefresher;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiPic;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.*;
import com.corgi.schedule.task.CorgiStatisticTask;
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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiBarService corgiBarService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference(retries = 1, timeout = 100000)
    private CorgiUserRecommendService corgiUserRecommendService;
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
        Long minUptime = 999999999999999999L;
        Long maxUptime = 0L;
        long peopleCount = 0;
        long noPeopleCount = 0;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    log.info("checking ... " + userPosition.getUserId());
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
                    List<Point> points = redisTemplate.opsForGeo().position("user", userPosition.getUserId());
                    if (!CollectionUtils.isEmpty(points)) {
                        peopleCount++;
                        Long uptime = userPosition.getUptime();
                        if (uptime < minUptime) {
                            minUptime = uptime;
                        }
                        log.info("exist userid: {} , uptime: {} ", userPosition.getUserId(), userPosition.getUptime());
                    } else if (userPosition.getUptime() > maxUptime) {
                        maxUptime = userPosition.getUptime();
                        noPeopleCount++;
                        log.info("unexist userid: {} , uptime: {} ", userPosition.getUserId(), userPosition.getUptime());
                    }
                    redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        log.info("min: {} count: {} max: {} no: {}", minUptime, peopleCount, maxUptime, noPeopleCount);
        return minUptime + "-" + peopleCount + "-" + maxUptime + "-" + noPeopleCount;
    }

    @GetMapping("clear_keys")
    public String clearKeys() {
        corgiUserMatchService.clearMatch();
        return "success";
    }

    @GetMapping("init_bar_city")
    public String initBarCity() {
        List<BarProfile> barProfiles = corgiBarService.getBarListByCity(null);
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
//                            if (checkPics.size() > 0) {
//                                for (CheckPic checkPic : checkPics) {
//                                    if (checkPic.getPicUrl().equals(userPic.getPicUrl())) {
//                                        userPic.setStatus(checkPic.getStatus());
//                                        userPic.setDataId(checkPic.getDataId());
//                                    }
//                                }
//                            }
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
        int page = 1;
        int pageSize = 1000;
        do {
            List<UserPosition> positions = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(positions)) {
                break;
            }
            for (UserPosition position : positions) {
                RecommendCalculater calculater = new RecommendCalculater();
                calculater.setUserId(position.getUserId());
                mqService.sendCaculater(calculater);
            }
        } while (true);
        return "success";
    }

    @GetMapping("init_influencer_billboard")
    public String initInfluencerBillboard() {
        corgiUserRecommendService.initInfluencer();
        return "success";
    }
}
