package com.calipso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CalipsoApplication {

	public static void main(String[] args) {
		SpringApplication.run(CalipsoApplication.class, args);
	}

}
