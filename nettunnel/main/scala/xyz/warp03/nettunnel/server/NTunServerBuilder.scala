/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
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
		return new NTunServer(this.server, this.workerCreator, this.maxPacketSize, this.maxConcurrentConns, this.sharedSecret, this.ports.iterator().next());
	}
}
