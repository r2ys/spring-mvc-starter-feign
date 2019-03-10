/**
 * Software License Declaration.
 * <p>
 * wandaph.com, Co,. Ltd.
 * Copyright ? 2017 All Rights Reserved.
 * <p>
 * Copyright Notice
 * This documents is provided to wandaph contracting agent or authorized programmer only.
 * This source code is written and edited by wandaph Co,.Ltd Inc specially for financial
 * business contracting agent or authorized cooperative company, in order to help them to
 * install, programme or central control in certain project by themselves independently.
 * <p>
 * Disclaimer
 * If this source code is needed by the one neither contracting agent nor authorized programmer
 * during the use of the code, should contact to wandaph Co,. Ltd Inc, and get the confirmation
 * and agreement of three departments managers  - Research Department, Marketing Department and
 * Production Department.Otherwise wandaph will charge the fee according to the programme itself.
 * <p>
 * Any one,including contracting agent and authorized programmer,cannot share this code to
 * the third party without the agreement of wandaph. If Any problem cannot be solved in the
 * procedure of programming should be feedback to wandaph Co,. Ltd Inc in time, Thank you!
 */
package com.wandaph.openfeign.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.wandaph.openfeign.util.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Date;
import java.util.Properties;

import static com.netflix.client.config.CommonClientConfigKey.AppName;

/**
 * 注册Eureka
 *
 * @author lvzhen
 * @version Id: EurekaRegistry.java, v 0.1 2019/3/8 14:42 lvzhen Exp $$
 */
public class EurekaRegistry {

    private static Logger logger = LoggerFactory.getLogger(EurekaRegistry.class);

    private static DiscoveryClient discoveryClient;

    private static InstanceInfo instanceInfo;

    private EurekaRegistry(String ServiceUrl, String AppName, int port) {
        initEurekaClient(ServiceUrl, AppName, port);
    }

    public synchronized static DiscoveryClient getDiscoveryClientInstance(String ServiceUrl, String AppName, int port) {
        if(discoveryClient ==null){
            return new EurekaRegistry(ServiceUrl, AppName, port).getDiscoveryClient();
        }
        return discoveryClient;
    }

    private DiscoveryClient getDiscoveryClient(){
        return discoveryClient;
    }

    /**
     * 初始化 EurekaClient 客户端
     *
     * @param ServiceUrl Eureka服务地址: http://localhost:8761/eureka/
     * @param AppName    应用名
     * @param port       端口号
     * @return
     */
    public DiscoveryClient initEurekaClient(String ServiceUrl, String AppName, int port) {
        logger.info("======================初始化EurekaClient配置信息Start===============================");
        //初始化默认配置
        Properties properties = new Properties();
        properties.setProperty("eureka.serviceUrl.default", ServiceUrl);
        properties.setProperty("eureka.port", String.valueOf(port));
        properties.setProperty("eureka.name", AppName);
        properties.setProperty("eureka.us-east-1.availabilityZones", "default");
        properties.setProperty("eureka.preferSameZone", "false");
        properties.setProperty("eureka.shouldUseDns", "false");
        ConfigurationManager.loadProperties(properties);

        //EurekaInstanceConfig 应用实例配置接口,应用名、应用的端口
        EurekaInstanceConfig instanceConfig = new DataCenterInstanceConfigExt();

        //InstanceInfo 应用实例信息 Eureka-Client 向 Eureka-Server 注册该对象信息
        instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();

        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(instanceConfig, instanceInfo);

        //EurekaClientConfig 配置  Eureka-Server 的连接地址、获取服务提供者列表的频率、注册自身为服务提供者的频率等等。
        EurekaClientConfig eurekaClientConfig = new DefaultEurekaClientConfig();

        discoveryClient = new DiscoveryClient(applicationInfoManager, eurekaClientConfig);

        logger.info("Registering application " + instanceConfig.getAppname() + " to eureka with STARTING status");
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.STARTING);

        logger.info("Simulating service initialization by sleeping for 2 seconds...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Nothing
        }
        // Now we change our status to UP
        logger.info("Registering application " + instanceConfig.getAppname() + " with eureka with status UP");

        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        logger.info("======================初始化EurekaClient配置信息End===============================");
        return discoveryClient;
    }


    public void stop() {
        if (discoveryClient != null) {
            logger.info("Shutting down server. Demo over.");
            discoveryClient.shutdown();
        }
    }

    /**
     * Application Service 的 Eureka Server 初始化以及注册是异步的，需要一段时间 此处等待初始化及注册成功
     *
     * @param eurekaClient
     */
    private void waitForRegistrationWithEureka(final EurekaClient eurekaClient) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // my vip address to listen on
                /// String vipAddress = configInstance.getStringProperty("eureka.vipAddress", "sampleservice.mydomain.net").get();
                String vipAddress = instanceInfo.getInstanceId();
                InstanceInfo nextServerInfo = null;
                while (nextServerInfo == null) {
                    try {
                        nextServerInfo = eurekaClient.getNextServerFromEureka(vipAddress, false);
                    } catch (Throwable e) {
                        logger.info("Waiting ... verifying service registration with eureka ...");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                logger.info("service registration with eureka  success...");
            }
        }).start();

    }

    private void processRequest(final Socket s) {
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = rd.readLine();
            if (line != null) {
                logger.info("Received a request from the example client: " + line);
            }
            String response = "BAR " + new Date();
            logger.info("Sending the response to the client: " + response);

            PrintStream out = new PrintStream(s.getOutputStream());
            out.println(response);

        } catch (Throwable e) {
            System.err.println("Error processing requests");
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class DataCenterInstanceConfigExt extends MyDataCenterInstanceConfig {
        /**
         * 让注册到服务的名称是机器的ip ，非主机名
         **/
        @Override
        public String getHostName(boolean refresh) {
            try {
                return InetUtils.getLocalIpAddr();
            } catch (Exception e) {
                return super.getHostName(refresh);
            }
        }

        /**
         * 虚拟网卡,获取不到本地IP问题
         **/
        @Override
        public String getIpAddress() {
            try {
                return InetUtils.getLocalIpAddr();
            } catch (Exception e) {
                return super.getIpAddress();
            }
        }

        /**
         * Eureka 页面展示Status 方式 host: appname : port
         **/
        @Override
        public String getInstanceId() {
            try {
                return getIpAddress() + ":" + getAppname() + ":" + getNonSecurePort();
            } catch (Exception e) {
                return super.getInstanceId();
            }
        }
    }

}