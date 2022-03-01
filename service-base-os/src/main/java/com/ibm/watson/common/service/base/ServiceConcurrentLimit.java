/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to limit the number of concurrent incoming requests to help protect the process from
 * getting overrun with requests that could run the JVM out of memory or other resources.
 * The limit can be applied to a subset of incoming request URIs using a list of URI regular expression patterns.
 * There are two limiting thresholds.  The first parameter is the number of concurrent requests that are allowed before
 * any more threads will get blocked.  The second parameter is the total number of incoming requests that
 * are allowed to run (both concurrent and blocked) before a 503 (not available) status is returned.
 *
 *  Here are the relevant properties.  They are read from the service.properties file.
 *
 *  com_ibm_watson_health_common_concurrent_reject_threshold -   Total number of incoming requests (that match the pattern) that are accepted.
 *                                  Any additional requests will be returned with a 503 error.  A valued of zero
 *                                  will disable all concurrent limiting function.  The default is 0.
 *  com_ibm_watson_health_common_concurrent_blocking_threshold - The total number of incoming requests that are allowed to run in parallel before
 *                                  blocking will occur. A value of zero will disable any blocking.  This
 *                                  value must be less than com_ibm_watson_health_common_concurrent_reject_threshold.
 *                                  The default is 0.
 *  com_ibm_watson_health_common_concurrent_uri_pattern_list   - A list of one or more regular expression patterns to match incoming
 *  								REST URIs that will be put under concurrent limit control.  The
 *  								patterns are separated with \\, to avoid conflicts with a simple comma
 *  								in the regular expression.
 */
public class ServiceConcurrentLimit {
	private static final Logger logger = LoggerFactory.getLogger(ServiceConcurrentLimit.class.getName());

	protected static final long MEGABYTES = 1024*1024;

	private static volatile ServiceConcurrentLimit instance;

	public static final String CONTAINER_CPU_LIMIT = "com_ibm_watson_health_services_container_cpu_limit";

	public static final String CONCURRENT_REJECT_THRESHOLD = "com_ibm_watson_health_common_concurrent_reject_threshold";
	public static final String CONCURRENT_BLOCKING_THRESHOLD = "com_ibm_watson_health_common_concurrent_blocking_threshold"; // Must be <= com_ibm_watson_health_common_concurrent_reject_threshold
	public static final String CONCURRENT_URL_PATTERN_LIST = "com_ibm_watson_health_common_concurrent_uri_pattern_list";

	private int containerCpuCores;
	
	private Semaphore concurrentBlockingSema;
	private volatile Object concurrentRequestsLock = new Object();  // Used to synchronize the counters below
	private volatile int concurrentRequests;
	private volatile int maxConcurrentRequests;
	private volatile long totalBlockedRequests;
	private volatile long totalRejectedRequests;

	private int concurrentRejectThreshold;
	private int concurrentBlockingThreshold;
	private List<Pattern> concurrentUriPatternList = new ArrayList<Pattern>();
	private boolean concurrentThresholdEnabled;
	private boolean concurrentBlockingThresholdEnabled;


	public static synchronized ServiceConcurrentLimit createInstance(Properties serviceProperties) {
		if(instance != null) {
			throw new IllegalStateException("Cannot create ServiceConcurrentLimit instance more that once.");
		}

		instance = new ServiceConcurrentLimit(serviceProperties);
		return instance;
	}

	public static ServiceConcurrentLimit getInstance() {
		return instance;
	}

