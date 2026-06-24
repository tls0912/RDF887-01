package com.czkuo.rdf88701.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Component
public class SecondaryPortFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        int localPort = req.getLocalPort();
        String path = req.getRequestURI();

        // 如果是 /api/stocker2mcs/ 就要求必須用 6001
        if (path.startsWith("/api/stocker2mcs") && localPort != 6001) {
            ((jakarta.servlet.http.HttpServletResponse) response)
                    .sendError(404, "Not Found");
            return;
        }

        chain.doFilter(request, response);
    }
}
