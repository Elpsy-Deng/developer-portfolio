package dev.jackteng.portfolio.auth;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/** Only our private, authenticated TLS gateway can assert a single numeric peer. */
@Component
public final class PeerAddresses {
    private final String key;
    private final IpAddressMatcher network;
    public PeerAddresses(@Value("${portfolio.proxy.key:}") String key,
            @Value("${portfolio.proxy.network:}") String cidr,
            @Value("${portfolio.auth.cookie-secure:true}") boolean secure) {
        if (key.isEmpty() && cidr.isEmpty()) { this.key=""; this.network=null; return; }
        if (!key.matches("[a-f0-9]{64}") || !cidr.matches("[0-9.]+/[0-9]{1,2}") || !secure)
            throw new IllegalArgumentException("Invalid trusted TLS gateway configuration");
        String[] parts=cidr.split("/"); int prefix=Integer.parseInt(parts[1]);
        try {
            if (prefix<16 || prefix>32 || !InetAddress.getByName(parts[0]).isSiteLocalAddress())
                throw new IllegalArgumentException("Private narrow gateway network required");
        } catch (java.net.UnknownHostException error) { throw new IllegalArgumentException("Invalid gateway network"); }
        this.key=key;this.network=new IpAddressMatcher(cidr);
    }
    public String address(HttpServletRequest request) {
        String claim=request.getHeader("X-Portfolio-Peer"), supplied=request.getHeader("X-Portfolio-Gateway");
        if(network==null || !network.matches(request.getRemoteAddr()) || supplied==null || supplied.length()!=64
                || !MessageDigest.isEqual(key.getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII))
                || claim==null || claim.length()>45 || !claim.matches("[0-9a-fA-F:.]+")
                || (!claim.contains(":") && !claim.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
                || java.util.Collections.list(request.getHeaders("X-Portfolio-Peer")).size()!=1
                || java.util.Collections.list(request.getHeaders("X-Portfolio-Gateway")).size()!=1)
            return request.getRemoteAddr();
        try { return InetAddress.getByName(claim).getHostAddress(); }
        catch(java.net.UnknownHostException error) { return request.getRemoteAddr(); }
    }
}
