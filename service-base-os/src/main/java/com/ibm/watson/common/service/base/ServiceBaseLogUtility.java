/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceBaseLogUtility {
	private static final Logger logger = LoggerFactory.getLogger(ServiceBaseLogUtility.class.getName());

	public final String SEND_OPERATIONAL_LOGS_PROPERTY = "send_operational_logs";

	private static volatile ServiceBaseLogUtility instance;
	protected List<Pattern> logFilterUriPatterns;
	protected boolean sendOperationalLogs = false;

	// Hide constructor for singleton pattern
	private ServiceBaseLogUtility() {
		try {
			Properties serviceProperties = ServiceContext.getInstance().getServiceProperties();

			// Get send operational log property
			String sendOperationalLogsValue = serviceProperties.getProperty(SEND_OPERATIONAL_LOGS_PROPERTY);
			if (sendOperationalLogsValue != null) {
				sendOperationalLogs = Boolean.parseBoolean(sendOperationalLogsValue);
			}

			logFilterUriPatterns = ServiceLogFilterUri.createInstance(serviceProperties).getLogFilterPatterns();
		}
		catch(Throwable e) {
			// Log error and fail initialization.
			logger.error("Exception during ServiceBaseLogUtility initialization.  Service marked with initialization failed", e);
			ServiceContext.getInstance().setInitializationFailed(true);
		}
	}

	/**
	 * Get singleton instance of the Log Utility
	 * @return
	 */
	public static synchronized ServiceBaseLogUtility getInstance() {
		if( instance == null ) {
			instance = new ServiceBaseLogUtility();
		}

		return instance;
	}

	public void logRequest(HttpServletRequest request) {
		ServiceLogKvBuilder messageKv = new ServiceLogKvBuilder();

		// Build message key/values for logstash
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_VERB, getRequestMethod(request));
		//Log the CRN on entry
		addCRNToLogMessage(messageKv);
		logger.info(">" + getRequestMethod(request) + " " + getRequestURL(request) + messageKv);
	}

	public void logResponse(HttpServletRequest request, HttpServletResponse response, double elapsedSecs) {
		HttpServletResponse servletResponse = (HttpServletResponse) response;
		ServiceLogKvBuilder messageKv = new ServiceLogKvBuilder();

		// Build message key/values for logstash
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_VERB, getRequestMethod(request));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_TIME, Double.toString(elapsedSecs));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_RC, Integer.toString(servletResponse.getStatus()));
		if (ServiceThreadLocal.getInputTextSize() != null) {
			messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_REQ_SIZE, Integer.toString(ServiceThreadLocal.getInputTextSize()));
		}
		if (ServiceThreadLocal.getAnnotatorCount() != null) {
			messageKv.addKv(ServiceLogKvBuilder.KV_KEY_API_REQ_COUNT, Integer.toString(ServiceThreadLocal.getAnnotatorCount()));
		}
	  //Log the CRN on exit
		addCRNToLogMessage(messageKv);
		logger.info("<" + getRequestMethod(request) + " " + getRequestURL(request) + messageKv);
	}

	/**
	 * Log request headers
	 * @param request
	 */
	public void logRequestHeader(HttpServletRequest request) {
        StringBuffer headerBuffer = new StringBuffer();
        Enumeration<String> headerList = request.getHeaderNames();
        if(headerList != null) {
        	// For each header name
        	while(headerList.hasMoreElements()) {
        		String headerName = headerList.nextElement();
        		if(headerName.equalsIgnoreCase("cookie")) {
        		    continue;
			} else if(headerName.equalsIgnoreCase("Authorization")) {
        		    headerBuffer.append(headerName).append(":\"*****\" ");
        		    continue;
        		}
        		
        		headerBuffer.append(headerName).append(":\"");

        		// For each value within a header
        		Enumeration<String> valueList = request.getHeaders(headerName);
        		if(valueList != null) {
	        		while(valueList.hasMoreElements()) {
	        			String headerValue = valueList.nextElement();
	        			headerBuffer.append(headerValue);
	        			if(valueList.hasMoreElements()) {
	        				headerBuffer.append(", ");
	        			}
	        		}
        		}
        		headerBuffer.append("\" ");
        	}
        }
        logger.info("Req Headers="+headerBuffer.toString());
	}

	/**
	 * Adds the CRN (Cloud Resource Name) to the log message if one exists in the header.
	 * @param messageKv
	 */
	protected void addCRNToLogMessage(ServiceLogKvBuilder messageKv) {
		//If we have a CRN, add it to the log
		Map<String, String> userInfoMap = ServiceThreadLocal.getWatsonUserInfoMap();
		if (sendOperationalLogs && userInfoMap != null && userInfoMap.containsKey("bluemix-crn")) {
			messageKv.addKv(ServiceLogKvBuilder.KV_KEY_LOG_SOURCE_CRN, userInfoMap.get("bluemix-crn"));
			//This must always be set to true
			messageKv.addKv(ServiceLogKvBuilder.KV_KEY_SAVE_SERVICE_COPY, "true");
		}
	}

     /**
     * Returns the HTTP method name associated with the specified servlet request.
     */
    protected String getRequestMethod(ServletRequest request) {
        String method = null;

        if (request instanceof HttpServletRequest) {
            method = ((HttpServletRequest) request).getMethod();
        }
        return (method != null ? method : "<unknown>");
    }

    /**
     * Returns the full request URL (i.e. http://host:port/a/path?queryString)
     * associated with the specified servlet request.
     */
    protected String getRequestURL(ServletRequest request) {
        String url = null;

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            StringBuffer sb = httpRequest.getRequestURL();
            String queryString = httpRequest.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                sb.append("?");
                sb.append(queryString);
            }
            url = sb.toString();
        }
        return (url != null ? url : "<unknown>");
    }

    /**
     * Determine if URI should be filtered for entry logging
     * @param uri
     * @return
     */
    public boolean filterLog(String uri) {
    	boolean found = false;

    	if( logFilterUriPatterns != null ) {
        	// Look for matching uri's
	    	for(Pattern p : logFilterUriPatterns) {
	    		if(p.matcher(uri).matches()) {
	    			found = true;
	    			break;
	    		}
	    	}
    	}

    	return found;
    }

}
