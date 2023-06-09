/*
 * Copyright (C) 2023 user94729 / warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.server;

import org.omegazero.net.server.{NetServer, NetServerBuilder};

class NTunServerBuilder extends NetServerBuilder {

	private var server: NetServer = null;
	private var maxPacketSize: Int = 16384;
	private var maxConcurrentConns: Int = 65535;
	private var sharedSecret: String = null;

	override def set(option: String, value: Object): NetServerBuilder = {
		if(option == "server"){
			this.server = value.asInstanceOf[NetServer];
		}else if(option == "maxPacketSize"){
			if(this.maxPacketSize < 128)
				throw new IllegalArgumentException("maxPacketSize must be at least 128");
			if(this.maxPacketSize > 65535)
				throw new IllegalArgumentException("maxPacketSize must be at most 65535");
			this.maxPacketSize = value.asInstanceOf[Int];
		}else if(option == "maxConcurrentConns"){
			if(this.maxConcurrentConns < 1)
				throw new IllegalArgumentException("maxConcurrentConns must be at least 1");
			this.maxConcurrentConns = value.asInstanceOf[Int];
		}else if(option == "sharedSecret"){
			this.sharedSecret = value.asInstanceOf[String];
		}else
			super.set(option, value);
		return this;
	}

	override def build(): NetServer = {
		if(this.ports == null)
			this.port(1);
		super.prepareBuild();
		if(this.listenPath != null)
			throw new UnsupportedOperationException("listenPath is not supported");
		// without this, "this.transportType" is ambiguous from the perspective of Scala (either references the field or method with the same name)
		// idk if/how this can be done better
		val transportType: org.omegazero.net.common.NetworkApplicationBuilder.TransportType = this.transportType;
		if(transportType != org.omegazero.net.common.NetworkApplicationBuilder.TransportType.STREAM)
			throw new UnsupportedOperationException("Transport type " + transportType);
		if(this.server == null)
			throw new UnsupportedOperationException("'server' must be given");
		/*if(this.encrypted){
			if(this.socket == null){
				val sslContext: javax.net.ssl.SSLContext = this.sslContext;
				if(sslContext == null)
					throw new UnsupportedOperationException("sslContext must be given with encryption enabled");
				this.socket = sslContext.getServerSocketFactory().createServerSocket();
			}else if(!this.socket.isInstanceOf[SSLServerSocket])
				throw new IllegalArgumentException("encryption is enabled but given socket is not a SSLServerSocket");
		}else{
			if(this.socket == null)
				this.socket = new ServerSocket();
		}
		if(this.bindAddresses != null && this.bindAddresses.size() != 1 || this.ports == null || this.ports.size() != 1)
			throw new IllegalArgumentException("Exactly one value each for bindAddresses and ports must be given");
		var laddr = new InetSocketAddress(if this.bindAddresses != null then this.bindAddresses.iterator().next() else null, this.ports.iterator().next());*/
		return new NTunServer(this.server, this.workerCreator, this.maxPacketSize, this.maxConcurrentConns, this.sharedSecret, this.ports.iterator().next());
	}
}
