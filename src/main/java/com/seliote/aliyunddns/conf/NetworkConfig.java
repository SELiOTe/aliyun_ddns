package com.seliote.aliyunddns.conf;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网络相关配置
 *
 * @author seliote
 * @since 2023-06-24
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "network")
public class NetworkConfig {

    private String interfaceName;
}
