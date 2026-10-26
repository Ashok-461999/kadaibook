package com.kadaibook;

import com.kadaibook.config.AiProperties;
import com.kadaibook.config.CorsProperties;
import com.kadaibook.config.DemoProperties;
import com.kadaibook.config.JwtProperties;
import com.kadaibook.config.RetentionProperties;
import com.kadaibook.config.StorageProperties;
import com.kadaibook.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        JwtProperties.class,
        AiProperties.class,
        StorageProperties.class,
        UploadProperties.class,
        RetentionProperties.class,
        CorsProperties.class,
        DemoProperties.class
})
public class KadaiBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(KadaiBookApplication.class, args);
    }
}
