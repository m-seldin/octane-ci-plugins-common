/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package com.hp.octane.integrations.testhelpers;

import com.hp.octane.integrations.utils.CIPluginSDKUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Each Octane Shared Space Endpoint simulator instance will function as an isolated context for tests targeting specific shared space
 * There can be unlimited number of such an endpoints
 * Each instance is thread and scope safe
 */

public class OctaneSPEndpointSimulator extends AbstractHandler {
	private static final Logger logger = LogManager.getLogger(OctaneSPEndpointSimulator.class);

	//  simulator's factory static content
	//
	private static Server server;
	private static HandlerCollection handlers;
	private static int DEFAULT_PORT = 3333;
	private static Integer selectedPort;
	private static final Map<String, OctaneSPEndpointSimulator> serverSimulators = new LinkedHashMap<>();

	/**
	 * Entry point to obtain Octane Server Simulator dedicated instance
	 *
	 * @param sharedSpaceId shared space ID will define uniqueness of instance
	 * @return initialize Octane Server Simulator
	 */
	synchronized public static OctaneSPEndpointSimulator addInstance(String sharedSpaceId) {
		if (sharedSpaceId == null || sharedSpaceId.isEmpty()) {
			throw new IllegalArgumentException("shared space ID MUST NOT be null nor empty");
		}

		if (server == null) {
			startServer();
		}
		return serverSimulators.computeIfAbsent(sharedSpaceId, OctaneSPEndpointSimulator::new);
	}

	/**
	 * Entry point to remove Server Simulator instance
	 * it is HIGHLY advised to clean up instances as a best practice, although there should be no harm if all the instances left intact to the end of the test suite run
	 *
	 * @param sharedSpaceId shared space ID identifier of the needed simulator's instance
	 */
	synchronized public static void removeInstance(String sharedSpaceId) {
		if (sharedSpaceId == null || sharedSpaceId.isEmpty()) {
			throw new IllegalArgumentException("shared space ID MUST NOT be null nor empty");
		}

		Handler ossAsHandler = serverSimulators.get(sharedSpaceId);
		if (ossAsHandler != null) {
			ossAsHandler.destroy();
			handlers.removeHandler(ossAsHandler);
			serverSimulators.remove(sharedSpaceId);
		}
	}

	/**
	 * Despite of the fact that different instances will simulate scoped contexts, the actual Jetty server handling all requests will be one
	 * This method returns its actual PORT
	 *
	 * @return effectively selected server port
	 */
	synchronized public static int getUnderlyingServerPort() {
		if (selectedPort == null) {
			startServer();
		}
		return selectedPort;
	}

	private static void startServer() {
		String rawPort = System.getProperty("octane.server.simulator.port");
		server = new Server(rawPort == null ? (selectedPort = DEFAULT_PORT) : (selectedPort = Integer.parseInt(rawPort)));
		try {
			handlers = new HandlerCollection(true);
			server.setHandler(handlers);
			server.start();
			logger.info("SUCCESSFULLY started, listening on port " + selectedPort);
		} catch (Exception e) {
			throw new RuntimeException("failed to start embedded Jetty", e);
		}
	}

	//  particular simulator instance's logic
	//  each instance will add its own request handler (self), which will work in a specific shared space context
	//
	private final String sp;
	private final Pattern signInApiPattern = Pattern.compile("/authentication/sign_in");
	private final Map<String, Consumer<Request>> apiHandlersRegistry = new LinkedHashMap<>();

	private OctaneSPEndpointSimulator(String sp) {
		this.sp = sp;
		handlers.addHandler(this);
	}

	public String getSharedSpaceId() {
		return sp;
	}

	@Override
	public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
		if (request.isHandled()) {
			return;
		}

		if (signInApiPattern.matcher(s).matches()) {
			signIn(request);
			return;
		}

		if (!authenticate(request)) {
			return;
		}

		if (!s.startsWith("/api/shared_spaces/" + sp + "/") && !s.startsWith("/internal-api/shared_spaces/" + sp + "/")) {
			return;
		}

		apiHandlersRegistry.keySet().stream()
				.filter(p -> Pattern.compile(p).matcher(s).matches())
				.findFirst()
				.ifPresent(apiPattern -> apiHandlersRegistry.get(apiPattern).accept(request));

		if (!request.isHandled()) {
			request.getResponse().setStatus(HttpStatus.SC_NOT_FOUND);
			request.setHandled(true);
		}
	}

	public void installApiHandler(String pattern, Consumer<Request> apiHandler) {
		if (apiHandlersRegistry.containsKey(pattern)) {
			throw new IllegalArgumentException("api handler for '" + pattern + "' already installed");
		}
		apiHandlersRegistry.put(pattern, apiHandler);
	}

	public void removeApiHandler(String pattern) {
		apiHandlersRegistry.remove(pattern);
	}

	private void signIn(Request request) throws IOException {
		String body = isToString(request.getInputStream());
		Map json = CIPluginSDKUtils.getObjectMapper().readValue(body, Map.class);
		String client = (String) json.get("client_id");
		String secret = (String) json.get("client_secret");
		Cookie securityCookie = new Cookie("LWSSO_COOKIE_KEY", client + ":" + secret);
		securityCookie.setHttpOnly(true);
		securityCookie.setDomain(".localhost");
		request.getResponse().addCookie(securityCookie);
		request.setHandled(true);
	}

	private boolean authenticate(Request request) {
		for (Cookie cookie : request.getCookies()) {
			if ("LWSSO_COOKIE_KEY".equals(cookie.getName())) {
				return true;
			}
		}
		request.getResponse().setStatus(HttpStatus.SC_UNAUTHORIZED);
		request.setHandled(true);
		return false;
	}

	private String isToString(InputStream is) throws IOException {
		byte[] buffer = new byte[4096];
		int readLen;
		StringBuilder result = new StringBuilder();
		while ((readLen = is.read(buffer, 0, buffer.length)) > 0) result.append(new String(buffer, 0, readLen));
		return result.toString();
	}
}
