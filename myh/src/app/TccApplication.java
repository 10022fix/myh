package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"app", "Control", "Model", "Web"})
public class TccApplication {
    public static void main(String[] args) {
        SpringApplication.run(TccApplication.class, args);
    }
}
