/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.text.ParseException;
import java.util.Date;

/**
 * Manager for API version tracking.
 *
 */
public class VersionManager {
	private static final ThreadLocal<Date> threadVersion = new ThreadLocal<>();

	/**
	 * Returns version (date) of the current API
	 *
	 * @return date - Version being processed.
	 */
	public static Date getVersion() {
		return threadVersion.get();
	}

	/**
	 * Sets the version (date) of the current API
	 *
	 * @param version
	 *            - Version being processed.
	 * @throws ParseException
	 */
	static void setVersion(Date version) throws ParseException {
		threadVersion.set(version);
	}
}
