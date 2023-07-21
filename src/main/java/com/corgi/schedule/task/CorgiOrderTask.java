package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
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
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final SimpleDateFormat order_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static final String ALI_URL = "https://openapi.alipay.com/gateway.do";
    /**
     * 应用id，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/190/201602493024
     **/
    public static final String APP_ID = "2021002164661374";
    /**
     * 应用私钥，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/207/201602469554
     **/
    public static final String APP_PRIVATE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCpy+7TbBdGVhbaQhAOkYk1b4zKjH2U5sNQgnq9c/fI74ynHqCTXox8t95e171GIN1H5YonnT1RHq/UE8FnKcvRCkNDswkpo8ILAf0KKlFgfN1kqQrfJK9xST98g1CKU+fZ2WWIk7bgyMrs6VEWa09KJdb4um8YAnUvb3eDT91CgCMpFiIIpMl9TBiN0+qcG+2ORtJrJmCo8QveUpZtOl3DwO28jv+4YV5iXXgdv0TtILVQSU/QnGtmIXzbPcCAD9FuLbOLnP3HJNRpGmWO1vkQhLmzdVD9n3Y0OB6BzjBvtWSB18/oC9vmAU+vF60/OaLxWOWNQ0LFU5EcjyVHAMifAgMBAAECggEAKLxnaMu27cX7p5NP3N7npy1C/tkjy9RtKWSUY91tpgRqnzGG3rRBSi6mp+RkYW3DCNu2AHkF2+9byaqPrNtnLZijuJs8aIQEKrXoakbqzRZH2z1/ATgA61HibFHowbcNmcNBS7n8lwM1RA9Zx+Io3KYlY/j+bCkyyhWY+6TudWR5JxmM32wQBMASbf8d1af8xDXTGQSKXF+1qPM9E5nL+VxiNU/wJJ5MszKlvQApxKyVZ+3bZ8r66/qtn2GwNq1yzX+CaYfyffHz2uk1PfzTqM3tNqszHAXKFgBgoZs9tbNM+uNnxDrPnnuDCA5Tlam4nPbLikiA9yTACMyNoTbBQQKBgQD1r+gl6cy0btVVAuHJA3bRQzdMgOxaToXyqiUFfMb+xSPyF31m5CxM+P+IeUM9R3kE7PmchXc73aPf6HLMEAIrJVnK0AlE/yFbDa8gDWyW/Ll7NyJuXVp7P72UsbgVWqjBpEqUp3LTOeXlsobl+UjAWAml4jgNlO5MJC6Zr7XCIQKBgQCw7IZ6MQgwIuVtyPRhgaASkdg5jjUV0odUp42UeZx9X76+CgOWT/3A1zs9jppukUfaQa9Z2LnotSiL9ya54dIATtB9yuzMsh+cxb0aC8chZTnxvUxLcfcmsQgVK3Iw4nYdi9v1wD0C2tmozyIdx2z/V20qpZ8G451oDHnGdCqyvwKBgQCsUZqTrO4kx2/dVk4ifMmDcI+CmxIrLNQKJYgd1yyDWKYjkJIl7neb7TDc+aBNhKm+6K8SNxIv7P6ZdyG9OqUqueHGvC8kM4WjpW9lHcVCCTPW1g7SNavWshg4CIZCg/nFB4Q/y0pgGEXE23h+KF/8eEMcFBSYghK5WM9Of80NwQKBgQCDF77M62fVwwWcwznQxeuF1usQOn67HLOJ1lzhlvqNK1R6G5Fs3vh22wPaKL/lDWDgJ6t2N1AJTbItg4P+V4TzFXMGwkWTpqgl0Z68nd1+sTKuHEVb4aXv1VzX0slZz3MVkXv6K+cJJoAAxPnSduIckPsijnW29RC8+AGDOrAooQKBgFgrTJs8AjuVORcbJbYGT9r9LKpZQJHbyi6ClQWQxN9uytBHIdN5+CZnTL9TtItPhDz7p20JLOSUwWufBTmcG6GJvjCE7/vWlny/trIgNJwe/Bjn4mqezhJRCaE5T0IcRo6q4JSQASuc8Sb7Zx/UGBLSMc32MAZVLT3KS+U1lzI/";

    /**
     * 支付宝公钥，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/207/201602487431
     **/
    //String APP_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqcvu02wXRlYW2kIQDpGJNW+Myox9lObDUIJ6vXP3yO+Mpx6gk16MfLfeXte9RiDdR+WKJ509UR6v1BPBZynL0QpDQ7MJKaPCCwH9CipRYHzdZKkK3ySvcUk/fINQilPn2dlliJO24MjK7OlRFmtPSiXW+LpvGAJ1L293g0/dQoAjKRYiCKTJfUwYjdPqnBvtjkbSayZgqPEL3lKWbTpdw8DtvI7/uGFeYl14Hb9E7SC1UElP0JxrZiF82z3AgA/Rbi2zi5z9xyTUaRpljtb5EIS5s3VQ/Z92NDgegc4wb7VkgdfP6Avb5gFPrxetPzmi8VjljUNCxVORHI8lRwDInwIDAQAB";
    public static final String ALIPAY_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxPhoJ5557xk1l5k9zLslYbTYb0TfO/c+51FyV+wN4F7VCadppxrOLdEean23gYw4+6qM4A3LvNBCptnrXuRlyb80j0pbYK8hPf5l8i5VSwERXf72kbOxwgKGZPurbBSvZM+QWXxY8yrjXbHqTNKJxIhIAVDWSJqxB2KPtxeJZlylJ1YhUfSEoxbYHKeW7JJWaZzzuVfYkCwAdWFX0wKAAeEVlXmD8dAjwvXjD4a0JFrQJFT7w2fsZXbiFjdu3ufcQzGZUw4oJ5wHwMrblcOPhYmUDbbRgriVUU9VpoMfXdK8LBUtnC3UXHz823XPEgIsXniCSKXp23r+iJxi9jA+0wIDAQAB";

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
                    log.info("querying order:{} ", order);
                    if (CorgiOrder.PAY_TYPE.WX.equals(order.getPayType())) {
                        this.queryWXOrder(order);
                    }
                    if (CorgiOrder.PAY_TYPE.ALIPAY.equals(order.getPayType())) {
                        this.queryAlipayOrder(order);
                    }
                    if (CorgiOrder.PAY_TYPE.IN_APP.equals(order.getPayType())) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.MINUTE, -5);
                        if (order.getCtime().compareTo(order_sdf.format(calendar.getTime())) < 0) {
                            order.setStatus(CorgiOrder.STATUS.CLOSE);
                            corgiOrderService.updateOrder(order);
                        }
                    }
                }
            }
        }
    }

    private void queryAlipayOrder(CorgiOrder order) {
        AlipayClient alipayClient = new DefaultAlipayClient(ALI_URL, APP_ID, APP_PRIVATE_KEY, "json", "GBK", ALIPAY_PUBLIC_KEY, "RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", order.getTradeNo());
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
            order.setResult(JSON.toJSONString(JSONObject.toJSONString(response)));
            if (response.isSuccess()) {
                order.setBuyerId(response.getBuyerLogonId());
                if (response.getSendPayDate() != null) {
                    order.setPayTime(sdf.format(response.getSendPayDate()));
                }
                String tradeStatus = response.getTradeStatus();
                if ("TRADE_FINISHED".equals(tradeStatus) || "TRADE_SUCCESS".equals(tradeStatus)) {
                    order.setStatus(CorgiOrder.STATUS.SUCCESS);
                } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                    order.setStatus(CorgiOrder.STATUS.CLOSE);
                } else {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, -5);
                    if (order.getCtime().compareTo(order_sdf.format(calendar.getTime())) < 0) {
                        AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
                        closeRequest.setBizContent(bizContent.toString());
                        alipayClient.execute(closeRequest);
                    }
                }
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE, -5);
                if (order.getCtime().compareTo(order_sdf.format(calendar.getTime())) < 0) {
                    order.setStatus(CorgiOrder.STATUS.CLOSE);
                }
            }
        } catch (AlipayApiException e) {
            log.error(e.getErrMsg(), e);
            order.setResult(e.getErrMsg());
        }
        corgiOrderService.updateOrder(order);
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
                calendar.add(Calendar.MINUTE, -5);
                if (order.getCtime().compareTo(order_sdf.format(calendar.getTime())) < 0) {
                    wxPay.closeOrder(orderQuery);
                    order.setStatus(CorgiOrder.STATUS.CLOSE);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            order.setResult(e.getMessage());
        }
        corgiOrderService.updateOrder(order);
    }
}
