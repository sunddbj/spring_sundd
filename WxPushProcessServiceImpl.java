package com.ruoyi.project.wechatInner.service.impl;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.WXConstants;
import com.ruoyi.common.constant.WxMpPushTypeConstants;
import com.ruoyi.common.exception.UtilException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.system.domain.WxNoticeLogs;
import com.ruoyi.project.system.service.IWxNoticeLogsService;
import com.ruoyi.project.wechatInner.domain.dto.WxPushDto;
import com.ruoyi.project.wechatInner.service.WxMpBusPushService;
import com.ruoyi.project.wechatInner.service.WxPushProcessService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @className: WxPushProcessServiceImpl
 * @description: 实现类
 * @author: sundd
 * @date: 2022/12/16 15:36
 * @version: 1.0
 */
@Service
@Slf4j
public class WxPushProcessServiceImpl  implements WxPushProcessService {

    @Autowired
    protected WxMpService wxMpService;

    @Autowired
    WxMpBusPushService wxMpBusPushService;

    @Autowired
    IWxNoticeLogsService iWxNoticeLogsService;

    @Override
    public void weiXinProcess(String prcscd, JSONObject commReq, JSONObject input) {

        WxPushDto wxPushDto = null;
        switch (prcscd){
            case WxMpPushTypeConstants.WX_UCMS001: //电子渠道登录提醒
                wxPushDto = wxMpBusPushService.electronicChannelLoginNotice(prcscd,commReq,input);
                break;
            case WxMpPushTypeConstants.WX_UCMS002: //TODO 机具异常提醒
                wxPushDto = wxMpBusPushService.machineWarningNotice(prcscd,input);
                break;
            case WxMpPushTypeConstants.WX_MSG001: //动账提醒通知
                wxPushDto = wxMpBusPushService.transSuccessNotice(prcscd,commReq,input);
                break;
            case WxMpPushTypeConstants.WX_UCMS003: //TODO 业绩实时推送
                wxPushDto = wxMpBusPushService.performanceNotice(prcscd,input);
                break;
            case WxMpPushTypeConstants.WX_UCMS005: //通用模板消息接口
                wxPushDto = wxMpBusPushService.generalMessageNotice(prcscd,commReq,input);
                break;
            case WxMpPushTypeConstants.WX_UCMS006: //获取用户是否绑卡
                wxMpBusPushService.getUserBindsCardNotice(prcscd, commReq, input);
                return;
            case WxMpPushTypeConstants.WX_UCMS007: //获取jsapiticket
                wxMpBusPushService.getJsapiticket(prcscd,commReq,input);
                return;
            default:
                wxMpBusPushService.dufaultNotice();
                break;
        }
        if (StringUtils.isEmpty(wxPushDto.getTemplateData())){
            log.error("业务数据为空！");
            throw new UtilException("业务数据为空！");
        }
        log.info("微信通知发送报文：{}",wxPushDto.toString());
        sendTemplateMsg(wxPushDto);
    }

    @Override
    public String sendTemplateMsg(WxPushDto wxPushDto){
        String msgId = StringUtils.EMPTY;
        WxNoticeLogs wxNoticeLogs = initWxNoticeLogs(wxPushDto);
        WxMpTemplateMessage wxMpTemplateMessage = WxMpTemplateMessage.builder()
                .toUser(wxPushDto.getOpenId())
                .templateId(wxPushDto.getTemplateId())
                .url(wxPushDto.getUrl())
                .build();
        assembleData(wxPushDto, wxMpTemplateMessage);
        try {
            msgId = wxMpService.getTemplateMsgService().sendTemplateMsg(wxMpTemplateMessage);
        } catch (WxErrorException e) {
            wxNoticeLogs.setStatus("2");
            wxNoticeLogs.setErrCode(String.valueOf(e.getError().getErrorCode()));
            wxNoticeLogs.setErrMsg(e.getError().getErrorMsg());
            iWxNoticeLogsService.updateWxNoticeLogs(wxNoticeLogs);
            log.error("微信公众号通知通讯异常！",e);
            throw new UtilException("微信公众号通知通讯异常！");
        }
        Assert.notBlank(msgId);
        wxNoticeLogs.setStatus("1");
        wxNoticeLogs.setMsgId(msgId);
        wxNoticeLogs.setErrCode("000000");
        wxNoticeLogs.setErrMsg("成功");
        iWxNoticeLogsService.updateWxNoticeLogs(wxNoticeLogs);
        log.info("微信通知成功，微信openId：{}，返回msgId：{}",wxPushDto.getOpenId(),msgId);
        return msgId;
    }

    private void assembleData(WxPushDto wxPushDto, WxMpTemplateMessage wxMpTemplateMessage) {
        List<String> templateDataList = wxPushDto.getTemplateData();
        for (int i = 0; i < templateDataList.size(); i++) {
            WxMpTemplateData wxMpTemplateData = new WxMpTemplateData();
            String data = templateDataList.get(i);
            if (i == 0){
                wxMpTemplateData.setName(WXConstants.WX_FIRST);
                wxMpTemplateData.setValue(data);
                wxMpTemplateData.setColor(wxPushDto.getColor());
            }else if (i == templateDataList.size()-1){
                wxMpTemplateData.setName(WXConstants.WX_REMARK);
                wxMpTemplateData.setValue(data);
                wxMpTemplateData.setColor(wxPushDto.getColor());
            }else {
                wxMpTemplateData.setName(WXConstants.WX_KEYWORD+i);
                wxMpTemplateData.setValue(data);
                wxMpTemplateData.setColor(wxPushDto.getColor());
            }
            wxMpTemplateMessage.addData(wxMpTemplateData);
        }
    }

    private WxNoticeLogs initWxNoticeLogs(WxPushDto wxPushDto) {
        WxNoticeLogs wxNoticeLogs = new WxNoticeLogs();
        wxNoticeLogs.setOpenId(wxPushDto.getOpenId());
        wxNoticeLogs.setTemplateId(wxPushDto.getTemplateId());
        wxNoticeLogs.setUrl(wxPushDto.getUrl());
        wxNoticeLogs.setColor(wxPushDto.getColor());
        wxNoticeLogs.setTemplateData(wxPushDto.getTemplateData().toString());
        wxNoticeLogs.setStatus("0");
        wxNoticeLogs.setCreatedDate(new Date());
        iWxNoticeLogsService.insertWxNoticeLogs(wxNoticeLogs);
        return wxNoticeLogs;
    }

}
