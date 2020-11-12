/*
 * (C) Copyright IBM Corp. 2018, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to read the log filter URI parameters from service.properties and build a
 * a list of URI regular expression patterns that should be filtered out for "entry" and "header" logging
 * entries.  This provides a way to reduce the amount of logging that occurs for chatty APIs.  Exit logging
 * will still occur.
 *
 * There are two properties and they are combined into a single pattern list.
 *   log_filter_uri_base_patterns - URI patterns that apply to the base URI APIs such as health-check.  Let this one default.
 *   log_filter_uri_service_patterns - Service-specific URIs
 *
 *   The properties contain a list of regular expressions patterns that are separated with \\, to avoid conflicts
 *   with a simple comma in the regular expression.  The incoming URI is the portion after <context root>/api and will
 *   include a leading slash but not the query parameters.
 */
 // Example:
 //    log_filter_uri_service_patterns=.*/example/hello$\\,.*/example/calc$
 //
public class ServiceLogFilterUri {
	private static final Logger logger = LoggerFactory.getLogger(ServiceLogFilterUri.class.getName());

	// service.property names
	public static final String LOG_FILTER_URI_BASE_PATTERNS = "log_filter_uri_base_patterns";  // Patterns for common service base
	public static final String LOG_FILTER_URI_SERVICE_PATTERNS = "log_filter_uri_service_patterns"; // Patterns for the specific service
	// Property defaults
	public static final String LOG_FILTER_URI_BASE_PATTERNS_DEFAULT = ".*/status/health_check$";
	public static final String LOG_FILTER_URI_SERVICE_PATTERNS_DEFAULT = "";

	private static volatile ServiceLogFilterUri instance;
	private List<Pattern> logFilterUriPatterns = new ArrayList<>(2);


	public static synchronized ServiceLogFilterUri createInstance(Properties serviceProperties) {
		if(instance != null) {
			throw new IllegalStateException("Cannot create ServiceLogFilterUri instance more that once.");
		}

		instance = new ServiceLogFilterUri(serviceProperties);
		return instance;
	}

	public static ServiceLogFilterUri getInstance() {
		return instance;
	}

	/**
	 * Create ServiceLogFilterUri singleton instance
	 * @param serviceProperties Service properties file
	 */
	private ServiceLogFilterUri(Properties serviceProperties) {

		// Process log filter base patterns property
		try {
			String logFilterUriBasePatterns = serviceProperties.getProperty(LOG_FILTER_URI_BASE_PATTERNS, LOG_FILTER_URI_BASE_PATTERNS_DEFAULT);
			logger.info("Property "+LOG_FILTER_URI_BASE_PATTERNS+"="+logFilterUriBasePatterns);
			if(!logFilterUriBasePatterns.isEmpty()) {
				String[] filterArray = logFilterUriBasePatterns.split("\\\\,"); // delimiter is \\, in a properties file
				for(String item : filterArray) {
					item = item.trim();
					if(!item.isEmpty()) {
						Pattern regex = Pattern.compile(item);
						logFilterUriPatterns.add(regex);
					}
					else {
						logger.warn("Empty pattern entry in "+LOG_FILTER_URI_BASE_PATTERNS+" property.  List="+logFilterUriBasePatterns);
					}
				}
			}
		}
		catch(PatternSyntaxException e) {
			logger.error("Invalid log filter URI base pattern", e);
			throw e;
		}

		// Process log filter service patterns property
		try {
			String logFilterUriServicePatterns = serviceProperties.getProperty(LOG_FILTER_URI_SERVICE_PATTERNS, LOG_FILTER_URI_SERVICE_PATTERNS_DEFAULT);
			logger.info("Property "+LOG_FILTER_URI_SERVICE_PATTERNS+"="+logFilterUriServicePatterns);
			if(!logFilterUriServicePatterns.isEmpty()) {
				String[] filterArray = logFilterUriServicePatterns.split("\\\\,"); // delimiter is \\, in a properties file
				for(String item : filterArray) {
					item = item.trim();
					if(!item.isEmpty()) {
						Pattern regex = Pattern.compile(item);
						logFilterUriPatterns.add(regex);
					}
					else {
						logger.warn("Empty pattern entry in "+LOG_FILTER_URI_SERVICE_PATTERNS+" property.  List="+logFilterUriServicePatterns);
					}
				}
			}
		}
		catch(PatternSyntaxException e) {
			logger.error("Invalid log filter URI service pattern", e);
			throw e;
		}

// TODO check if failed
	}

	/**
	 * Get log filter URI pattern list
	 * @return List of log filters
	 */
	public List<Pattern> getLogFilterPatterns() {
		return logFilterUriPatterns;
	}

}
