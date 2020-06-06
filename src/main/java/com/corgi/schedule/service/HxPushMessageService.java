package com.corgi.schedule.service;

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
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
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
        return sendMessage(systemMessage, userIds, new HashMap());
    }


    public boolean sendMessage(SystemMessage systemMessage, List<String> userIds, HashMap msg) {
        String url = HOST + orgName + "/" + appName + MESSAGE_URL;
        HashMap message = new HashMap();
        message.put("target_type", "users");
        message.put("target", userIds);
        msg.put("msg", systemMessage.getContent());
        msg.put("type", "txt");
        message.put("msg", msg);
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
}
