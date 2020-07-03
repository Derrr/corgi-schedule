package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.schedule.service.HxPushMessageService;
import com.corgi.user.api.CorgiSystemMessageService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
    @Autowired
    private HxPushMessageService hxPushMessageService;

    @Async
    @Scheduled(cron = "0 0/1 * * * *")
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

    public void sendMessages(SystemMessage systemMessage) {
        List<MessageRule> messageRules = corgiSystemMessageService.getMessageRule(systemMessage.getId());
        int page = 1;
        int pageSize = 1;
        if (!CollectionUtils.isEmpty(messageRules)) {
            UserDetail userDetail = getUserQuery(messageRules);
            do {
                List<UserProfile> userProfiles;
                if (!StringUtils.isEmpty(userDetail.getNickname())) {
                    userProfiles = corgiUserService.searchUsers(userDetail, null, 1, 100);
                    for (UserProfile userProfile : userProfiles) {
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
                userProfiles.forEach(userProfile -> {
                    MessageRecord messageRecord = new MessageRecord();
                    messageRecord.setMessageId(systemMessage.getId());
                    messageRecord.setStatus("sending");
                    messageRecord.setNickname(userProfile.getNickname());
                    messageRecord.setUserId(userProfile.getUserId());
                    corgiSystemMessageService.addMessageRecord(messageRecord);
                });
                if (hxPushMessageService.sendMessage(systemMessage, Arrays.asList("corgi" + userProfiles.get(0).getUserId()))) {
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
                if (userProfiles.size() < pageSize) {
                    break;
                }
                break;
            } while (true);
        }
        SystemMessage updateMessage = new SystemMessage();
        updateMessage.setId(systemMessage.getId());
        updateMessage.setStatus(SystemMessage.STATUS_SENT);
        corgiSystemMessageService.updateSystemMessage(updateMessage);
    }

    public UserDetail getUserQuery(List<MessageRule> messageRules) {
        UserDetail userDetail = new UserDetail();
        for (MessageRule messageRule : messageRules) {
            if ("nickname".equals(messageRule.getRuleKey())) {
                userDetail.setNickname(messageRule.getRuleValue());
            } else if ("userId".equals(messageRule.getRuleKey())) {
                userDetail.setUserId(messageRule.getRuleValue());
            }
        }
        return userDetail;
    }

}
