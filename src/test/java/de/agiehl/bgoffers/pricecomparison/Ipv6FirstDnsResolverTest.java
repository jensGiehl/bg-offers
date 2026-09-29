package de.agiehl.bgoffers.pricecomparison;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class Ipv6FirstDnsResolverTest {

    @Test
    void prefersIpv6AndKeepsIpv4AsFallback() throws Exception {
        var ipv4 = InetAddress.getByName("192.0.2.1");
        var ipv6 = InetAddress.getByName("2001:db8::1");
        var resolver = new Ipv6FirstDnsResolver(new FixedDnsResolver(ipv4, ipv6));

        var addresses = resolver.resolve("example.test");

        assertThat(addresses).containsExactly(ipv6, ipv4);
        assertThat(addresses[0]).isInstanceOf(Inet6Address.class);
    }

    private record FixedDnsResolver(InetAddress... addresses) implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) {
            return addresses.clone();
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return host;
        }
    }
}
