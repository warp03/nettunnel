/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.omegazero.net.socket.{AbstractSocketConnection, SocketConnection, TLSConnection};

class NTunConnection(private val endpoint: NTunEndpoint, val connectionId: Int, private val localAddress: SocketAddress, private val remoteAddress: SocketAddress)
		extends AbstractSocketConnection {

	var connected = true;
	var lastIOTime = System.currentTimeMillis();
	private var remoteWindowSize = new AtomicInteger(NetTunnel.DEFAULT_WINDOW_SIZE);
	private var receiveData = true;
	private var blockedBytes = new AtomicInteger();

	override def connect(x$0: Int): Unit = ()

	override def close(): Unit = this.destroy();
	override def destroy(): Unit = if this.connected then this.endpoint.destroyConnection(this.connectionId) else ();
	override def flush(): Boolean = this.endpoint.bconnection.flush();

	override def getLastIOTime(): Long = this.lastIOTime;
	override def getLocalAddress(): SocketAddress = this.localAddress;
	override def getRemoteAddress(): SocketAddress = this.remoteAddress;
	override def isConnected(): Boolean = super.hasConnected() && this.connected;
	override def isWritable(): Boolean = this.remoteWindowSize.get() > 0;

	override def read(): Array[Byte] = null;
	override def setReadBlock(readBlock: Boolean): Unit = {
		var prevRecv = this.receiveData;
		this.receiveData = !readBlock;
		if(!prevRecv && this.receiveData){
			val lwincr = this.blockedBytes.getAndSet(0);
			if(lwincr > 0)
				this.endpoint.writeDataAck(this.connectionId, lwincr);
		}
	}

	override def write(data: Array[Byte], off: Int, len: Int): Unit = {
		this.lastIOTime = System.currentTimeMillis();
		if(!super.hasConnected()){
			if(data != null && len > 0){
				if(off == 0 && len == data.length)
					super.queueWrite(data);
				else
					super.queueWrite(data.slice(off, off + len));
			}
		}else{
			this.endpoint.writeData(this.connectionId, data.slice(off, off + len));
			this.remoteWindowSize.addAndGet(-len);
		}
	}

	override def writeQueue(data: Array[Byte], off: Int, len: Int): Unit = this.write(data, off, len);

	def recvData(data: Array[Byte]): Unit = {
		this.handleData(data);
		if(this.receiveData)
			this.endpoint.writeDataAck(this.connectionId, data.length);
		else
			this.blockedBytes.addAndGet(data.length);
	}

	def recvDataAck(wincr: Int): Unit = {
		var wasWritable = this.isWritable();
		this.remoteWindowSize.addAndGet(wincr);
		if(this.isWritable() && !wasWritable)
			this.handleWritable();
	}
}

class NTunTLSConnection(endpoint: NTunEndpoint, connectionId: Int, localAddress: SocketAddress, remoteAddress: SocketAddress, private val alpName: String)
		extends NTunConnection(endpoint, connectionId, localAddress, remoteAddress) with TLSConnection {

	if(!endpoint.bconnection.isInstanceOf[TLSConnection])
		throw new IllegalArgumentException("bconnection must be a TLSConnection");
	private val btlsconn = endpoint.bconnection.asInstanceOf[TLSConnection];

	override def isSocketConnected(): Boolean = this.isConnected();
	override def getProtocol(): String = this.btlsconn.getProtocol();
	override def getCipher(): String = this.btlsconn.getCipher();
	override def getApplicationProtocol(): String = this.alpName;
}
