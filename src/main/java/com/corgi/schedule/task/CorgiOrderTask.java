package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.schedule.sdk.*;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiOrderService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.ActivityLike;
import com.corgi.user.entity.CorgiOrder;
import com.corgi.user.entity.CorgiVlogHot;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiOrderTask {
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
    private WXPay wxPay;


    @Async
    @Scheduled(cron = "0 0/1 * * * *")
    //@Scheduled(fixedRate = 3600 * 1000)
    public void run() {
        CorgiOrder query = CorgiOrder.builder()
                .status(CorgiOrder.STATUS.CREATED)
                .build();
        int page = 0;
        int size = 100;

        while (true) {
            page++;
            List<CorgiOrder> orders = corgiOrderService.getOrderByPage(query, page, size);
            if (CollectionUtils.isEmpty(orders)) {
                break;
            }
            for (CorgiOrder order : orders) {
                if (CorgiOrder.STATUS.CREATED.equals(order.getStatus())) {
                    if (CorgiOrder.PAY_TYPE.WX.equals(order.getPayType())) {
                        this.queryWXOrder(order);
                    }
                }
            }
        }
    }

    private void queryWXOrder(CorgiOrder order) {
        Map<String, String> orderQuery = new HashMap<>();
        orderQuery.put("out_trade_no", order.getTradeNo());
        try {
            Map<String, String> result = wxPay.orderQuery(orderQuery);
            order.setResult(JSON.toJSONString(result));
            if (WXPayConstants.FAIL.equals(result.get("return_code"))
                    || WXPayConstants.FAIL.equals(result.get("result_code"))) {
                corgiOrderService.updateOrder(order);
                return;
            }
            String tradeStatus = result.get("trade_state");
            order.setPayTime(result.get("time_end"));
            order.setBuyerId(result.get("openid"));
            if ("SUCCESS".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.SUCCESS);
            } else if ("CLOSED".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.CLOSE);
            } else if ("PAYERROR".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.FAIL);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE,-15);
                wxPay.closeOrder(orderQuery);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            order.setResult(e.getMessage());
        }
        corgiOrderService.updateOrder(order);
    }
}
