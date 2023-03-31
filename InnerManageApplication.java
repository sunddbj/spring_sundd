package com.ruoyi;

import com.ruoyi.common.integration.LccbServerSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class InnerManageApplication implements CommandLineRunner
{
    @Autowired
    private LccbServerSocket lccbServerSocket;

    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(InnerManageApplication.class, args);
    }

    @Override
    public void run(String... args) {
        lccbServerSocket.start();
    }
}
