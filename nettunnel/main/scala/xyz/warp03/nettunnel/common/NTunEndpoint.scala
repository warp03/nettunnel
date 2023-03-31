/*
 * Copyright (C) 2023 user94729 / warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel.common;

import java.net.{InetSocketAddress, Socket};
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.omegazero.common.event.Tasks;
import org.omegazero.common.logging.Logger;
import org.omegazero.net.server.NetServer;
import org.omegazero.net.socket.SocketConnection;

import xyz.warp03.nettunnel.common.NTunConnection;

import xyz.warp03.nettunnel.common.NetTunnel.*;

object NTunEndpoint {

	private final val logger = Logger.create();
}

abstract class NTunEndpoint(val bconnection: SocketConnection, maxPacketSize: Int, maxConcurrentConns: Int, private val sharedSecret: String) {

	private val logger = NTunEndpoint.logger;

	protected val frameBuffer: Array[Byte] = Array.fill(maxPacketSize){0};
	protected var frameBufferIndex = 0;

	private var tunnelConnErr: Option[Throwable] = None;
	private val handshakeNonce: Array[Byte] = Array.fill(64)((scala.util.Random.nextInt(256) - 128).toByte);
	private var hsLocalAuthed = false;
	private var hsPeerAuthed = false;
	protected var handshakeComplete = false;

	protected val connections: Array[NTunConnection] = Array.fill(maxConcurrentConns){null};

	protected var lastHeartbeat: Long = System.nanoTime();
	protected var hbCheckInterval: Object = null;

	logger.debug(this.bconnection.getRemoteAddress(), " New tunnel connection");

	def init(): Unit = {
		this.bconnection.on("data", (data: Array[Byte]) => this.onData(data));
		this.bconnection.on("error", (err: Throwable) => {
			logger.debug(this.bconnection.getRemoteAddress(), " Tunnel connection error: ", err);
			var perr = err;
			if(perr.isInstanceOf[org.omegazero.common.event.task.ExecutionFailedException] && perr.getCause().isInstanceOf[NTunException])
				perr = perr.getCause();
			this.tunnelConnErr = Some(perr);
		});
		this.bconnection.on("writable", this.onWritable _);
		this.bconnection.on("close", this.onClose _);

		this.writeFrame(FRAME_TYPE_HANDSHAKE, Array[Byte](1) ++ this.handshakeNonce);

		this.hbCheckInterval = Tasks.I.interval(() => {
			this.writeFrame(FRAME_TYPE_HEARTBEAT, Array());
			if(System.nanoTime() - this.lastHeartbeat > 18000000000L){
				logger.debug(this.bconnection.getRemoteAddress(), " Heartbeat timeout");
				this.bconnection.destroy();
			}
		}, 5000);
	}

	protected def onData(data: Array[Byte]): Unit = {
		var di = 0;
		while(di < data.length){
			var frameSize = 0;
			if(this.frameBufferIndex == 0 && data.length - di > FRAME_HEADER_SIZE && {frameSize = data(di) & 0xff | (data(di + 1) & 0xff) << 8; frameSize} <= data.length - di){
				this.processFrame(if di > 0 || frameSize != data.length then data.slice(di, di + frameSize) else data);
				di += frameSize;
			}else{
				var clen = Math.min(this.frameBuffer.length - this.frameBufferIndex, data.length - di);
				if(clen == 0)
					throw new NTunException("Frame too large");
				System.arraycopy(data, di, this.frameBuffer, this.frameBufferIndex, clen);
				di += clen;
				this.frameBufferIndex += clen;
				while(this.frameBufferIndex > FRAME_HEADER_SIZE && {frameSize = this.frameBuffer(0) & 0xff | (this.frameBuffer(1) & 0xff) << 8; frameSize} <= this.frameBufferIndex){
					this.processFrame(this.frameBuffer.take(frameSize));
					if(this.frameBufferIndex > frameSize)
						System.arraycopy(this.frameBuffer, frameSize, this.frameBuffer, 0, this.frameBufferIndex - frameSize);
					this.frameBufferIndex -= frameSize;
				}
			}
		}
	}

	protected def onHandshakeComplete(): Unit = {
		if(this.handshakeComplete)
			throw new IllegalStateException("Duplicate handshake completion");
		logger.debug(this.bconnection.getRemoteAddress(), " Handshake completed");
		this.handshakeComplete = true;
	}

	protected def onWritable(): Unit = {
		this.forEachConnection((conn) => {
			conn.handleWritable();
		});
	}

	protected def onClose(): Unit = {
		Tasks.I.clear(this.hbCheckInterval);
		logger.debug(this.bconnection.getRemoteAddress(), " Tunnel connection closed");
		var err = if this.tunnelConnErr.isDefined then new NTunException("Tunnel connection closed: " + this.tunnelConnErr.get, this.tunnelConnErr.get)
				else new NTunException("Tunnel connection closed");
		this.forEachConnection((conn) => {
			conn.handleError(err);
			conn.handleClose();
		});
	}

	def forEachConnection(callback: NTunConnection => Unit): Unit = {
		for(conn <- this.connections){
			if(conn != null)
				callback(conn);
		}
	}

	def getConnection(id: Int): NTunConnection = {
		if(id < 0 || id >= this.connections.length)
			return null;
		return this.connections(id);
	}

	def writeData(id: Int, data: Array[Byte]): Unit = {
		var maxPayload = this.maxPacketSize - FRAME_HEADER_SIZE - 3;
		if(data.length <= maxPayload){
			this.writeFrame(FRAME_TYPE_DATA, idToBytes(id) ++ data);
		}else{
			var di = 0;
			while(di < data.length){
				var nextP = Math.min(data.length - di, maxPayload);
				this.writeFrame(FRAME_TYPE_DATA, idToBytes(id) ++ data.slice(di, di + nextP));
				di += nextP;
			}
		}
	}

	def destroyConnection(id: Int): Unit = {
		this.destroyConnection0(id, None, true);
	}

	protected def destroyConnection0(id: Int, err: Option[Throwable], sendClose: Boolean): Unit = {
		var conn = this.getConnection(id);
		if(conn == null)
			return;
		this.connections(id) = null;
		if(err.isDefined)
			conn.handleError(err.get);
		conn.connected = false;
		conn.handleClose();
		if(sendClose && !this.bconnection.hasDisconnected())
			this.writeFrame(FRAME_TYPE_CLOSE, idToBytes(id));
	}

	protected def writeFrame(ftype: Byte, data: Array[Byte]): Unit = {
		if(logger.debug())
			logger.trace("local -> ", this.bconnection.getRemoteAddress(), " NTun Frame: type=", ftype, " data.length=", data.length);
		var size = FRAME_HEADER_SIZE + data.length;
		var hdr = new Array[Byte](FRAME_HEADER_SIZE);
		hdr(0) = size.toByte;
		hdr(1) = (size >>> 8).toByte;
		hdr(2) = ftype.toByte;
		this.bconnection.writeQueue(hdr);
		this.bconnection.write(data);
	}

	protected def processFrame(data: Array[Byte]): Unit = {
		if(logger.debug())
			logger.trace(this.bconnection.getRemoteAddress(), " -> local NTun Frame: type=", data(2), " data.length=", data.length);
		data(2) match {
			case FRAME_TYPE_HANDSHAKE => {
				if(data.length != FRAME_HEADER_SIZE + 65)
					return;
				var stage = data(FRAME_HEADER_SIZE);
				if(stage == 1){
					if(this.sharedSecret != null){
						val mac = Mac.getInstance("HmacSHA512");
						mac.init(new SecretKeySpec(this.sharedSecret.getBytes(), "HmacSHA512"));
						this.writeFrame(FRAME_TYPE_HANDSHAKE, Array[Byte](2) ++ mac.doFinal(data.drop(FRAME_HEADER_SIZE + 1)));
					}else
						this.writeFrame(FRAME_TYPE_HANDSHAKE, Array[Byte](2) ++ Array.fill(64){0.toByte});
				}else if(stage == 2){
					var expect = {
						if(this.sharedSecret != null){
							val mac = Mac.getInstance("HmacSHA512");
							mac.init(new SecretKeySpec(this.sharedSecret.getBytes(), "HmacSHA512"));
							mac.doFinal(this.handshakeNonce);
						}else
							Array.fill(64){0.toByte};
					};
					if(data.drop(FRAME_HEADER_SIZE + 1).sameElements(expect)){
						logger.debug(this.bconnection.getRemoteAddress(), " Received valid HANDSHAKE packet");
						this.hsPeerAuthed = true;
						this.writeFrame(FRAME_TYPE_HANDSHAKE, Array[Byte](3) ++ Array.fill(64){0.toByte});
						if(this.hsLocalAuthed && this.hsPeerAuthed)
							this.onHandshakeComplete();
					}else
						throw new NTunException("Handshake failure (invalid HMAC)");
				}else if(stage == 3){
					this.hsLocalAuthed = true;
					if(this.hsLocalAuthed && this.hsPeerAuthed)
						this.onHandshakeComplete();
				}else
					throw new NTunException("Invalid HANDSHAKE packet (invalid stage " + stage + ")");
			}
			case FRAME_TYPE_NEWCONN => {
				if(!this.handshakeComplete || data.length < FRAME_HEADER_SIZE + 3)
					return;
				var id = bytesToId(data, FRAME_HEADER_SIZE);
				this.newTunConn(id, data.drop(FRAME_HEADER_SIZE + 3));
			}
			case FRAME_TYPE_DATA => {
				if(!this.handshakeComplete || data.length < FRAME_HEADER_SIZE + 4) // 3 bytes header + >0 data bytes
					return;
				var id = bytesToId(data, FRAME_HEADER_SIZE);
				var conn = this.getConnection(id);
				if(conn == null)
					return;
				conn.lastIOTime = System.currentTimeMillis();
				conn.handleData(data.drop(FRAME_HEADER_SIZE + 3));
			}
			case FRAME_TYPE_CLOSE => {
				if(!this.handshakeComplete || data.length < FRAME_HEADER_SIZE + 3)
					return;
				var id = bytesToId(data, FRAME_HEADER_SIZE);
				var err: Option[Throwable] = None;
				if(data.length > FRAME_HEADER_SIZE + 3)
					err = Some(new NTunException(new String(data.drop(FRAME_HEADER_SIZE + 3))));
				this.destroyConnection0(id, err, false);
			}
			case FRAME_TYPE_HEARTBEAT => {
				this.lastHeartbeat = System.nanoTime();
			}
			case _ => throw new NTunException("Invalid frame type: " + data(2));
		}
	}

	protected def newTunConn(id: Int, additional: Array[Byte]): Unit = ();
}
