package com.ruoyi.common.integration;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.WXConstants;
import com.ruoyi.common.integration.context.WxPushContextHolder;
import com.ruoyi.common.utils.JsonXmlUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.project.wechatInner.domain.dto.WxPushDto;
import com.ruoyi.project.wechatInner.service.WxPushProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.*;
import java.net.Socket;

@Slf4j
public class ServerReceiveThread implements Runnable {

    private Socket client;
    private WxPushProcessService wxPushProcessService;

    private static final String respMsg = "RespMsg";

    public ServerReceiveThread(Socket socket,WxPushProcessService wxPushProcessService) {
        this.client = socket;
        this.wxPushProcessService = wxPushProcessService;
    }

    @Override
    public void run() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        DataInputStream dataInputStream = null;
        DataOutputStream dataOutputStream = null;
        ByteArrayOutputStream currentReceivePacket = null;
        String receiveMessage = StringUtils.EMPTY;
        String responseMessage = StringUtils.EMPTY;
        String pckgsq = StringUtils.EMPTY;
        try {
            dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());
            currentReceivePacket = new ByteArrayOutputStream();
            String reqAddress = client.getRemoteSocketAddress().toString();
            receiveMessage = receiveMessage(dataInputStream, currentReceivePacket);
            log.info("请求地址：{}",reqAddress);
            pckgsq = receiveMessage.substring(16, 36);
            log.info("业务处理开始，线程ID:{}，线程名称：{}",Thread.currentThread().getId(),Thread.currentThread().getName());
            busProess(receiveMessage);
            log.info("业务处理结束，线程ID:{}，线程名称：{}",Thread.currentThread().getId(),Thread.currentThread().getName());
            WxPushDto context = WxPushContextHolder.getContext();
            AbstractResponseMessage respMsgBean = (AbstractResponseMessage)SpringUtils.getBean(context.getPrcscd() + respMsg);
            responseMessage = respMsgBean.process(context.getResMsg().getJSONObject(WXConstants.WX_RESP_SYS_JSON),
                    context.getResMsg().getJSONObject(WXConstants.WX_RESP_COMM_REQ),
                    context.getResMsg().getJSONObject(WXConstants.WX_RESP_OUT_PUT));
        } catch (NumberFormatException e){
            responseMessage = "F5-Heartbeat response";
        } catch (Exception e) {
            if (currentReceivePacket != null){
                log.error("处理报文异常",e);
            }
            responseMessage = "                01720160420123456789000057170000000100180111010170170030                                            <?xml version=\"1.0\" encoding=\"utf-8\"?><root><object name=\"sys\"><field name=\"erortx\" value=\"交易处理失败！\"/><field name=\"erorcd\" value=\"WX9999999\"/><field name=\"pckgsq\" value=\""+pckgsq+"\"/></object></root>";
            throw new RuntimeException("接收报文处理异常：",e);
        } finally {
            try {
                responsePacket(dataOutputStream, responseMessage);
            } catch (IOException ioException) {
                log.error("处理数据异常时返回错误结果异常！",ioException);
            }
            stopWatch.stop();
            if (!StringUtils.contains(responseMessage,"F5-Heartbeat")){
                log.info("接收通知数据处理时间：{},流水号：{}",stopWatch.getTotalTimeMillis(),pckgsq);
            }
            WxPushContextHolder.clearContext();
            try {
                if (currentReceivePacket != null)
                    currentReceivePacket.close();
                if (dataOutputStream != null)
                    dataOutputStream.close();
                if (dataInputStream != null)
                    dataInputStream.close();
                if (client != null)
                    client.close();
            } catch (IOException e) {
                log.error("流关闭异常！");
                throw new RuntimeException("流关闭异常！");
            }

        }
    }

    private void busProess(String receive) {
        String prcscd = StringUtils.EMPTY; //业务码
        JSONObject commReq = new JSONObject();//交易公共请求对象
        JSONObject input = new JSONObject();//交易业务输入对象
        String pckgsq = receive.substring(16, 36);
        String substringXml = receive.substring(116);
        JSONObject xml2Json = JsonXmlUtils.xml2Json(substringXml);
        JSONArray objectJSONArray = xml2Json.getJSONArray(WXConstants.WX_OBJECT);
        log.debug("XML转Json处理后的数据：{}",xml2Json);
        for (int i = 0; i < objectJSONArray.size(); i++) {
            JSONObject busObject = objectJSONArray.getJSONObject(i);
            if (busObject.containsKey(WXConstants.WX_SYS)){
                JSONObject sys = busObject.getJSONObject(WXConstants.WX_SYS);
                prcscd = sys.getString(WXConstants.WX_PRCSCD);
            }
            if (busObject.containsKey(WXConstants.WX_COMM_REQ)){
                commReq = busObject.getJSONObject(WXConstants.WX_COMM_REQ);
            }
            if (busObject.containsKey(WXConstants.WX_INPUT)){
                input = busObject.getJSONObject(WXConstants.WX_INPUT);
                input.put(WXConstants.WX_REQ_PCKGSQ,pckgsq);
            }
        }
        wxPushProcessService.weiXinProcess(prcscd,commReq,input);
    }

    private void responsePacket(DataOutputStream dataOutputStream, String packet) throws IOException {
        String head = generateResponseHead(packet);
        String message = head + packet;
        if (!StringUtils.contains(packet,"F5-Heartbeat")){
            log.info("返回报文：{}",message);
        }
        dataOutputStream.write(message.getBytes("utf-8"));
        dataOutputStream.flush();
    }

    private String generateResponseHead(String packet) throws UnsupportedEncodingException {
        Integer length = Integer.valueOf((packet.getBytes("UTF-8")).length);
        return String.format("%08d", new Object[] { length });
    }

    private String receiveMessage(DataInputStream din, ByteArrayOutputStream currentReceivePacket) throws IOException {
        Integer packetLength = receivePacketLength(din, currentReceivePacket);
        byte[] readBuffer = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String message = "";
        try {
            int totalSize = 0;
            int size = -1;
            while ((size = din.read(readBuffer)) != -1) {
                currentReceivePacket.write(readBuffer, 0, size);
                baos.write(readBuffer, 0, size);
                totalSize += size;
                if (totalSize >= packetLength.intValue())
                    break;
            }
            message = baos.toString("utf-8");
        } finally {
            baos.close();
        }
        log.info("完整报文：{}",currentReceivePacket.toString("utf-8"));
        return message;
    }

    private Integer receivePacketLength(DataInputStream din, ByteArrayOutputStream currentReceivePacket) throws IOException {
        byte[] readBuffer = new byte[8];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int totalSize = 0;
        int size = -1;
        boolean received = false;

        while ((size = din.read(readBuffer)) != -1) {
            if (!received) {
                received = true;
            }
            currentReceivePacket.write(readBuffer, 0, size);
            baos.write(readBuffer, 0, size);
            totalSize += size;
            if (totalSize == 8)
                break;
        }
        Integer length = Integer.valueOf(0);
        try {
            length = Integer.valueOf(Integer.parseInt(baos.toString()));
        } finally {
            baos.close();
        }
        log.info("请求报文长度是：{}",length);
        return length;
    }
}

