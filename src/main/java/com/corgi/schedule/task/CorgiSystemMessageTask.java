package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiSystemMessageTask {

    private String orgName = "1101200130181163";
    private String appName = "corgi";
    private static final String HOST = "https://a1.easemob.com/";
    private static final String MESSAGE_URL = "/messages";
    private final static PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager();
    private static ThreadLocal<String> RESULT = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        poolConnManager.setMaxTotal(2000);
        poolConnManager.setDefaultMaxPerRoute(1000);
    }

    private CloseableHttpClient getCloseableHttpClient() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(poolConnManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .build();

        return httpClient;
    }

    @Reference
    private CorgiSystemMessageService corgiSystemMessageService;
    @Reference
    private CorgiUserService corgiUserService;

    @Async
    @Scheduled(cron = "0 0/10 * * * *")
    public void run() {
        List<SystemMessage> systemMessageList = corgiSystemMessageService.getSystemMessagesByTime(System.currentTimeMillis());
        if (!CollectionUtils.isEmpty(systemMessageList)) {
            for (SystemMessage systemMessage : systemMessageList) {
                systemMessage.setStatus(SystemMessage.STATUS_SENT);
                corgiSystemMessageService.updateSystemMessage(systemMessage);
                sendMessages(systemMessage);
            }
        }
    }

    public void sendMessages(SystemMessage systemMessage) {
        systemMessage.setStatus(SystemMessage.STATUS_SENT);
        corgiSystemMessageService.updateSystemMessage(systemMessage);
        List<MessageRule> messageRules = corgiSystemMessageService.getMessageRule(systemMessage.getId());
        int page = 1;
        int pageSize = 500;
        if (!CollectionUtils.isEmpty(messageRules)) {
            UserDetail userDetail = getUserQuery(messageRules);
            do {
                List<UserProfile> userProfiles = corgiUserService.searchUsers(userDetail, null, page, pageSize);
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
                if (sendMessage(systemMessage, Arrays.asList("corgi" + userProfiles.get(0).getUserId()))) {
                    userProfiles.forEach(userProfile -> {
                        MessageRecord messageRecord = new MessageRecord();
                        messageRecord.setMessageId(systemMessage.getId());
                        messageRecord.setStatus("success");
                        messageRecord.setUserId(userProfile.getUserId());
                    });
                } else {
                    userProfiles.forEach(userProfile -> {
                        MessageRecord messageRecord = new MessageRecord();
                        messageRecord.setMessageId(systemMessage.getId());
                        messageRecord.setStatus("failed");
                        messageRecord.setReason(RESULT.get());
                        messageRecord.setUserId(userProfile.getUserId());
                    });
                }
                page++;
                if (userProfiles.size() < pageSize) {
                    break;
                }
                break;
            } while (true);
        }
    }

    public boolean sendMessage(SystemMessage systemMessage, List<String> userIds) {
        String url = HOST + orgName + "/" + appName + MESSAGE_URL;
        HashMap message = new HashMap();
        message.put("target_type", "users");
        message.put("target", userIds);
        message.put("msg", systemMessage.getContent());
        message.put("type", "txt");
        return this.postJson(url, message);
    }

    public boolean postJson(String url, HashMap message) {
        String result = null;
        CloseableHttpClient httpClient = getCloseableHttpClient();
        HttpPost httpPost = new HttpPost(url);
        CloseableHttpResponse response = null;
        try {

            httpPost.setHeader("Accept", "application/json;charset=UTF-8");
            httpPost.setHeader("Content-Type", "application/json");

            StringEntity stringEntity = new StringEntity(JSONObject.toJSONString(message));
            stringEntity.setContentType("application/json;charset=UTF-8");

            httpPost.setEntity(stringEntity);
            response = httpClient.execute(httpPost);
            if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                log.info("请求成功：{}", result);
                response.getEntity().getContent().close();
                return true;
            } else if (response != null) {
                result = EntityUtils.toString(response.getEntity(), Charset.defaultCharset());
                log.error("请求 {} 获取失败, 状态异常：{} 返回结果: {}", url, response.getStatusLine().getStatusCode(), result);

            }

        } catch (IOException e) {
            log.error("请求地址出错," + url + "错误信息:", e);
            result = e.getMessage();
            httpPost.abort();
        } catch (IllegalArgumentException e) {
            log.error("返回参数错误", e);
            result = e.getMessage();
            httpPost.abort();
        } finally {
            if (response != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        RESULT.set(result);
        return false;
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
