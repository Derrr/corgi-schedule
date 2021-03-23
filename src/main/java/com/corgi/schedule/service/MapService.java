package com.corgi.schedule.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.entity.CorgiArea;
import com.corgi.user.api.CorgiAreaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class MapService {

    public String getCity(Double lat, Double lng) {
        String location = lng + "," + lat;
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String result = "";
        String url = "https://restapi.amap.com/v3/place/around?key=1243993719b451359f144ee24ebcb744&radius=10000&location=" + location;
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
            JSONObject object = JSONObject.parseObject(result);
            String status = object.getString("status");
            if ("1".equals(status)) {
                JSONArray pois = object.getJSONArray("pois");
                int total = pois.size();
                if (total == 0) {
                    return "";
                }
                for (int i = 0; i < total; i++) {
                    JSONObject poi = pois.getJSONObject(i);
                    String cityName = poi.getString("cityname");
                    String pname = poi.getString("pname");
                    if (!StringUtils.isEmpty(cityName) || !StringUtils.isEmpty(pname)) {
                        return pname + "-" + cityName;
                    }
                }
            } else {
                log.error(object.getString("info"));
                return "";
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
        return "";
    }

    public String initCity(String city) {
        String error;
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String result = "";
        String url = "https://restapi.amap.com/v3/place/text?city=" + city + "&&types=150500&offset=25&key=1243993719b451359f144ee24ebcb744&extensions=all&page=";
        int page = 1;
        try {
            // 通过址默认配置创建一个httpClient实例
            httpClient = HttpClients.createDefault();

            while (true) {
                // 创建httpGet远程连接实例
                HttpGet httpGet = new HttpGet(url + page);
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
                JSONObject object = JSONObject.parseObject(result);
                String status = object.getString("status");
                if ("1".equals(status)) {
                    int count = object.getInteger("count");
                    JSONArray pois = object.getJSONArray("pois");
                    int total = pois.size();
                    if (total == 0) {
                        return "finish";
                    }
                    for (int i = 0; i < total; i++) {
                        JSONObject poi = pois.getJSONObject(i);
                        String adname = poi.getString("adname");
                        String type = poi.getString("type");
                        log.info("type=" + type);
                        if (type.contains("出入口")) {
                            continue;
                        }
                        String areaName = poi.getString("name").replaceAll("\\(地铁站\\)", "");
                        String[] location = poi.getString("location").split(",");
                        String[] addressArr = poi.getString("address").split(";");
                        for (int j = 0; j < addressArr.length; j++) {
                            CorgiArea area = CorgiArea.builder().areaName(areaName)
                                    .city(city).adname(adname).type(CorgiArea.STATION)
                                    .lng(Double.valueOf(location[0])).lat(Double.valueOf(location[1]))
                                    .address(addressArr[j])
                                    .build();
                        }

                    }
                    if (page * 25 >= count) {
                        return "finish";
                    } else {
                        page++;
                    }
                } else {
                    return object.getString("info");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
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
        return error;
    }
}
