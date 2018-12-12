package eurekaserver.eurekaservercfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaservercfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaservercfigApplication.class, args);
    }
}