	private ServiceConcurrentLimit(Properties serviceProperties) {
		// Load concurrent reject threshold service property
		String concurrentRequestsMaxProperty = serviceProperties.getProperty(CONCURRENT_REJECT_THRESHOLD, "0");
		try {
			concurrentRejectThreshold = Integer.valueOf(concurrentRequestsMaxProperty);
			concurrentThresholdEnabled = concurrentRejectThreshold > 0;
			logger.info("Max concurrent requestes="+concurrentRejectThreshold);
		}
		catch(NumberFormatException e) {
			logger.error("Format exception for service property \""+CONCURRENT_REJECT_THRESHOLD+"\", value="+concurrentRequestsMaxProperty);
			throw new IllegalArgumentException("Format exception for service property CONCURRENT_REQUESTS_MAX, value="+
					concurrentRequestsMaxProperty, e);
		}

		// Bail out if concurrent limit feature is not enabled
		if(!concurrentThresholdEnabled) {
			return;
		}

		// Load Container CPU limit value (could be in millicores with an 'm' suffix)
		String containerCpuLimitProperty = serviceProperties.getProperty(CONTAINER_CPU_LIMIT);
		try {
			if(containerCpuLimitProperty != null && !containerCpuLimitProperty.trim().isEmpty()) {
				containerCpuLimitProperty = containerCpuLimitProperty.trim();
				logger.info("Container CPU limit ("+CONTAINER_CPU_LIMIT+"): "+containerCpuLimitProperty);
				if(containerCpuLimitProperty.endsWith("m")) {
					// Convert from millicore units to an integer
					containerCpuCores = Math.round(Float.valueOf(containerCpuLimitProperty.substring(0, containerCpuLimitProperty.length()-1))/1000);
				}
				else {
					containerCpuCores = Math.round(Float.valueOf(containerCpuLimitProperty));
				}
				if(containerCpuCores <= 0) {
					containerCpuCores = 1;
				}
				logger.info("Container CPU cores="+containerCpuCores);
			}
		}
		catch(NumberFormatException e) {
			logger.error("Format exception for service property \""+CONTAINER_CPU_LIMIT+"\", value="+containerCpuLimitProperty);
			throw new IllegalArgumentException("Format exception for service property CONTAINER_CPU_LIMIT, value="+
					containerCpuLimitProperty, e);
		}

		// Load concurrent blocking threshold service property
		String concurretBlockedMaxProperty = serviceProperties.getProperty(CONCURRENT_BLOCKING_THRESHOLD, "0");
		try {
			concurrentBlockingThreshold = Integer.valueOf(concurretBlockedMaxProperty);
			// Check for max blocked max <= concurrent
			if(concurrentBlockingThreshold >= concurrentRejectThreshold) {
				logger.error("Invalid value for service property \""+CONCURRENT_BLOCKING_THRESHOLD+"\", value="+
						concurrentBlockingThreshold+", must be < "+CONCURRENT_REJECT_THRESHOLD+", value="+concurrentRejectThreshold);
				throw new IllegalArgumentException("Invalid value for service property \""+CONCURRENT_BLOCKING_THRESHOLD+"\", value="+
						concurrentBlockingThreshold+", must be < \""+CONCURRENT_REJECT_THRESHOLD+"\", value="+concurrentRejectThreshold);
			}
			
			// Override concurrentBlockingThreshold if containerCpuCores was specified and is less than concurrentBlockingThreshold
			if( (containerCpuCores > 0) && (containerCpuCores < concurrentBlockingThreshold) ) {
				concurrentBlockingThreshold = containerCpuCores;
				logger.info("Concurrent blocking threshold is being limited by container CPU limit, value="+concurrentBlockingThreshold);
			}
			
			if((concurrentBlockingThreshold > 0) && (concurrentBlockingThreshold != concurrentRejectThreshold)) {
				concurrentBlockingThresholdEnabled = true;
				concurrentBlockingSema = new Semaphore(concurrentBlockingThreshold, true);
			}
			else {
				concurrentBlockingThresholdEnabled = false;
			}
			logger.info("Max concurrent blocked="+concurrentBlockingThreshold);
		}
		catch(NumberFormatException e) {
			logger.error("Format exception for service property \""+CONCURRENT_REJECT_THRESHOLD+"\", value="+concurrentRequestsMaxProperty);
			throw new IllegalArgumentException("Format exception for service property CONCURRENT_REQUESTS_MAX, value="+
					concurrentRequestsMaxProperty, e);
		}

		// Process concurrent URL prefix list
		try {
			String uriList = serviceProperties.getProperty(CONCURRENT_URL_PATTERN_LIST, "");
			logger.info("Concurrent URI pattern property: "+uriList);
			if(!uriList.isEmpty()) {
				String[] uriArray = uriList.split("\\\\,"); // delimiter is \\, in a properties file
				for(String item : uriArray) {
					item = item.trim();
					if(!item.isEmpty()) {
						Pattern regex = Pattern.compile(item);
						concurrentUriPatternList.add(regex);
					}
					else {
						logger.warn("Empty pattern entry in "+CONCURRENT_URL_PATTERN_LIST+" property.  List="+uriList);
					}
				}
			}

			if(concurrentUriPatternList.size() == 0) {
				logger.warn("Concurrent URI pattern list is empty");
			}
		}
		catch(PatternSyntaxException e) {
			logger.error("Invalid concurrent URI pattern: "+e);
			throw e;
		}
	}

