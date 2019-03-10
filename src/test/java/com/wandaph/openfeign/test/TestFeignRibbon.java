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
package com.wandaph.openfeign.test;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.niws.loadbalancer.DefaultNIWSServerListFilter;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.niws.loadbalancer.NIWSDiscoveryPing;
import com.wandaph.openfeign.eureka.EurekaRegistry;
import com.wandaph.openfeign.support.ResponseEntityDecoder;
import com.wandaph.openfeign.support.SpringDecoder;
import com.wandaph.openfeign.support.SpringEncoder;
import com.wandaph.openfeign.support.SpringMvcContract;
import com.wandaph.openfeign.test.clients.RemoteClient;
import com.wandaph.openfeign.test.dto.CommonResult;
import com.wandaph.openfeign.test.dto.Person;
import feign.Feign;
import feign.ribbon.LBClient;
import feign.ribbon.LBClientFactory;
import feign.ribbon.RibbonClient;

import javax.inject.Provider;

/**
 * @author lvzhen
 * @version Id: TestFeignRibbon.java, v 0.1 2019/3/7 11:34 lvzhen Exp $$
 */
public class TestFeignRibbon {
    public static void main(String[] args) throws Exception {
        String servideId = "wandaph-dclean-zhoucong";
        String serviceUurl = "http://10.53.156.75:8761/eureka/";
        String appName = "spring-mvc-web";
        int appPort = 8081;

        //初始化 EurekaClient 客户端
        final DiscoveryClient client = EurekaRegistry.getDiscoveryClientInstance(serviceUurl, appName, appPort);

        //基本使用默认配置
        final IClientConfig clientConfig = new DefaultClientConfigImpl();
        clientConfig.loadDefaultValues();
        //设置vipAddress，该值对应spring.application.name配置，指定某个应用
        clientConfig.set(CommonClientConfigKey.DeploymentContextBasedVipAddresses, servideId);
        Provider<EurekaClient> eurekaClientProvider = new Provider<EurekaClient>() {
            @Override
            public synchronized EurekaClient get() {
                return client;
            }
        };

        //根据eureka client获取服务列表，client以provide形式提供
        DiscoveryEnabledNIWSServerList discoveryEnabledNIWSServerList =
                new DiscoveryEnabledNIWSServerList(clientConfig, eurekaClientProvider);

        //实例化负载均衡器接口ILoadBalancer，这里使用了ZoneAwareLoadBalancer，这也是spring cloud默认使用的。该实现可以避免了跨区域（Zone）访问的情况。
        //其中的参数分别为，
        // 1）某个具体应用的客户端配置，
        // 2）负载均衡的处理规则IRule对象，负载均衡器实际进行服务实例选择任务是委托给了IRule实例中的choose函数来实现,这里使用了ZoneAvoidanceRule，
        // 3）实例化检查服务实例是否正常服务的IPing接口对象，负载均衡器启动一个用于定时检查Server是否健康的任务。该任务默认的执行间隔为：10秒。这里没有做真实的ping操作，他只是检查DiscoveryEnabledNIWSServerList定时刷新过来的服务列表中的每个服务的状态；
        // 4）如上，ServerList接口有两个方法，分别为获取初始化的服务实例清单和获取更新的服务实例清单；
        // 5）ServerListFilter接口实现，用于对服务实例列表的过滤，根据规则返回过滤后的服务列表；
        // 6）ServerListUpdater服务更新器接口 实现动态获取更新服务列表，默认30秒执行一次
        final ILoadBalancer loadBalancer = new ZoneAwareLoadBalancer(clientConfig,
                new ZoneAvoidanceRule(), new NIWSDiscoveryPing(),
                discoveryEnabledNIWSServerList, new DefaultNIWSServerListFilter());

        RibbonClient ribbonClient = RibbonClient.builder().lbClientFactory(new LBClientFactory() {
            @Override
            public LBClient create(String clientName) {
                /*IClientConfig config = ClientFactory.getNamedConfig(clientName);
                ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
                ZoneAwareLoadBalancer zb = (ZoneAwareLoadBalancer) lb;
                zb.setRule(new RandomRule()); //RoundRobinRule ,RandomRule*/
                return LBClient.create(loadBalancer, clientConfig);
            }
        }).build();

        RemoteClient service = Feign.builder()
                /*  .encoder(new JacksonEncoder())
                  .decoder(new JacksonDecoder())*/
                .encoder(new SpringEncoder())
                .decoder(new ResponseEntityDecoder(new SpringDecoder()))
                .contract(new SpringMvcContract())
                .client(ribbonClient)
                .target(RemoteClient.class, "http://" + servideId);

        //参数信息
        Person param = new Person();
        param.setName("scott");
        //service.updatePerson(param);

        for (int i = 0; i < 5; i++) {
            CommonResult result = service.testDelete();
            System.out.println(result.getCode());
        }
    }


}