package de.agiehl.bgoffers.pricecomparison;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;

final class Ipv6FirstDnsResolver implements DnsResolver {

    private final DnsResolver delegate;

    Ipv6FirstDnsResolver() {
        this(SystemDefaultDnsResolver.INSTANCE);
    }

    Ipv6FirstDnsResolver(DnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        var addresses = delegate.resolve(host);
        Arrays.sort(addresses, Comparator.comparingInt(this::addressPriority));
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }

    private int addressPriority(InetAddress address) {
        return address instanceof Inet6Address ? 0 : 1;
    }
}
