package com.corgi.schedule.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.entity.CorgiPic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunGreenService {

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    String REGION_ID = "cn-shanghai";

    private IAcsClient managementClient;

    private Random random = new Random(System.currentTimeMillis());

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public boolean checkPic(List<? extends CorgiPic> urls, String bizType) {
        if (CollectionUtils.isEmpty(urls)) {
            return false;
        }
        log.info("pics = " + urls);

        ImageSyncScanRequest imageSyncScanRequest = new ImageSyncScanRequest();
        // 指定api返回格式
        imageSyncScanRequest.setAcceptFormat(FormatType.JSON);
        // 指定请求方法
        imageSyncScanRequest.setMethod(MethodType.POST);
        imageSyncScanRequest.setEncoding("utf-8");
        //支持http和https
        imageSyncScanRequest.setProtocol(ProtocolType.HTTP);


        JSONObject httpBody = new JSONObject();
        /**
         * 设置要检测的场景, 计费是按照该处传递的场景进行
         * 一次请求中可以同时检测多张图片，每张图片可以同时检测多个风险场景，计费按照场景计算
         * 例如：检测2张图片，场景传递porn、terrorism，计费会按照2张图片鉴黄，2张图片暴恐检测计算
         * porn: porn表示色情场景检测
         */
        httpBody.put("scenes", Arrays.asList("porn", "ad", "terrorism"));

        /**
         * 设置待检测图片， 一张图片一个task
         * 多张图片同时检测时，处理的时间由最后一个处理完的图片决定
         * 通常情况下批量检测的平均rt比单张检测的要长, 一次批量提交的图片数越多，rt被拉长的概率越高
         * 这里以单张图片检测作为示例, 如果是批量图片检测，请自行构建多个task
         */
        List<JSONObject> tasks = new ArrayList<>();
        Date now = new Date();
        for (CorgiPic pic : urls) {
            JSONObject task = new JSONObject();
            String id = getDataId();
            task.put("dataId", id);
            pic.setDataId(id);
            //设置图片链接
            task.put("url", pic.getPicUrl());
            task.put("time", now);
            tasks.add(task);
        }
        httpBody.put("tasks", tasks);
        httpBody.put("bizType", bizType);

        imageSyncScanRequest.setHttpContent(org.apache.commons.codec.binary.StringUtils.getBytesUtf8(httpBody.toJSONString()),
                "UTF-8", FormatType.JSON);
        /**
         * 请设置超时时间, 服务端全链路处理超时时间为10秒，请做相应设置
         * 如果您设置的ReadTimeout小于服务端处理的时间，程序中会获得一个read timeout异常
         */
        imageSyncScanRequest.setConnectTimeout(3000);
        imageSyncScanRequest.setReadTimeout(10000);
        HttpResponse httpResponse = null;
        try {
            httpResponse = managementClient.doAction(imageSyncScanRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //服务端接收到请求，并完成处理返回的结果
        if (httpResponse != null && httpResponse.isSuccess()) {
            JSONObject scrResponse = JSON.parseObject(org.apache.commons.codec.binary.StringUtils.newStringUtf8(httpResponse.getHttpContent()));
            int requestCode = scrResponse.getIntValue("code");
            //每一张图片的检测结果
            JSONArray taskResults = scrResponse.getJSONArray("data");
            if (200 == requestCode) {
                for (Object taskResult : taskResults) {
                    log.info(((JSONObject) taskResult).toJSONString());
                    //单张图片的处理结果
                    int taskCode = ((JSONObject) taskResult).getIntValue("code");
                    //图片要检测的场景的处理结果, 如果是多个场景，则会有每个场景的结果
                    JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                    if (200 == taskCode) {
                        for (Object sceneResult : sceneResults) {
                            String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                            if (!suggestion.equals("pass")) {
                                return false;
                            }
                        }
                    } else {
                        String result = "task process fail. task response:" + JSON.toJSONString(taskResult);
                        //单张图片处理失败, 原因视具体的情况详细分析
                        log.info(result);
                        return false;
                    }
                }
            } else {
                log.error("请求失败....");
                return false;
            }
        }
        return true;
    }

    private String getDataId() {
        return UUID.randomUUID().toString() + random.nextInt(100);
    }


}
