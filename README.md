# 阿里云 DDNS

阿里云 DDNS，使用前需先在 [阿里云 DNS 控制台](https://dns.console.aliyun.com) 中先添加一条 DNS 解析。执行过程中会根据已配置的解析规则对 IPV4 或 IPV6 地址解析进行更新。

## 使用

1. 源代码打包或下载 release 中最新 jar 包至指定目录
2. 在步骤 1 的目录中执行 `docker build -t aliyun-ddns:v1 .`
3. 编辑 docker-compose.yml 修改相应环境变量，执行 `docker-compose up -d` 启动容器

## 配置

| 环境变量               | 说明                              | 是否必填 | 默认值                             |
|--------------------|---------------------------------|------|---------------------------------|
| ACCESS_KEY_ID      | 阿里云 access key id               | 是    | /                               |
| ACCESS_KEY_SECRET  | 阿里云 access key secret           | 是    | /                               |
| DOMAIN_NAME        | 域名                              | 是    | /                               |
| DOMAIN_RR          | 域名前缀                            | 是    | /                               |
| LOG_LEVEL          | 日志级别，可选值 `debug` `info` `error` | 否    | `debug`                         |
| API_PERIOD_SECONDS | API 调用频率，即多久更新一次域名解析，单位秒        | 否    | 300                             |
| API_REGION         | 阿里云 API 区域                      | 否    | cn-hangzhou                     |
| API_ENDPOINT       | 阿里云 endpoint                    | 否    | alidns.cn-hangzhou.aliyuncs.com |
