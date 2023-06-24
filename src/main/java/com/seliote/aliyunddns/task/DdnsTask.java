package com.seliote.aliyunddns.task;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.alidns20150109.AsyncClient;
import com.aliyun.sdk.service.alidns20150109.models.DescribeDomainRecordsRequest;
import com.aliyun.sdk.service.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.sdk.service.alidns20150109.models.UpdateDomainRecordRequest;
import com.google.gson.Gson;
import com.seliote.aliyunddns.conf.AliyunConfig;
import darabonba.core.client.ClientOverrideConfiguration;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * DDNS 任务
 *
 * @author seliote
 * @since 2023-06-24
 */
@Slf4j
@Component
public class DdnsTask {

    public static final String IPV4_ADDR = "A";
    public static final String IPV6_TYPE = "AAAA";

    private final AliyunConfig aliyunConfig;
    private final AsyncClient asyncClient;

    @Autowired
    public DdnsTask(AliyunConfig aliyunConfig) {
        this.aliyunConfig = aliyunConfig;
        this.asyncClient = AsyncClient.builder()
                .region(aliyunConfig.getApi().getRegion())
                .credentialsProvider(StaticCredentialProvider.create(
                        Credential.builder()
                                .accessKeyId(aliyunConfig.getAccessKey().getId())
                                .accessKeySecret(aliyunConfig.getAccessKey().getSecret())
                                .build()))
                .overrideConfiguration(ClientOverrideConfiguration.create()
                        .setEndpointOverride(aliyunConfig.getApi().getEndpoint())
                        .setConnectTimeout(Duration.ofMillis(aliyunConfig.getApi().getConnTimeout())))
                .build();
        this.aliyunConfig.getAccessKey().setId("");
        this.aliyunConfig.getAccessKey().setSecret("");
    }

    @PreDestroy
    public void destroy() {
        asyncClient.close();
    }

    @Scheduled(fixedDelayString = "${aliyun.api.period-seconds}", timeUnit = TimeUnit.SECONDS)
    public void task() throws ExecutionException, InterruptedException, SocketException, UnknownHostException {
        var domainRecord = describeDomainRecord();
        var ip = ipAddr(IPV4_ADDR.equals(domainRecord.getType()));
        if (ip.equals(domainRecord.getValue())) {
            log.debug("IP address {} is same to DNS record, skip config", ip);
            return;
        }
        updateDomainRecord(domainRecord.getRecordId(), domainRecord.getRr(),
                domainRecord.getType(), domainRecord.getTTL(), ip);
    }

    private DescribeDomainRecordsResponseBody.Record describeDomainRecord()
            throws ExecutionException, InterruptedException {
        var req = DescribeDomainRecordsRequest.builder()
                .domainName(aliyunConfig.getDns().getDomainName())
                .keyWord(aliyunConfig.getDns().getRr())
                .build();
        log.debug("Request DescribeDomainRecordsRequest for '{}.{}'", req.getKeyWord(), req.getDomainName());
        var future = asyncClient.describeDomainRecords(req);
        var resp = future.get();
        log.debug(new Gson().toJson(resp));
        var body = resp.getBody();
        if (body != null && body.getTotalCount() != null && body.getTotalCount() != 0
                && body.getDomainRecords() != null && body.getDomainRecords().getRecord() != null) {
            for (var record : body.getDomainRecords().getRecord()) {
                if (aliyunConfig.getDns().getRr().equals(record.getRr())) {
                    if (!IPV6_TYPE.equals(record.getType()) && !IPV4_ADDR.equals(record.getType())) {
                        log.error("DNS record type is {}, only support 'A' or 'AAAA' record", record.getType());
                        throw new IllegalStateException("Error DNS record type");
                    }
                    return record;
                }
            }
        }
        log.error("There is no DNS record for '{}.{}', " +
                        "please check at aliyun DNS console: https://dns.console.aliyun.com",
                aliyunConfig.getDns().getRr(), aliyunConfig.getDns().getDomainName());
        throw new IllegalStateException("No DNS record");
    }

    private String ipAddr(Boolean ipv4) throws SocketException, UnknownHostException {
        var networkInterfaces = NetworkInterface.getNetworkInterfaces();
        var ipList = new ArrayList<String>();
        while (networkInterfaces.hasMoreElements()) {
            var networkInterface = networkInterfaces.nextElement();
            log.debug("Detect network interface {}", networkInterface.getDisplayName());
            var addrs = networkInterface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                var addr = addrs.nextElement();
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    log.debug("Skip address '{}' because it is not a WAN address", addr);
                    continue;
                }
                if ((ipv4 && addr instanceof Inet6Address) || (!ipv4 && addr instanceof Inet4Address)) {
                    log.debug("Skip address '{}' in {} mode", addr, ipv4 ? "IPV4" : "IPV6");
                    continue;
                }
                var ip = InetAddress.getByAddress(addr.getAddress()).getHostAddress();
                ipList.add(ip);
                log.debug("Add '{}' to candidate", addr.getHostAddress());
            }
        }
        if (ipList.size() != 1) {
            log.error("There is no useful or more then one {} address",
                    ipv4 ? "IPV4" : "IPV6");
            throw new IllegalStateException("No useful or more then one IP address");
        }
        return ipList.get(0);
    }

    private void updateDomainRecord(String recordId, String rr, String type, Long ttl, String value)
            throws ExecutionException, InterruptedException {
        log.info("Update RR record '{}' value to '{}'", rr, value);
        var req = UpdateDomainRecordRequest.builder()
                .recordId(recordId)
                .rr(rr)
                .type(type)
                .value(value)
                .TTL(ttl)
                .build();
        var future = asyncClient.updateDomainRecord(req);
        var resp = future.get();
        log.info("Success update record");
        log.debug(new Gson().toJson(resp));
    }
}
