/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ibm.watson.common.service.base.security.MainServletFilter;
import com.ibm.watson.service.base.model.ServiceError;

/**
 * Services utility class to assist in capturing key data points as thread-local variables.
 */
public class ServiceThreadLocal {

	private static final ThreadLocal<Integer> threadInputTextSize = new ThreadLocal<>();
	private static final ThreadLocal<Integer> threadAnnotatorCount= new ThreadLocal<>();
	private static final ThreadLocal<String> threadCorrelationId = new ThreadLocal<>();
	private static final ThreadLocal<String> threadRequestId = new ThreadLocal<>();
	private static final ThreadLocal<String> threadTenantArtifactVersion = new ThreadLocal<>();
	private static final ThreadLocal<String> threadSuperTenantArtifactVersion = new ThreadLocal<>();
	private static final ThreadLocal<Map<String,String>> threadRequestHeaders = new ThreadLocal<>();
	private static final ThreadLocal<Map<String,String>> threadWatsonUserInfoMap = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> threadDebug = new ThreadLocal<>();

	/**
	 * Returns the thread-local variable for count of the number of annotators found within the pipeline request flow.
	 *
	 * @return Annotator count if present or null if not.
	 */
	public static Integer getAnnotatorCount() {
		return threadAnnotatorCount.get();
	}

	/**
	 * Sets the thread-local variable for count of the number of annotators found within the pipeline request flow.
	 *
	 * @param annotatorCount Count of the number of annotators to be processed as part of the pipeline request flow.
	 * @throws ParseException
	 */
	public static void setAnnotatorCount(Integer annotatorCount) throws ParseException {
		threadAnnotatorCount.set(annotatorCount);
	}

	/**
	 * Returns the thread-local variable for request input text size.
	 *
	 * @return 	Input text size if present or null if not.
	 */
	public static Integer getInputTextSize() {
		return threadInputTextSize.get();
	}

	/**
	 * Sets the thread-local variable for request input text size.
	 *
	 * @param inputTextSize Size of the input text being processed.
	 * @throws ParseException
	 */
	public static void setInputTextSize(Integer inputTextSize) throws ParseException {
		threadInputTextSize.set(inputTextSize);
	}

	/**
	 * Gets the thread-local variable for correlation ID.
	 *
	 * @return Correlation ID if present or null if not.
	 */
	public static String getCorrelationId() {
		return threadCorrelationId.get();
	}

	/**
	 * Sets the thread-local variable for correlation ID.
	 *
	 * @param correlationId Correlation ID
	 */
	public static void setCorrelationId(String correlationId) {
		threadCorrelationId.set(correlationId);
	}

	/**
	 * Gets the thread-local variable for request ID.  The request ID is unique for every request and it is
	 * not passed to other services.
	 *
	 * @return Request ID if present or null if not
	 */
	public static String getRequestId() {
		return threadRequestId.get();
	}

	/**
	 * Sets the thread-local variable for request ID.  This should be set to a unique id for every request
	 * and should not be passed in or out of the service through HTTP headers.
	 *
	 * @param requestId Request ID
	 */
	public static void setRequestId(String requestId) {
		threadRequestId.set(requestId);
	}

	/**
	 * Gets the thread local tenant artifact version.  The tenant version is unique for every request.
	 * @return
	 */
	public static String getTenantArtifactVersion() {
		return threadTenantArtifactVersion.get();
	}

	/**
	 * Sets the thread local tenant artifact version.  This will be unique for every request.
	 * @param tenantArtifactVersion
	 */
	public static void setTenantArtifactVersion(String tenantArtifactVersion) {
		threadTenantArtifactVersion.set(tenantArtifactVersion);
	}

	/**
	 * Gets the thread local super tenant artifact version.  This will be unique for every request.
	 * @return
	 */
	public static String getSuperTenantArtifactVersion() {
		return threadSuperTenantArtifactVersion.get();
	}

	/**
	 * Sets the thread local super tenant artifact version.  This will be unique for every request.
	 * @param superTenantArtifactVersion
	 */
	public static void setSuperTenantArtifactVersion(String superTenantArtifactVersion) {
		threadSuperTenantArtifactVersion.set(superTenantArtifactVersion);
	}
	
	/**
	 * Indicates if the debug flag is set for this thread
	 * @return
	 */
	public static Boolean getThreadDebug() {
	  return threadDebug.get();
	}
	
	/**
	 * Sets the debug flag for this thread
	 * @param debug
	 */
	public static void setThreadDebug(Boolean debug) {
	  threadDebug.set(debug);
	}

	/**
	 * Gets the thread-local variable for request headers.
	 *
	 * @return request headers if present or null if not.
	 */
	public static Map<String,String> getRequestHeaders() {
		return threadRequestHeaders.get();
	}

	/**
	 * Sets the thread-local variable for request headers.
	 *
	 * @param requestHeaders Request headers
	 */
	public static void setRequestHeaders(Map<String,String> requestHeaders) {
		threadRequestHeaders.set(requestHeaders);
		if (requestHeaders == null) {
			threadWatsonUserInfoMap.set(null);
		} else {
			Map<String, String> userInfoMap = new HashMap<String, String>();
			String watsonUserInfo = requestHeaders.get(MainServletFilter.HEADER_WATSON_USER_INFO);
			if (watsonUserInfo != null) {
				String[] wuiValues = watsonUserInfo.split(";");
				for (int i = 0; i < wuiValues.length; ++i) {
					String[] nameValue = wuiValues[i].split("=");
					if (nameValue.length != 2) {
						continue;
					}
					userInfoMap.put(nameValue[0], nameValue[1]);
				}
			}
			threadWatsonUserInfoMap.set(userInfoMap);
		}
	}

	/**
	 * Returns a map of the key/value pairs specified in the X-Watson-UserInfo.  These need
	 * to be parsed from the header so it's faster to do it once and cache it in ServiceThreadLocal
	 * @return
	 */
	public static Map<String, String> getWatsonUserInfoMap() {
		return threadWatsonUserInfoMap.get();
	}

	/**
	 * Do the cleanup of the thread-local storage for each of the variables managed within this class.
	 *
	 * @return null if successful, or error if not
	 */
	public static Response doCleanup() {
		try {
			ServiceThreadLocal.setAnnotatorCount(null);
			ServiceThreadLocal.setInputTextSize(null);
			ServiceThreadLocal.setCorrelationId(null);
			ServiceThreadLocal.setRequestId(null);
			ServiceThreadLocal.setRequestHeaders(null);
			ServiceThreadLocal.setTenantArtifactVersion(null);
			ServiceThreadLocal.setSuperTenantArtifactVersion(null);
			ServiceThreadLocal.setThreadDebug(null);
			// TODO Why is parse exception here?
			} catch (ParseException e) {
			  ServiceError se = new ServiceError().setCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).setMessage(Status.INTERNAL_SERVER_ERROR.getReasonPhrase()).setDescription("Exception while processing thread-local cleanup: " + e.toString());
			  return Response.status(se.getCode()).entity(se).build();
		}

		return null;
	}

}
