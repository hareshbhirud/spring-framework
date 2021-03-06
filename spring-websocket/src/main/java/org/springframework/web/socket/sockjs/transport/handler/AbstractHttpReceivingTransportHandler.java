/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.sockjs.transport.handler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.sockjs.SockJsException;
import org.springframework.web.socket.sockjs.transport.TransportHandler;
import org.springframework.web.socket.sockjs.transport.session.AbstractHttpSockJsSession;

import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Base class for HTTP transport handlers that receive messages via HTTP POST.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractHttpReceivingTransportHandler
		extends TransportHandlerSupport implements TransportHandler {


	@Override
	public final void handleRequest(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, WebSocketSession wsSession) throws SockJsException {

		// TODO: check "Sec-WebSocket-Protocol" header
		// https://github.com/sockjs/sockjs-client/issues/130

		Assert.notNull(wsSession, "No session");
		AbstractHttpSockJsSession sockJsSession = (AbstractHttpSockJsSession) wsSession;

		handleRequestInternal(request, response, wsHandler, sockJsSession);
	}

	protected void handleRequestInternal(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, AbstractHttpSockJsSession sockJsSession) throws SockJsException {

		String[] messages = null;
		try {
			messages = readMessages(request);
		}
		catch (JsonMappingException ex) {
			logger.error("Failed to read message: " + ex.getMessage());
			handleReadError(response, "Payload expected.", sockJsSession.getId());
			return;
		}
		catch (IOException ex) {
			logger.error("Failed to read message: " + ex.getMessage());
			handleReadError(response, "Broken JSON encoding.", sockJsSession.getId());
			return;
		}
		catch (Throwable t) {
			logger.error("Failed to read message: " + t.getMessage());
			handleReadError(response, "Failed to read message(s)", sockJsSession.getId());
			return;
		}

		if (messages == null) {
			handleReadError(response, "Payload expected.", sockJsSession.getId());
			return;
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Received message(s): " + Arrays.asList(messages));
		}

		response.setStatusCode(getResponseStatus());
		response.getHeaders().setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")));

		sockJsSession.delegateMessages(messages);
	}

	private void handleReadError(ServerHttpResponse resp, String error, String sessionId) {
		try {
			resp.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
			resp.getBody().write(error.getBytes("UTF-8"));
		}
		catch (IOException ex) {
			throw new SockJsException("Failed to send error: " + error, sessionId, ex);
		}
	}

	protected abstract String[] readMessages(ServerHttpRequest request) throws IOException;

	protected abstract HttpStatus getResponseStatus();

}
