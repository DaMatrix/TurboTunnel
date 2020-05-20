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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ConnectTimeoutException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.daporkchop.turbotunnel.util.BiDirectionalSocketConnector;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static net.daporkchop.lib.common.util.PValidation.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5Server.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ChannelHandler.Sharable
public final class SOCKS5RequestHandler extends ChannelInboundHandlerAdapter {
    public static final SOCKS5RequestHandler INSTANCE = new SOCKS5RequestHandler();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
        super.channelRegistered(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        checkState(msg instanceof ByteBuf, "invalid message");
        ByteBuf data = (ByteBuf) msg;

        checkState(data.readableBytes() >= 4);

        checkState(data.readByte() == VERSION, "Invalid version!");

        SOCKS5ServerState state = ctx.channel().attr(STATE_KEY).get();

        state.command(SOCKS5Command.fromIndex(data.readByte() & 0xFF));
        data.readByte(); //skip

        switch (data.readByte() & 0xFF) {
            case TYPE_IPV4: {
                checkState(data.readableBytes() >= 4 + 2);
                byte[] b = new byte[4];
                data.readBytes(b);
                state.address(new InetSocketAddress(InetAddress.getByAddress(b), data.readUnsignedShort()));
            }
            break;
            case TYPE_IPV6: {
                checkState(data.readableBytes() >= 16 + 2);
                byte[] b = new byte[16];
                data.readBytes(b);
                state.address(new InetSocketAddress(InetAddress.getByAddress(b), data.readUnsignedShort()));
            }
            break;
            case TYPE_DOMAIN: {
                checkState(data.readableBytes() >= 1 + 2);
                int len = data.readByte() & 0xFF;
                checkState(data.readableBytes() >= len + 2);
                state.address(InetSocketAddress.createUnresolved(data.readCharSequence(len, StandardCharsets.US_ASCII).toString(), data.readUnsignedShort()));
            }
            break;
            default:
                throw new IllegalStateException();
        }

        state.command().handle(ctx.channel(), state)
                .addListener((GenericFutureListener<Future<Channel>>) f -> {
                    SOCKS5Status status;
                    if (f.isSuccess()) {
                        status = SOCKS5Status.REQUEST_GRANTED;
                    } else {
                        Throwable cause = f.cause();
                        cause.printStackTrace();
                        if (cause instanceof ConnectTimeoutException) {
                            status = SOCKS5Status.TTL_EXPIRED;
                        } else {
                            status = SOCKS5Status.GENERAL_FAILURE;
                        }
                    }

                    ByteBuf buf = ctx.alloc().ioBuffer();
                    buf.writeByte(VERSION);
                    buf.writeByte(status.ordinal());
                    buf.writeByte(0);

                    if (f.isSuccess()) {
                        Channel channel = f.getNow();

                        InetSocketAddress address = (InetSocketAddress) channel.localAddress();
                        if (address.getAddress() instanceof Inet4Address) {
                            buf.writeByte(TYPE_IPV4);
                            buf.writeBytes(address.getAddress().getAddress());
                        } else if (address.getAddress() instanceof Inet6Address) {
                            buf.writeByte(TYPE_IPV6);
                            buf.writeBytes(address.getAddress().getAddress());
                        } else {
                            throw new UnsupportedOperationException(String.valueOf(address.getAddress()));
                        }
                        buf.writeShort(address.getPort());

                        ctx.channel().writeAndFlush(buf);
                        ctx.channel().pipeline().remove(this);
                        new BiDirectionalSocketConnector(ctx.channel(), channel);

                        System.out.printf("Request from %s: %s (handled with local address: %s)\n", ctx.channel().remoteAddress(), state, channel.localAddress());
                    } else {
                        buf.writeByte(TYPE_IPV4);
                        buf.writeInt(0);
                        buf.writeShort(0);

                        ctx.channel().writeAndFlush(buf);
                        ctx.channel().close();
                    }
                });
    }
}
