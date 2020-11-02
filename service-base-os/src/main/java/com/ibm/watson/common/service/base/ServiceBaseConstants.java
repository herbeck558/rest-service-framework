/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

/**
 * Global service constants
 *
 */
public class ServiceBaseConstants {

	public static final String SERVICE_API_ROOT = "api";

	// Build-time service property keys. These are passed in on the service.properties file
	public static final String BUILD_VERSION = "build.version";
	public static final String BUILD_OS = "build.os";
	public static final String BUILD_PATH = "build.path";
	public static final String BUILD_TIMESTAMP = "build.timestamp";
	public static final String MINOR_VERSION_DESCRIPTION = "The release date of the version of the API you want to use. "
			+ "Specify dates in YYYY-MM-DD format.";
}
