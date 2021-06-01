package com.corgi.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//@SpringBootTest
class ScheduleApplicationTests {

	@Test
	void contextLoads() {
		System.out.println(new Double(Math.round(Math.pow(182 * 1.0, 1.5) * 10)).intValue());
	}

}
