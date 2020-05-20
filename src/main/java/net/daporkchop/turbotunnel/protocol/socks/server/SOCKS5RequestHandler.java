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

package net.daporkchop.turbotunnel.protocol.socks.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.daporkchop.turbotunnel.protocol.socks.SOCKS5Command;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static net.daporkchop.lib.common.util.PValidation.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5.*;
import static net.daporkchop.turbotunnel.protocol.socks.server.SOCKS5Server.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ChannelHandler.Sharable
public final class SOCKS5RequestHandler extends ChannelInboundHandlerAdapter {
    public static final SOCKS5RequestHandler INSTANCE = new SOCKS5RequestHandler();

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

        System.out.printf("Request from %s: %s\n", ctx.channel().remoteAddress(), state);
        ctx.close();
    }
}
