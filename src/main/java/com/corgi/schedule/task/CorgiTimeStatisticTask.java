package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.entity.CorgiStatistic;
import com.corgi.schedule.service.MQService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.api.TlxActivityService;
import com.corgi.user.entity.TlxActivity;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiTimeStatisticTask {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserRecommendService corgiUserRecommendService;
    @Reference
    private TlxActivityService tlxActivityService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    private static SimpleDateFormat dau_sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat activity_sdf = new SimpleDateFormat("yyyy/MM/dd");
    private static SimpleDateFormat hour_sdf = new SimpleDateFormat("HH");
    private static List<String> groupOrder = Arrays.asList("偏瘦", "偏胖", "肌肉", "肉壮", "精壮", "匀称");

    @Async
    @Scheduled(cron = "0 0 2 * * *")
    //@Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runPreferGroup() {
        log.info("into prefer group.....");
        this.refreshWeight();
        this.refreshGroupBound();
        int page = 1;
        while (true) {
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
            log.info("into prefer group.....page" + page);
            if (CollectionUtils.isEmpty(userPositionList)) {
                break;
            }
            page++;
            for (UserPosition userPosition : userPositionList) {
                RecommendCalculater recommendCalculater = new RecommendCalculater();
                recommendCalculater.setUserId(userPosition.getUserId());
                mqService.sendPreferGroup(recommendCalculater);
            }
        }

    }

    @Async
    @Scheduled(cron = "0 0 4 * * *")
    //@Scheduled(fixedRate = 7 * 24 * 3600 * 1000)
    public void runGroup() {
        int page = 1;
        while (true) {
            List<UserPosition> userPositionList = corgiUserService.getUserPositionByPage(page, 1000);
            if (CollectionUtils.isEmpty(userPositionList)) {
                break;
            }
            page++;
            for (UserPosition userPosition : userPositionList) {
                RecommendCalculater recommendCalculater = new RecommendCalculater();
                recommendCalculater.setUserId(userPosition.getUserId());
                mqService.sendGroup(recommendCalculater);
            }
        }
        this.refreshTlx();
    }

    private void refreshTlx(){
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String result = "";
        String url = "http://www.tianlangxing.top/activities/get";
        try {
            // 通过址默认配置创建一个httpClient实例
            httpClient = HttpClients.createDefault();

            // 创建httpGet远程连接实例
            HttpGet httpGet = new HttpGet(url);
            // 设置配置请求参数
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(35000)
                    .setConnectionRequestTimeout(35000)
                    .setSocketTimeout(60000)
                    .build();
            // 为httpGet实例设置配置
            httpGet.setConfig(requestConfig);
            // 执行get请求得到返回对象
            response = httpClient.execute(httpGet);
            // 通过返回对象获取返回数据
            HttpEntity entity = response.getEntity();
            // 通过EntityUtils中的toString方法将结果转换为字符串
            result = EntityUtils.toString(entity);
            JSONArray array = JSON.parseArray(result);
            String version = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TlxActivity tlxActivity = new TlxActivity();
                tlxActivity.setId(obj.getString("ID"));
                tlxActivity.setCity(obj.getString("city"));
                tlxActivity.setBody(obj.getString("body"));
                tlxActivity.setDays(obj.getString("days"));
                tlxActivity.setPeriod(obj.getString("period"));
                tlxActivity.setDepartdate(obj.getString("departdate"));
                tlxActivity.setExpenseDetail(obj.getString("expense_detail"));
                tlxActivity.setHeaderImage(obj.getString("header_image"));
                tlxActivity.setLongtitle(obj.getString("longtitle"));
                tlxActivity.setMeetingPoint(obj.getString("meeting_point"));
                tlxActivity.setNote(obj.getString("note"));
                tlxActivity.setPosterImage(obj.getString("poster_image"));
                tlxActivity.setPrice(obj.getString("price"));
                tlxActivity.setShorttitle(obj.getString("shorttitle"));
                tlxActivity.setTripContent(obj.getString("trip_content"));
                tlxActivity.setVersion(version);
                tlxActivityService.updateActivity(tlxActivity);
            }
            if (array.size() > 0) {
                tlxActivityService.refreshStatus(version);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭资源
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void refreshWeight() {
        HashMap<String, Double> result = corgiUserRecommendService.getGroupCor("all");
        if (!CollectionUtils.isEmpty(result)) {
            Double total = result.values().stream().reduce((m, n) -> m + n).get();
            Double totalCount = corgiUserRecommendService.getGroupWeight(0, "");
            if (total != 0) {
                for (String key : groupOrder) {
                    Double value = result.get(key) == null ? 0.0 : result.get(key) / total;
                    Integer groupCount = (int) (Math.round(value * totalCount));
                    log.info(key + " count: " + groupCount);
                    Double weight = corgiUserRecommendService.getGroupWeight(groupCount, key);
                    log.info(key + " weight: " + weight);
                    if (weight == null) {
                        log.info("wrong weight:" + key);
                        weight = 1.0;
                    }
                    redisTemplate.opsForHash().put("group_weight", key, weight + "");
                    redisTemplate.opsForHash().put("group_count", key, groupCount + "");

                    String incrementKey = "group_weight_" + key;
                    redisTemplate.delete(incrementKey);
                    redisTemplate.opsForValue().increment(incrementKey);
                    redisTemplate.expire(incrementKey, 12l, TimeUnit.HOURS);
                }
            }
            redisTemplate.expire("group_weight", 25l, TimeUnit.HOURS);
            redisTemplate.expire("group_count", 25l, TimeUnit.HOURS);
        }
    }

    private void refreshGroupBound() {
        this.refreshGroupCor("1099", "偏胖");
        this.refreshGroupCor("1638", "匀称");
        this.refreshGroupCor("207151", "肉壮");
        this.refreshGroupCor("256144", "肌肉");
        this.refreshGroupCor("361673", "精壮");
        this.refreshGroupCor("522428", "偏瘦");
    }

    private void refreshGroupCor(String userId, String group) {
        HashMap<String, Double> groupWeight = corgiUserRecommendService.getGroupCor(userId);
        for (String weightGroup : groupWeight.keySet()) {
            if (group.equals(weightGroup)) {
                corgiUserRecommendService.updateGroupCor(userId, weightGroup, 10000.0);
            } else {
                corgiUserRecommendService.updateGroupCor(userId, weightGroup, 0.0);
            }
        }
    }
}
