package org.anti.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"org.anti.seckill"})
@RestController
@MapperScan("org.anti.seckill.mapper")
public class App {

    @RequestMapping("/")
    public String index() {
        return "Hello World!!!";
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
