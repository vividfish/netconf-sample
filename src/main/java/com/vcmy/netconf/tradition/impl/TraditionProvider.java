/*
 * Copyright © 2017 vcmy and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.vcmy.netconf.tradition.impl;

import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import com.google.common.base.Optional;
import com.vcmy.netconf.tradition.impl.NetconfClient;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;

/**
 * @author vivid
 * @Description netconf控制传统设备，main方法已通过h3c测试， 参考文档：
 *              http://www.h3c.com/cn/d_201711/1045982_30005_0.htm#_Toc498362994
 *
 */
public class TraditionProvider {

	private static final Logger LOG = LoggerFactory.getLogger(TraditionProvider.class);

	private static int message_id = 100;

	/**
	 * message_id生成
	 */
	public int genarate_message_id() {
		if (message_id > 2100000000)
			message_id = 100;
		else
			message_id++;
		return message_id;
	}

	private NetconfClient createClient(String host, int port, String ip, String loginName, String loginPwd) {
		NetconfClient netconfClient = null;
		try {
			HashedWheelTimer hashedWheelTimer = new HashedWheelTimer();
			NioEventLoopGroup nettyGroup = new NioEventLoopGroup();
			NetconfClientDispatcherImpl netconfClientDispatcher = new NetconfClientDispatcherImpl(nettyGroup,
					nettyGroup, hashedWheelTimer);

			LoginPassword authHandler = new LoginPassword(loginName, loginPwd);
			netconfClient = new NetconfClient(host, netconfClientDispatcher,
					NetconfClient.getClientConfig(ip, port, true, Optional.of(authHandler)));

		} catch (Exception e) {
			e.printStackTrace();
		}
		return netconfClient;
	}

	private String genarate_getconfig() {
		StringBuilder builder = new StringBuilder();
		this.message_id++;

		builder.append("<rpc message-id=\"" + message_id + "\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
		builder.append("<get-config>");
		builder.append("<source>");
		builder.append("<running/>");
		builder.append("</source>");
		builder.append("</get-config>");
		builder.append("</rpc>");
		return builder.toString();
	}

	private String genarate_setconfig() {
		StringBuilder builder = new StringBuilder();
		this.message_id++;

		builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		builder.append(
				"<rpc xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"  xmlns:web=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"");
		// message_id
		builder.append(String.valueOf(genarate_message_id()));
		builder.append("\">\n");

		builder.append("<edit-config>");
		builder.append("<target>");
		builder.append("<running/>");
		builder.append("</target>");
		builder.append("<config>");
		// 若是删除操作，将operation内的merge改为delete即可
		builder.append("<top xmlns=\"http://www.h3c.com/netconf/config:1.0\" web:operation=\"merge\">");

		// vxlan configuration 此配置是基于h3c的s12500设备的，step步骤 是基于附件”H3C 交换机 VXLAN
		// 典型配置举例”
		// 如是其他设备，仅供参考
		// step1. 开启 L2VPN
		builder.append("<L2VPN web:operation=\"merge\">");
		builder.append("<Base>");
		builder.append("<Enable>true</Enable>");
		builder.append("</Base>");

		// step3. 创建vsi
		builder.append("<VSIs>");
		builder.append("<VSI>");
		builder.append("<VsiName>evpna</VsiName>");
		builder.append("</VSI>");
		builder.append("</VSIs>");
		builder.append("</L2VPN>");

		// step2. 配置 VXLAN隧道工作在二层转发模式。（仅 S12500-X & S12500X-AF
		// 系列交换机需要执行本配置，S6800 系列交换机不需要配置）
		// step4. 创建tunnel 1和2
		builder.append("<TUNNEL>");
		builder.append("<GlobalConfig>");
		builder.append("<VxlanIpForward>false</VxlanIpForward>");
		builder.append("</GlobalConfig>");
		builder.append("<Tunnels>");
		builder.append("<Tunnel>");
		builder.append("<ID>1</ID>");
		builder.append("<Mode>24</Mode>");
		builder.append("<IPv4Addr>");
		builder.append("<SrcAddr>1.1.1.1</SrcAddr>");
		builder.append("<DstAddr>2.2.2.2</DstAddr>");
		builder.append("</IPv4Addr>");
		builder.append("</Tunnel>");
		builder.append("<Tunnel>");
		builder.append("<ID>2</ID>");
		builder.append("<Mode>24</Mode>");
		builder.append("<IPv4Addr>");
		builder.append("<SrcAddr>1.1.1.1</SrcAddr>");
		builder.append("<DstAddr>3.3.3.3</DstAddr>");
		builder.append("</IPv4Addr>");
		builder.append("</Tunnel>");
		builder.append("</Tunnels>");
		builder.append("</TUNNEL>");

		// step.5 将vxlan 10关联tunnel 1和2
		builder.append("<VXLAN>");
		builder.append("<Tunnels>");
		builder.append("<Tunnel>");
		builder.append("<VxlanID>10</VxlanID>");
		builder.append("<TunnelID>1</TunnelID>");
		builder.append("</Tunnel>");
		builder.append("<Tunnel>");
		builder.append("<VxlanID>10</VxlanID>");
		builder.append("<TunnelID>2</TunnelID>");
		builder.append("</Tunnel>");
		builder.append("</Tunnels>");
		builder.append("<VXLANs>");
		builder.append("<Vxlan>");
		builder.append("<VxlanID>10</VxlanID>");
		builder.append("<VsiName>evpna</VsiName>");
		builder.append("</Vxlan>");
		builder.append("</VXLANs>");
		builder.append("</VXLAN>");

		builder.append("</top>");
		builder.append("</config>");
		builder.append("</edit-config>");
		builder.append("</rpc>");
		return builder.toString();
	}

	private String genarate_saveconfig() {
		StringBuilder builder = new StringBuilder();
		this.message_id++;
		builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		builder.append("<rpc message-id=\"" + message_id + "\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
		builder.append("<save>");
		builder.append("<file>config.cfg</file>");
		builder.append("</save>");
		builder.append("</rpc>");
		return builder.toString();
	}

	private String sendMessage(NetconfClient netconfClient, String rpc) {
		String result = null;
		try {
			Document doc = XmlUtil.readXmlToDocument(rpc);
			NetconfMessage message = netconfClient.sendMessage(new NetconfMessage(doc));
			result = XmlUtil.toString(message.getDocument());
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public static void main(String[] args) {
		TraditionProvider test = new TraditionProvider();
		NetconfClient netconfClient = test.createClient("s12500", 830, "192.168.128.52", "vcmy", "123456");

		// getconfig
		System.out.println("------get device config-------");
		test.sendMessage(netconfClient, test.genarate_getconfig());

		// setconfig
		System.out.println("------set device config-------");
		// test.sendMessage(netconfClient, test.genarate_setconfig());

		// saveconfig
		System.out.println("------save device config-------");
		// test.sendMessage(netconfClient, test.genarate_saveconfig());
	}
}