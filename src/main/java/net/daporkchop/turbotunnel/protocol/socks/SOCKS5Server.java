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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.network.nettycommon.eventloopgroup.pool.EventLoopGroupPool;
import net.daporkchop.turbotunnel.loadbalance.InetAddressBalancer;
import net.daporkchop.turbotunnel.util.CloseChannelOnExceptionHandler;
import net.daporkchop.turbotunnel.util.NoopChannelInitializer;

/**
 * @author DaPorkchop_
 */
@Accessors(fluent = true)
public class SOCKS5Server extends ChannelInitializer<Channel> implements AutoCloseable {
    static final AttributeKey<SOCKS5ServerState> STATE_KEY = AttributeKey.newInstance("socks5_state");

    @Getter
    private final EventLoopGroupPool loopGroupPool;
    private final EventLoopGroup loopGroup;
    private final Channel serverChannel;
    private final Bootstrap clientBootstrap;
    @Getter
    private final InetAddressBalancer balancer;

    public SOCKS5Server(@NonNull EventLoopGroupPool loopGroupPool, @NonNull InetAddressBalancer balancer, int port) {
        this.loopGroupPool = loopGroupPool;
        this.balancer = balancer;
        this.loopGroup = loopGroupPool.get();

        this.serverChannel = new ServerBootstrap()
                .channelFactory(loopGroupPool.transport().channelFactorySocketServer())
                .group(this.loopGroup)
                .childHandler(this)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.AUTO_READ, false)
                .bind(port).channel();
        this.serverChannel.closeFuture().addListener(f -> this.loopGroupPool.release(this.loopGroup));

        this.clientBootstrap = new Bootstrap()
                .channelFactory(loopGroupPool.transport().channelFactorySocketClient())
                .group(this.loopGroup)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.AUTO_READ, false)
                .handler(NoopChannelInitializer.INSTANCE);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        //System.out.println(this.serverChannel);
        ch.attr(STATE_KEY).set(new SOCKS5ServerState(this));

        ch.pipeline()
                .addLast("socks5", SOCKS5GreetingHandler.INSTANCE)
                .addLast("exception", CloseChannelOnExceptionHandler.INSTANCE);
    }

    @Override
    public void close() {
        this.serverChannel.close();
    }

    public Bootstrap getClientBootstrap() {
        return this.clientBootstrap.clone();
    }
}
