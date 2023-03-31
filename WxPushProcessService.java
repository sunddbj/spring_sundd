package com.ruoyi.project.wechatInner.service;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.project.wechatInner.domain.dto.WxPushDto;

/**
 * @className: WxPushProcessService
 * @description: 微信处理类
 * @author: sundd
 * @date: 2022/12/16 15:33
 * @version: 1.0
 */
public interface WxPushProcessService {

    /*
     * 功能描述：微信交易处理
     * @param prcscd
     * @param commReq
     * @param input
     * @return void
     * @author sundd
     * @date 2022/12/16 15:35
     **/
    void weiXinProcess(String prcscd, JSONObject commReq,JSONObject input);

    /*
     * 功能描述：
     * 微信发送
     * @param wxPushDto
     * @return java.lang.String
     * @author sundd
     * @date 2023/3/24 10:10
     **/
    public String sendTemplateMsg(WxPushDto wxPushDto);
}
