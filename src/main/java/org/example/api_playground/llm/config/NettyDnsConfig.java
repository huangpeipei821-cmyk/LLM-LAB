package org.example.api_playground.llm.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class NettyDnsConfig {
    static {
        // Netty 默认用 Google DNS (8.8.8.8, 8.8.4.4)，国内不通
        // 换成国内 DNS：114 DNS 和阿里 DNS
        System.setProperty("reactor.netty.dns.nameservers", "114.114.114.114,223.5.5.5");
    }
}
