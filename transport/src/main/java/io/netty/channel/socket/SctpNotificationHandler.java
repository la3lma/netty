/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.Notification;
import com.sun.nio.sctp.PeerAddressChangeNotification;
import com.sun.nio.sctp.SendFailedNotification;
import com.sun.nio.sctp.ShutdownNotification;

import io.netty.channel.ChannelPipeline;


/**
 * {@link AbstractNotificationHandler} implementation which will handle all {@link Notification}s by trigger a
 * {@link SctpNotificationEvent} in the {@link ChannelPipeline} of a {@link SctpChannel}.
 */
public final class SctpNotificationHandler extends AbstractNotificationHandler<Object> {

    private final SctpChannel sctpChannel;

    public SctpNotificationHandler(SctpChannel sctpChannel) {
        if (sctpChannel == null) {
            throw new NullPointerException("sctpChannel");
        }
        this.sctpChannel = sctpChannel;
    }

    @Override
    public HandlerResult handleNotification(AssociationChangeNotification notification, Object o) {
        fireEvent(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(PeerAddressChangeNotification notification, Object o) {
        fireEvent(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(SendFailedNotification notification, Object o) {
        fireEvent(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(ShutdownNotification notification, Object o) {
        fireEvent(notification, o);
        sctpChannel.close();
        return HandlerResult.RETURN;
    }

    private void fireEvent(Notification notification, Object o) {
        sctpChannel.pipeline().fireUserEventTriggered(new SctpNotificationEvent(notification, o));
    }
}

