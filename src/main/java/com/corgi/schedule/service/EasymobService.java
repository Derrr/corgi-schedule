package com.corgi.schedule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class EasymobService {
//    @Autowired
//    private EMService emService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private final static String TOKEN_KEY = "easymob_token";
//
//    public void refreshUser(UserProfile userProfile) {
//        emService.metadata().setMetadataToUser("corgi" + userProfile.getUserId(),
//                JSONObject.parseObject(JSONObject.toJSONString(userProfile), HashMap.class));
//    }
//
//    public void deleteUser(String userId) {
//        emService.user().delete("corgi" + userId);
//    }
//
//    public String getToken() {
//        String token = redisTemplate.opsForValue().get(TOKEN_KEY);
//        if (StringUtils.isEmpty(token)) {
//            token = emService.token().getAppToken().block().getValue();
//            redisTemplate.opsForValue().set(TOKEN_KEY, token, 30l, TimeUnit.DAYS);
//        }
//        return token;
//    }
}
