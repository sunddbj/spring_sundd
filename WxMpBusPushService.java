package com.ruoyi.project.wechatInner.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.WXConstants;
import com.ruoyi.common.enums.ChannelCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.integration.context.WxPushContextHolder;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.redis.RedisCache;
import com.ruoyi.project.system.domain.WeixinRemindAccount;
import com.ruoyi.project.system.domain.WeixinUserCard;
import com.ruoyi.project.system.domain.WxTransCode;
import com.ruoyi.project.system.service.IWeixinRemindAccountService;
import com.ruoyi.project.system.service.IWeixinUserCardService;
import com.ruoyi.project.system.service.IWxTransCodeService;
import com.ruoyi.project.wechatInner.domain.dto.WxPushDto;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @className: WxMpBusPushService
 * @description: 业务处理
 * @author: sundd
 * @date: 2022/12/19 16:46
 * @version: 1.0
 */
@Service
@Slf4j
public class WxMpBusPushService {

    @Value("${wx.mp.appId}")
    private String appId;

    @Autowired
    RedisCache redisCache;

    @Autowired
    IWeixinRemindAccountService iWeixinRemindAccountService;

    @Autowired
    private IWeixinUserCardService iWeixinUserCardService;

    @Autowired
    protected WxMpService wxMpService;

    @Autowired
    IWxTransCodeService iWxTransCodeService;
    /*
     * 功能描述：电子渠道登录提醒
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 20:37
     **/
    public WxPushDto electronicChannelLoginNotice(String prcscd,JSONObject inCommReq, JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        if (StringUtils.equals(input.getString("busType"),"01") && StringUtils.equals(input.getString("smsType"),"01")){
            String channel = input.getString("channel");//登录渠道
            String channelstr = "电子银行";
            if("030".equals(channel)){
                channelstr = "手机银行";
            }else if("017".equals(channel)){
                channelstr = "个人网银";
            }
            rs.add("尊敬的客户，您最新的电子渠道登录提醒：");
            rs.add(input.getString("userName"));
            rs.add(channelstr);
            rs.add(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS,DateUtils.parseDate(input.getString("logonTime"))));
            rs.add("为了您的用户安全，请确定是否为本人登录。");
        }
        String openId = getOpenId(null, input.getString("certType"), input.getString("certNo"), null, null, null);

