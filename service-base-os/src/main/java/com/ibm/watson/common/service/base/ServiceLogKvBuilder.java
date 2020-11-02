/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServiceLogKvBuilder {

//	public static final String KV_KEY_API_USER = "api_user";

	// Suffix value with _i or _f to get logstash filter to convert to integer or float.
	public static final String KV_KEY_API_TIME = "api_time";
	public static final String KV_KEY_API_VERB = "api_verb";
	public static final String KV_KEY_API_RC = "api_rc";
	public static final String KV_KEY_API_REQ_SIZE = "api_size_i";
	public static final String KV_KEY_API_REQ_COUNT = "api_count_i";
	public static final String KV_KEY_LOG_SOURCE_CRN = "logSourceCRN";
	public static final String KV_KEY_SAVE_SERVICE_COPY = "saveServiceCopy";

	public static final String KV_KEY_CONCURRENT_EVENT = "conc_event";
	public static final String KV_KEY_CONCURRENT_REQ = "conc_req_i";
	public static final String KV_KEY_CONCURRENT_REJECTS = "conc_rejects_i";
	public static final String KV_KEY_CONCURRENT_BLOCKS = "conc_blocks_i";
	public static final String KV_KEY_HEAP_MAX = "heap_max_i";
	public static final String KV_KEY_HEAP_COMMIT = "heap_commit_i";
	public static final String KV_KEY_HEAP_INUSE = "heap_inuse_i";

    public static final String KV_MESSAGE_DELIMITER = "|";
    Map<String,String> kvMap = new LinkedHashMap<>();

    public ServiceLogKvBuilder() {
    	super();
    }

    public ServiceLogKvBuilder(String key, String value) {
    	addKv(key, value);
    }

    public ServiceLogKvBuilder(Map<String,String> newMap) {
    	kvMap.putAll(newMap);
    }

    public void addKv(String key, String value) {
    	kvMap.put(key, value);
    }

    public void clear() {
    	kvMap.clear();
    }

    @Override
    public String toString() {
    	StringBuffer logString = new StringBuffer(" kv"+KV_MESSAGE_DELIMITER);

    	for(String key : kvMap.keySet() ) {
    		logString.append(key+"="+kvMap.get(key)+" ");
    	}

    	// Add ending delimiter
    	logString.append(KV_MESSAGE_DELIMITER);

    	return logString.toString();
    }
}