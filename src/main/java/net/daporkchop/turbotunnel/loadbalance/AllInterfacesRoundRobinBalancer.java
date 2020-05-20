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

package net.daporkchop.turbotunnel.loadbalance;

import lombok.NonNull;
import net.daporkchop.lib.common.ref.Ref;
import net.daporkchop.lib.common.ref.ThreadRef;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
public class AllInterfacesRoundRobinBalancer implements InetAddressBalancer {
    private final Ref<Matcher> matcherCache;

    public AllInterfacesRoundRobinBalancer(@NonNull Pattern interfaceFilter) {
        this.matcherCache = ThreadRef.regex(interfaceFilter);
    }

    @Override
    public InetAddress next(boolean v4Allowed, boolean v6Allowed) throws Exception {
        List<InetAddress> list = new ArrayList<>();

        Matcher matcher = this.matcherCache.get();

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface interfaz = interfaces.nextElement();
            if (!matcher.reset(interfaz.getName()).find()) {
                continue;
            }
            Enumeration<InetAddress> addresses = interfaz.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || (!v4Allowed && address instanceof Inet4Address)
                        || (!v6Allowed && address instanceof Inet6Address)) {
                    continue;
                }
                list.add(address);
            }
        }

        checkState(!list.isEmpty(), "No addresses found?!?!");
        System.out.println(list.stream().map(InetAddress::toString).collect(Collectors.joining("\n")));
        InetAddress address = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        System.out.println("Using " + address);
        return address;
        //return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
