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

package net.daporkchop.turbotunnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.daporkchop.lib.common.function.throwing.EFunction;
import net.daporkchop.lib.network.nettycommon.PorkNettyHelper;
import net.daporkchop.turbotunnel.loadbalance.FixedRandomBalancer;
import net.daporkchop.turbotunnel.loadbalance.InetAddressBalancer;
import net.daporkchop.turbotunnel.protocol.socks.SOCKS5Server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.stream.StreamSupport;

/**
 * @author DaPorkchop_
 */
public class Main {
    public static void main(String... args) throws UnknownHostException, IOException {
        String configName = args.length > 0 ? args[0] : "config.json";

        JsonObject obj;
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configName), StandardCharsets.UTF_8))) {
            obj = new JsonParser().parse(reader).getAsJsonObject();
        }

        InetAddressBalancer balancer = new FixedRandomBalancer(
                StreamSupport.stream(obj.getAsJsonArray("v4").spliterator(), false)
                        .map(JsonElement::getAsString)
                        .map((EFunction<String, InetAddress>) InetAddress::getByName)
                        .map(Inet4Address.class::cast)
                        .toArray(Inet4Address[]::new),
                StreamSupport.stream(obj.getAsJsonArray("v6").spliterator(), false)
                        .map(JsonElement::getAsString)
                        .map((EFunction<String, InetAddress>) InetAddress::getByName)
                        .map(Inet6Address.class::cast)
                        .toArray(Inet6Address[]::new),
                obj.get("prefer6").getAsBoolean());

        try (SOCKS5Server server = new SOCKS5Server(PorkNettyHelper.getPoolTCP(), balancer)) {
            new Scanner(System.in).nextLine();
        }
    }
}
