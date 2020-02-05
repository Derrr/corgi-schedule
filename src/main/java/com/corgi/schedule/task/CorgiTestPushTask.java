package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserLogin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CorgiTestPushTask {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Async
    @Scheduled(cron = "0 0 10-22 * * *")
    public void run() {
        for (int i = 1; i < 1000; i++) {
            PushMessage pushMessage = PushMessage.builder()
                    .message("定时测试")
                    .sourceUserId("-1")
                    .type(PushMessage.DEFAULT)
                    .targetUserId(i + "")
                    .build();
            rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
        }

    }
}
