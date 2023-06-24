package com.seliote.aliyunddns.task;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.alidns20150109.AsyncClient;
import com.aliyun.sdk.service.alidns20150109.models.DescribeDomainRecordsRequest;
import com.aliyun.sdk.service.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.sdk.service.alidns20150109.models.UpdateDomainRecordRequest;
import com.google.gson.Gson;
import com.seliote.aliyunddns.conf.AliyunConfig;
import com.seliote.aliyunddns.conf.NetworkConfig;
import darabonba.core.client.ClientOverrideConfiguration;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public static final String IPV4_TYPE = "A";
    public static final String IPV6_TYPE = "AAAA";

    private final AliyunConfig aliyunConfig;
    private final NetworkConfig networkConfig;
    private final AsyncClient asyncClient;

    @Autowired
    public DdnsTask(AliyunConfig aliyunConfig, NetworkConfig networkConfig) {
        this.aliyunConfig = aliyunConfig;
        this.networkConfig = networkConfig;
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
        var domainRecords = describeDomainRecord();
        for (DescribeDomainRecordsResponseBody.Record domainRecord : domainRecords) {
            boolean isIpv4 = IPV4_TYPE.equals(domainRecord.getType());
            var ip = ipAddr(isIpv4);
            if (ip.isEmpty()) {
                log.warn("Failed get '{}' address for interface {}, skip override configuration",
                        isIpv4 ? IPV4_TYPE : IPV6_TYPE, networkConfig.getInterfaceName());
                continue;
            }
            if (ip.get().equals(domainRecord.getValue())) {
                log.debug("IP address '{}' is same to Aliyun DNS record, skip override configuration", ip);
                continue;
            }
            updateDomainRecord(domainRecord.getRecordId(), domainRecord.getRr(),
                    domainRecord.getType(), domainRecord.getTTL(), ip.get());
        }
    }

    private List<DescribeDomainRecordsResponseBody.Record> describeDomainRecord()
            throws ExecutionException, InterruptedException {
        List<DescribeDomainRecordsResponseBody.Record> records = new ArrayList<>();
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
                if (aliyunConfig.getDns().getRr().equals(record.getRr())
                        && (IPV4_TYPE.equals(record.getType()) || IPV6_TYPE.equals(record.getType()))) {
                    log.debug("Found DNS record '{}.{}', IP is {}",
                            record.getRr(), record.getDomainName(), record.getValue());
                    records.add(record);
                }
            }
        }
        if (records.size() == 0) {
            log.error("There is no DNS record for '{}.{}' with type '{}' or '{}', " +
                            "please check at aliyun DNS console: https://dns.console.aliyun.com",
                    IPV4_TYPE, IPV6_TYPE, aliyunConfig.getDns().getRr(), aliyunConfig.getDns().getDomainName());
            throw new IllegalStateException("No DNS record matched");
        }
        return records;
    }

    private Optional<String> ipAddr(Boolean ipv4) throws SocketException, UnknownHostException {
        log.debug("Get IP address by interface name {}", networkConfig.getInterfaceName());
        var networkInterface = NetworkInterface.getByName(networkConfig.getInterfaceName());
        var ipList = new ArrayList<String>();
        var addrs = networkInterface.getInetAddresses();
        while (addrs.hasMoreElements()) {
            var addr = addrs.nextElement();
            if ((ipv4 && addr instanceof Inet6Address) || (!ipv4 && addr instanceof Inet4Address)) {
                log.debug("Skip address '{}' in {} mode", addr, ipv4 ? "IPV4" : "IPV6");
                continue;
            }
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                log.debug("Skip address '{}' because it is not a WAN address", addr);
                continue;
            }
            var ip = InetAddress.getByAddress(addr.getAddress()).getHostAddress();
            ipList.add(ip);
            log.debug("Add '{}' to candidate", addr.getHostAddress());

        }
        if (ipList.size() != 1) {
            log.warn("There is no useful or more then one {} address",
                    ipv4 ? "IPV4" : "IPV6");
            return Optional.empty();
        }
        return Optional.of(ipList.get(0));
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
