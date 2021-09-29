package com.corgi.schedule.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.corgi.user.entity.SystemMessage;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class HxPushMessageService {
    private String orgName = "1101200130181163";
    private String appName = "corgi";
    private static final String HOST = "https://a1.easemob.com/";
    private static final String MESSAGE_URL = "/messages";
    private static final String TOKEN_URL = "/token";
    private final static PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager();
    public static ThreadLocal<String> RESULT = new ThreadLocal<>();

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

    public boolean sendMessage(SystemMessage systemMessage, List<String> userIds) {
        SystemMessage message = new SystemMessage();
        BeanUtils.copyProperties(systemMessage, message);
        HashMap extra = new HashMap();
        if ("907".equals(message.getType())) {
            extra.put("content", JSON.parse(systemMessage.getContent()));
            message.setContent(systemMessage.getTitle());
        }
        return !"false".equals(sendMessage(message, userIds, extra));
    }


    public String sendMessage(SystemMessage systemMessage, List<String> userIds, HashMap extra) {
        String from = systemMessage.getFrom();
        if (StringUtils.isEmpty(from)) {
            from = "corgihelper";
        }
        String url = HOST + orgName + "/" + appName + MESSAGE_URL;
        HashMap message = new HashMap();
        HashMap apnsContent = new HashMap();
        String content = systemMessage.getContent();
        String title = systemMessage.getTitle();
        try {
            content = new String(systemMessage.getContent().getBytes(), "UTF-8");
            if (StringUtils.isEmpty(title)) {
                title = content;
            } else {
                title = new String(title.getBytes(), "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage(), e);
        }
        apnsContent.put("em_push_content", title);

        extra.put("type", systemMessage.getType());
        extra.put("em_apns_ext", apnsContent);
        extra.put("title", systemMessage.getTitle());
        extra.put("desc", systemMessage.getContent());
        extra.put("picUrl", systemMessage.getPicUrl());
        extra.put("urlType", systemMessage.getUrlType());
        extra.put("url", systemMessage.getUrl());

        message.put("target_type", "users");
        message.put("target", userIds);
        message.put("from", from);
        HashMap msg = new HashMap();
        msg.put("msg", content);
        msg.put("type", "txt");
        message.put("msg", msg);
        message.put("ext", extra);
        String token = getToken();
        JSONObject object = JSONObject.parseObject(token);
        try {
            String accessToken = object.getString("access_token");
            return this.postJson(url, message, accessToken);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "false";
    }

    public String getToken() {
        String url = HOST + orgName + "/" + appName + TOKEN_URL;
        HashMap message = new HashMap();
        message.put("grant_type", "client_credentials");
        message.put("client_id", "YXA6NW6WhxTlSd6PW28d8s2geQ");
        message.put("client_secret", "YXA6bXC8NAPVUHKlxTlhCSSZOVwyiAQ");
        return this.postJson(url, message, null);
    }

    public String postJson(String url, HashMap message, String token) {
        String result = null;
        CloseableHttpClient httpClient = getCloseableHttpClient();
        HttpPost httpPost = new HttpPost(url);
        CloseableHttpResponse response = null;
        try {

            httpPost.setHeader("Accept", "application/json;charset=UTF-8");
            httpPost.setHeader("Content-Type", "application/json");
            if (!StringUtils.isEmpty(token)) {
                httpPost.setHeader("Authorization", "Bearer " + token);
            }
            log.info("request: {} ", JSONObject.toJSONString(message));
            StringEntity stringEntity = new StringEntity(JSONObject.toJSONString(message), Charset.forName("UTF-8"));
            stringEntity.setContentType("application/json;charset=UTF-8");

            httpPost.setEntity(stringEntity);
            response = httpClient.execute(httpPost);
            if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                result = EntityUtils.toString(response.getEntity(), Charset.defaultCharset());
                log.info("请求成功：{}", result);
                response.getEntity().getContent().close();
                return result;
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
        return "false";
    }
}
