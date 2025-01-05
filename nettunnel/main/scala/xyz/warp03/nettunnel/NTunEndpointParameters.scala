/*
 * Copyright (C) 2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel;

class NTunEndpointParameters {

	private var vmaxPacketSize: Int = 16384;
	private var vmaxConcurrentConns: Int = 65535;
	private var vsharedSecret: String = null;
	private var vunreliableTransport: Boolean = false;

	private var locked: Boolean = false;

	def setParameter(option: String, value: Object): Boolean = {
		if(this.locked)
			throw new IllegalStateException("NTunEndpointParameters object is locked");
		if(option == "maxPacketSize"){
			if(this.vmaxPacketSize < 128)
				throw new IllegalArgumentException("maxPacketSize must be at least 128");
			if(this.vmaxPacketSize > 65535)
				throw new IllegalArgumentException("maxPacketSize must be at most 65535");
			this.vmaxPacketSize = value.asInstanceOf[Int];
		}else if(option == "maxConcurrentConns"){
			if(this.vmaxConcurrentConns < 1)
				throw new IllegalArgumentException("maxConcurrentConns must be at least 1");
			this.vmaxConcurrentConns = value.asInstanceOf[Int];
		}else if(option == "sharedSecret"){
			this.vsharedSecret = value.asInstanceOf[String];
		}else if(option == "unreliableTransport"){
			this.vunreliableTransport = value.asInstanceOf[Boolean];
		}else
			return false;
		return true;
	}

	def lock(): Unit = this.locked = true;

	def maxPacketSize: Int = this.vmaxPacketSize;
	def maxConcurrentConns: Int = this.vmaxConcurrentConns;
	def sharedSecret: String = this.vsharedSecret;
	def unreliableTransport: Boolean = this.vunreliableTransport;
}
