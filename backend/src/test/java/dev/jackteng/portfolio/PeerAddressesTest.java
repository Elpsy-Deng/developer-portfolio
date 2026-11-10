package dev.jackteng.portfolio;

import dev.jackteng.portfolio.auth.PeerAddresses;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerAddressesTest {
    final String key="a".repeat(64);
    MockHttpServletRequest request(String remote,String token,String peer){
        var r=new MockHttpServletRequest();r.setRemoteAddr(remote);
        if(token!=null)r.addHeader("X-Portfolio-Gateway",token);
        if(peer!=null)r.addHeader("X-Portfolio-Peer",peer);
        r.addHeader("X-Forwarded-For","198.51.100.99");r.addHeader("Forwarded","for=198.51.100.98");return r;
    }
    @Test void optInNeedsPrivateNarrowNetworkStrongKeyAndSecureCookies(){
        for(String network:new String[]{"0.0.0.0/0","172.18.0.0/8","8.8.8.0/24","localhost/32","172.18.0.0/33"})
            assertThrows(IllegalArgumentException.class,()->new PeerAddresses(key,network,true));
        assertThrows(IllegalArgumentException.class,()->new PeerAddresses(key,"172.18.0.0/16",false));
        assertThrows(IllegalArgumentException.class,()->new PeerAddresses("short","172.18.0.0/16",true));
        assertThrows(IllegalArgumentException.class,()->new PeerAddresses(key,"",true));
    }
    @Test void disabledOrUntrustedSocketOrWrongKeyIgnoresAllHeaderClaims(){
        assertEquals("172.18.0.2",new PeerAddresses("","",false).address(request("172.18.0.2",key,"203.0.113.1")));
        var p=new PeerAddresses(key,"172.18.0.0/16",true);
        assertEquals("192.0.2.1",p.address(request("192.0.2.1",key,"203.0.113.1")));
        assertEquals("172.18.0.2",p.address(request("172.18.0.2","b".repeat(64),"203.0.113.1")));
    }
    @Test void trustedSingleNumericPeersStayDistinctAndIpv6Canonical(){
        var p=new PeerAddresses(key,"172.18.0.0/16",true);
        assertEquals("203.0.113.1",p.address(request("172.18.0.2",key,"203.0.113.1")));
        assertEquals("203.0.113.2",p.address(request("172.18.0.2",key,"203.0.113.2")));
        assertEquals("2001:db8:0:0:0:0:0:1",p.address(request("172.18.0.2",key,"2001:db8::1")));
    }
    @Test void malformedListsHostnamesDuplicateClaimsFallBackToSocket(){
        var p=new PeerAddresses(key,"172.18.0.0/16",true);
        for(String bad:new String[]{"example.com","1.2.3","999.2.3.4","203.0.113.1, 203.0.113.2","::1%eth0"})
            assertEquals("172.18.0.2",p.address(request("172.18.0.2",key,bad)));
        var r=request("172.18.0.2",key,"203.0.113.1");r.addHeader("X-Portfolio-Peer","203.0.113.2");assertEquals("172.18.0.2",p.address(r));
    }
}
