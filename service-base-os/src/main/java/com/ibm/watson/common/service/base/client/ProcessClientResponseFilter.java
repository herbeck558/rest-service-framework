/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base.client;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
public class ProcessClientResponseFilter implements ClientResponseFilter {

//	public ProcessClientRequestFilter() {
//		System.out.println("in clientRequestFilter constructor");
//	}

	@Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
            throws IOException {

            // Nothing to do yet
			// TODO uncomment for this class in ClientUtility when code is added here
	}

}
