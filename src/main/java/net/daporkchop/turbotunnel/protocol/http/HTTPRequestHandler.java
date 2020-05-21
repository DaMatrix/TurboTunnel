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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.daporkchop.lib.common.ref.Ref;
import net.daporkchop.lib.common.ref.ThreadRef;
import net.daporkchop.turbotunnel.util.BiDirectionalSocketConnector;
import net.daporkchop.turbotunnel.util.ProxyCommon;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.daporkchop.lib.common.util.PValidation.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5Server.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Sharable
public final class HTTPRequestHandler extends ChannelInboundHandlerAdapter {
    public static final HTTPRequestHandler INSTANCE = new HTTPRequestHandler();

    public static final Ref<Matcher> REQUEST_MATCHER_CACHE = ThreadRef.regex(Pattern.compile("^([A-Z]+) ([^ ]+) (HTTP/1\\.[01])\r\n"));
    public static final Ref<Matcher> HEADER_MATCHER_CACHE = ThreadRef.regex(Pattern.compile("^([^:]+):(?> ?)(.+)$"));

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
        super.channelRegistered(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        checkState(msg instanceof ByteBuf, "invalid message");
        ByteBuf data = (ByteBuf) msg;

        HTTPServerState state = ctx.channel().attr(HTTPServer.STATE_KEY).get();

        String request = data.toString(StandardCharsets.US_ASCII);
        System.out.println(request);
        checkState(request.endsWith("\r\n\r\n"));
        Matcher requestMatcher = REQUEST_MATCHER_CACHE.get().reset(request);
        checkState(requestMatcher.find());

        state.httpVersion(requestMatcher.group(3));

        //find headers
        {
            String[] splitHeaders = request.split("\r\n");
            Matcher headerMatcher = HEADER_MATCHER_CACHE.get();
            for (int i = 1; i < splitHeaders.length; i++) {
                checkState(headerMatcher.reset(splitHeaders[i]).find(), splitHeaders[i]);
                checkState(state.headers().putIfAbsent(headerMatcher.group(1), headerMatcher.group(2)) == null, headerMatcher.group(1));
            }
            System.out.println("Headers: " + state.headers());
        }

        switch (requestMatcher.group(1)) {
            case "CONNECT": {
                String[] split = requestMatcher.group(2).split(":");
                state.address(InetSocketAddress.createUnresolved(split[0], Integer.parseUnsignedInt(split[1])));

                ProxyCommon.openConnectionTo(ctx.channel(), state.server()::getClientBootstrap, state.address(), state.server().balancer())
                        .addListener((GenericFutureListener<Future<Channel>>) f -> {
                            ByteBuf buf = ctx.alloc().ioBuffer();
                            buf.writeCharSequence(state.httpVersion(), StandardCharsets.US_ASCII);
                            buf.writeByte(' ');
                            if (f.isSuccess()) {
                                buf.writeCharSequence("200 OK\r\n\r\n", StandardCharsets.US_ASCII);
                                ctx.channel().writeAndFlush(buf);

                                try {
                                    ctx.channel().pipeline().remove(this);
                                } catch (NoSuchElementException e) {
                                    //removed
                                    return;
                                }
                                new BiDirectionalSocketConnector(ctx.channel(), f.getNow());

                                System.out.printf("Tunnel request from %s: %s (handled with local address: %s)\n", ctx.channel().remoteAddress(), state, f.getNow().localAddress());
                            } else {
                                f.cause().printStackTrace();

                                buf.writeCharSequence("500 Internal Server Error\r\n\r\n", StandardCharsets.US_ASCII);
                                ctx.channel().writeAndFlush(buf);
                                ctx.channel().close();
                            }
                        });
            }
            break;
            case "GET": {
                /*String rawUrl = requestMatcher.group(2);
                URL url = new URL(rawUrl);
                checkArg("http".equals(url.getProtocol()), rawUrl);

                state.address(InetSocketAddress.createUnresolved(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()));
                state.headers().remove("Proxy-Connection");

                ProxyCommon.openConnectionTo(ctx.channel(), state.server()::getClientBootstrap, state.address(), state.server().balancer())
                        .addListener((GenericFutureListener<Future<Channel>>) f -> {
                            ByteBuf buf = ctx.alloc().ioBuffer();
                            if (f.isSuccess()) {
                                buf.writeCharSequence("GET ", StandardCharsets.US_ASCII);
                                buf.writeCharSequence(url.getPath(), StandardCharsets.US_ASCII);
                                if (url.getQuery() != null) {
                                    buf.writeByte('?');
                                    buf.writeCharSequence(url.getQuery(), StandardCharsets.US_ASCII);
                                }
                                buf.writeCharSequence(state.httpVersion(), StandardCharsets.US_ASCII);
                                state.headers().forEach((key, value) -> {
                                    buf.writeByte('\r').writeByte('\n');
                                    buf.writeCharSequence(key, StandardCharsets.US_ASCII);
                                    buf.writeByte(':').writeByte(' ');
                                    buf.writeCharSequence(value, StandardCharsets.US_ASCII);
                                });
                                buf.writeCharSequence("\r\n\r\n", StandardCharsets.US_ASCII);
                                ctx.channel().writeAndFlush(buf);

                                try {
                                    ctx.channel().pipeline().remove(this);
                                } catch (NoSuchElementException e) {
                                    //removed
                                    return;
                                }
                                new BiDirectionalSocketConnector(ctx.channel(), f.getNow());

                                System.out.printf("Proxy request from %s: %s (handled with local address: %s)\n", ctx.channel().remoteAddress(), state, f.getNow().localAddress());
                            } else {
                                f.cause().printStackTrace();

                                buf.writeCharSequence(state.httpVersion(), StandardCharsets.US_ASCII);
                                buf.writeByte(' ');
                                buf.writeCharSequence("502 Bad Gateway\r\n\r\n", StandardCharsets.US_ASCII);
                                ctx.channel().writeAndFlush(buf);
                                ctx.channel().close();
                            }
                        });*/
            }
            break;
            default:
                ByteBuf buf = ctx.alloc().ioBuffer();
                buf.writeCharSequence(state.httpVersion(), StandardCharsets.US_ASCII);
                buf.writeByte(' ');
                buf.writeCharSequence("405 Method Not Allowed\r\n\r\n", StandardCharsets.US_ASCII);
                ctx.channel().writeAndFlush(buf);
                ctx.channel().close();
        }
    }
}
