package com.corgi.schedule;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import com.corgi.common.CorgiQueueName;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author tairanliu
 */
@SpringBootApplication
@EnableScheduling
@EnableDubboConfiguration
@EnableAsync
public class ScheduleApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScheduleApplication.class, args);
	}

	@Bean
	public Queue pushMessageQueue() {
		return new Queue(CorgiQueueName.PUSH_MESSAGE_QUEUE);
	}

	@Bean
	public Queue userRecommednQueue() {
		return new Queue(CorgiQueueName.USER_RECOMMEND_QUEUE);
	}

}
