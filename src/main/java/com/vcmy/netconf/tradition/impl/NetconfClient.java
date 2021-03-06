/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.vcmy.netconf.tradition.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.opendaylight.protocol.framework.NeverReconnectStrategy;

/**
 * Synchronous netconf client suitable for testing.
 */
public class NetconfClient implements Closeable {

	public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

	private final String label;
	private final NetconfClientSession clientSession;
	private final NetconfClientSessionListener sessionListener;
	private final long sessionId;

	public NetconfClient(String clientLabel, NetconfClientDispatcher netconfClientDispatcher,
			final NetconfClientConfiguration config) throws InterruptedException {
		this.label = clientLabel;
		sessionListener = config.getSessionListener();
		Future<NetconfClientSession> clientFuture = netconfClientDispatcher.createClient(config);
		clientSession = get(clientFuture);
		this.sessionId = clientSession.getSessionId();
	}

	private static NetconfClientSession get(Future<NetconfClientSession> clientFuture) throws InterruptedException {
		try {
			return clientFuture.get();
		} catch (CancellationException e) {
			throw new RuntimeException("Cancelling " + NetconfClient.class.getSimpleName(), e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("Unable to create " + NetconfClient.class.getSimpleName(), e);
		}
	}

	public Future<NetconfMessage> sendRequest(NetconfMessage message) {
		return ((SimpleNetconfClientSessionListener) sessionListener).sendRequest(message);
	}

	public NetconfMessage sendMessage(NetconfMessage message, int attemptMsDelay)
			throws ExecutionException, InterruptedException, TimeoutException {
		return sendRequest(message).get(attemptMsDelay, TimeUnit.MILLISECONDS);
	}

	public NetconfMessage sendMessage(NetconfMessage message)
			throws ExecutionException, InterruptedException, TimeoutException {
		return sendMessage(message, DEFAULT_CONNECT_TIMEOUT);
	}

	public void close() throws IOException {
		clientSession.close();
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("TestingNetconfClient{");
		sb.append("label=").append(label);
		sb.append(", sessionId=").append(sessionId);
		sb.append('}');
		return sb.toString();
	}

	public long getSessionId() {
		return sessionId;
	}

	public Set<String> getCapabilities() {
		Preconditions.checkState(clientSession != null, "Client was not initialized successfully");
		return Sets.newHashSet(clientSession.getServerCapabilities());
	}

	public static void main(String[] args) throws Exception {
		HashedWheelTimer hashedWheelTimer = new HashedWheelTimer();
		NioEventLoopGroup nettyGroup = new NioEventLoopGroup();
		NetconfClientDispatcherImpl netconfClientDispatcher = new NetconfClientDispatcherImpl(nettyGroup, nettyGroup,
				hashedWheelTimer);
		LoginPassword authHandler = new LoginPassword("vcmy", "123456");
		NetconfClient client = new NetconfClient("client", netconfClientDispatcher,
				getClientConfig("192.168.128.53", 830, true, Optional.of(authHandler)));
		System.out.println(client);
		System.console().writer().println(client.getCapabilities());
	}

	public static NetconfClientConfiguration getClientConfig(String host, int port, boolean ssh,
			Optional<? extends AuthenticationHandler> maybeAuthHandler) throws UnknownHostException {
		InetSocketAddress netconfAddress = new InetSocketAddress(InetAddress.getByName(host), port);
		final NetconfClientConfigurationBuilder b = NetconfClientConfigurationBuilder.create();
		b.withAddress(netconfAddress);
		b.withSessionListener(new SimpleNetconfClientSessionListener());
		b.withReconnectStrategy(new NeverReconnectStrategy(GlobalEventExecutor.INSTANCE,
				NetconfClientConfigurationBuilder.DEFAULT_CONNECTION_TIMEOUT_MILLIS));
		if (ssh) {
			b.withProtocol(NetconfClientProtocol.SSH);
			b.withAuthHandler(maybeAuthHandler.get());
		} else {
			b.withProtocol(NetconfClientProtocol.TCP);
		}
		return b.build();
	}
}
