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
package com.wandaph.openfeign.util;

/**
 * @author lvzhen
 * @version Id: InetUtils.java, v 0.1 2019/2/27 16:24 lvzhen Exp $$
 */

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Pattern;

public class InetUtils {

    private static final Logger log = LoggerFactory.getLogger(InetUtils.class);

    private static volatile String localIp;
    private static Pattern ipPattern = Pattern.compile("(\\d{1,3}\\.)+\\d{1,3}");
    private static final String[] lanIpPrefixs = new String[]{"127.", "192.168", "10.", "100."};

    public static String getIpAddressAndPort() throws MalformedObjectNameException, NullPointerException,
            UnknownHostException {
        MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
        String host = InetAddress.getLocalHost().getHostAddress();
        String port = objectNames.iterator().next().getKeyProperty("port");
        String ipadd = "http" + "://" + host + ":" + port;
        System.out.println(ipadd);
        return ipadd;
    }

    public static int getTomcatOrJettyPort() throws MalformedObjectNameException, NullPointerException {
        int tomcatPort = 0;
        MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
        tomcatPort = Integer.valueOf(objectNames.iterator().next().getKeyProperty("port"));
        if (!org.springframework.util.StringUtils.isEmpty(System.getProperty("jetty.port"))) {
            tomcatPort = Integer.parseInt(System.getProperty("jetty.port"));
        }
        return tomcatPort;
    }


    public static String getLocalIpAddr() {
        if (localIp != null) {
            return localIp;
        } else {
            Enumeration en;
            try {
                en = NetworkInterface.getNetworkInterfaces();
                String currentIp = null;
                label45:
                while (en.hasMoreElements()) {
                    NetworkInterface i = (NetworkInterface) en.nextElement();
                    Enumeration en2 = i.getInetAddresses();
                    while (en2.hasMoreElements()) {
                        InetAddress addr = (InetAddress) en2.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            currentIp = addr.getHostAddress();
                            if (isInnerIp(currentIp)) {
                                localIp = currentIp;
                                break label45;
                            }
                        }
                    }
                }
                if (localIp == null) {
                    localIp = currentIp;
                }
            } catch (Exception var6) {
                ;
            }
            if (localIp == null) {
                en = null;
                try {
                    InetAddress inet = InetAddress.getLocalHost();
                    localIp = inet.getHostAddress();
                } catch (UnknownHostException var5) {
                    ;
                }
            }
            return localIp;
        }
    }

    public static boolean isInnerIp(String ipAddr) {
        if (!StringUtils.isBlank(ipAddr) && isIp(ipAddr)) {
            String[] var1 = lanIpPrefixs;
            int var2 = var1.length;

            for (int var3 = 0; var3 < var2; ++var3) {
                String prefix = var1[var3];
                if (ipAddr.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public static boolean isIp(String ipAddr) {
        return StringUtils.isBlank(ipAddr) ? false : ipPattern.matcher(ipAddr).matches();
    }

    public static void main(String[] args) {
        System.out.println(InetUtils.getLocalIpAddr());
    }
}