	public int getConcurrentRequests() {
		// TODO Do we need to synchronize on a read for these volatile type members?
		return concurrentRequests;
	}

	public int getMaxConcurrentRequests() {
		return maxConcurrentRequests;
	}

	public long getTotalRejectedRequests() {
		return totalRejectedRequests;
	}

	public long getTotalBlockedRequests() {
		return totalBlockedRequests;
	}

	public int getConcurrentRejectThreshold() {
		return concurrentRejectThreshold;
	}

	public int getConcurrentBlockingThreshold() {
		return concurrentBlockingThreshold;
	}

	public boolean isConcurrentThresholdEnabled() {
		return concurrentThresholdEnabled;
	}

	public boolean shouldLimitRequest(String uri) {
		boolean found=false;
		if(concurrentThresholdEnabled) {
			for(Pattern item : concurrentUriPatternList) {
				if(item.matcher(uri).matches()) {
					found = true;
					break;
				}
			}
		}
		return found;
	}

	public boolean acquireRequest() {
		int maxAllowed = concurrentRejectThreshold;
		boolean acquired = true;
		int localConcurrentRequests = 0;

		// Keep track of concurrent requests if enabled
		if(concurrentThresholdEnabled) {
			synchronized(concurrentRequestsLock) {
				if(concurrentRequests >= maxAllowed) {
					//System.out.println("limit hit");
					totalRejectedRequests++;
					logConcurrentEvent("reject", concurrentRequests+1);
					acquired = false;
				}
				else {
					concurrentRequests++;
					localConcurrentRequests = concurrentRequests; // Save count for next step since it could change
					if(concurrentRequests > maxConcurrentRequests) {
						maxConcurrentRequests = concurrentRequests;
					}
				}
			}

			if(acquired && concurrentBlockingThresholdEnabled) {
				// Block if semaphore limit is met
				try {
					if(concurrentBlockingSema.availablePermits() <= 0){
						// Since we can't lock on the concurrentBlockingSema, this count might be off slightly
						// if the permit count changes before acquire is called.
						totalBlockedRequests++;
						logConcurrentEvent("block", localConcurrentRequests);
						//System.out.println("blocking, current req="+concurrentRequests+", total blocked="+totalBlockedRequests);
					}
					concurrentBlockingSema.acquire(); // Will block if limit hit
				}
				catch(InterruptedException e) {
					// Just continue
				}
			}
		}

		return acquired;
	}

	public void releaseRequest() {
		if(concurrentThresholdEnabled) {
			if(concurrentBlockingThresholdEnabled) {
				concurrentBlockingSema.release();
			}
			synchronized(concurrentRequestsLock) {
				// TODO stop from going negative?
				concurrentRequests--;
			}
		}
	}

	/**
	 * Log a concurrent event
	 * This method makes a log entry when there is a concurrent limit or blocking event.
	 * @param type Type of event, limit or block
	 * @param localConcurrentRequests Concurrent request count at time of log call
	 */
	protected void logConcurrentEvent(String type, int localConcurrentRequests) {

		Runtime rt = Runtime.getRuntime();
		// TODO Could these values change between calls?
		long heapMax = rt.maxMemory()/MEGABYTES;
		long heapCommit = rt.totalMemory()/MEGABYTES;
		long heapInUse = (rt.totalMemory()-rt.freeMemory()) / MEGABYTES;

		// Build message key/values for logstash
		ServiceLogKvBuilder messageKv = new ServiceLogKvBuilder();
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_CONCURRENT_EVENT, type);
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_CONCURRENT_REQ, Integer.toString(localConcurrentRequests));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_CONCURRENT_BLOCKS, Long.toString(totalBlockedRequests));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_CONCURRENT_REJECTS, Long.toString(totalRejectedRequests));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_HEAP_MAX, Long.toString(heapMax));;
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_HEAP_COMMIT, Long.toString(heapCommit));
		messageKv.addKv(ServiceLogKvBuilder.KV_KEY_HEAP_INUSE, Long.toString(heapInUse));

		logger.info("Concurrent event: "+type+messageKv);
	}

}
