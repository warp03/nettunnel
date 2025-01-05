/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.server;

import java.net.{InetAddress, InetSocketAddress};
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.server.NetServer;
import org.omegazero.net.socket.{SocketConnection, TLSConnection};

import xyz.warp03.nettunnel.{NetTunnel, NTunConnection, NTunEndpoint, NTunEndpointParameters, NTunException, NTunTLSConnection};

object NTunServer {

	private final val logger = Logger.create();
}

class NTunServer(private val server: NetServer, private val workerCreator: java.util.function.Function[SocketConnection, Consumer[Runnable]],
		private val params: NTunEndpointParameters, private val vport: Int) extends NetServer {

	private val logger = NTunServer.logger;

	private var connectionCallback: Consumer[SocketConnection] = null;

	if(this.params.sharedSecret == null)
		logger.warn("sharedSecret is null, all connections will be accepted");


	override def init(): Unit = {
		this.server.setConnectionCallback(this.newConn(_));
		this.server.init();
	}

	override def close(): Unit = {
		this.server.close();
	}

	override def start(): Unit = {
		this.server.start();
	}

	override def setConnectionCallback(callback: Consumer[SocketConnection]): Unit = {
		this.connectionCallback = callback;
	}

	private def newConn(connection: SocketConnection): Unit = {
		var ep = new NTunServerEndpoint(connection, this.params);
		ep.init();
	}

	class NTunServerEndpoint(bconnection: SocketConnection, params: NTunEndpointParameters) extends NTunEndpoint(bconnection, params) {

		override protected def newTunConn(id: Int, additional: Array[Byte]): Unit = {
			if(this.connections(id) != null)
				throw new IllegalStateException("newConn was called with existing connection ID " + id);
			var addI = 0;
			var targetPort = additional(addI) & 0xff | (additional(addI + 1) & 0xff) << 8;
			addI += 2;
			if(NTunServer.this.vport > 1 && targetPort != NTunServer.this.vport){
				this.writeFrame(NetTunnel.FRAME_TYPE_CLOSE, NetTunnel.uint24ToBytes(id) ++ "Invalid Port".getBytes());
				return;
			}
			var remoteAddress = {
				if(additional(addI) == 0){
					addI += 1;
					this.bconnection.getRemoteAddress();
				}else if(additional(addI) == 1){
					var addrlen = additional(addI + 3);
					var addr = InetAddress.getByAddress(additional.slice(addI + 4, addI + 4 + addrlen));
					var port = additional(addI + 1) & 0xff | (additional(addI + 2) & 0xff) << 8;
					addI += 4 + addrlen;
					new InetSocketAddress(addr, port);
				}else
					throw new NTunException("Unknown SocketAddress type: " + additional(addI));
			};
			var alpName = {
				var alpName = new String(additional.slice(addI + 1, addI + 1 + additional(addI)));
				addI += 1 + additional(addI);
				alpName;
			};
			var localAddress = new InetSocketAddress(NetTunnel.DUMMY_ADDRESS, targetPort);
			var conn = if(this.bconnection.isInstanceOf[TLSConnection] && alpName != "~") then new NTunTLSConnection(this, id, localAddress, remoteAddress, alpName)
					else new NTunConnection(this, id, localAddress, remoteAddress);
			conn.setDefaultErrorListener((err: Throwable) => {
				if(err.isInstanceOf[java.io.IOException])
					NetCommon.logSocketError(logger, "Socket Error", conn, err);
				else
					logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", err);
			});
			if(NTunServer.this.workerCreator != null)
				conn.setWorker(NTunServer.this.workerCreator.apply(conn));
			this.connections(id) = conn;
			logger.debug(this.bconnection.getRemoteAddress(), " Received NEWCONN, created new connection ", id);
			conn.on("connect", () => {
				NTunServer.this.connectionCallback.accept(conn);
			});
			conn.handleConnect();
		}
	}
}
