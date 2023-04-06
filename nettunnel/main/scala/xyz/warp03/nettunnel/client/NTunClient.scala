/*
 * Copyright (C) 2023 user94729 / warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.client;

import java.net.{InetAddress, InetSocketAddress};
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.net.client.NetClientManager;
import org.omegazero.net.client.params.{ConnectionParameters, TLSConnectionParameters};
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.socket.{SocketConnection, TLSConnection};

import xyz.warp03.nettunnel.common.{NetTunnel, NTunConnection, NTunEndpoint, NTunException, NTunTLSConnection};

object NTunClient {

	private final val logger = Logger.create();
}

class NTunClient(private val clientMgr: NetClientManager, private val workerCreator: java.util.function.Function[SocketConnection, Consumer[Runnable]],
		private val maxPacketSize: Int, private val maxConcurrentConns: Int, private val sharedSecret: String, private val serverParams: ConnectionParameters) extends NetClientManager {

	private val logger = NTunClient.logger;

	private var cep: NTunClientEndpoint = null;

	if(this.sharedSecret == null)
		logger.warn("sharedSecret is null, all connections will be accepted");


	override def init(): Unit = {
		this.clientMgr.init();
	}

	override def close(): Unit = {
		this.clientMgr.close();
	}

	override def start(): Unit = {
		this.clientMgr.start();
	}

	override def connection(params: ConnectionParameters): SocketConnection = {
		if(this.cep == null){
			var bconnection = this.clientMgr.connection(this.serverParams);
			this.cep = new NTunClientEndpoint(bconnection, this.maxPacketSize, this.maxConcurrentConns, this.sharedSecret);
			this.cep.init();
		}
		return this.cep.createTunConnection(params);
	}

	class NTunClientEndpoint(bconnection: SocketConnection, maxPacketSize: Int, maxConcurrentConns: Int, sharedSecret: String)
			extends NTunEndpoint(bconnection, maxPacketSize, maxConcurrentConns, sharedSecret) {

		private var nextConnId = 0;

		override def init(): Unit = {
			super.init();
			bconnection.on("connect", () => {
				logger.debug(this.bconnection.getRemoteAddress(), " Tunnel connection established");
			});
			bconnection.connect(5000);
		}

		override protected def onHandshakeComplete(): Unit = {
			super.onHandshakeComplete();
			this.forEachConnection((conn) => {
				if(!conn.hasConnected() && conn.hasAttachment("__newconndesc")){
					this.writeFrame(NetTunnel.FRAME_TYPE_NEWCONN, conn.getAttachment("__newconndesc").asInstanceOf[Array[Byte]]);
					conn.handleConnect();
				}
			});
		}

		override protected def onClose(): Unit = {
			super.onClose();
			NTunClient.this.cep = null;
		}

		def createTunConnection(params: ConnectionParameters): SocketConnection = {
			var alpName = "~";
			var (conn, id) = this.connections.synchronized {
				var id = this.nextConnId;
				var idsc = 0;
				while(this.connections(id) != null){
					id += 1;
					if(id >= this.connections.length)
						id = 0;
					idsc += 1;
					if(idsc > this.connections.length)
						throw new NTunException("Cannot create new connection (maxConcurrentConns exceeded)");
				}
				if(id + 1 < this.connections.length)
					this.nextConnId = id + 1;
				else
					this.nextConnId = 0;
				var conn = if(this.bconnection.isInstanceOf[TLSConnection] && params.isInstanceOf[TLSConnectionParameters]){
					var alpnNames = params.asInstanceOf[TLSConnectionParameters].getAlpnNames();
					alpName = if alpnNames != null && alpnNames.length > 0 then alpnNames(0) else "";
					new NTunTLSConnection(this, id, NetTunnel.DUMMY_SOADDRESS, this.bconnection.getRemoteAddress(), alpName);
				} else new NTunConnection(this, id, NetTunnel.DUMMY_SOADDRESS, this.bconnection.getRemoteAddress());
				this.connections(id) = conn;
				(conn, id);
			}
			conn.setDefaultErrorListener((err: Throwable) => {
				if(err.isInstanceOf[java.io.IOException])
					NetCommon.logSocketError(logger, "Socket Error", conn, err);
				else
					logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", err);
			});
			if(NTunClient.this.workerCreator != null)
				conn.setWorker(NTunClient.this.workerCreator.apply(conn));
			var port = if params.getRemote().isInstanceOf[InetSocketAddress] then params.getRemote().asInstanceOf[InetSocketAddress].getPort() else 0;
			var newconndesc = NetTunnel.uint24ToBytes(id) ++ Array[Byte](port.toByte, (port >> 8).toByte, 0, alpName.length.toByte) ++ alpName.getBytes();
			if(this.handshakeComplete){
				this.writeFrame(NetTunnel.FRAME_TYPE_NEWCONN, newconndesc);
				conn.handleConnect();
			}else
				conn.setAttachment("__newconndesc", newconndesc);
			return conn;
		}
	}
}
