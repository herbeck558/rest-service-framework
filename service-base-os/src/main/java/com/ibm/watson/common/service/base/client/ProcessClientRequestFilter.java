/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base.client;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.watson.common.service.base.ServiceBaseUtility;
import com.ibm.watson.common.service.base.ServiceThreadLocal;
import com.ibm.watson.common.service.base.security.MainServletFilter;

@Provider
public class ProcessClientRequestFilter implements ClientRequestFilter {
	private static final Logger logger = LoggerFactory.getLogger(ProcessClientRequestFilter.class.getName());

//	public ProcessClientRequestFilter() {
//		System.out.println("in clientRequestFilter constructor");
//	}

	@Override
    public void filter(ClientRequestContext requestContext) throws IOException {

		// Get correlation ID and add it in the outgoing header
		String correlationId = ServiceBaseUtility.getCorrelationId();

		if(correlationId != null && !correlationId.isEmpty()) {
			// Check if correlation ID is already present in outgoing header, if not, set it to this
			// thread's correlation ID.
			// TODO Directly using the CORRELATION_ID_DEFAULT_HEADER is temporary, need to create a header utility class
			MultivaluedMap<String,Object> headerMap = requestContext.getHeaders();
			if(!headerMap.containsKey(MainServletFilter.CORRELATION_ID_DEFAULT_HEADER)) {
				headerMap.putSingle(MainServletFilter.CORRELATION_ID_DEFAULT_HEADER, correlationId);
			}
		}
		
		//Set the debug flag into the outgoing headers
		Boolean debug = ServiceThreadLocal.getThreadDebug();
		if (debug != null) {
		  MultivaluedMap<String,Object> outgoingHeaders = requestContext.getHeaders();
      outgoingHeaders.putSingle(MainServletFilter.HEADER_DEBUG, debug.toString());
		}

		//Get the the tenant and super tenant versions and set them into the appropriate outgoing headers
		String tenantVersion = ServiceThreadLocal.getTenantArtifactVersion();
		if (tenantVersion != null && !tenantVersion.trim().isEmpty()) {
			MultivaluedMap<String,Object> outgoingHeaders = requestContext.getHeaders();
			outgoingHeaders.putSingle(MainServletFilter.HEADER_TENANT_ARTIFACT_VERSION, tenantVersion);
		}

		String superTenantVersion = ServiceThreadLocal.getSuperTenantArtifactVersion();
		if (superTenantVersion != null && !superTenantVersion.trim().isEmpty()) {
			MultivaluedMap<String,Object> outgoingHeaders = requestContext.getHeaders();
			outgoingHeaders.putSingle(MainServletFilter.HEADER_SUPER_TENANT_ARTIFACT_VERSION, superTenantVersion);
		}

		// Propagate specific headers to outgoing client request if present.
		Map<String,String> headersToPropagate = ServiceThreadLocal.getRequestHeaders();
		if(headersToPropagate !=null) {
			MultivaluedMap<String,Object> outgoingHeaders = requestContext.getHeaders();
			for(String headerName : headersToPropagate.keySet()) {
				// Only add if not already in outgoing request
				if(!outgoingHeaders.containsKey(headerName)) {
					String headerValue = headersToPropagate.get(headerName);
					if(headerValue != null && !headerValue.isEmpty()) {
						outgoingHeaders.putSingle(headerName, headerValue);
						if(logger.isDebugEnabled()) logger.debug("Adding header to client request: "+headerName+": "+headerValue);
					}
				}
			}
		}
	}
}
