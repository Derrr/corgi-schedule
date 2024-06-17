package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserWechat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiHourStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiUserWechatService corgiUserWechatService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Autowired
    private MQService mqService;

    //@Async
    //@Scheduled(fixedRate = 3600 * 1000)
    @Scheduled(cron = "0 0 9-22 * * *")
    public void run() {
        log.info("check user wechat.....");
        int page = 1;
        while (true) {
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
            log.info("into prefer group.....page" + page);
            if (CollectionUtils.isEmpty(userPositionList)) {
                break;
            }
            page++;
            for (UserPosition userPosition : userPositionList) {
                UserWechat userWechat = corgiUserWechatService.getUserWechat(userPosition.getUserId());
                UserDetail userDetail = corgiUserService.getUserDetailBasic(userPosition.getUserId());
                if (userDetail == null) {
                    if (userWechat != null && "1".equals(userWechat.getStatus())) {
                        userWechat.setStatus("0");
                        corgiUserWechatService.updateUserWechat(userWechat);
                    }
                    continue;
                }
                if (userWechat != null) {
                    if ("1".equals(userWechat.getStatus())) {
                        this.countWeight(userWechat);
                    }
                    continue;
                }
                if (!UserDetail.VERIFIED.equals(userDetail.getAvatarCheckStatus())) {
                    continue;
                }
                if (corgiUserFollowService.countFollowed(userPosition.getUserId()) < 100) {
                    continue;
                }
                if (corgiUserActivityService.countUserActivity(userPosition.getUserId()) < 3) {
                    continue;
                }
                userWechat = new UserWechat();
                userWechat.setUserId(userPosition.getUserId());
                userWechat.setStatus("0");
                corgiUserWechatService.updateUserWechat(userWechat);
                HashMap<String, Object> extra = new HashMap<>();
                extra.put("type", "907");
                JSONArray content = new JSONArray();
                content.add(new JSONObject().fluentPut("text", " 恭喜！你已满足上传微信的条件，现在去上传可赚取零花钱哦~"));
                extra.put("content", content);
                extra.put("bottomText", "去上传>>");
                extra.put("bottomUrl", userPosition.getUserId());
                extra.put("bottomUrlType", "15");
                extra.put("alertTitle", "您可以上传微信啦");
                mqService.sendMessage(PushMessage.builder()
                        .type(PushMessage.DEFAULT)
                        .sourceUserId("corgihelper")
                        .targetUserId(userPosition.getUserId())
                        .message("恭喜！你已满足上传微信的条件，现在去上传可赚取零花钱哦~")
                        .extra(extra)
                        .build());
                this.countWeight(userWechat);
            }
        }
    }

    private void countWeight(UserWechat userWechat) {
        UserDetail query = new UserDetail();
        query.setCheckStatus("real");
        query.setUserId(userWechat.getUserId());
        int total = (int) corgiLikeService.countLikeByUser(query);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        query.setCtime(new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()));
        int period = (int) corgiLikeService.countLikeByUser(query);
        Integer weight = total / 3 + period + Integer.valueOf(userWechat.getId()) / 100;
        corgiUserWechatService.updateUserWechatCount(query.getUserId(), total, period, weight);
    }
}
