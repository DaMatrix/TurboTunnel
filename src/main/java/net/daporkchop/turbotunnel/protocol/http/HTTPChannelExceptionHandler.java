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

package net.daporkchop.turbotunnel.protocol.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

import static net.daporkchop.turbotunnel.protocol.http.HTTPServer.STATE_KEY;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ChannelHandler.Sharable
public final class HTTPChannelExceptionHandler extends ChannelInboundHandlerAdapter {
    public static final HTTPChannelExceptionHandler INSTANCE = new HTTPChannelExceptionHandler();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        HTTPServerState state = ctx.channel().attr(STATE_KEY).get();
        ByteBuf buf = ctx.alloc().ioBuffer();

        if (state != null && state.httpVersion() != null)   {
            buf.writeCharSequence(state.httpVersion(), StandardCharsets.US_ASCII);
            buf.writeByte(' ');
        } else {
            buf.writeCharSequence("HTTP/1.1 ", StandardCharsets.US_ASCII);
        }
        buf.writeCharSequence("500 Internal Server Error\r\n\r\n", StandardCharsets.US_ASCII);

        ctx.channel().writeAndFlush(buf);
        ctx.channel().close();
        cause.printStackTrace();
    }
}
