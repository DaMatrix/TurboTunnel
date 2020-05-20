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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import static net.daporkchop.lib.common.util.PValidation.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5Server.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ChannelHandler.Sharable
public final class SOCKS5GreetingHandler extends ChannelInboundHandlerAdapter {
    public static final SOCKS5GreetingHandler INSTANCE = new SOCKS5GreetingHandler();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
        super.channelRegistered(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        checkState(msg instanceof ByteBuf, "invalid message");
        ByteBuf data = (ByteBuf) msg;

        checkState(data.readableBytes() >= 2);
        checkState(data.readByte() == VERSION, "Invalid version!");
        int nauth = data.readByte() & 0xFF;
        checkState(data.readableBytes() >= nauth);

        SOCKS5Authentication[] supportedAuth = IntStream.range(0, nauth)
                .map(i -> data.readByte() & 0xFF)
                .mapToObj(SOCKS5Authentication::fromIndex)
                .filter(Objects::nonNull)
                .filter(SOCKS5Authentication::supported)
                .toArray(SOCKS5Authentication[]::new);
        checkState(supportedAuth.length > 0, "No supported authentication modes given!");

        ctx.channel().attr(STATE_KEY).get().auth(supportedAuth[0]);

        //System.out.printf("Supported authentication methods: %s\n", Arrays.toString(supportedAuth));

        ctx.pipeline().replace(this, "socks5", SOCKS5RequestHandler.INSTANCE);

        ctx.writeAndFlush(
                ctx.alloc().ioBuffer(2, 2) //respond with server choice
                        .writeByte(VERSION) //VER
                        .writeByte(supportedAuth[0].ordinal()), //CAUTH
                ctx.voidPromise());
        ctx.read();
    }
}
