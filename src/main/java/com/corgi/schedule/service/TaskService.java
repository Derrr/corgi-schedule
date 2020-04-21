package com.corgi.schedule.service;

import com.corgi.common.messages.TraceFollow;
import com.corgi.user.api.CorgiStatisticService;
import jdk.nashorn.internal.ir.annotations.Reference;
import lombok.extern.slf4j.Slf4j;
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

    public void countUserTrace(String date) {
        List<HashMap> traces = corgiStatisticService.countUserTrace(date);
        Double total = corgiStatisticService.countTotalUserTrace(date);
        corgiStatisticService.addUserTraceSum(date, TraceFollow.TOTAL, total);
        if (!CollectionUtils.isEmpty(traces)) {
            for (HashMap trace : traces) {
                corgiStatisticService.addUserTraceSum(date, (String) trace.get("type"), Double.parseDouble(trace.get("time") + ""));
            }
        }
    }
}
