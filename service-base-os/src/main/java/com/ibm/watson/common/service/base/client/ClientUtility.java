/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.client;

import javax.ws.rs.client.ClientBuilder;

/**
 * Utility methods for jax-rs REST-based clients.
 *
 */
public class ClientUtility {

    public final static String RECEIVE_TIMEOUT_PROPERTY = "com.ibm.ws.jaxrs.client.receive.timeout";
    public final static String CONNECT_TIMEOUT_PROPERTY = "com.ibm.ws.jaxrs.client.connection.timeout";

	/**
	 * Wrapper the jax-rs ClientBuilder.newClientBuilder() with ours so we can set a global filter
	 * on it to manage the passing of current thread local context to new
	 * client requests.
	 * @return
	 */
	public static ClientBuilder getClientBuilder() {

		ClientBuilder builder = ClientBuilder.newBuilder();

		// Add common client filters and properties here
		builder.register(new ProcessClientRequestFilter());
		// TODO uncomment the following line once code is added to the response filter
		// builder.register(new ProcessClientResponseFilter());

		return builder;

	}

}
