package com.example.serialposition.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Browsers send an Origin header on POST and DELETE requests even when the page was
 * served by the same host. Behind a proxy or tunnel that terminates HTTPS, the server
 * sees plain http while the browser's Origin says https, so Spring's own same-origin
 * test fails and the request is rejected as cross-origin (HTTP 403).
 *
 * A request whose Origin names the very host it was sent to is not cross-origin, so
 * the header is hidden from the CORS check. Requests from any other origin, such as a
 * page on a different site, are left untouched and still go through the CORS rules.
 */
@Component
public class SameHostOriginFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isSameHost(request.getHeader(HttpHeaders.ORIGIN), request.getHeader(HttpHeaders.HOST))) {
            chain.doFilter(new WithoutOrigin(request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /** True if the Origin's host and port equal the Host header the request was sent to. */
    static boolean isSameHost(String origin, String host) {
        if (origin == null || host == null) {
            return false;
        }
        try {
            String authority = URI.create(origin).getAuthority();
            return authority != null && authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final class WithoutOrigin extends HttpServletRequestWrapper {

        WithoutOrigin(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.ORIGIN.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.ORIGIN.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(HttpHeaders.ORIGIN::equalsIgnoreCase);
            return Collections.enumeration(names);
        }
    }
}
