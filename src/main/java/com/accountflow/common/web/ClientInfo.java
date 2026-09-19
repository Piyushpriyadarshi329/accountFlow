package com.accountflow.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client attributes recorded against security events.
 *
 * <p>X-Forwarded-For is only trustworthy behind a proxy that overwrites it.
 * When deploying behind a load balancer, set
 * {@code server.forward-headers-strategy=framework} and let Spring parse it
 * rather than reading the raw header here.
 */
public final class ClientInfo {

	private ClientInfo() {
	}

	public static String ipAddress(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	public static String userAgent(HttpServletRequest request) {
		String agent = request.getHeader("User-Agent");
		return (agent != null && agent.length() > 256) ? agent.substring(0, 256) : agent;
	}

}
