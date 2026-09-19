package com.accountflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccountFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountFlowApplication.class, args);
	}

}
