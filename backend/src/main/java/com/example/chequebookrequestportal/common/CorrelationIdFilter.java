package com.example.chequebookrequestportal.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader("X-Correlation-ID");
        if (id == null || id.isBlank())
            id = UUID.randomUUID().toString();
        req.setAttribute("correlationId", id);
        res.setHeader("X-Correlation-ID", id);
        chain.doFilter(req, res);
    }
}
