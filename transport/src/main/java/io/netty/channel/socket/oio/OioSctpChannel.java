/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.oio;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSctpChannelConfig;
import io.netty.channel.socket.SctpChannelConfig;
import io.netty.channel.socket.SctpMessage;
import io.netty.channel.socket.SctpNotificationHandler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link io.netty.channel.socket.SctpChannel} implementation which use blocking mode and allows to read / write
 * {@link SctpMessage}s to the underlying {@link SctpChannel}.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class OioSctpChannel extends AbstractOioMessageChannel
        implements io.netty.channel.socket.SctpChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);

    private final SctpChannel ch;
    private final SctpChannelConfig config;

    private final Selector readSelector;
    private final Selector writeSelector;
    private final Selector connectSelector;

    private final NotificationHandler<?> notificationHandler;

    private static SctpChannel openChannel() {
        try {
            return SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    /**
     * Create a new instance with an new {@link SctpChannel}.
     */
    public OioSctpChannel() {
        this(openChannel());
    }

    /**
     * Create a new instance from the given {@link SctpChannel}.
     *
     * @param ch    the {@link SctpChannel} which is used by this instance
     */
    public OioSctpChannel(SctpChannel ch) {
        this(null, null, ch);
    }

    /**
     * Create a new instance from the given {@link SctpChannel}.
     *
     * @param parent    the parent {@link Channel} which was used to create this instance. This can be null if the
     *                  {@link} has no parent as it was created by your self.
     * @param id        the id which should be used for this instance or {@code null} if a new one should be generated
     * @param ch        the {@link SctpChannel} which is used by this instance
     */
    public OioSctpChannel(Channel parent, Integer id, SctpChannel ch) {
        super(parent, id);
        this.ch = ch;
        boolean success = false;
        try {
            ch.configureBlocking(false);
            readSelector = Selector.open();
            writeSelector = Selector.open();
            connectSelector = Selector.open();

            ch.register(readSelector, SelectionKey.OP_READ);
            ch.register(writeSelector, SelectionKey.OP_WRITE);
            ch.register(connectSelector, SelectionKey.OP_CONNECT);

            config = new DefaultSctpChannelConfig(ch);
            notificationHandler = new SctpNotificationHandler(this);
            success = true;
        } catch (Exception e) {
            throw new ChannelException("failed to initialize a sctp channel", e);
        } finally {
            if (!success) {
                try {
                    ch.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a sctp channel.", e);
                }
            }
        }
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SctpChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        if (readSuspended || !readSelector.isOpen()) {
            return 0;
        }

        int readMessages = 0;

        final int selectedKeys = readSelector.select(SO_TIMEOUT);
        final boolean keysSelected = selectedKeys > 0;

        if (!keysSelected) {
            return readMessages;
        }

        Set<SelectionKey> reableKeys = readSelector.selectedKeys();
        try {
            for (SelectionKey ignored : reableKeys) {
                ByteBuffer data = ByteBuffer.allocate(config().getReceiveBufferSize());
                MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
                if (messageInfo == null) {
                    return readMessages;
                }

                data.flip();
                buf.add(new SctpMessage(messageInfo, Unpooled.wrappedBuffer(data)));

                readMessages ++;

                if (readSuspended) {
                    return readMessages;
                }
            }
        } finally {
            reableKeys.clear();
        }

        return readMessages;
    }

    @Override
    protected void doWriteMessages(MessageBuf<Object> buf) throws Exception {
        if (!writeSelector.isOpen()) {
            return;
        }
        final int selectedKeys = writeSelector.select(SO_TIMEOUT);
        if (selectedKeys > 0) {
            final Set<SelectionKey> writableKeys = writeSelector.selectedKeys();
            for (SelectionKey ignored : writableKeys) {
                SctpMessage packet = (SctpMessage) buf.poll();
                if (packet == null) {
                    return;
                }
                ByteBuf data = packet.payloadBuffer();
                int dataLen = data.readableBytes();
                ByteBuffer nioData;

                if (data.nioBufferCount() != -1) {
                    nioData = data.nioBuffer();
                } else {
                    nioData = ByteBuffer.allocate(dataLen);
                    data.getBytes(data.readerIndex(), nioData);
                    nioData.flip();
                }

                final MessageInfo mi = MessageInfo.createOutgoing(association(), null, packet.streamIdentifier());
                mi.payloadProtocolID(packet.protocolIdentifier());
                mi.streamNumber(packet.streamIdentifier());

                ch.send(nioData, mi);
            }
            writableKeys.clear();
        }
    }

    @Override
    public Association association() {
        try {
            return ch.association();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isActive() {
        return isOpen() && association() != null;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            Iterator<SocketAddress> i = ch.getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<SocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getAllLocalAddresses();
            final Set<SocketAddress> addresses = new HashSet<SocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add(socketAddress);
            }
            return addresses;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            Iterator<SocketAddress> i = ch.getRemoteAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<SocketAddress> allRemoteAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getRemoteAddresses();
            final Set<SocketAddress> addresses = new HashSet<SocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add(socketAddress);
            }
            return addresses;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        ch.bind(localAddress);
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress,
                             SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            ch.bind(localAddress);
        }

        boolean success = false;
        try {
            ch.connect(remoteAddress);
            boolean  finishConnect = false;
            while (!finishConnect) {
                if (connectSelector.select(SO_TIMEOUT) >= 0) {
                    final Set<SelectionKey> selectionKeys = connectSelector.selectedKeys();
                    for (SelectionKey key : selectionKeys) {
                       if (key.isConnectable()) {
                           selectionKeys.clear();
                           finishConnect = true;
                           break;
                       }
                    }
                    selectionKeys.clear();
                }
            }
            success = ch.finishConnect();
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        closeSelector("read", readSelector);
        closeSelector("write", writeSelector);
        closeSelector("connect", connectSelector);
        ch.close();
    }

    private static void closeSelector(String selectorName, Selector selector) {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn("Failed to close a " + selectorName + " selector.", e);
        }
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newFuture());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelFuture future) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.bindAddress(localAddress);
                future.setSuccess();
            } catch (Throwable t) {
                future.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    bindAddress(localAddress, future);
                }
            });
        }
        return future;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        return unbindAddress(localAddress, newFuture());
    }

    @Override
    public ChannelFuture unbindAddress(final InetAddress localAddress, final ChannelFuture future) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.unbindAddress(localAddress);
                future.setSuccess();
            } catch (Throwable t) {
                future.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, future);
                }
            });
        }
        return future;
    }
}
