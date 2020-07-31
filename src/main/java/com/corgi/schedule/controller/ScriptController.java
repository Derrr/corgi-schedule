package com.corgi.schedule.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.MatchRefresher;
import com.corgi.common.messages.PushMessage;
import com.corgi.schedule.service.HxPushMessageService;
import com.corgi.schedule.service.MQService;
import com.corgi.schedule.service.MapService;
import com.corgi.schedule.service.TaskService;
import com.corgi.schedule.task.CorgiStatisticTask;
import com.corgi.user.api.CorgiAreaService;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.api.CorgiUserMatchService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.SystemMessage;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
    private CorgiBarService corgiBarService;
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
}
