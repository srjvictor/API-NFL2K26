package com.nfl.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NflApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(NflApiApplication.class, args);
	}

}
