package com.corgi.schedule.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.schedule.service.MapService;
import com.corgi.user.api.CorgiUserMatchService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tairanliu
 */
@RestController
@RequestMapping("script")
public class ScriptController {
    @Autowired
    private MapService mapService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("init_station")
    public String initStation(@RequestParam("city") String city) {
        return mapService.initCity(city);
    }

    @GetMapping("refresh_position")
    public String refreshPosition() {
        List<UserPosition> userPositionList;
        int page = 1;
        int pageSize = 1000;
        do {
            userPositionList = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (userPositionList != null) {
                for (UserPosition userPosition : userPositionList) {
                    redisTemplate.opsForGeo().remove("user",userPosition.getUserId());
                    if (userPosition.getLng() == null || userPosition.getLng() > 180 || userPosition.getLng() < -180) {
                        continue;
                    }
                    if (userPosition.getLat() == null || userPosition.getLat() > 90 || userPosition.getLat() < -90) {
                        continue;
                    }
                    if (StringUtils.isEmpty(userPosition.getUserId())) {
                        continue;
                    }
                    redisTemplate.opsForGeo().add("user", new Point(userPosition.getLng(), userPosition.getLat()), userPosition.getUserId());
                }
            }
        } while (!CollectionUtils.isEmpty(userPositionList));
        return "success";
    }

    @GetMapping("clear_keys")
    public String clearKeys() {
        corgiUserMatchService.clearMatch();
        return "success";
    }
}
