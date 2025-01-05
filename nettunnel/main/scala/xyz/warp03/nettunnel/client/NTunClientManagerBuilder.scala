/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.client;

import org.omegazero.net.client.{NetClientManager, NetClientManagerBuilder};
import org.omegazero.net.client.params.ConnectionParameters;

import xyz.warp03.nettunnel.NTunEndpointParameters;

class NTunClientManagerBuilder extends NetClientManagerBuilder {

	private var clientMgr: NetClientManager = null;
	private var params: NTunEndpointParameters = new NTunEndpointParameters();
	private var serverParams: ConnectionParameters = null;

	override def set(option: String, value: Object): NetClientManagerBuilder = {
		if(option == "clientMgr"){
			this.clientMgr = value.asInstanceOf[NetClientManager];
		}else if(option == "serverParams"){
			this.serverParams = value.asInstanceOf[ConnectionParameters];
		}else{
			var v = this.params.setParameter(option, value);
			if(!v)
				super.set(option, value);
		}
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
		this.params.lock();
		return new NTunClient(this.clientMgr, this.workerCreator, this.params, this.serverParams);
	}
}
