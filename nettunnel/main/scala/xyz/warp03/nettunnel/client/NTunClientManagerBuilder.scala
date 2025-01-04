/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.client;

import org.omegazero.net.client.{NetClientManager, NetClientManagerBuilder};
import org.omegazero.net.client.params.ConnectionParameters;

class NTunClientManagerBuilder extends NetClientManagerBuilder {

	private var clientMgr: NetClientManager = null;
	private var maxPacketSize: Int = 16384;
	private var maxConcurrentConns: Int = 65535;
	private var sharedSecret: String = null;
	private var serverParams: ConnectionParameters = null;

	override def set(option: String, value: Object): NetClientManagerBuilder = {
		if(option == "clientMgr"){
			this.clientMgr = value.asInstanceOf[NetClientManager];
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
		}else if(option == "serverParams"){
			this.serverParams = value.asInstanceOf[ConnectionParameters];
		}else
			super.set(option, value);
		return this;
	}

	override def build(): NetClientManager = {
		super.prepareBuild();
		val transportType: org.omegazero.net.common.NetworkApplicationBuilder.TransportType = this.transportType;
		if(transportType != org.omegazero.net.common.NetworkApplicationBuilder.TransportType.STREAM)
			throw new UnsupportedOperationException("Transport type " + transportType);
		if(this.clientMgr == null)
			throw new UnsupportedOperationException("'clientMgr' must be given");
		if(this.serverParams == null)
			throw new UnsupportedOperationException("'serverParams' must be given");
		return new NTunClient(this.clientMgr, this.workerCreator, this.maxPacketSize, this.maxConcurrentConns, this.sharedSecret, this.serverParams);
	}
}
