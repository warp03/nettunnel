/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.omzproxy;

import java.net.{InetAddress, InetSocketAddress};
import javax.net.ssl.SSLContext;

import org.omegazero.common.config.{ConfigObject, ConfigurationOption};
import org.omegazero.common.eventbus.{EventBusSubscriber, SubscribeEvent};
import org.omegazero.common.plugins.ExtendedPluginConfiguration;
import org.omegazero.net.client.params.{ConnectionParameters, TLSConnectionParameters};
import org.omegazero.net.common.NetworkApplicationBuilder;
import org.omegazero.net.util.TrustManagerUtil;
import org.omegazero.proxy.core.Proxy;
import org.omegazero.proxy.util.FeatureSet;

import xyz.warp03.nettunnel.NetTunnel;

@EventBusSubscriber
class NTunPlugin {

	@ConfigurationOption
	private var enable: Boolean = true;
	@ConfigurationOption
	private var alwaysEnableServer: Boolean = false;
	@ConfigurationOption
	private var alwaysEnableClient: Boolean = false;
	@ConfigurationOption
	private var baseImpl: String = "nio";
	@ConfigurationOption
	private var maxPacketSize: Int = 17000;
	@ConfigurationOption
	private var maxConcurrentConns: Int = 65535;
	@ConfigurationOption
	private var sharedSecret: String = null;
	@ConfigurationOption
	private var encrypted: Boolean = false;
	@ConfigurationOption
	private var serverBindPort: Int = NetTunnel.DEFAULT_PORT;
	private var serverParams: ConnectionParameters = null;


	@ExtendedPluginConfiguration
	def extendedConfig(config: ConfigObject): Unit = {
		var sparamsObj = config.optObject("serverParams");
		if(sparamsObj != null){
			var saddr = new InetSocketAddress(InetAddress.getByName(sparamsObj.getString("address")), sparamsObj.optInt("port", NetTunnel.DEFAULT_PORT));
			var laddrStr = sparamsObj.optString("localAddress", null);
			var laddr = if laddrStr != null then new InetSocketAddress(InetAddress.getByName(laddrStr), 0) else null;
			this.serverParams = if this.encrypted then new TLSConnectionParameters(saddr, laddr) else new ConnectionParameters(saddr, laddr);
		}else if(config.optBoolean("useUpstreamServerParams", false)){
			val uEncrypted = this.encrypted;
			this.serverParams = new TLSConnectionParameters(null) {

				override def getRemote(): InetSocketAddress = {
					var userver = Proxy.getInstance().getDefaultUpstreamServer();
					return new InetSocketAddress(userver.getAddress(), if uEncrypted then userver.getSecurePort() else userver.getPlainPort());
				}

				override def getLocal(): InetSocketAddress = {
					var userver = Proxy.getInstance().getDefaultUpstreamServer();
					return if userver.getLocalAddress() != null then new InetSocketAddress(userver.getLocalAddress(), 0) else null;
				}
			};
		}
	}

	@SubscribeEvent
	def onPreinit(): Unit = {
		NetTunnel.ntunInit();
	}

	@SubscribeEvent
	def proxy_featureInit(featureSet: FeatureSet): Unit = {
		if(!this.enable)
			return;
		var config = Proxy.getInstance().getConfig();
		if(this.alwaysEnableServer || featureSet.containsFeature("ntun.server.plain") || featureSet.containsFeature("ntun.server.tls")){
			var bbuilder = NetworkApplicationBuilder.newServer(this.baseImpl)
					.bindAddresses(config.getBindAddresses())
					.port(this.serverBindPort);
			if(this.encrypted){
				bbuilder.sslContext(Proxy.getInstance().getSslContext());
			}
			var ntunServer = NetworkApplicationBuilder.newServer("ntun")
					.workerCreator(Proxy.getInstance().getSessionWorkerProvider _)
					.set("server", bbuilder.build())
					.set("maxPacketSize", this.maxPacketSize)
					.set("maxConcurrentConns", this.maxConcurrentConns)
					.set("sharedSecret", this.sharedSecret)
					.build();
			Proxy.getInstance().getRegistry().registerServerInstance(ntunServer);
		}
		if(this.alwaysEnableClient || featureSet.containsFeature("ntun.client.plain") || featureSet.containsFeature("ntun.client.tls")){
			if(this.serverParams == null)
				throw new IllegalStateException("ntun.client enabled but serverParams configuration missing");
			var bbuilder = NetworkApplicationBuilder.newClientManager(this.baseImpl);
			if(this.encrypted){
				var clientSslContext = SSLContext.getInstance("TLS");
				clientSslContext.init(null, TrustManagerUtil.getTrustManagersWithAdditionalCertificateFiles(config.getTrustedCertificates()), null);
				bbuilder.sslContext(clientSslContext);
			}
			var ntunClientMgr = NetworkApplicationBuilder.newClientManager("ntun")
					.set("clientMgr", bbuilder.build())
					.set("maxPacketSize", this.maxPacketSize)
					.set("maxConcurrentConns", this.maxConcurrentConns)
					.set("sharedSecret", this.sharedSecret)
					.set("serverParams", this.serverParams)
					.build();
			Proxy.getInstance().getRegistry().registerClientManager("ntun.client.plain", ntunClientMgr);
			Proxy.getInstance().getRegistry().registerClientManager("ntun.client.tls", ntunClientMgr);
		}
	}
}
