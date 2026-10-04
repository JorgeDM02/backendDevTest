package com.inditex.devtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DevtestApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevtestApplication.class, args);
	}

}
