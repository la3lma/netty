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
package io.netty.channel;

import java.net.SocketAddress;

/**
 * {@link ChannelHandler} implementation which represents a combination out of a {@link ChannelStateHandler} and
 * the {@link ChannelOperationHandler}.
 *
 * It is a good starting point if your {@link ChannelHandler} implementation needs to intercept operations and also
 * state updates.
 */
public abstract class ChannelHandlerAdapter extends ChannelStateHandlerAdapter implements ChannelOperationHandler {

    /**
     * Calls {@link ChannelHandlerContext#bind(SocketAddress, ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                     ChannelFuture future) throws Exception {
        ctx.bind(localAddress, future);
    }

    /**
     * Calls {@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.connect(remoteAddress, localAddress, future);
    }

    /**
     * Calls {@link ChannelHandlerContext#disconnect(ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        ctx.disconnect(future);
    }

    /**
     * Calls {@link ChannelHandlerContext#close(ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void close(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        ctx.close(future);
    }

    /**
     * Calls {@link ChannelHandlerContext#close(ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        ctx.deregister(future);
    }

    /**
     * Calls {@link ChannelHandlerContext#flush(ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     *
     * Be aware that if your class also implement {@link ChannelOutboundHandler} it need to {@code @Override} this
     * method and provide some proper implementation. Fail to do so, will result in an {@link IllegalStateException}!
     */
    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        if (this instanceof ChannelOutboundHandler) {
            throw new IllegalStateException(
                    "flush(...) must be overridden by " + getClass().getName() +
                            ", which implements " + ChannelOutboundHandler.class.getSimpleName());
        }
        ctx.flush(future);
    }

    /**
     * Calls {@link ChannelHandlerContext#sendFile(FileRegion, ChannelFuture)} to forward
     * to the next {@link ChannelOperationHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelFuture future) throws Exception {
        ctx.sendFile(region, future);
    }
}