        assembleWxDto(prcscd, inCommReq, wxPushDto, rs, openId, "001");
        return wxPushDto;
    }

    /*
     * 功能描述：机具异常提醒
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 20:37
     **/
    public WxPushDto machineWarningNotice(String prcscd,JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        return wxPushDto;
    }

    /**
     * 功能描述：动账提醒通知
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 16:48
     **/
    public WxPushDto transSuccessNotice(String prcscd,JSONObject inCommReq,JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        input.getString("batchno");
        input.getString("serisq");
        String msgcont = input.getString("msgcont");
        String replaceAll = msgcont.replaceAll("&quot;", "\"");
        JSONObject msgcontJson = JSONObject.parseObject(replaceAll);
        JSONObject dztxbody = msgcontJson.getJSONObject("tzxxbody");
        JSONObject header = msgcontJson.getJSONObject("header");
        String openId = getOpenId(null,null,null,null,dztxbody.getString("kehuzhao"),"F");
        //业务类型 001-动账提醒 002-日赚动账提醒
        String yewuleix = StringUtils.isBlank(dztxbody.getString("yewuleix")) ? "" : dztxbody.getString("yewuleix");
        if (StringUtils.equals("002",yewuleix)){
            dailyEarningReminder(rs, dztxbody, header); //日赚动账提醒
        }else {
            dynamicAccReminder(openId, rs, dztxbody, header); //动账提醒
        }
        assembleWxDto(prcscd, inCommReq, wxPushDto, rs, openId, "002");
        return wxPushDto;
    }

    /*
     * 功能描述：业绩实时推送
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 20:37
     **/
    public WxPushDto performanceNotice(String prcscd, JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        return wxPushDto;
    }

    /*
     * 功能描述：通用模板消息接口
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 20:37
     **/
    public WxPushDto generalMessageNotice(String prcscd,JSONObject inCommReq, JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        String temNo = input.getString("TemNo");//模板编号
        String openId = input.getString("OpenId");//微信OpenId
        String cardId = input.getString("CustNo");//客户账户
        String idType = input.getString("CustIdType");//客户证件类型
        String idNo = input.getString("CustIdNo");//客户证件号
        String first = input.getString("First");//推送first
        String data = input.getString("Data");//推送Data
        String remark = input.getString("Remark");//推送remark
        if(StringUtils.isBlank(openId)){
            openId = getOpenId(null, idType, idNo, null, cardId, null);
        }
        data = data.replaceAll("&quot;", "\"");
        String substring = "{"+data.trim().substring(1, data.length() - 1)+"}";
        JSONObject dataJson = JSONObject.parseObject(substring);
        String strFirst = first.replaceAll("(\\\\r\\\\n|\\\\r|\\\\n|\\\\n\\\\r)", "");
        rs.add(strFirst);
        for (int i = 1; i < 10; i++) {
            String value = dataJson.getString(WXConstants.WX_KEYWORD + i);
            if (StringUtils.isBlank(value)){
                break;
            }
            rs.add(JSONObject.parseObject(value).getString("value"));
        }
        rs.add(remark);

        assembleWxDto(prcscd, inCommReq, wxPushDto, rs, openId, temNo);
        return wxPushDto;
    }

    /*
     * 功能描述：获取jsapiticket
     * @param prcscd
     * @param input
     * @return com.ruoyi.project.wechatInner.domain.dto.WxPushDto
     * @author sundd
     * @date 2022/12/29 9:23
     **/
    public WxPushDto getJsapiticket(String prcscd,JSONObject inCommReq, JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        JSONObject commReq = new JSONObject();
        JSONObject output = new JSONObject();
        try {
            String jsapiTicket = wxMpService.getJsapiTicket();
            String jsapiKey = String.format("wx:ticket:key:%s:jsapi", appId);
            long expire = redisCache.getExpire(jsapiKey);
            output.put("jsApiTicket",jsapiTicket);
            output.put("outTime",expire);
        } catch (WxErrorException e) {
            log.error("error",e);
        }
        JSONObject resMsg = assembleUCMSSuccessMsg(inCommReq,input,commReq,output);
        wxPushDto.setPrcscd(prcscd);
        wxPushDto.setResMsg(resMsg);
        WxPushContextHolder.setContext(wxPushDto);
        return wxPushDto;
    }

    /*
     * 功能描述：获取用户是否绑卡
     * @param prcscd
     * @param input
     * @return com.ruoyi.project.wechatInner.domain.dto.WxPushDto
     * @author sundd
     * @date 2022/12/29 9:23
     **/
    public WxPushDto getUserBindsCardNotice(String prcscd,JSONObject inCommReq, JSONObject input){
        WxPushDto wxPushDto = new WxPushDto();
        String openId = input.getString("openId");//微信OpenId
        JSONObject commReq = new JSONObject();
        JSONObject output = new JSONObject();
        WeixinUserCard weixinUserCard = new WeixinUserCard();
        weixinUserCard.setOpenid(openId);
        List<WeixinUserCard> weixinUserCards = iWeixinUserCardService.selectWeixinUserCardList(weixinUserCard);
        if(StringUtils.isEmpty(weixinUserCards)){
            output.put("isBindUser","false");
        }else {
            output.put("isBindUser","true");
            output.put("idNumber",weixinUserCards.get(0).getIdnumber());
            output.put("idType",weixinUserCards.get(0).getIdtype());
            output.put("mobile",weixinUserCards.get(0).getMobile());
        }
        JSONObject resMsg = assembleUCMSSuccessMsg(inCommReq,input,commReq,output);
        wxPushDto.setPrcscd(prcscd);
        wxPushDto.setOpenId(openId);
        wxPushDto.setResMsg(resMsg);
        WxPushContextHolder.setContext(wxPushDto);
        return wxPushDto;
    }

    /**
     * 功能描述：贷款审批通知
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 16:48
     **/
    public WxPushDto loanApprovalNotice(){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        return wxPushDto;
    }

    /**
     * 功能描述：额度审批通过通知
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 16:48
     **/
    public WxPushDto quotaApprovedNotice(){
        WxPushDto wxPushDto = new WxPushDto();
        List<String> rs = new ArrayList<>();
        return wxPushDto;
    }

    /**
     * 功能描述：默认通知类型抛出异常
     * @return java.util.List<java.lang.String>
     * @author sundd
     * @date 2022/12/19 16:48
     **/
    public void dufaultNotice(){
        log.error("未找到业务通知类型，请查证！");
        throw new ServiceException("未找到业务通知类型!");
    }
    //组装微信数据dto
    private void assembleWxDto(String prcscd, JSONObject inCommReq, WxPushDto wxPushDto, List<String> rs, String openId,String code) {
        JSONObject resMsg = assembleSuccessMsg(inCommReq);
        wxPushDto.setPrcscd(prcscd);
        wxPushDto.setOpenId(openId);
        WxTransCode wxTransCode = iWxTransCodeService.selectWxTransCodeByCode(code);
        wxPushDto.setTemplateId(wxTransCode.getTemplateId());
        wxPushDto.setUrl(wxTransCode.getUrl());
        wxPushDto.setColor(wxTransCode.getColor());
        wxPushDto.setTemplateData(rs);
        wxPushDto.setResMsg(resMsg);
        WxPushContextHolder.setContext(wxPushDto);
    }

    //组装返回报文（暂时为公共）
    private JSONObject assembleSuccessMsg(JSONObject inCommReq) {
        JSONObject resMsg = new JSONObject();
        JSONObject sysJson = new JSONObject();
        sysJson.put("pckgsq",inCommReq.getString(WXConstants.WX_REQ_WAIBLIUS));
        sysJson.put("erortx", WXConstants.WX_RESP_ERORTX);
        sysJson.put("erorcd",WXConstants.WX_RESP_ERORCD);
        resMsg.put(WXConstants.WX_RESP_SYS_JSON, sysJson);
        //返回报文头（暂无）
        resMsg.put(WXConstants.WX_RESP_COMM_REQ, new JSONObject());
        //返回报文体（暂无）
        resMsg.put(WXConstants.WX_RESP_OUT_PUT, new JSONObject());
        return resMsg;
    }

    private JSONObject assembleUCMSSuccessMsg(JSONObject inCommReq ,JSONObject input,JSONObject commReq,JSONObject output) {
        JSONObject resMsg = new JSONObject();
        JSONObject sysJson = new JSONObject();
        sysJson.put("pckgsq",inCommReq.getString(WXConstants.WX_REQ_WAIBLIUS));
        sysJson.put("erortx", WXConstants.WX_RESP_ERORTX);
        sysJson.put("erorcd",WXConstants.WX_RESP_ERORCD);
        resMsg.put(WXConstants.WX_RESP_SYS_JSON, sysJson);
        //返回报文头
        resMsg.put(WXConstants.WX_RESP_COMM_REQ, commReq);
        //返回报文体
        resMsg.put(WXConstants.WX_RESP_OUT_PUT, output);
        return resMsg;
    }


    /*
     * 功能描述：获取openId
     * @param isalert 是否动账
     * @param idtype 证件类型
     * @param idnumber 证件号码
     * @param customerId 核心客户号
     * @return java.lang.String
     * @author sundd
     * @date 2022/12/17 10:18
     **/
    private String getOpenId(String isalert,String idtype,String idnumber,String customerId,String cardId,String delFlag) {
        WeixinUserCard weixinUserCard = new WeixinUserCard();
        if (!StringUtils.isEmpty(isalert)){
            weixinUserCard.setIsalert(isalert);
        }
        if (!StringUtils.isEmpty(idtype)){
            weixinUserCard.setIdtype(idtype);
        }
        if (!StringUtils.isEmpty(idnumber)){
            weixinUserCard.setIdnumber(idnumber);
        }
        if (!StringUtils.isEmpty(customerId)){
            weixinUserCard.setCustomerId(customerId);
        }
        if (!StringUtils.isEmpty(cardId)){
            weixinUserCard.setCardid(cardId);
        }
        weixinUserCard.setDelflag("F");
        log.info("查询微信openId参数：{}",weixinUserCard);
        if (StringUtils.isNull(weixinUserCard)){
            log.error("查询微信openId参数为空！");
            throw new ServiceException("查询微信openId参数为空！");
        }
        List<WeixinUserCard> weixinUserCards = iWeixinUserCardService.selectWeixinUserCardList(weixinUserCard);
        if (StringUtils.isEmpty(weixinUserCards)){
            log.error("未查询到微信openId！数据为：{}",weixinUserCard.toString());
            throw new ServiceException("未查询到微信openId！数据为："+weixinUserCard.toString());
        }
        return weixinUserCards.get(0).getOpenid();
    }



    private void dynamicAccReminder(String openId, List<String> rs, JSONObject dztxbody, JSONObject header) {

        String duifkhzh = dztxbody.getString("duifzhho");
        if (!StringUtils.isEmpty(duifkhzh)){
            duifkhzh = duifkhzh.substring(duifkhzh.length()-4);//对方客户账户

        }
        String qudaohao = header.getString("qudaohao");
        BigDecimal jiaoyije = dztxbody.getBigDecimal("jiaoyije");//交易金额
        String jiedaibz = dztxbody.getString("jiedaibz");//借贷标志0—支出1—收入
        String jiaoyirq = header.getString("jiaoyirq");//交易日期
        String jiaoyisj = header.getString("jiaoyisj");//交易时间
        String kehuzhao = dztxbody.getString("kehuzhao");//卡号
        String zhaiyoms = dztxbody.getString("zhaiyoms");//摘要描述
        String zhaiyodm = dztxbody.getString("zhaiyodm");//摘要代码
        String hxremark = dztxbody.getString("hxremark").trim();//备注
        String duifminc = dztxbody.getString("duifminc");//对方户名
        String beizhuxx = dztxbody.getString("beizhuuu");//附言
        BigDecimal keyongye = dztxbody.getBigDecimal("keyongye");//可用余额
        if (StringUtils.isEmpty(qudaohao)){
            qudaohao = "——";
        }else {
            ChannelCode enumByCode = ChannelCode.findEnumByCode(qudaohao);
            if (StringUtils.isNull(enumByCode)){
                qudaohao = "——";
            }else {
                qudaohao = enumByCode.getName();
            }
        }
        if (StringUtils.equals("03",header.getString("tzxinxzl"))){ //是否为动账
            WeixinRemindAccount weixinRemindAccount = new WeixinRemindAccount();
            weixinRemindAccount.setWraOpenId(openId);
            weixinRemindAccount.setWraIdNumber(kehuzhao);
            List<WeixinRemindAccount> weixinRemindAccounts = iWeixinRemindAccountService.selectWeixinRemindAccountList(weixinRemindAccount);
            if(weixinRemindAccounts.size()<=0){
                log.error("该客户没有开通动账提醒");
                throw new ServiceException("该客户没有开通动账提醒");
            }
            String remind = weixinRemindAccounts.get(0).getWraRemind();//是否开启动账提醒
            BigDecimal qdjine = weixinRemindAccounts.get(0).getWraMoney();//起点金额
            String balance = weixinRemindAccounts.get(0).getWraBalance();//是否显示余额
            if("0".equals(remind)){
                log.error("该客户没有开通动账提醒");
                throw new ServiceException("该客户没有开通动账提醒");
            }
            if(jiaoyije.compareTo(qdjine) == -1){
                log.error("交易金额未达到指定起点金额");
                throw new ServiceException("交易金额未达到指定起点金额");
            }
            String jiaoyijeStr = ("0".equals(jiedaibz)?"支出":"收入")+"人民币"+jiaoyije;
            jiaoyisj = StringUtils.leftPad(jiaoyisj, 9, '0');
            String transDate = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS,DateUtils.parseDate(jiaoyirq+jiaoyisj));

            rs.add("尊敬的客户：您尾号"+kehuzhao.substring(kehuzhao.length()-4)+"的借记卡交易信息如下,");
            rs.add(transDate);
            rs.add(qudaohao);
            rs.add(zhaiyoms);
            rs.add(jiaoyijeStr+"元");
            if (StringUtils.equals("0",balance)){  //未开启余额提醒
                rs.add("****");
                if (StringUtils.isNotEmpty(hxremark)){
                    rs.add(hxremark);
                }else if("038".equals(header.get("qudaohao")) && "MB8001".equals(zhaiyodm)){
                    String tjx ="付款方名称："+(duifminc==null?"无":duifminc)+"\\n付款方账号："+(duifkhzh==null?"无":duifkhzh)+"\\n附言："+(beizhuxx==null?"无":beizhuxx)+"";
                    rs.add(tjx);
                }else {
                    rs.add("您当前的设置不支持显示余额，如需开通，请到“账户管理”进行设置。");
                }
            }else {
                rs.add(keyongye.toString()+"元");
                if (StringUtils.isNotEmpty(hxremark)){
                    rs.add(hxremark);
                }else if("038".equals(header.get("qudaohao")) && "MB8001".equals(zhaiyodm)){
                    String tjx ="付款方名称："+(duifminc==null?"无":duifminc)+"\\n付款方账号："+(duifkhzh==null?"无":duifkhzh)+"\\n附言："+(beizhuxx==null?"无":beizhuxx)+"";
                    rs.add(tjx);
                }else {
                    rs.add("如需隐藏余额，请到“账户管理”进行设置。");
                }
            }
        }
    }

    private void dailyEarningReminder(List<String> rs, JSONObject dztxbody, JSONObject header) {
        String jiaoyileix = dztxbody.getString("jiaoyileix");//交易类型标志
        String qudaohao = header.getString("qudaohao");//交易渠道
        String jiaoyirq = header.getString("jiaoyirq");//交易日期
        String kehuzhao = dztxbody.getString("kehuzhao");//卡号
        BigDecimal rizhlixi = dztxbody.getBigDecimal("rizhlixi");//日赚利息
        BigDecimal zhanghye = dztxbody.getBigDecimal("zhanghye");//账户余额
        BigDecimal rizhyuee = dztxbody.getBigDecimal("rizhyuee");//日赚账户余额
        if("001".equals(jiaoyileix)){
            jiaoyileix = "转息";
        }
        if (StringUtils.isEmpty(qudaohao)){
            qudaohao = "——";
        }else {
            ChannelCode enumByCode = ChannelCode.findEnumByCode(qudaohao);
            if (StringUtils.isNull(enumByCode)){
                qudaohao = "——";
            }else {
                qudaohao = enumByCode.getName();
            }
        }
        String transDate = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD,DateUtils.parseDate(jiaoyirq));
        rs.add("尊敬的客户：您尾号"+kehuzhao.substring(kehuzhao.length()-4)+"的借记卡交易信息如下,");
        rs.add(transDate);
        rs.add(qudaohao);
        rs.add(jiaoyileix);
        rs.add(rizhlixi+"元");
        rs.add(zhanghye+"元");
        rs.add("转入一天通知存款（日赚）利息"+rizhlixi+"元，当前日赚账户余额"+rizhyuee+"元");
    }

}
