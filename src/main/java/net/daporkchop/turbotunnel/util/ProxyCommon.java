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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.daporkchop.turbotunnel.loadbalance.InetAddressBalancer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static net.daporkchop.lib.common.util.PValidation.checkState;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class ProxyCommon {
    public Future<Channel> openConnectionTo(@NonNull Channel src, @NonNull Supplier<Bootstrap> bootstrapFactory, @NonNull InetSocketAddress address, @NonNull InetAddressBalancer balancer) throws Exception  {
        InetAddress remoteAddress = address.getAddress();
        //System.out.printf("Choosing binding for remote address: %s\n", state.address());
        InetAddress[] v4Addresses;
        InetAddress[] v6Addresses;
        if (remoteAddress == null) {
            String hostname = address.getHostName();
            InetAddress[] allAddresses = InetAddress.getAllByName(hostname);
            System.out.println("Resolved addresses: " + Arrays.toString(allAddresses));
            v4Addresses = Arrays.stream(allAddresses).filter(Inet4Address.class::isInstance).toArray(InetAddress[]::new);
            v6Addresses = Arrays.stream(allAddresses).filter(Inet6Address.class::isInstance).toArray(InetAddress[]::new);
            checkState(v4Addresses.length > 0 || v6Addresses.length > 0, "no remote addresses found...");
        } else if (remoteAddress instanceof Inet4Address) {
            v4Addresses = new InetAddress[]{remoteAddress};
            v6Addresses = new InetAddress[0];
        } else if (remoteAddress instanceof Inet6Address) {
            v4Addresses = new InetAddress[0];
            v6Addresses = new InetAddress[]{remoteAddress};
        } else {
            throw new IllegalArgumentException(String.valueOf(remoteAddress));
        }

        InetAddress localAddress = balancer.next(v4Addresses.length > 0, v6Addresses.length > 0);
        if (localAddress instanceof Inet4Address)   {
            remoteAddress = v4Addresses[ThreadLocalRandom.current().nextInt(v4Addresses.length)];
        } else if (localAddress instanceof Inet6Address)   {
            remoteAddress = v6Addresses[ThreadLocalRandom.current().nextInt(v6Addresses.length)];
        } else {
            throw new IllegalArgumentException(String.valueOf(localAddress));
        }

        Promise<Channel> promise = src.eventLoop().newPromise();

        System.out.printf("Connecting to %s from %s\n", remoteAddress, localAddress);

        ChannelFuture future = bootstrapFactory.get()
                .localAddress(localAddress, 0)
                .option(ChannelOption.AUTO_READ, false)
                .connect(remoteAddress, address.getPort())
                .addListener((ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        promise.trySuccess(f.channel());
                    } else {
                        promise.tryFailure(f.cause());
                    }
                });
        return promise;
    }
}
