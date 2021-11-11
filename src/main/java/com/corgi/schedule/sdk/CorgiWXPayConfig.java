package com.corgi.schedule.sdk;


import java.io.InputStream;

public class CorgiWXPayConfig extends WXPayConfig {

    public static final WXPayConfig config = new CorgiWXPayConfig();
    private IWXPayDomain domain = new IWXPayDomain() {
        private DomainInfo domainInfo = new DomainInfo("api.mch.weixin.qq.com", true);
        @Override
        public void report(String domain, long elapsedTimeMillis, Exception ex) {

        }
        @Override
        public DomainInfo getDomain(WXPayConfig config) {
            return this.domainInfo;
        }
    };

    @Override
    public String getAppID() {
        return "wx19ea9b13f4eb65d4";
    }

    @Override
    public boolean shouldAutoReport() {
        return false;
    }

    @Override
    public String getMchID() {
        return "1588297071";
    }

    @Override
    public String getKey() {
        return "sIZeaIwGecFb5byfHVAGm0p7Yf9GZg73";
        //return "6b61f80a7b4ff075c563fc923725c157";
    }

    @Override
    public InputStream getCertStream() {
        return null;
    }

    @Override
    public IWXPayDomain getWXPayDomain() {
        return this.domain;
    }
}
