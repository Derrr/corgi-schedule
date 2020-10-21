package com.corgi.schedule.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.common.messages.TraceFollow;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class TaskService {
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private MQService mqService;

    public void countUserTrace(String date) {
        log.info("into count user trace...");
        List<HashMap> traces = corgiStatisticService.countUserTrace(date);
        log.info(traces + "...traces");
        Double total = corgiStatisticService.countTotalUserTrace(date);
        corgiStatisticService.addUserTraceSum(date, TraceFollow.TOTAL, total);
        if (!CollectionUtils.isEmpty(traces)) {
            for (HashMap trace : traces) {
                log.info("trace..." + trace);
                corgiStatisticService.addUserTraceSum(date, (String) trace.get("type"), Double.parseDouble(trace.get("time") + ""));
            }
        }
    }

    public void calculateRecommend(){
        int page = 1;
        int pageSize = 1000;
        do {
            List<UserPosition> positions = corgiUserService.getUserPositionByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(positions)) {
                break;
            }
            for (UserPosition position : positions) {
                RecommendCalculater calculater = new RecommendCalculater();
                calculater.setUserId(position.getUserId());
                mqService.sendCalculater(calculater);
            }
        } while (true);
    }

}
