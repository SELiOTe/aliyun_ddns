package com.seliote.aliyunddns.conf;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云配置
 *
 * @author seliote
 * @since 2023-06-24
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aliyun")
public class AliyunConfig {

    private final AccessKey accessKey = new AccessKey();
    private final Api api = new Api();
    private final Dns dns = new Dns();

    @Getter
    @Setter
    public static class AccessKey {
        private String id;
        private String secret;
    }

    @Getter
    @Setter
    public static class Api {
        private Long periodSeconds;
        private String region;
        private String endpoint;
        private Long connTimeout;
    }

    @Getter
    @Setter
    public static class Dns {
        private String domainName;
        private String rr;
    }
}
