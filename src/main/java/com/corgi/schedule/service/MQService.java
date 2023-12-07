package com.corgi.schedule.service;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.RecommendCalculater;
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

    public void sendBillboardMessage(PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.ONBOARD_QUEUE, pushMessage);
    }

    public void sendInfluencerLeftMessage(PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.INFLUENCER_LEFT_QUEUE, pushMessage);
    }

    public void sendCalculater(RecommendCalculater calculater) {
        rabbitTemplate.convertAndSend(CorgiQueueName.USER_RECOMMEND_QUEUE, calculater);
    }

    public void sendActivityCalculater(RecommendCalculater calculater) {
        rabbitTemplate.convertAndSend(CorgiQueueName.ACTIVITY_RECOMMEND_QUEUE, calculater);
    }

    public void sendDateMessage(PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.USER_DATE_QUEUE, pushMessage);
    }

    public void sendPreferGroup(RecommendCalculater calculater) {
        rabbitTemplate.convertAndSend(CorgiQueueName.USER_PREFER_QUEUE, calculater);
    }

    public void sendGroup(RecommendCalculater calculater) {
        rabbitTemplate.convertAndSend(CorgiQueueName.USER_GROUP_QUEUE, calculater);
    }
}
