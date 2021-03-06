/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2020 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.turbotunnel.protocol.socks;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import lombok.NonNull;
import net.daporkchop.turbotunnel.util.ProxyCommon;

/**
 * The different commands that can be issued by a SOCKS5 client.
 *
 * @author DaPorkchop_
 */
public enum SOCKS5Command {
    TCP_CONNECT {
        @Override
        public Future<Channel> handle(@NonNull Channel channel, @NonNull SOCKS5ServerState state) throws Exception {
            return ProxyCommon.openConnectionTo(channel, state.server()::getClientBootstrap, state.address(), state.server().balancer());
        }
    },
    TCP_BIND {
        @Override
        public Future<Channel> handle(@NonNull Channel channel, @NonNull SOCKS5ServerState state) throws Exception {
            return channel.eventLoop().newFailedFuture(new UnsupportedOperationException("TCP_BIND"));
        }
    },
    UDP_ASSOCIATE {
        @Override
        public Future<Channel> handle(@NonNull Channel channel, @NonNull SOCKS5ServerState state) throws Exception {
            return channel.eventLoop().newFailedFuture(new UnsupportedOperationException("UDP_ASSOCIATE"));
        }
    };

    private static final SOCKS5Command[] VALUES = values();

    public static SOCKS5Command fromIndex(int index) {
        index -= 1;
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }

    public abstract Future<Channel> handle(@NonNull Channel channel, @NonNull SOCKS5ServerState state) throws Exception;
}
