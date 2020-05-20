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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
public class FixedRandomBalancer implements InetAddressBalancer {
    private final Inet4Address[] v4;
    private final Inet6Address[] v6;
    private final boolean prefer6;

    public FixedRandomBalancer(@NonNull Inet4Address[] v4, @NonNull Inet6Address[] v6, boolean prefer6) {
        this.v4 = Arrays.stream(v4).filter(Objects::nonNull).toArray(Inet4Address[]::new);
        this.v6 = Arrays.stream(v6).filter(Objects::nonNull).toArray(Inet6Address[]::new);
        this.prefer6 = prefer6;
    }

    @Override
    public InetAddress next(boolean v4Allowed, boolean v6Allowed) throws Exception {
        if (this.prefer6) {
            if (v6Allowed) {
                return this.v6[ThreadLocalRandom.current().nextInt(this.v6.length)];
            } else if (v4Allowed) {
                return this.v4[ThreadLocalRandom.current().nextInt(this.v4.length)];
            }
        } else {
            if (v4Allowed) {
                return this.v4[ThreadLocalRandom.current().nextInt(this.v4.length)];
            } else if (v6Allowed) {
                return this.v6[ThreadLocalRandom.current().nextInt(this.v6.length)];
            }
        }
        throw new IllegalStateException();
    }
}
