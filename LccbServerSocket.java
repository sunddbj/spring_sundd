package com.ruoyi.common.integration;

import com.ruoyi.framework.config.properties.NettyServerProperties;
import com.ruoyi.project.wechatInner.service.WxPushProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@Component
@Slf4j
public class LccbServerSocket {

    @Autowired
    ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private NettyServerProperties nettyServerProperties;

    @Autowired
    WxPushProcessService wxPushProcessService;

    public static ServerSocket serverSocket = null;

    public void start() {
        try {
            serverSocket = new ServerSocket(nettyServerProperties.getPort());
            log.info("socket服务端开启，端口：{}",nettyServerProperties.getPort());
            while (true){
                Socket socket = serverSocket.accept();
                taskExecutor.execute(new ServerReceiveThread(socket,wxPushProcessService));
            }
        } catch (IOException e) {
            log.error("socket服务启动异常",e);
            throw new RuntimeException("socket服务启动异常");
        }
    }
}
