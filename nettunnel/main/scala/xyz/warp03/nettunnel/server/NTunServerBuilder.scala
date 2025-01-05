/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.server;

import org.omegazero.net.server.{NetServer, NetServerBuilder};

import xyz.warp03.nettunnel.NTunEndpointParameters;

class NTunServerBuilder extends NetServerBuilder {

	private var server: NetServer = null;
	private var params: NTunEndpointParameters = new NTunEndpointParameters();

	override def set(option: String, value: Object): NetServerBuilder = {
		if(option == "server"){
			this.server = value.asInstanceOf[NetServer];
		}else{
			var v = this.params.setParameter(option, value);
			if(!v)
				super.set(option, value);
		}
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
		this.params.lock();
		return new NTunServer(this.server, this.workerCreator, this.params, this.ports.iterator().next());
	}
}
