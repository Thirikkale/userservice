package com.thirikkale.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.dir:uploads/}")
    private String uploadDir;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(clientHttpRequestFactory());
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds connection timeout
        factory.setReadTimeout(30000); // 30 seconds read timeout (reduced from 2 minutes)
        return factory;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Get absolute path to uploads directory
        Path uploadsPath = Paths.get(uploadDir).toAbsolutePath();
        String uploadsLocation = "file:///" + uploadsPath.toString().replace("\\", "/") + "/";

        // Map /uploads/** URLs to the uploads directory
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsLocation);

        System.out.println("📂 Static file serving configured:");
        System.out.println("   URL Pattern: /uploads/**");
        System.out.println("   File Location: " + uploadsLocation);
    }
}