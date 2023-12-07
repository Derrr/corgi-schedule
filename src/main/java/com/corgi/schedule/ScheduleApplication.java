package com.corgi.schedule;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import com.corgi.common.CorgiQueueName;
import com.easemob.im.server.EMProperties;
import com.easemob.im.server.EMService;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executors;

/**
 * @author tairanliu
 */
@SpringBootApplication
@EnableScheduling
@EnableDubboConfiguration
@EnableAsync
public class ScheduleApplication implements SchedulingConfigurer {

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

	@Override
	public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {
		scheduledTaskRegistrar.setScheduler(Executors.newScheduledThreadPool(10));
	}

	/**
	 * 异步任务执行线程池
	 * @return
	 */
	@Bean(name = "asyncExecutor")
	public ThreadPoolTaskExecutor asyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(100);
		executor.setThreadNamePrefix("asyncTaskExecutor-");
		executor.initialize();
		return executor;
	}

	@Bean
	public EMService service() {

		EMProperties properties = EMProperties.builder()
				.setAppkey("1101200130181163#corgi")
				.setClientId("YXA6NW6WhxTlSd6PW28d8s2geQ")
				.setClientSecret("YXA6bXC8NAPVUHKlxTlhCSSZOVwyiAQ")
				.build();

		return new EMService(properties);
	}
}
