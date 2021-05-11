package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.schedule.service.HxPushMessageService;
import com.corgi.user.api.CorgiSystemMessageService;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiSystemMessageTask {

    @Reference
    private CorgiSystemMessageService corgiSystemMessageService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Autowired
    private HxPushMessageService hxPushMessageService;

    @Async
    @Scheduled(cron = "0 0/10 * * * *")
    public void run() {
        log.info("start sending...");
        List<SystemMessage> systemMessageList = corgiSystemMessageService.getSystemMessagesByTime(System.currentTimeMillis());
        log.info("sending {} ", systemMessageList.size());
        if (!CollectionUtils.isEmpty(systemMessageList)) {
            for (SystemMessage systemMessage : systemMessageList) {
                SystemMessage updateMessage = new SystemMessage();
                updateMessage.setId(systemMessage.getId());
                updateMessage.setStatus("sending");
                corgiSystemMessageService.updateSystemMessage(updateMessage);
            }
            for (SystemMessage systemMessage : systemMessageList) {
                log.info("sending {} ", systemMessage.getContent());
                sendMessages(systemMessage);
            }
        }
    }

    @Async
    @Scheduled(cron = "0 25 10 * * *")
    //@Scheduled(fixedRate = 1000 * 3600 * 24)
    public void birthdayNotice() {
        SimpleDateFormat sdf = new SimpleDateFormat("/MM/dd");
        Long time = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L;
        String date = sdf.format(new Date());
        List<String> userIds = corgiUserService.getUserByBirthday(date, time);
        HashMap extra = new HashMap();
        if (!CollectionUtils.isEmpty(userIds)) {
            for (String userId : userIds) {
                UserDetail userDetail = corgiUserService.getUserDetailBasic(userId);
                SystemMessage systemMessage = new SystemMessage();
                systemMessage.setType("907");
                systemMessage.setContent("你关注的 " + userDetail.getNickname() + " 今天过生日啦，快去祝贺他吧");
                JSONArray content = new JSONArray();
                content.add(new JSONObject().fluentPut("text", "今天是您关注的好友 "));
                content.add(new JSONObject().fluentPut("text", "@" + userDetail.getNickname()).fluentPut("url", userDetail.getUserId()).fluentPut("urlType", "4"));
                content.add(new JSONObject().fluentPut("text", " 生日哦，快发个信息祝福一下吧！说不定就成了呢  ~ "));
                content.add(new JSONObject().fluentPut("text", "祝福一下>").fluentPut("url", userDetail.getUserId()).fluentPut("urlType", "5"));
                extra.put("content", content);
                int page = 1;
                int pageSize = 300;
                while (true) {
                    List<UserProfile> followers = corgiUserFollowService.getFollowedUserByPage(userId, 0L, page, pageSize);
                    if (CollectionUtils.isEmpty(followers)) {
                        break;
                    }
                    page++;
                    List<String> corgiIds = new ArrayList<>();
                    for (UserProfile follower : followers) {
                        if ("real".equals(follower.getCheckDesc())) {
                            corgiIds.add("corgi" + follower.getUserId());
                        }
                    }
                    hxPushMessageService.sendMessage(systemMessage, corgiIds, extra);
                }
            }
        }
    }

    public void sendMessages(SystemMessage systemMessage) {
        List<MessageRule> messageRules = corgiSystemMessageService.getMessageRule(systemMessage.getId());
        int page = 1;
        int pageSize = 100;
        if (!CollectionUtils.isEmpty(messageRules)) {
            UserDetail userDetail = getUserQuery(messageRules);
            do {
                List<UserProfile> userProfiles = new ArrayList<>();
                if (!StringUtils.isEmpty(userDetail.getNickname())) {
                    List<UserProfile> userProfilesTmp = corgiUserService.searchUsers(userDetail, null, page, pageSize);
                    for (UserProfile userProfile : userProfilesTmp) {
                        if (userDetail.getNickname().equals(userProfile.getNickname())) {
                            userProfiles = Arrays.asList(userProfile);
                            break;
                        }
                    }
                } else {
                    userProfiles = corgiUserService.searchUsers(userDetail, null, page, pageSize);
                }
                if (CollectionUtils.isEmpty(userProfiles)) {
                    break;
                }
                log.info("sending user profiles... {} ", userProfiles.size());
                List<String> userIds = new ArrayList<>();
                userProfiles.forEach(userProfile -> {
                    MessageRecord messageRecord = new MessageRecord();
                    messageRecord.setMessageId(systemMessage.getId());
                    messageRecord.setStatus("sending");
                    messageRecord.setNickname(userProfile.getNickname());
                    messageRecord.setUserId(userProfile.getUserId());
                    corgiSystemMessageService.addMessageRecord(messageRecord);
                    log.info("prepare sending to... {} ", userProfile.getNickname());
                    userIds.add("corgi" + userProfile.getUserId());
                });
                if (hxPushMessageService.sendMessage(systemMessage, userIds)) {
                    userProfiles.forEach(userProfile -> {
                        MessageRecord messageRecord = new MessageRecord();
                        messageRecord.setMessageId(systemMessage.getId());
                        messageRecord.setStatus("success");
                        messageRecord.setUserId(userProfile.getUserId());
                        corgiSystemMessageService.updateMessageRecord(messageRecord);
                    });
                } else {
                    userProfiles.forEach(userProfile -> {
                        MessageRecord messageRecord = new MessageRecord();
                        messageRecord.setMessageId(systemMessage.getId());
                        messageRecord.setStatus("failed");
                        messageRecord.setReason(HxPushMessageService.RESULT.get());
                        messageRecord.setUserId(userProfile.getUserId());
                        corgiSystemMessageService.updateMessageRecord(messageRecord);
                    });
                }
                page++;
                //break;
            } while (true);
        }
        SystemMessage updateMessage = new SystemMessage();
        updateMessage.setId(systemMessage.getId());
        updateMessage.setStatus(SystemMessage.STATUS_SENT);
        corgiSystemMessageService.updateSystemMessage(updateMessage);
    }

    public UserDetail getUserQuery(List<MessageRule> messageRules) {
        UserDetail userDetail = new UserDetail();
        userDetail.setVersion("1.4.4");
        for (MessageRule messageRule : messageRules) {
            if ("nickname".equals(messageRule.getRuleKey())) {
                userDetail.setNickname(messageRule.getRuleValue());
            } else if ("userId".equals(messageRule.getRuleKey())) {
                if (messageRule.getRuleValue() == null) {
                    messageRule.setRuleValue("null");
                }
                userDetail.setUserId(messageRule.getRuleValue());
            } else if ("avatarStatus".equals(messageRule.getRuleKey())) {
                userDetail.setAvatarStatus(messageRule.getRuleValue());
            }
        }
        return userDetail;
    }

}
