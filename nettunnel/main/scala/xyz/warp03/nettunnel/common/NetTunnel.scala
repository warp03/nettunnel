/*
 * Copyright (C) 2023 user94729 / warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.common;

import java.net.{InetAddress, InetSocketAddress};

import org.omegazero.net.common.NetworkApplicationBuilder;

object NetTunnel {

	final val VERSION = "$BUILDVERSION";

	final val DEFAULT_PORT = 1184;

	final val FRAME_HEADER_SIZE = 3;
	final val FRAME_TYPE_HANDSHAKE = 0;
	final val FRAME_TYPE_NEWCONN = 1;
	final val FRAME_TYPE_DATA = 2;
	final val FRAME_TYPE_DATA_ACK = 3;
	final val FRAME_TYPE_CLOSE = 4;
	final val FRAME_TYPE_HEARTBEAT = 5;

	final val DUMMY_ADDRESS = InetAddress.getByAddress(Array[Byte](127, 0, 0, 1));
	final val DUMMY_SOADDRESS = new InetSocketAddress(DUMMY_ADDRESS, 0);

	final val DEFAULT_WINDOW_SIZE = 65536;


	def ntunInit(): Unit = {
		NetworkApplicationBuilder.addImplementationAlias("ntun_server", "xyz.warp03.nettunnel.server.NTunServerBuilder");
		NetworkApplicationBuilder.addImplementationAlias("ntun_client", "xyz.warp03.nettunnel.client.NTunClientManagerBuilder");
	}

	def bytesToUint24(data: Array[Byte], off: Int): Int = data(off) & 0xff | (data(off + 1) & 0xff) << 8 | (data(off + 2) & 0xff) << 16;
	def uint24ToBytes(value: Int): Array[Byte] = Array[Byte](value.toByte, (value >> 8).toByte, (value >> 16).toByte);
}
