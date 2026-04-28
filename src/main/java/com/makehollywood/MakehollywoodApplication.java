package com.makehollywood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MakehollywoodApplication {

	public static void main(String[] args) {
		SpringApplication.run(MakehollywoodApplication.class, args);
	}

}
