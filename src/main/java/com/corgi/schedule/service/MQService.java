package com.corgi.schedule.service;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author tairanliu
 */
@Service
public class MQService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
    }

}
