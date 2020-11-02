/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;


/**
 * Class required in order to pass values from ServiceThreadLocal thread local to child threads
 *
 *
 *
		Thread t = new Thread(new Runnable() {
			ServiceThreadLocalCopier tm = new ServiceThreadLocalCopier();
			@Override
			public void run() {
				tm.recreateTenantManager();
				System.out.println("child tenant=" + TenantManager.getTenant().getTenantId());
			}
		});
		t.start();
 *
 */
public class ServiceThreadLocalCopier {

	private Map<String,String> mdcMap;
	private Integer inputTextSize;
	private Integer annotatorCount;
	private static String correlationId;
	private static String requestId;
	private final Map<String,String> requestHeaders = new HashMap<String,String>();

	public ServiceThreadLocalCopier (){
		mdcMap = MDC.getCopyOfContextMap();
		annotatorCount = ServiceThreadLocal.getAnnotatorCount();
		inputTextSize = ServiceThreadLocal.getInputTextSize();
		correlationId = ServiceThreadLocal.getCorrelationId();
		requestId = ServiceThreadLocal.getRequestId();
		Map<String, String> headers = ServiceThreadLocal.getRequestHeaders();

		if ( headers != null ) {
			for(String header : headers.keySet()) {
				requestHeaders.put(header,  headers.get(header));
			}
		}
	}

	public void recreateServiceThreadLocal() throws ParseException {
		ServiceThreadLocal.setAnnotatorCount(annotatorCount);
		ServiceThreadLocal.setCorrelationId(correlationId);
		ServiceThreadLocal.setRequestId(requestId);
		ServiceThreadLocal.setInputTextSize(inputTextSize);
		ServiceThreadLocal.setRequestHeaders(requestHeaders);

		// Add parent thread's mdc map copy to this thread
		MDC.setContextMap(mdcMap);
	}
}

