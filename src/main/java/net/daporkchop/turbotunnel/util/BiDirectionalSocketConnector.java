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

package net.daporkchop.turbotunnel.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.NonNull;

import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
@ChannelHandler.Sharable
public class BiDirectionalSocketConnector extends ChannelInboundHandlerAdapter {
    protected final Channel a;
    protected final Channel b;

    public BiDirectionalSocketConnector(@NonNull Channel a, @NonNull Channel b) {
        checkArg(a != b, "channels must be distinct!");
        this.a = a;
        this.b = b;
        a.config().setAutoRead(false);
        b.config().setAutoRead(false);
        a.pipeline().addLast("forward", this);
        b.pipeline().addLast("forward", this);
        a.read();
        b.read();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel() == this.a)    {
            //System.out.printf("Forwarding message (%s) from %s to %s\n", msg, ctx.channel().remoteAddress(), this.b.remoteAddress());
            this.b.writeAndFlush(msg).addListener(f -> this.a.read());
        } else if (ctx.channel() == this.b) {
            //System.out.printf("Forwarding message (%s) from %s to %s\n", msg, ctx.channel().remoteAddress(), this.a.remoteAddress());
            this.a.writeAndFlush(msg).addListener(f -> this.b.read());
        } else {
            System.err.println(String.valueOf(ctx.channel()));
            throw new IllegalStateException();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.a.close();
        this.b.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        this.a.close();
        this.b.close();
        cause.printStackTrace();
    }
}
