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
import io.netty.channel.ChannelHandlerContext;
import lombok.NonNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.IntStream;

import static net.daporkchop.lib.common.util.PValidation.*;
import static net.daporkchop.turbotunnel.protocol.socks.SOCKS5.*;

/**
 * The different states during a server-side SOCKS5 handshake.
 *
 * @author DaPorkchop_
 */
public enum SOCKS5ServerState {
    GREETING {
        @Override
        public Object handle(@NonNull ChannelHandlerContext ctx, @NonNull ByteBuf data) throws Exception {
            if (data.readableBytes() < 2) {
                return null;
            }
            int readerIndex = data.readerIndex();
            int nauth = data.getByte(readerIndex + 1) & 0xFF;
            if (data.readableBytes() < nauth + 2) {
                return null;
            }

            //we have enough data to read the whole packet, continue:
            checkState(data.readByte() == VERSION, "Invalid version!");
            data.readByte();
            SOCKS5Authentication[] SUPPORTED_AUTH = IntStream.range(0, nauth)
                    .map(i -> data.getByte(readerIndex + i + 2) & 0xFF)
                    .mapToObj(SOCKS5Authentication::fromIndex)
                    .filter(Objects::nonNull)
                    .toArray(SOCKS5Authentication[]::new);
            checkState(SUPPORTED_AUTH.length > 0, "No supported authentication modes given!");

            ctx.channel().attr(KEY_AUTHENTICATION).set(SUPPORTED_AUTH[0]);

            return ctx.alloc().ioBuffer(2, 2) //respond with server choice
                    .writeByte(VERSION) //VER
                    .writeByte(SUPPORTED_AUTH[0].ordinal()); //CAUTH
        }

        @Override
        public SOCKS5ServerState next() {
            return CONNECTION_REQUEST;
        }
    },
    CONNECTION_REQUEST {
        @Override
        public Object handle(@NonNull ChannelHandlerContext ctx, @NonNull ByteBuf data) throws Exception {
            if (data.readableBytes() < 4) {
                return null;
            }
            data.markReaderIndex();

            checkState(data.readByte() == VERSION, "Invalid version!");
            SOCKS5Command command = SOCKS5Command.fromIndex(data.readByte() & 0xFF);
            checkState(command != null, "Unknown command!");
            data.readByte(); //skip

            InetSocketAddress address;
            switch (data.readByte() & 0xFF) {
                case TYPE_IPV4: {
                    if (data.readableBytes() < 4 + 2) {
                        data.resetReaderIndex();
                        return null;
                    }
                    byte[] b = new byte[4];
                    data.readBytes(b);
                    address = new InetSocketAddress(InetAddress.getByAddress(b), data.readUnsignedShort());
                }
                break;
                case TYPE_IPV6: {
                    if (data.readableBytes() < 4 + 2) {
                        data.resetReaderIndex();
                        return null;
                    }
                    byte[] b = new byte[16];
                    data.readBytes(b);
                    address = new InetSocketAddress(InetAddress.getByAddress(b), data.readUnsignedShort());
                }
                break;
                case TYPE_DOMAIN: {
                    if (data.readableBytes() < 1 + 2) {
                        data.resetReaderIndex();
                        return null;
                    }
                    int len = data.readByte() & 0xFF;
                    if (data.readableBytes() < len + 2) {
                        data.resetReaderIndex();
                        return null;
                    }
                    address = new InetSocketAddress(data.readCharSequence(len, StandardCharsets.US_ASCII).toString(), data.readUnsignedShort());
                }
                break;
                default:
                    throw new IllegalStateException();
            }

            ctx.channel().attr(KEY_ADDRESS).set(address);
            ctx.channel().attr(KEY_COMMAND).set(command);
            return address;
        }

        @Override
        public SOCKS5ServerState next() {
            return null;
        }
    };

    public abstract Object handle(@NonNull ChannelHandlerContext ctx, @NonNull ByteBuf data) throws Exception;

    public abstract SOCKS5ServerState next();
}
